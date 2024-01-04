package decider.event.store;

import java.util.List;
import java.util.UUID;

public class AddingDecider implements Decider<Integer> {
    // commands
    record GetDiff(Integer toMatch) {}

    // events
    record DiffEvent(Integer amount) {}

    // business rule: must always be odd.
    // generates an event that must be added to the current state to equal command
    @Override
    public List<Event<?>> mutate(Integer state, Command<?> commandWrapper) {
        var command = commandWrapper.data();
        if (command instanceof GetDiff i) {
            var diff = i.toMatch - state;
            return List.of(new Event<>(new DiffEvent(diff)));
        }
        throw new UnsupportedOperationException("Invalid command");
    }

    @Override
    public Integer apply(Integer currentState, Event<?> event) {
        Integer nextState = currentState;
        if (event.data() instanceof DiffEvent e) {
            nextState = currentState + e.amount();
        }
        if (nextState % 2 != 1) {
            throw new IllegalStateException("Business rule violation! State must always be odd.");
        }
        else {
            return nextState;
        }
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
