/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package decider.event.store;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import decider.event.store.CounterDecider.Increment;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlResult;
import io.r2dbc.postgresql.codec.Json;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class Sequence {
    private Long counter;

    public Sequence() {
        counter = 0L;
    }

    public Long get() {
        counter = counter + 1;
        return counter;
    }
}

class DbSandbox {

    PostgresqlConnectionFactory connectionFactory() {

        Map<String, String> options = new HashMap<>();
        options.put("lock_timeout", "10s");

        PostgresqlConnectionFactory connectionFactory =
                new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
                        .host("localhost")
                        .port(5402) // optional, defaults to 5432
                        .username("postgres")
                        .password("password")
                        .database("postgres") // optional
                        .options(options) // optional
                        .build());
        return connectionFactory;
    }

    @Disabled
    @Test
    public void DbQuery() {
        var connection = connectionFactory().create().block();
        connection
                .createStatement("select now() transaction_time")
                .execute()
                .flatMap(it -> it.map((row, rowMetadata) -> {
                    return row.get("transaction_time", String.class);
                }))
                .as(StepVerifier::create)
                .expectNext("read uncommitted")
                .verifyComplete();
    }

    private static Flux<PostgresqlResult> insertRows(PostgresqlConnection connection, Sequence seq, int rowsToInsert) {
        var sql = "INSERT INTO command_log (request_id, command_type, command) "
                + "values('%s', 'decider.event.store.CounterDecider$Increment', "
                + "('%s')::jsonb)";

        return Flux.range(1, rowsToInsert).flatMapSequential(i -> {
            return connection
                    .createStatement(String.format(sql, UUID.randomUUID(), "{\"amount\": " + seq.get() + "}"))
                    .execute();
        });
    }

    @Test
    public void GenerateCommands() {
        var cf = connectionFactory();
        var connection = cf.create().block();
        var seq = new Sequence();
        Random random = new Random();
        // random to be more like real life and to have a wider range of burst
        // int rowsToInsert = random.nextInt(91) + 10; // Generate random rows between 10 and 80
        // or constant for less variability
        var rowsToInsert = 20;
        for (int i = 1; i < 30; i++) { // Change the iteration limit as needed
            try {
                Thread.sleep(1000);
                insertRows(connection, seq, rowsToInsert).blockLast();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } // Sleep for one second (handle InterruptedException)
        }
    }
}

/*
 * This isn't really a test, just more like a place to try things out.
 * Useful for development, but could be a maintenance burden to keep around after
 * things are figured out.
 */
record EventV2(Long amount, Long amount2) {}

class Sandbox {
    @Test
    public void weakTypeDeserialize() {
        // looks like this will default values that aren't in the json,
        // but unexpected values in the json will cause a JsonMappingException,
        // unless configured to not fail on unknown properties.
        var jsonString = "{\"amount\": 5, \"amount3\": 7}";
        var jsonPayload = Json.of(jsonString);
        ObjectMapper objectMapper = JsonMapper.builder().build();
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        try {
            System.out.println("deserialized: before");
            var event2 = objectMapper.readValue(jsonPayload.asString(), EventV2.class);
            System.out.println("deserialized: " + event2);
        } catch (JsonMappingException e) {
            // TODO Auto-generated catch block
            System.out.println("JsonMappingException");
            e.printStackTrace();
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            System.out.println("JsonProcessingException");
            e.printStackTrace();
        }
    }

    @Test
    public void deserializeEvent() {
        var className = "decider.event.store.Decider$Decrement";
        var jsonString = "{\"amount\": 5}";
        var jsonPayload = Json.of(jsonString);
        ObjectMapper objectMapper = JsonMapper.builder().build();
        try {
            var incrememnt1 = objectMapper.readValue(jsonPayload.asString(), Increment.class);
            System.out.println("deserialized: " + incrememnt1);
            var incrememnt2 = objectMapper.readValue(jsonPayload.asString(), Class.forName(className));
            System.out.println("deserialized: " + incrememnt2);
        } catch (JsonMappingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}

class AppTest {
    @Test
    void catchInScan() {
        Flux<Integer> source = Flux.just(1, 2, 3, 4);

        source.scan((acc, value) -> {
                    try {
                        if (value == 3) {
                            throw new RuntimeException("Exception occurred at value 3");
                        }
                        return acc + value;
                    } catch (RuntimeException e) {
                        // Handle the exception and return a default value to continue
                        return acc + 0; // Return a default value (0 in this case)
                    }
                })
                .subscribe(System.out::println, throwable -> System.err.println("Error: " + throwable.getMessage()));
    }
}
