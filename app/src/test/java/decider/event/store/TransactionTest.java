package decider.event.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.LocalDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ActiveProfiles("test")
@Testcontainers
@SpringBootTest(classes = InfrastructureConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class TransactionTest {

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    Storage storage;

    @Autowired
    PubSubConnection pubSubConnection;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.r2dbc.url",
                () -> String.format(
                        "r2dbc:postgresql://%s:%d/%s",
                        postgresContainer.getHost(),
                        postgresContainer.getFirstMappedPort(),
                        postgresContainer.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgresContainer::getUsername);
        registry.add("spring.r2dbc.password", postgresContainer::getPassword);
    }

    @BeforeAll
    public static void runFlywayMigrations() {
        // postgresContainer.start(); // Ensure the container is started explicitly

        // Obtain the base directory of the project
        String baseDir = System.getProperty("user.dir");

        // Construct the relative path to the migration scripts
        String relativePath = "../db/migrations";
        FluentConfiguration flywayConfig = Flyway.configure()
                .dataSource(
                        postgresContainer.getJdbcUrl(),
                        postgresContainer.getUsername(),
                        postgresContainer.getPassword())
                .locations("filesystem:" + baseDir + "/" + relativePath);
        var flyway = flywayConfig.load();
        flyway.migrate();
    }

    @Test
    void ice() {
        storage.queryCurrentTime()
                .as(StepVerifier::create)
                .expectNextMatches(currentTime -> {
                    System.out.println("time: " + currentTime);
                    return currentTime != null && currentTime.isBefore(LocalDateTime.now());
                })
                .verifyComplete();
    }

    @Test
    void ArithmeticSequence() throws JsonProcessingException {
        // var expected = 500501L; // for sum of 1 to 1000
        var elementCount = 200;
        var expected = 20100L; // for sum of 1 to 200
        var commands = Flux.range(1, elementCount).flatMapSequential(i -> {
            var command = new CounterDecider.Increment(i);
            return storage.insertCommand(UUID.randomUUID(), command);
        });
        var insertDuration =
                commands.as(StepVerifier::create).expectNextCount(elementCount).verifyComplete();
        System.out.println("insert duration " + insertDuration);

        var commandProcessor = new CommandProcessor(storage, pubSubConnection);
        var decider = new CounterDecider();
        commandProcessor
                .process(decider)
                .take(elementCount)
                .as(StepVerifier::create)
                .expectNextCount(199)
                .assertNext(lastState -> {
                    assertThat(lastState.totalCount()).isEqualTo(expected);
                })
                .verifyComplete();
    }
}
