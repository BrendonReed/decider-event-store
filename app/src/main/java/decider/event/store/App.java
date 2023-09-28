package decider.event.store;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class App {

    // create decider function (mutate): state -> command -> event list
    // create evolve function (apply): state -> event -> state
    // save in postgres
    // do subscription on event log to make view

    public static void main(String[] args) {
        var storage = new Storage("localhost", 
            5402,
            "postgres",
            "postgres",
            "password");
        storage.queryCurrentTime().subscribe(System.out::println);
        System.out.println("starting!");

        var events = new ArrayList<Event<?>>();
        var timestamp = OffsetDateTime.now();

        // command generator:
        // 1) listens for mutations
        // 2) validates command structure (pure functions)
        // 3) validates against system state if needed, like uniqueness (impure)
        // 4) enhances data with context like time, caller data, maybe system data
        // 5) writes to command log
        var commandLog = List.of(
            new Command<Decider.Increment>(timestamp, new Decider.Increment(1)),
            new Command<Decider.Increment>(timestamp, new Decider.Increment(1)),
            new Command<Decider.Increment>(timestamp, new Decider.Increment(1)),
            new Command<Decider.Decrement>(timestamp, new Decider.Decrement(1))
            );

        // command processor:
        // 1) listens for commands on command log
        // 2) processes command
        // 3) checks state after new events for validity
        // 4) saves events
        // 5) maybe calculates next state
        for (Command<?> command : commandLog) {
            var currentState = Utils.fold(Decider.initialState(), events, Decider::evolve);
            var newEvents = Decider.decide(currentState, command);
            // save newEvents
            events.addAll(newEvents);
            newEvents.forEach(e -> {
                storage.saveEvent(e).block();
            });
            var newState = Utils.fold(Decider.initialState(), events, Decider::evolve);
            System.out.println("current State: "  + newState);
        }

        System.out.println("final events: " + events);
        System.out.println("calc final state:" + Utils.fold(new Decider.State(0), events, Decider::evolve));
    }
}

