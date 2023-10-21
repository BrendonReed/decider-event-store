package decider.event.store;

import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.Id;

// Domain functions, independent per stream
// in this case, a super simple counter domain
// all pure functions, easily testable
public class Decider {

    // aka mutate
    static List<Event<?>> decide(CounterState state, Command<?> commandWrapper) {
        var command = commandWrapper.data();
        var transactionTime = commandWrapper.transactionTime();
        if (command instanceof Increment i) {
            return List.of(new Event<>(transactionTime, i));
        } else if (command instanceof Decrement d) {
            return List.of(new Event<>(transactionTime, d));
        }
        throw new UnsupportedOperationException("invalid command");
    }

    // aka applicator
    static CounterState evolve(CounterState currentState, Event<?> event) {
        if (event.data() instanceof Increment e) {
            var newState = new CounterState(currentState.id, currentState.totalCount() + e.amount());
            return newState;
        } else if (event.data() instanceof Decrement e) {
            var newState = new CounterState(currentState.id, currentState.totalCount() - e.amount());
            return newState;
        }
        throw new UnsupportedOperationException("invalid event");
    }

    static boolean isTerminal(CounterState state) {
        return false;
    }

    static CounterState initialState(UUID id) {
        return new CounterState(id, 0);
    }

    // shared command and event types
    record Increment(long amount) {}

    record Decrement(long amount) {}

    // state
    record CounterState(@Id UUID id, long totalCount) {}
}
