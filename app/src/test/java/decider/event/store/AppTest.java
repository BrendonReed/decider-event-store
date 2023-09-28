/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package decider.event.store;

import static org.junit.jupiter.api.Assertions.*;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class DbSandbox {

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

class AppTest {
    @Test
    void appHasAGreeting() {
        App classUnderTest = new App();
        assertNotNull(classUnderTest, "app should have a greeting");
    }
}
