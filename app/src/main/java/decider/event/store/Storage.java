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

@Component
@Slf4j
public class Storage {

    public final R2dbcEntityTemplate template;
    private final JsonUtil jsonUtil;

    @Autowired
    public Storage(R2dbcEntityTemplate template, JsonUtil jsonUtil) {
        this.template = template;
        this.jsonUtil = jsonUtil;
    }

    public Flux<EventLog> getEventsForStream(UUID streamId) {
        return template.select(EventLog.class)
                .from("event_log")
                // .matching(query(where("stream_id").is(streamId)))
                .all();
    }

    public Flux<CommandLog> getCommands(int batchSize) {
        var sql =
                """
            select command_log.*
            from command_log
            where not exists (
                select *
                from processed_command
                where command_id = command_log.id)
            order by command_log.id
            limit :batchSize
            """;
        return template.getDatabaseClient()
                .sql(sql)
                .bind("batchSize", batchSize)
                .map((row, metadata) -> {
                    CommandLog command = template.getConverter().read(CommandLog.class, row, metadata);
                    return command;
                })
                .all();
    }

    public Flux<CommandLog> getInifiteStreamOfUnprocessedCommands(Flux<Notification> sub) {

        var batchSize = 100;
        var pollingInterval = Duration.ofSeconds(2);
        var triggers = Flux.merge(Flux.interval(pollingInterval), sub);
        return getCommands(batchSize)
                .concatWith(triggers.onBackpressureDrop(data -> {
                            log.debug("dropping");
                        })
                        .concatMap(t -> getCommands(batchSize)))
                .doOnError(error -> {
                    // Log details when an error occurs
                    log.error("Error occurred: {}", error.getMessage());
                })
        // .retryWhen(Retry.backoff(3, Duration.ofMillis(1000)))
        ;
    }

    // TODO: add test to make sure the transaction works.
    @Transactional
    public <ED> Mono<ProcessedCommand> saveDto(Long commandLogId, List<EventLog> events, UUID streamId) {
        // because of the way stream is processed, it's possible to have duplicates
        // so it's important that this process is idempotent, so if the command
        // has already been processed, then just skip it.
        var existing = template.select(ProcessedCommand.class)
                .from("processed_command")
                .matching(query(where("command_id").is(commandLogId)))
                .one();

        var saveEvents = Flux.fromIterable(events)
                .flatMapSequential(event -> {
                    var el = event;
                    return template.insert(el);
                })
                .reduce((maxObject, nextObject) -> {
                    if (nextObject.id() > maxObject.id()) {
                        return nextObject;
                    } else {
                        return maxObject;
                    }
                });
        return existing.switchIfEmpty(saveEvents.flatMap(maxEvent -> {
            var pc = new ProcessedCommand(commandLogId, maxEvent.id(), "success");
            return template.insert(pc);
        }));
    }

    public Mono<ProcessedCommand> saveFailedCommand(Long commandLogId) {
        return getLatestEventId().flatMap(eventId -> {
            var pc = new ProcessedCommand(commandLogId, eventId, "failure");
            return template.insert(pc);
        });
    }

    // public Flux<? extends Event<?>> getLatestEvents(Long latestEvent) {
    //     List<Criteria> criteriaList = new ArrayList<>();

    //     criteriaList.add(where("id").greaterThan(latestEvent));
    //     Criteria combinedCriteria = criteriaList.stream().reduce(Criteria.empty(), Criteria::and, Criteria::and);
    //     return template.select(EventLog.class)
    //             .from("event_log")
    //             .matching(query(combinedCriteria))
    //             .all()
    //             .map(this::toEvent);
    // }

    public Mono<Long> getLatestEventId() {
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

    public <T> Mono<CommandLog> insertCommand(UUID requestId, T payload) {
        var jsonMeta = jsonUtil.toJson(payload);
        var cp = new CommandLog(null, requestId, jsonMeta.objectType(), jsonMeta.json());
        return template.insert(cp);
    }
}
