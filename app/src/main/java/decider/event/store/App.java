package decider.event.store;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;

import reactor.core.publisher.Flux;

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

        var events = new ArrayList<Event<?>>();
        var timestamp = OffsetDateTime.now();

        // command generator:
        // 1) listens for mutations
        // 2) validates command structure (pure functions)
        // 3) validates against system state if needed, like uniqueness (impure)
        // 4) enhances data with context like time, caller data, maybe system data
        // 5) writes to command log
        var commandLog = Flux.just(
                new Command<Decider.Increment>(timestamp, new Decider.Increment(1)),
                new Command<Decider.Increment>(timestamp, new Decider.Increment(1)),
                new Command<Decider.Increment>(timestamp, new Decider.Increment(1)),
                new Command<Decider.Decrement>(timestamp, new Decider.Decrement(1)));

        // command processor:
        // 1) listens for commands on command log
        // 2) processes command
        // 3) checks state after new events for validity
        // 4) saves events
        // 5) maybe calculates next state
        var main = commandLog
                .map(command -> {
                    // should load current state from storage?
                    var currentState = Utils.fold(Decider.initialState(), events, Decider::evolve);
                    var newEvents = Decider.decide(currentState, command);
                    // save newEvents
                    // verify this is legit?
                    var newState = Utils.fold(Decider.initialState(), events, Decider::evolve);
                    return newEvents;
                }).flatMap(newEvents -> {
                    events.addAll(newEvents);
                    return storage.saveEvents(newEvents);
                });
        main.blockLast(Duration.ofMinutes(1));

        System.out.println("final events: " + events);
        System.out.println("calc final state:" + Utils.fold(new Decider.State(0), events, Decider::evolve));
    }
}
