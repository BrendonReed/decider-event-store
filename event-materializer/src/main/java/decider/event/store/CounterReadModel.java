package decider.event.store;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Id;

@Slf4j
public class CounterReadModel implements ReadModel<CounterReadModel.CounterState, CounterReadModel.CounterEvent> {

    public CounterState apply(CounterState currentState, CounterEvent event) {
        return event.apply(currentState);
    }

    public CounterState initialState() {
        return new CounterState(null, 0);
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
            var newState = new CounterState(this.streamId(), currentState.totalCount() + amount());
            return newState;
        }
    }

    public record Decremented(long amount, Long tenantId, UUID streamId) implements CounterEvent {

        @Override
        public CounterState apply(CounterState currentState) {
            return new CounterState(this.streamId(), currentState.totalCount() - amount());
        }
    }

    // state
    public record CounterState(@Id UUID id, long totalCount) {}
}
