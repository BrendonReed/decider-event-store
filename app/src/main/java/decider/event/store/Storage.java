package decider.event.store;

import static org.springframework.data.relational.core.query.Criteria.*;
import static org.springframework.data.relational.core.query.Query.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.r2dbc.postgresql.api.Notification;
import io.r2dbc.postgresql.codec.Json;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.Id;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class Storage {

    public final R2dbcEntityTemplate template;
    public final ObjectMapper objectMapper;
    public final ObjectWriter objectWriter;

    @Autowired
    public Storage(R2dbcEntityTemplate template, ObjectMapper objectMapper) {
        this.template = template;
        this.objectMapper = objectMapper;
        this.objectWriter = objectMapper.writer();
    }

    public Flux<? extends Event<?>> getEventsForStream2(UUID streamId) {
        return template.select(EventLog.class)
                .from("event_log")
                .matching(query(where("stream_id").is(streamId)))
                .all()
                .map(this::toEvent);
    }

    public Flux<EventLog> getEventsForStream(UUID streamId) {
        return template.select(EventLog.class)
                .from("event_log")
                .matching(query(where("stream_id").is(streamId)))
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

    public Flux<? extends Command<?>> getInifiteStreamOfUnprocessedCommands(Flux<Notification> sub) {

        var batchSize = 100;
        var pollingInterval = Duration.ofSeconds(2);
        var triggers = Flux.merge(Flux.interval(pollingInterval), sub);
        var r = getCommands(batchSize)
                        .concatWith(triggers.onBackpressureDrop(data -> {
                                    log.debug("dropping");
                                })
                                .concatMap(t -> getCommands(batchSize)))
                        .doOnError(error -> {
                            // Log details when an error occurs
                            System.out.println("Error occurred: " + error.getMessage());
                        })
                        .map(commandDto -> {
                            var command = deserializeCommand(commandDto);
                            return command;
                        })
                // .retryWhen(Retry.backoff(3, Duration.ofMillis(1000)))
                ;
        return r;
    }

    private EventLog serializeEvent(Event<?> event, UUID streamId) {
        try {
            byte[] json = objectWriter.writeValueAsBytes(event.data());
            var asx = Json.of(json);
            var eventType = event.data().getClass().getName();
            return new EventLog(null, streamId, eventType, asx);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new UnsupportedOperationException();
        }
    }
    // TODO: add test to make sure the transaction works.
    @Transactional
    public Mono<ProcessedCommand> save(Long commandLogId, List<Event<?>> events, UUID streamId) {
        // because of the way stream is processed, it's possible to have duplicates
        // so it's important that this process is idempotent, so if the command
        // has already been processed, then just skip it.
        var existing = template.select(ProcessedCommand.class)
                .from("processed_command")
                .matching(query(where("command_id").is(commandLogId)))
                .one();

        var saveEvents = Flux.fromIterable(events)
                .flatMapSequential(event -> {
                    var ep = serializeEvent(event, streamId);
                    return template.insert(ep);
                })
                .map(e -> e)
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

    public Flux<? extends Event<?>> getLatestEvents(Long latestEvent) {
        List<Criteria> criteriaList = new ArrayList<>();

        criteriaList.add(where("id").greaterThan(latestEvent));
        Criteria combinedCriteria = criteriaList.stream().reduce(Criteria.empty(), Criteria::and, Criteria::and);
        return template.select(EventLog.class)
                .from("event_log")
                .matching(query(combinedCriteria))
                .all()
                .map(this::toEvent);
    }

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
        try {
            byte[] json = objectWriter.writeValueAsBytes(payload);
            var commandType = payload.getClass().getName();
            var commandJson = Json.of(json);
            var cp = new CommandLog(null, requestId, commandType, commandJson);
            return template.insert(cp);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new UnsupportedOperationException();
        }
    }

    Event<?> toEvent(EventLog ep) {
        try {
            // TODO: for event versioning future preparation, this should
            // probably do explicit weak typed mapping. Not sure if Jackson has
            // some support for that...
            var data = objectMapper.readValue(ep.payload().asString(), Class.forName(ep.eventType()));
            return new Event<>(data);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new UnsupportedOperationException();
        } catch (ClassNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    Command<?> deserializeCommand(CommandLog commandDto) {

        try {
            // TODO: for event versioning future preparation, this should
            // probably do explicit weak typed mapping. Not sure if Jackson has
            // some support for that...
            var data = objectMapper.readValue(commandDto.command().asString(), Class.forName(commandDto.commandType()));
            return new Command<>(commandDto.id(), commandDto.requestId(), data);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new UnsupportedOperationException();
        } catch (ClassNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }
}

record EventLog(@Id Long id, UUID streamId, String eventType, Json payload) {}

record CommandLog(@Id Long id, UUID requestId, String commandType, Json command) {}

record ProcessedCommand(Long commandId, Long eventLogId, String disposition) {}
