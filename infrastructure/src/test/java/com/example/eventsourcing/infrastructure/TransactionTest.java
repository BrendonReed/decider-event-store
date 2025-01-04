package com.example.eventsourcing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThat;

import com.example.eventsourcing.infrastructure.AddingDecider.GetDiff;
import com.example.eventsourcing.infrastructure.CounterDecider.Increment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
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
    ObjectMapper objectMapper;

    @Autowired
    JsonUtil jsonUtil;

    @Autowired
    CommandProcessingRepository storage;

    // for pointing test data at local db for easier observation
    // @DynamicPropertySource
    static void configureLocalhostProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.r2dbc.url", () -> String.format("r2dbc:postgresql://%s:%d/%s", "localhost", 5402, "postgres"));
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
            storage.insertCommand(UUID.randomUUID(), command, 1L, streamId.toString(), i - 1L)
                    .block();
        }

        var decider = new CounterDecider();
        var dtoMapper = new CounterSerialization(this.jsonUtil);
        var commandProcessor = new CommandProcessor<>(storage, decider, dtoMapper);
        StepVerifier.create(commandProcessor.processWithDbState(500, 100))
                .expectNextCount(elementCount - 1)
                .expectNextMatches(lastState -> {
                    return lastState.totalCount() == expected;
                })
                .thenCancel()
                .verify();
    }

    @Test
    void FailsBusinessRule() throws JsonProcessingException {
        var commands = List.of(
                // state is 0
                new GetDiff(1),
                // emits 1 (0 + 1 = 1)
                // state is 1
                new GetDiff(4)
                // emits a 3 (1 + 3 = 4)
                // state is 4 -- fails because rule is must always be odd
                );
        var asOf = 0L;
        for (var command : commands) {
            storage.insertCommand(
                            UUID.randomUUID(),
                            command,
                            command.tenantId(),
                            command.streamId().toString(),
                            asOf)
                    .block();
            asOf += 1;
        }

        var decider = new AddingDecider();
        var dtoMapper = new AddingSerialization(jsonUtil);
        var commandProcessor = new CommandProcessor<>(storage, decider, dtoMapper);
        commandProcessor
                .process(100, 2000)
                .as(StepVerifier::create)
                .expectNextMatches(nextState -> {
                    return nextState == 1;
                })
                .expectNextMatches(nextState -> {
                    // still 1 because the command failed
                    return nextState == 1;
                })
                .thenCancel()
                .verify();
    }

    @Test
    void FailsIfConflict() throws JsonProcessingException {
        var commands = List.of(
                // state is 0
                new GetDiff(1),
                // emits 1 (0 + 1 = 1)
                // state is 1
                new GetDiff(5)
                // emits 4 (5 - 1 = 4)
                // state is 5, but fails because of an optimistic locking conflict
                );
        var asOf = 0L; // 0 in both commands to force conflict
        for (var command : commands) {
            storage.insertCommand(
                            UUID.randomUUID(),
                            command,
                            command.tenantId(),
                            command.streamId().toString(),
                            asOf)
                    .block();
        }

        var thirdCommand = new GetDiff(3);
        // emits 2 (3 - 1 = 2)
        // state is 3
        storage.insertCommand(
                        UUID.randomUUID(),
                        thirdCommand,
                        thirdCommand.tenantId(),
                        thirdCommand.streamId().toString(),
                        1L)
                .block();

        // second command should fail because of optimistic locking.
        // it expects a state as of revision id 0, but another event will have been there
        // so reject it as the current state is different than it was when the command was issued
        var decider = new AddingDecider();
        var dtoMapper = new AddingSerialization(this.jsonUtil);
        var commandProcessor = new CommandProcessor<>(storage, decider, dtoMapper);
        commandProcessor
                .process(100, 500)
                .as(StepVerifier::create)
                .assertNext(nextState -> assertThat(nextState).isEqualTo(1))
                .assertNext(nextState -> {
                    // this is a little weird, and could be different. When it fails,
                    // it still returns the new state generated. This doesn't get saved,
                    // but still shows up in the stream.
                    assertThat(nextState).isEqualTo(5);
                })
                .assertNext(nextState -> {
                    // so to verify the conflict didn't get save, check state
                    // on the next command
                    assertThat(nextState).isEqualTo(3);
                })
                .thenCancel()
                .verify();
    }
}
