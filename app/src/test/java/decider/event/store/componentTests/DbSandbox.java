package decider.event.store.componentTests;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlResult;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/*
 * This isn't really a test, just more like a place to try things out.
 * Useful for development, but could be a maintenance burden to keep around after
 * things are figured out.
 */
public class DbSandbox {
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
                    .createStatement(String.format(sql, UUID.randomUUID(), "{\"amount\": " + seq.get() + ", \"streamId\": \"3BE87B37-B538-40BC-A53C-24A630BFFA2A\", \"tenantId\": 1 }"))
                    .execute();
        });
    }

    @Disabled
    @Test
    public void GenerateCommands() {
        var cf = connectionFactory();
        var connection = cf.create().block();
        var seq = new Sequence();
        Random random = new Random();
        // random to be more like real life and to have a wider range of burst
        // int rowsToInsert = random.nextInt(91) + 10; // Generate random rows between 10 and 80
        // or constant for less variability
        var rowsToInsert = 100;
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
