package decider.event.store;

import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow.Publisher;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class App {

    // create decider function (mutate): state -> command -> event list
    // create evolve function (apply): state -> event -> state
    // save in postgres
    // do subscription on event log to make view

    public static void main(String[] args) {

        Map<String, String> options = new HashMap<>();
        options.put("lock_timeout", "10s");

        PostgresqlConnectionFactory connectionFactory = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
            .host("localhost")
            .port(5402)  // optional, defaults to 5432
            .username("postgres")
            .password("password")
            .database("postgres")  // optional
            .options(options) // optional
            .build());

        Mono<PostgresqlConnection> mono = connectionFactory.create();
        Flux<String> z = mono.flatMapMany(connection -> {

            var r = connection.createStatement("SELECT 1 col")
                .execute()
                .flatMap(it -> it.map((row, rowMetadata) -> row.get("col", String.class)));
            return r;
        }).map(v -> {
            System.out.println("v: " + v);
            return v;
        });
        System.out.println(z);

        var events = new ArrayList<Event>();
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
            var newState = new State(currentState.count() + e.amount());
            return newState;
        }
        else if (event instanceof DecrementEvent e) {
            var newState = new State(currentState.count() - e.amount());
            return newState;
        }
        throw new UnsupportedOperationException("invalid event");
    }
}

interface Event {}
record Command<T>(OffsetDateTime transactionTime, T data) { }
record Event2<T>(OffsetDateTime transactionTime, T data) { }

record IncrementEvent(long amount) implements Event {}
record DecrementEvent(long amount) implements Event {}
record Increment(long count) {}
record Decrement(long count) {}

record State(long count) {}