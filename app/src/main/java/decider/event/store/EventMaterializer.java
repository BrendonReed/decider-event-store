package decider.event.store;

import decider.event.store.Decider.CounterState;
import java.util.UUID;
import java.util.function.BiFunction;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class EventMaterializer {
    public Storage storage;

    public EventMaterializer(Storage storage) {
        this.storage = storage;
        this.checkpoint = 0L;
        this.state = Decider.initialState(UUID.fromString("4498a039-ce94-49b2-aff9-3ca12a8623d5"));
    }

    public Long checkpoint;
    public CounterState state;

    // in a loop -
    // find next event - checkpoint.event_id + 1
    // call a function with event for new state
    // save new state
    // update checkpoints
    public Mono<CounterState> next() {
        // TODO: setting checkpoint and saving state should be transactional
        return storage.getLatestEvents(checkpoint)
                .map(ep -> {
                    checkpoint = ep.eventId();
                    return Decider.deserializeEvent(ep.eventType(), ep.transactionTime(), ep.payload());
                })
                .reduce(state, Decider::evolve)
                .flatMap(s -> {
                    this.state = s;
                    return storage.saveState(s);
                });
    }

    public <A> Mono<A> materializeG(
            A previousState, Flux<Event<?>> events, BiFunction<A, ? super Event<?>, A> accumulator) {
        var nextState = events.reduce(previousState, accumulator);
        return nextState;
    }
}
