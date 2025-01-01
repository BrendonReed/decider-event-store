package decider.event.store;

import static org.springframework.data.relational.core.query.Criteria.*;
import static org.springframework.data.relational.core.query.Query.*;

import decider.event.store.DbRecordTypes.CommandLog;
import decider.event.store.DbRecordTypes.EventLog;
import decider.event.store.DbRecordTypes.ProcessedCommand;
import io.r2dbc.postgresql.api.Notification;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import shared.SequentialUniqueIdObserver;

@Component
@Slf4j
public class CommandProcessingRepository {

    public final R2dbcEntityTemplate template;
    private final JsonUtil jsonUtil;

    @Autowired
    public CommandProcessingRepository(R2dbcEntityTemplate template, JsonUtil jsonUtil) {
        this.template = template;
        this.jsonUtil = jsonUtil;
    }

    public Flux<EventLog> getAllEvents() {
        return template.select(EventLog.class)
                .from("event_log")
                // .matching(query(where("stream_id").is(streamId)))
                .all();
    }

    private Flux<CommandLog> getCommands(int batchSize, long lastCommand) {
        var sql =
                """
            select command_log.*
            from command_log
            where command_log.id > :lastCommand
            order by command_log.id
            limit :batchSize
            """;
        return template.getDatabaseClient()
                .sql(sql)
                .bind("batchSize", batchSize)
                .bind("lastCommand", lastCommand)
                .map((row, metadata) -> {
                    CommandLog command = template.getConverter().read(CommandLog.class, row, metadata);
                    return command;
                })
                .all();
    }

    public Flux<CommandLog> getInfiniteStreamOfUnprocessedCommands2(
            Flux<Notification> sub, int batchSize, int pollIntervalMilliseconds) {

        var uniqueFilter = new SequentialUniqueIdObserver(0L);
        var r = Flux.defer(() -> {
            return getCommands(batchSize, uniqueFilter.max.get());
        });
        return r.filter(c -> uniqueFilter.isFirstInstance(c.id())).repeat();
    }

    public Flux<CommandLog> getInfiniteStreamOfUnprocessedCommands(
            Flux<Notification> sub, int batchSize, int pollIntervalMilliseconds) {

        var pollingInterval = Duration.ofMillis(pollIntervalMilliseconds);
        var uniqueFilter = new SequentialUniqueIdObserver(0L);
        var triggers = Flux.merge(Flux.interval(pollingInterval), sub);
        return getCommands(batchSize, uniqueFilter.max.get())
                .concatWith(triggers.onBackpressureDrop(data -> {
                            log.debug("dropping");
                        })
                        .concatMap(t -> getCommands(batchSize, uniqueFilter.max.get())))
                .doOnError(error -> {
                    // Log details when an error occurs
                    log.error("Error occurred: {}", error.getMessage());
                })
                .filter(c -> uniqueFilter.isFirstInstance(c.id()))
        // .retryWhen(Retry.backoff(3, Duration.ofMillis(1000)))
        ;
    }

    public Mono<EventLog> getConflictingEvents(UUID streamId, Long expectedEventId) {
        return template.select(EventLog.class)
                .from("event_log")
                .matching(query(where("stream_id").is(streamId).and("id").greaterThan(expectedEventId)))
                .one();
    }

    // TODO: add test to make sure the transaction works.
    @Transactional
    public <ED> Mono<ProcessedCommand> saveDtoRejectConflict(
            Long commandLogId, List<EventLog> events, UUID streamId, Long asOfRevisionId) {
        // because of the way stream is processed, it's possible to have duplicates
        // so it's important that this process is idempotent, so if the command
        // has already been processed, then just skip it.
        var conflicting = getConflictingEvents(streamId, asOfRevisionId);
        var saveFailedOnConflict = conflicting.flatMap(e -> {
            log.info("found conflict");
            return saveFailedCommand(commandLogId);
        });

        var saveEvents = Flux.fromIterable(events)
                .flatMapSequential(event -> {
                    return template.insert(event);
                })
                .reduce((maxObject, nextObject) -> {
                    if (nextObject.id() > maxObject.id()) {
                        return nextObject;
                    } else {
                        return maxObject;
                    }
                });
        var saveEventsAndCommand = saveEvents.flatMap(maxEvent -> {
            log.info("saving events from command: {} expecting: {}", commandLogId, asOfRevisionId);
            var pc = new ProcessedCommand(commandLogId, maxEvent.id(), "success");
            return template.insert(pc);
        });
        return saveFailedOnConflict.switchIfEmpty(saveEventsAndCommand);
    }

    public Mono<ProcessedCommand> saveFailedCommand(Long commandLogId) {
        return getLatestEventId().flatMap(eventId -> {
            var pc = new ProcessedCommand(commandLogId, eventId, "failure");
            return template.insert(pc);
        });
    }

    private Mono<Long> getLatestEventId() {
        var sql = "select max(id) max_id from event_log";

        return template.getDatabaseClient()
                .sql(sql)
                .map(row -> {
                    return row.get("max_id", Long.class);
                })
                .one();
    }

    public Flux<LocalDateTime> queryCurrentTime() {
        var sql = "select now() current_time";
        return template.getDatabaseClient()
                .sql(sql)
                .map(row -> {
                    return row.get("current_time", LocalDateTime.class);
                })
                .all();
    }

    public <T> Mono<CommandLog> insertCommand(
            UUID requestId, T payload, Long tenantId, UUID streamId, Long asOfRevision) {
        var jsonMeta = jsonUtil.toJson(payload);
        var cp = new CommandLog(
                null, requestId, tenantId, streamId, asOfRevision, jsonMeta.objectType(), jsonMeta.json());
        return template.insert(cp);
    }
}
