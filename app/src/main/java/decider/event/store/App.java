package decider.event.store;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.UUID;
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
    private CommandProcessor commandProcessor;

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Override
    public void run(String... args) throws Exception {

        var decider = new CounterDecider();
        var run = commandProcessor.process(decider);
        run.blockLast(Duration.ofMinutes(400));
    }

    public void eventMaterializer() {
        var streamId = UUID.fromString("4498a039-ce94-49b2-aff9-3ca12a8623d5");

        var decider = new CounterDecider();
        var materializer = new EventMaterializer<CounterState>(storage, decider.initialState(streamId));

        var listener = pubSubConnection.registerListener("event_updated").flatMap(x -> {
            String eventStreamId = Utils.unsafeExtract(x.getParameter());
            // get stored events, materialize a view and store it
            return materializer.next(decider::apply);
        });
        // listener.subscribeOn(Schedulers.parallel());
        listener.subscribe();
        log.debug("subscribed to pg listener");
    }

    public void cliInput() {
        log.info("starting");
        var decider = new CounterDecider();
        var streamId = UUID.fromString("4498a039-ce94-49b2-aff9-3ca12a8623d5");

        var events = new ArrayList<Event<?>>();
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
            Flux<Command<?>> cliInput = Flux.generate(() -> 1L, (state, sink) -> {
                System.out.println("Please enter a value");
                String y = in.nextLine();
                Integer asInt = Integer.parseInt(y); // validates
                var command = asInt >= 0
                        ? new Command<CounterDecider.Increment>(
                                state, UUID.randomUUID(), new CounterDecider.Increment(asInt))
                        : new Command<CounterDecider.Decrement>(
                                state, UUID.randomUUID(), new CounterDecider.Decrement(-asInt));

                sink.next(command);
                return state + 1;
            });
        }
    }
}
