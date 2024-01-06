package decider.event.store.componentTests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import decider.event.store.AddingDecider;
import decider.event.store.AddingDecider.GetDiff;
import decider.event.store.CommandProcessor;
import decider.event.store.CounterDecider;
import decider.event.store.DeciderMapper;
import decider.event.store.Dtos;
import decider.event.store.InfrastructureConfiguration;
import decider.event.store.JsonUtil;
import decider.event.store.NoDtoMapper;
import decider.event.store.PubSubConnection;
import decider.event.store.Storage;
import java.time.LocalDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeEach;
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

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JsonUtil jsonUtil;

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

    @BeforeEach
    public void runFlywayMigrations() {
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
                .locations("filesystem:" + baseDir + "/" + relativePath)
                .cleanDisabled(false);
        var flyway = flywayConfig.load();
        flyway.clean();
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
            var command = new Dtos.IncrementDto(i);
            return storage.insertCommand(UUID.randomUUID(), command);
        });
        var insertDuration =
                commands.as(StepVerifier::create).expectNextCount(elementCount).verifyComplete();
        System.out.println("insert duration " + insertDuration);

        var decider = new CounterDecider();
        var dtoMapper = new DeciderMapper(jsonUtil);
        var commandProcessor = new CommandProcessor<>(storage, pubSubConnection, decider, dtoMapper);
        commandProcessor
                .process()
                .take(elementCount)
                .as(StepVerifier::create)
                .expectNextCount(199)
                .assertNext(lastState -> {
                    assertThat(lastState.totalCount()).isEqualTo(expected);
                })
                .verifyComplete();
    }

    @Test
    void FailsBusinessRule() throws JsonProcessingException {
        var commands = Flux.just(
                new GetDiff(1),
                // state is 1
                new GetDiff(4)
                // state is 5
                );
        var runCommands = commands.flatMapSequential(command -> {
            return storage.insertCommand(UUID.randomUUID(), command);
        });

        var insertDuration =
                runCommands.as(StepVerifier::create).expectNextCount(2).verifyComplete();
        System.out.println("insert duration " + insertDuration);

        var decider = new AddingDecider();
        var dtoMapper = new NoDtoMapper(jsonUtil);
        var commandProcessor = new CommandProcessor<>(storage, pubSubConnection, decider, dtoMapper);
        commandProcessor
                .process()
                .take(1)
                .as(StepVerifier::create)
                .assertNext(nextState -> assertThat(nextState).isEqualTo(1))
                // .assertNext(lastState -> assertThat(lastState).isEqualTo(4))
                .verifyComplete();
    }
}
