package decider.event.store;

import java.util.List;

import domain.Decider;

public class AddingDecider implements Decider<AddingDecider.AddingCommand, AddingDecider.AddingEvent, Integer> {
    // commands
    interface AddingCommand {
        List<? extends AddingEvent> mutate(Integer state);
    }

    public record GetDiff(Integer toMatch) implements AddingCommand {

        @Override
        public List<? extends AddingEvent> mutate(Integer state) {
            var diff = toMatch - state;
            return List.of(new DiffEvent(diff));
        }
    }

    // events
    public interface AddingEvent {
        Integer apply(Integer currentState);
    }

    public record DiffEvent(Integer amount) implements AddingEvent {

        @Override
        public Integer apply(Integer currentState) {
            Integer nextState = currentState;
            nextState = currentState + amount();
            if (nextState % 2 != 1) {
                throw new IllegalStateException("Business rule violation! State must always be odd.");
            } else {
                return nextState;
            }
        }
    }

    // business rule: must always be odd.
    // generates an event that must be added to the current state to equal command
    @Override
    public List<? extends AddingEvent> mutate(Integer state, AddingCommand command) {
        return command.mutate(state);
    }

    @Override
    public Integer apply(Integer currentState, AddingEvent event) {
        return event.apply(currentState);
    }

    @Override
    public boolean isTerminal(Integer state) {
        return false;
    }

    @Override
    public Integer initialState() {
        return 0;
    }
}
