package decider.event.store;

import decider.event.store.Decider.CounterState;
import java.util.UUID;
import java.util.function.BiFunction;
import reactor.core.publisher.Flux;
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

    public <A> Mono<A> materializeG(
            A previousState, Flux<Event<?>> events, BiFunction<A, ? super Event<?>, A> accumulator) {
        var nextState = events.reduce(previousState, accumulator);
        return nextState;
    }
    // probably generally this isn't just calling evolve, so having it separate for
    // testing is the idea. But also this is domain specific?
    public Mono<CounterState> materializeH(CounterState previousState, Flux<Event<?>> events) {
        var nextState = events.reduce(previousState, Decider::evolve);
        return nextState;
    }

    // in a loop -
    // find next event - checkpoint.event_id + 1
    // call a function with event for new state
    // save new state
    // update checkpoints
    public Mono<CounterState> materializeN(String streamId, Long lastRevision) {

        var streamIdUuid = UUID.fromString(streamId);
        var persistedEvents = storage.getEventsForStream(streamIdUuid);
        Flux<Event<?>> asEvents = persistedEvents.map(ep -> {
            return Decider.deserializeEvent(ep.eventType(), ep.transactionTime(), ep.payload());
        });
        Mono<CounterState> persistedState = asEvents.reduce(Decider.initialState(streamIdUuid), Decider::evolve);
        Mono<CounterState> persistedS = storage.getState();
        return persistedState.flatMap(s -> materializeH(s, asEvents)).flatMap(storage::saveState);
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
