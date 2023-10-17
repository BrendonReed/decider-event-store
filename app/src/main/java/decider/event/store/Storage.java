package decider.event.store;

import static org.springframework.data.relational.core.query.Criteria.*;
import static org.springframework.data.relational.core.query.Query.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.json.JsonMapper;
import decider.event.store.Decider.CounterState;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.api.Notification;
import io.r2dbc.postgresql.api.PostgresqlResult;
import io.r2dbc.postgresql.codec.Json;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class Storage {

    private PostgresqlConnectionFactory connectionFactory;

    public Storage(String host, int port, String database, String username, String password) {
        Map<String, String> options = new HashMap<>();
        options.put("lock_timeout", "10s");
        this.connectionFactory = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
                .host(host)
                .port(5402) // optional, defaults to 5432
                .username(username)
                .password(password)
                .database(database) // optional
                .options(options) // optional
                .build());
    }

    public Flux<Notification> registerListener(String channel) {
        return connectionFactory.create().flatMapMany(receiver -> {
            var listen = receiver.createStatement("LISTEN " + channel)
                    .execute()
                    .flatMap(PostgresqlResult::getRowsUpdated)
                    .thenMany(receiver.getNotifications());
            return listen;
        });
    }

    public Mono<EventPersistance> saveEvent(EventPersistance event) {
        var template = new R2dbcEntityTemplate(connectionFactory);
        return template.insert(event);
    }

    public Mono<CounterState> saveState(CounterState state) {
        var template = new R2dbcEntityTemplate(connectionFactory);
        return template.update(state);
    }

    public Mono<CounterState> getState() {
        var template = new R2dbcEntityTemplate(connectionFactory);
        return template.select(CounterState.class).from("counter_state").first();
    }

    public Mono<Event<?>> saveEvent(Event<?> event, UUID streamId) {
        var template = new R2dbcEntityTemplate(connectionFactory);
        var ep = EventPersistance.fromEvent(event, streamId);
        return template.insert(ep).map(i -> event);
    }

    public Flux<Event<?>> saveEvents(List<Event<?>> events, UUID streamId) {
        var r = Flux.fromIterable(events).flatMap(x -> this.saveEvent(x, streamId));
        return r;
    }

    public Flux<EventPersistance> getEventsForStream(UUID streamId) {
        var template = new R2dbcEntityTemplate(connectionFactory);
        return template.select(EventPersistance.class)
                .from("event_persistance")
                .matching(query(where("stream_id").is(streamId)))
                .all();
    }

    public Flux<EventPersistance> getLatestEvents(Long latestEvent) {
        var template = new R2dbcEntityTemplate(connectionFactory);
        return template.select(EventPersistance.class)
                .from("event_persistance")
                .matching(query(where("event_id").greaterThan(latestEvent)))
                .all();
    }

    public Flux<String> queryCurrentTime() {
        var r = connectionFactory.create().flatMapMany(connection -> {
            return connection
                    .createStatement("select now() transaction_time")
                    .execute()
                    .flatMap(it -> it.map((row, rowMetadata) -> {
                        return row.get("transaction_time", String.class);
                    }));
        });
        return r;
    }

    public Flux<String> queryCurrentTimeBlocking() {
        // the block can be useful for experimenting
        // but in practice, it should be composed in the main reactive flow
        // and subscribed/blocked at the root in one place
        var connection = connectionFactory.create().block();
        return connection
                .createStatement("select now() transaction_time")
                .execute()
                .flatMap(it -> it.map((row, rowMetadata) -> {
                    return row.get("transaction_time", String.class);
                }));
    }
}

record EventPersistance(Long eventId, UUID streamId, Instant transactionTime, String eventType, Json payload) {
    public static EventPersistance fromEvent(Event<?> event, UUID streamId) {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        final ObjectWriter w = objectMapper.writer();
        try {
            byte[] json = w.writeValueAsBytes(event.data());
            var asx = Json.of(json);
            var eventType = event.data().getClass().getName();
            return new EventPersistance(null, streamId, event.transactionTime(), eventType, asx);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new UnsupportedOperationException();
        }
    }
}
