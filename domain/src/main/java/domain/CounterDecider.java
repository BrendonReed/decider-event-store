package domain;

import com.example.eventsourcing.Decider;
import java.util.List;
import java.util.UUID;

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
        UUID streamId();

        List<? extends CounterEvent> mutate(CounterState state);
    }

    public record Increment(long amount, Long tenantId, UUID streamId) implements CounterCommand {

        @Override
        public List<? extends CounterEvent> mutate(CounterState state) {
            return List.of(new Incremented(amount, tenantId, streamId));
        }
    }

    public record Decrement(long amount, Long tenantId, UUID streamId) implements CounterCommand {

        @Override
        public List<? extends CounterEvent> mutate(CounterState state) {
            return List.of(new Decremented(amount, tenantId, streamId));
        }
    }

    // events
    public interface CounterEvent {
        UUID streamId();

        Long tenantId();

        CounterState apply(CounterState currentState);
    }

    public record Incremented(long amount, Long tenantId, UUID streamId) implements CounterEvent {

        @Override
        public CounterState apply(CounterState currentState) {
            return new CounterState(currentState.id(), currentState.totalCount() + amount());
        }
    }

    public record Decremented(long amount, Long tenantId, UUID streamId) implements CounterEvent {

        @Override
        public CounterState apply(CounterState currentState) {
            return new CounterState(currentState.id(), currentState.totalCount() - amount());
        }
    }

    // state
    public record CounterState(UUID id, long totalCount) {}
}
