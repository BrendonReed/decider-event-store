package decider.event.store;

import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.Id;

public class CounterDecider
        implements Decider<CounterDecider.CounterCommand, CounterDecider.CounterEvent, CounterDecider.CounterState> {

    public List<? extends CounterEvent> mutate(CounterState state, CounterCommand command) {
        return command.mutate(state);
    }

    @Override
    public CounterState apply(CounterState currentState, CounterEvent event) {
        return event.apply(currentState);
    }

    @Override
    public CounterState initialState() {
        return new CounterState(UUID.randomUUID(), 0);
    }

    public boolean isTerminal(CounterState state) {
        return false;
    }

    // commands
    public interface CounterCommand {
        List<? extends CounterEvent> mutate(CounterState state);
    }

    public record Increment(long amount) implements CounterCommand {

        @Override
        public List<? extends CounterEvent> mutate(CounterState state) {
            return List.of(new Incremented(amount));
        }
    }

    public record Decrement(long amount) implements CounterCommand {

        @Override
        public List<? extends CounterEvent> mutate(CounterState state) {
            return List.of(new Decremented(amount));
        }
    }

    // events
    public interface CounterEvent {
        CounterState apply(CounterState currentState);
    }

    public record Incremented(long amount) implements CounterEvent {

        @Override
        public CounterState apply(CounterState currentState) {
            return new CounterState(currentState.id(), currentState.totalCount() + amount());
        }
    }

    public record Decremented(long amount) implements CounterEvent {

        @Override
        public CounterState apply(CounterState currentState) {
            return new CounterState(currentState.id(), currentState.totalCount() - amount());
        }
    }

    // state
    public record CounterState(@Id UUID id, long totalCount) {}
}
