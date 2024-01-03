package decider.event.store;

import java.util.List;
import java.util.UUID;

public class AddingDecider implements Decider<Integer> {
    // commands
    record GetDiff(Integer toMatch) {}

    // events
    record DiffEvent(Integer amount) {}

    @Override
    public MutationResult mutate2(Integer state, Command<?> commandWrapper) {
        var command = commandWrapper.data();
        if (command instanceof GetDiff i) {
            var diff = i.toMatch - state;
            var newState = state + diff;
            // must be odd
            if (newState % 2 != 1) {
                return new MutationResult.Failure("Business rule violation! State must always be odd.");
            }
            return new MutationResult.Success(List.of(new Event<>(new DiffEvent(diff))));
        }
        throw new UnsupportedOperationException("Invalid command");
    }
    // business rule: must always be odd.
    // generates an event that must be added to the current state to equal command
    @Override
    public List<Event<?>> mutate(Integer state, Command<?> commandWrapper) {
        var command = commandWrapper.data();
        if (command instanceof GetDiff i) {
            var diff = i.toMatch - state;
            var newState = state + diff;
            // must be odd
            if (newState % 2 != 1) {
                throw new IllegalStateException("Business rule violation! State must always be odd.");
            }
            return List.of(new Event<>(new DiffEvent(diff)));
        }
        throw new UnsupportedOperationException("Invalid command");
    }

    @Override
    public Integer apply(Integer currentState, Event<?> event) {
        if (event.data() instanceof DiffEvent e) {
            return currentState + e.amount();
        }
        throw new UnsupportedOperationException("Invalid event");
    }

    @Override
    public boolean isTerminal(Integer state) {
        return false;
    }

    @Override
    public Integer initialState(UUID id) {
        return 0;
    }
}
