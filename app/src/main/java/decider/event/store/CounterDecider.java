package decider.event.store;

import java.util.List;
import java.util.UUID;

// Domain functions, independent per stream
// in this case, a super simple counter domain
// all pure functions, easily testable

public class CounterDecider implements Decider<CounterState> {

    @Override
    public MutationResult mutate2(CounterState state, Command<?> commandWrapper) {
        var command = commandWrapper.data();
        if (command instanceof Increment i) {
            return new MutationResult.Success(List.of(new Event<>(i)));
        } else if (command instanceof Decrement d) {
            return new MutationResult.Success(List.of(new Event<>(d)));
        }
        throw new UnsupportedOperationException("invalid command");
    }

    @Override
    public List<Event<?>> mutate(CounterState state, Command<?> commandWrapper) {
        var command = commandWrapper.data();
        if (command instanceof Increment i) {
            return List.of(new Event<>(i));
        } else if (command instanceof Decrement d) {
            return List.of(new Event<>(d));
        }
        throw new UnsupportedOperationException("invalid command");
    }
    // aka mutate
    static List<Event<?>> decide(CounterState state, Command<?> commandWrapper) {
        var command = commandWrapper.data();
        if (command instanceof Increment i) {
            return List.of(new Event<>(i));
        } else if (command instanceof Decrement d) {
            return List.of(new Event<>(d));
        }
        throw new UnsupportedOperationException("invalid command");
    }

    // aka applicator
    @Override
    public CounterState apply(CounterState currentState, Event<?> event) {
        if (event.data() instanceof Increment e) {
            var newState = new CounterState(currentState.id(), currentState.totalCount() + e.amount());
            return newState;
        } else if (event.data() instanceof Decrement e) {
            var newState = new CounterState(currentState.id(), currentState.totalCount() - e.amount());
            return newState;
        }
        throw new UnsupportedOperationException("invalid event");
    }

    static CounterState evolve(CounterState currentState, Event<?> event) {
        if (event.data() instanceof Increment e) {
            var newState = new CounterState(currentState.id(), currentState.totalCount() + e.amount());
            return newState;
        } else if (event.data() instanceof Decrement e) {
            var newState = new CounterState(currentState.id(), currentState.totalCount() - e.amount());
            return newState;
        }
        throw new UnsupportedOperationException("invalid event");
    }

    @Override
    public CounterState initialState(UUID id) {
        return new CounterState(id, 0);
    }

    // shared command and event types
    record Increment(long amount) {}

    record Decrement(long amount) {}

    @Override
    public boolean isTerminal(CounterState state) {
        return false;
    }
}
