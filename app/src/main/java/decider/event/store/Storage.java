package decider.event.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.codec.Json;
import java.time.OffsetDateTime;
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

    public Mono<EventPersistance> saveEvent(EventPersistance event) {
        var template = new R2dbcEntityTemplate(connectionFactory);
        return template.insert(event);
    }

    public Mono<Event<?>> saveEvent(Event<?> event) {
        var template = new R2dbcEntityTemplate(connectionFactory);
        var ep = EventPersistance.fromEvent(event);
        return template.insert(ep).map(i -> event);
    }

    public Flux<Event<?>> saveEvents(List<Event<?>> events) {
        var r = Flux.fromIterable(events).flatMap(this::saveEvent);
        return r;
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

record EventPersistance(UUID id, OffsetDateTime transactionTime, String eventType, Json payload) {
    public static EventPersistance fromEvent(Event<?> event) {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        final ObjectWriter w = objectMapper.writer();
        try {
            byte[] json = w.writeValueAsBytes(event.data());
            var asx = Json.of(json);
            var eventType = event.data().getClass().getName();
            return new EventPersistance(UUID.randomUUID(), event.transactionTime(), eventType, asx);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new UnsupportedOperationException();
        }
    }
}
