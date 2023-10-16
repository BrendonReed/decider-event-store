package decider.event.store;

import decider.event.store.Decider.CounterState;
import java.util.UUID;
import reactor.core.publisher.Mono;

public class EventMaterializer {
    public Storage storage;

    // given a previous state and a list of events, make a new state
    // save the next state. Update checkpoint to id of last event
    // get the previous state from persistance
    // get the list of events from querying between the id of previous state
    //   and the

    public EventMaterializer(Storage storage) {
        this.storage = storage;
    }

    public Mono<CounterState> materialize(String streamId, Long lastRevision) {

        var streamIdUuid = UUID.fromString(streamId);
        return storage.getEventsForStream(streamIdUuid)
                .map(ep -> {
                    return Decider.deserializeEvent(ep.eventType(), ep.transactionTime(), ep.payload());
                })
                .reduce(Decider.initialState(streamIdUuid), Decider::evolve)
                .flatMap(storage::saveState);
    }
}
