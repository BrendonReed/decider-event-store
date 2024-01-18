package decider.event.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import decider.event.store.CounterReadModel.CounterEvent;
import decider.event.store.CounterReadModel.CounterState;
import decider.event.store.config.PubSubConnection;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
        // load initial state from view table
        // kick off main loop.
        var mapper = new CounterReadModelSerialization(jsonUtil, objectMapper);
        var rm = new CounterReadModel();
        var materializer =
                new EventMaterializer<CounterState, CounterEvent>(storage, pubSubConnection, mapper, rm);
        // var run = pubSubConnection.registerListener("event_updated").flatMap(x -> {
        //    String streamId = Utils.unsafeExtract(x.getParameter());
        //    // get stored events, materialize a view and store it
        //    return materializer.next(rm::apply);
        // });
        // run.blockLast(Duration.ofMinutes(4000));
        var run = materializer.process(rm.initialState());
        run.blockLast();
    }
}