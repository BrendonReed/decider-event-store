package decider.event.store;

import static org.springframework.data.relational.core.query.Criteria.*;
import static org.springframework.data.relational.core.query.Query.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.r2dbc.postgresql.api.Notification;
import io.r2dbc.postgresql.codec.Json;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.Id;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class Storage {

    public final R2dbcEntityTemplate template;

    @Autowired
    public Storage(R2dbcEntityTemplate template) {
        this.template = template;
    }

    public Mono<EventPersistance> saveEvent(Event<?> event, UUID streamId) {
        var ep = EventPersistance.fromEvent(event, streamId);
        return template.insert(ep);
    }

    public Flux<EventPersistance> getEventsForStream(UUID streamId) {
        return template.select(EventPersistance.class)
                .from("event_persistance")
                .matching(query(where("stream_id").is(streamId)))
                .all();
    }

    public Flux<CommandPersistance> getCommands(int batchSize) {
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
                    CommandPersistance command = template.getConverter().read(CommandPersistance.class, row, metadata);
                    return command;
                })
                .all();
    }

    public Flux<CommandPersistance> getInifiteStreamOfUnprocessedCommands(Flux<Notification> sub) {

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
                    System.out.println("Error occurred: " + error.getMessage());
                })
        // .retryWhen(Retry.backoff(3, Duration.ofMillis(1000)))
        ;
    }

    // TODO: add test to make sure the transaction works.
    @Transactional
    public Mono<ProcessedCommand> save(CommandPersistance command, List<Event<?>> events, UUID streamId) {
        // because of the way stream is processed, it's possible to have duplicates
        // so it's important that this process is idempotent, so if the command
        // has already been processed, then just skip it.
        var existing = template.select(ProcessedCommand.class)
                .from("processed_command")
                .matching(query(where("command_id").is(command.id())))
                .one();

        var saveEvents = Flux.fromIterable(events)
                .flatMapSequential(event -> saveEvent(event, streamId))
                .map(e -> e)
                .reduce((maxObject, nextObject) -> {
                    if (nextObject.eventId() > maxObject.eventId()) {
                        return nextObject;
                    } else {
                        return maxObject;
                    }
                });
        return existing.switchIfEmpty(saveEvents.flatMap(maxEvent -> {
            var pc = new ProcessedCommand(command.id(), maxEvent.eventId(), "success");
            return template.insert(pc);
        }));
    }

    public Flux<EventPersistance> getLatestEvents(Long latestEvent) {
        return template.select(EventPersistance.class)
                .from("event_persistance")
                .matching(query(where("event_id").greaterThan(latestEvent)))
                .all();
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

    public <T> Mono<CommandPersistance> insertCommand(UUID requestId, T payload) {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        final ObjectWriter w = objectMapper.writer();
        try {
            byte[] json = w.writeValueAsBytes(payload);
            var commandType = payload.getClass().getName();
            var commandJson = Json.of(json);
            var cp = new CommandPersistance(null, requestId, commandType, commandJson);
            return template.insert(cp);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new UnsupportedOperationException();
        }
    }

    static Event<?> toEvent(EventPersistance ep) {
        return deserializeEvent(ep.eventType(), ep.payload());
    }

    static Event<?> deserializeEvent(String eventType, Json jsonPayload) {

        ObjectMapper objectMapper = JsonMapper.builder().build();
        try {
            // TODO: for event versioning future preparation, this should
            // probably do explicit weak typed mapping. Not sure if Jackson has
            // some support for that...
            var data = objectMapper.readValue(jsonPayload.asString(), Class.forName(eventType));
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

    static Command<?> deserializeCommand(String commandType, UUID requestId, Json jsonPayload) {

        ObjectMapper objectMapper = JsonMapper.builder().build();
        try {
            // TODO: for event versioning future preparation, this should
            // probably do explicit weak typed mapping. Not sure if Jackson has
            // some support for that...
            var data = objectMapper.readValue(jsonPayload.asString(), Class.forName(commandType));
            return new Command<>(requestId, data);
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

record EventPersistance(@Id Long eventId, UUID streamId, String eventType, Json payload) {
    public static EventPersistance fromEvent(Event<?> event, UUID streamId) {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        final ObjectWriter w = objectMapper.writer();
        try {
            byte[] json = w.writeValueAsBytes(event.data());
            var asx = Json.of(json);
            var eventType = event.data().getClass().getName();
            return new EventPersistance(null, streamId, eventType, asx);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new UnsupportedOperationException();
        }
    }
}

@Table("command_log")
record CommandPersistance(@Id Long id, UUID requestId, String commandType, Json command) {}

record ProcessedCommand(Long commandId, Long eventId, String disposition) {}
