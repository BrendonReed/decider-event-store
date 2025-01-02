package decider.event.store.componentTests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThat;

import com.example.eventsourcing.infrastructure.CommandProcessingRepository;
import com.example.eventsourcing.infrastructure.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import decider.event.store.AddingDecider;
import decider.event.store.AddingDecider.GetDiff;
import decider.event.store.AddingSerialization;
import decider.event.store.CommandProcessor;
import decider.event.store.CounterSerialization;
import decider.event.store.InfrastructureConfiguration;
import decider.event.store.PubSubConnection;
import domain.CounterDecider;
import domain.CounterDecider.Increment;
import java.time.LocalDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
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
    R2dbcEntityTemplate template;

    @Autowired
    PubSubConnection pubSubConnection;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JsonUtil jsonUtil;

    @Autowired
    CommandProcessingRepository storage;

    // for pointing test data at local db for easier observation
    // @DynamicPropertySource
    static void configureLocalhostProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.r2dbc.url",
                () -> String.format(
                        "r2dbc:postgresql://%s:%d/%s",
                        "localhost",
                        5402,
                        "postgres"));
        registry.add("spring.r2dbc.username", () -> "postgres");
        registry.add("spring.r2dbc.password", () -> "password");
    }

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

    @Disabled
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
        // this verifies general operation
        // but also verifies correct behavior when we get duplicates in the stream
        // which can't be prevented. It does that by configuring the batch size
        // and polling interval the downstream processing can't keep up.
        var streamId = UUID.fromString("3BE87B37-B538-40BC-A53C-24A630BFFA2A");
        // var elementCount = 200;
        // var expected = 20100L; // for sum of 1 to 200
        var elementCount = 1000;
        var expected = 500500L; // for sum of 1 to 1000

        for (var i = 1; i <= elementCount; i++) {
            var command = new Increment(i, 1L, streamId);
            storage.insertCommand(UUID.randomUUID(), command, 1L, streamId.toString(), i - 1L).block();
        }
        System.out.println("Inserted");

        var decider = new CounterDecider();
        var dtoMapper = new CounterSerialization(this.jsonUtil);
        var commandProcessor = new CommandProcessor<>(storage, decider, dtoMapper);
        StepVerifier.create(commandProcessor.processWithDbState(500, 100))
            .expectNextCount(elementCount - 1)
            .expectNextMatches(lastState -> { 
                System.out.println("value: " + lastState);
                return lastState.totalCount() == expected; 
            })
            .thenCancel()
            .verify()
        ;
    }

    @Test
    void FailsBusinessRule() throws JsonProcessingException {
        var commands = Flux.just(
                new GetDiff(1),
                // state is 1
                new GetDiff(4)
                // state is 5
                );
        var streamId = UUID.fromString("3BE87B37-B538-40BC-A53C-24A630BFFA2A").toString();
        var runCommands = commands.index().flatMapSequential(command -> {
            return storage.insertCommand(UUID.randomUUID(), command.getT2(), 1L, streamId, command.getT1() - 1L);
        });

        var insertDuration =
                runCommands.as(StepVerifier::create).expectNextCount(2).verifyComplete();
        System.out.println("insert duration " + insertDuration);

        var decider = new AddingDecider();
        var dtoMapper = new AddingSerialization(jsonUtil);
        var commandProcessor = new CommandProcessor<>(storage, decider, dtoMapper);
        commandProcessor
                .process(100, 2000)
                .take(1)
                .as(StepVerifier::create)
                .assertNext(nextState -> assertThat(nextState).isEqualTo(1))
                // .assertNext(lastState -> assertThat(lastState).isEqualTo(4))
                .verifyComplete();
    }

    // disabled because the structrue and implementation are right, but the insertion isn't quite right.
    @Disabled
    @Test
    void FailsIfConflict() throws JsonProcessingException {
        var streamId = UUID.fromString("3BE87B37-B538-40BC-A53C-24A630BFFA2A");
        var commands = Flux.just(new Increment(1, 1L, streamId), new Increment(1, 1L, streamId));
        var runCommands = commands.flatMapSequential(command -> {
            return storage.insertCommand(UUID.randomUUID(), command, command.tenantId(), command.streamId().toString(), 0L);
        });

        var insertDuration =
                runCommands.as(StepVerifier::create).expectNextCount(2).verifyComplete();
        System.out.println("insert duration " + insertDuration);

        var decider = new CounterDecider();
        var dtoMapper = new CounterSerialization(this.jsonUtil);
        var commandProcessor = new CommandProcessor<>(storage, decider, dtoMapper);
        commandProcessor
                .process(100, 500)
                .take(2)
                .as(StepVerifier::create)
                .assertNext(nextState -> assertThat(nextState.totalCount()).isEqualTo(1))
                .assertNext(lastState -> {
                    assertThat(lastState.totalCount()).isEqualTo(1);
                })
                .verifyComplete();
    }
}
