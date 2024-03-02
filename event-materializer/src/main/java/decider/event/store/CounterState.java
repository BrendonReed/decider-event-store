package decider.event.store;

import java.util.UUID;

import org.springframework.data.annotation.Id;

import domain.CounterDecider.CounterEvent;
import domain.CounterDecider.Decremented;
import domain.CounterDecider.Incremented;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record CounterState(@Id UUID id, long totalCount) {

    public static CounterState apply(CounterState currentState, CounterEvent event) {
        if (event instanceof Incremented) {
            var totalCount = ((Incremented)event).amount() + currentState.totalCount;
            return new CounterState(currentState.id, totalCount);
        }
        else if (event instanceof Decremented) {
            var totalCount = currentState.totalCount - ((Decremented)event).amount();
            return new CounterState(currentState.id, totalCount);
        }
        else {
            return currentState;
        }
    }

    public CounterState initialState() {
        return new CounterState(null, 0);
    }
}
