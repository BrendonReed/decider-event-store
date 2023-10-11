package decider.event.store;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.UUID;
import reactor.core.publisher.Flux;

public class App {

    // create decider function (mutate): state -> command -> event list
    // create evolve function (apply): state -> event -> state
    // save in postgres
    // do subscription on event log to make view

    public static void main(String[] args) {
        var storage = new Storage("localhost", 5402, "postgres", "postgres", "password");

        var listener = storage.registerListener("event_updated").flatMap(x -> {
            String streamId = Utils.unsafeExtract(x.getParameter());
            // get stored events, materialize a view and store it
            return storage.getEventsForStream(UUID.fromString(streamId))
                    .map(ep -> {
                        return Decider.deserializeEvent(ep.eventType(), ep.transactionTime(), ep.payload());
                    })
                    .reduce(Decider.initialState(), Decider::evolve)
                    .map(s -> {
                        System.out.println("materialized state: " + s);
                        return s;
                    })
                    .flatMap(storage::saveState);
        });

        System.out.println("starting");
        // listener.subscribeOn(Schedulers.parallel());
        listener.subscribe();
        System.out.println("subscribed to pg listener");

        var events = new ArrayList<Event<?>>();
        var timestamp = Instant.now();
        try (Scanner in = new Scanner(System.in)) {
            // command generator:
            // 1) listens for mutations
            // 2) validates command structure (pure functions)
            // 3) validates against system state if needed, like uniqueness (impure)
            // 4) enhances data with context like time, caller data, maybe system data
            // 5) writes to command log
            var streamId = UUID.randomUUID();
            // command generator
            // listens for input
            // validates
            // checks business rules
            // creates command and appends to command log
            Flux<Command<?>> cliInput = Flux.generate(() -> 0, (state, sink) -> {
                System.out.println("Please enter a value");
                String y = in.nextLine();
                Integer asInt = Integer.parseInt(y); // validates
                var command = asInt >= 0
                        ? new Command<Decider.Increment>(timestamp, UUID.randomUUID(), new Decider.Increment(asInt))
                        : new Command<Decider.Decrement>(timestamp, UUID.randomUUID(), new Decider.Decrement(-asInt));

                sink.next(command);
                return state + asInt;
            });
            // command processor:
            // 1) listens for commands on command log
            // 2) processes command
            // 3) saves command in processed command lo
            // .   stores pointer into command log
            // .   success/failure
            // .   pointer into event log for most recent event after processing
            // 4) checks state after new events for validity
            // 5) saves events
            // 6) maybe calculates next state
            var main = cliInput.map(command -> {
                        // should load current state from storage?
                        var currentState = Utils.fold(Decider.initialState(), events, Decider::evolve);
                        var newEvents = Decider.decide(currentState, command);
                        // save newEvents
                        // verify this is legit?
                        var newState = Utils.fold(Decider.initialState(), events, Decider::evolve);
                        return newEvents;
                    })
                    .flatMap(newEvents -> {
                        events.addAll(newEvents);
                        return storage.saveEvents(newEvents, streamId);
                    });
            main.blockLast(Duration.ofMinutes(1));
        }

        System.out.println("final events: " + events);
        System.out.println("calc final state:" + Utils.fold(new Decider.CounterState(0), events, Decider::evolve));
    }
}
