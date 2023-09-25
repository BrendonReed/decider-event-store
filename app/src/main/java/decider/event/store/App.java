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
        var events = new ArrayList<Event>();
        var c = new Command<Increment>(OffsetDateTime.now(), new Increment(1));
        var timestamp = OffsetDateTime.now();
        var commandLog = List.of(
            new Command<Increment>(timestamp, new Increment(1)),
            new Command<Increment>(timestamp, new Increment(1)),
            new Command<Increment>(timestamp, new Increment(1)),
            new Command<Decrement>(timestamp, new Decrement(1))
            );

        for (Command<?> command : commandLog) {
            var currentState = Utils.fold(new State(0), events, App::evolve);
            var newEvents = decide(currentState, command);
            events.addAll(newEvents);
            var newState = Utils.fold(new State(0), events, App::evolve);
            System.out.println("current State: "  + newState);
        }

        System.out.println("final events: " + events);
        System.out.println("calc final state:" + Utils.fold(new State(0), events, App::evolve));
    }

    // aka mutate
    static List<Event> decide(State state, Command<?> commandWrapper) {
        var command = commandWrapper.data();
        if (command instanceof Increment i) {
            return List.of(new IncrementEvent(i.count()));
        }
        else if (command instanceof Decrement i) {
            return List.of(new DecrementEvent(i.count()));
        }
        throw new UnsupportedOperationException("invalid command");
    }

    // aka applicator
    static State evolve(State currentState, Event event) {
        if (event instanceof IncrementEvent e) {
            var newState = new State(currentState.count() + e.count());
            return newState;
        }
        else if (event instanceof DecrementEvent e) {
            var newState = new State(currentState.count() - e.count());
            return newState;
        }
        throw new UnsupportedOperationException("invalid event");
    }
}

interface Event {}
record Command<T>(OffsetDateTime transactionTime, T data) { }

record IncrementEvent(long count) implements Event {}
record DecrementEvent(long count) implements Event {}
record Increment(long count) {}
record Decrement(long count) {}

record State(long count) {}