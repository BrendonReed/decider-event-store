package decider.event.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import decider.event.store.config.PubSubConnection;
import domain.CounterDecider.CounterEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
@Slf4j
@Profile("!test")
public class App implements CommandLineRunner {

    // create decider function (mutate): state -> command -> event list
    // create evolve function (apply): state -> event -> state
    // save in postgres
    // do subscription on event log to make view

    private Storage storage;
    private PubSubConnection pubSubConnection;
    private ObjectMapper objectMapper;
    private JsonUtil jsonUtil;
    private CounterStatePersistence statePersistence;

    public App(
            Storage storage,
            PubSubConnection pubSubConnection,
            ObjectMapper objectMapper,
            JsonUtil jsonUtil,
            CounterStatePersistence statePersistence) {
        this.storage = storage;
        this.pubSubConnection = pubSubConnection;
        this.objectMapper = objectMapper;
        this.jsonUtil = jsonUtil;
        this.statePersistence = statePersistence;
    }

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // load initial state from view table
        // kick off main loop.
        var mapper = new CounterReadModelSerialization(jsonUtil, objectMapper);
        var materializer =
                new EventMaterializer<CounterState, CounterEvent>(storage, pubSubConnection, mapper, statePersistence);
        var run = materializer.process(
                statePersistence.getState(), statePersistence.getCheckpoint(), CounterState::apply);
        run.blockLast();
    }
}
