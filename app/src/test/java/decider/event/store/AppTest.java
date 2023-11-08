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
import decider.event.store.Decider.Increment;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.codec.Json;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class DbSandbox {

    @Disabled
    @Test
    public void DbQuery() {
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
        var connection = connectionFactory.create().block();
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

        var e = Storage.deserializeEvent(className, Instant.now(), jsonPayload);
        System.out.println("and event: " + e);
    }
}

class AppTest {
    @Test
    void appHasAGreeting() {
        App classUnderTest = new App();
        assertNotNull(classUnderTest, "app should have a greeting");
    }
}
