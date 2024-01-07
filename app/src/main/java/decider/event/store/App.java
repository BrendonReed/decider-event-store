package decider.event.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Scanner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import reactor.core.publisher.Flux;

@SpringBootApplication
@EnableTransactionManagement
@Slf4j
@Profile("!test")
public class App implements CommandLineRunner {

    // create decider function (mutate): state -> command -> event list
    // create evolve function (apply): state -> event -> state
    // save in postgres
    // do subscription on event log to make view
    @Autowired
    private Storage storage;

    @Autowired
    private PubSubConnection pubSubConnection;

    @Autowired
    public ObjectMapper objectMapper;

    @Autowired
    public JsonUtil jsonUtil;

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        var decider = new CounterDecider();
        var dtoMapper = new DeciderMapper(jsonUtil, objectMapper);
        var commandProcessor = new CommandProcessor<>(storage, pubSubConnection, decider, dtoMapper);
        var run = commandProcessor.process();
        run.blockLast(Duration.ofMinutes(400));
    }

    public void cliInput() {
        log.info("starting");

        var timestamp = Instant.now();
        try (Scanner in = new Scanner(System.in)) {
            // command generator:
            // 1) listens for mutations
            // 2) validates command structure (pure functions)
            // 3) validates against system state if needed, like uniqueness (impure)
            // 4) enhances data with context like time, caller data, maybe system data
            // 5) writes to command log
            // command generator
            // listens for input
            // validates
            // checks business rules
            // creates command and appends to command log
            Flux<Integer> cliInput = Flux.generate(() -> 1L, (state, sink) -> {
                System.out.println("Please enter a value");
                String y = in.nextLine();
                Integer asInt = Integer.parseInt(y); // validates
                var command = asInt;
                sink.next(command);
                return state + 1;
            });
        }
    }
}
