package decider.event.store;

import decider.event.store.Decider.CounterState;
import java.util.UUID;
import java.util.function.BiFunction;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class EventMaterializer<A> {
    public Storage storage;

    public EventMaterializer(Storage storage) {
        this.storage = storage;
        this.checkpoint = 0L;
        this.state = Decider.initialState(UUID.fromString("4498a039-ce94-49b2-aff9-3ca12a8623d5"));
    }

    public EventMaterializer(Storage storage, A initialState) {
        this.storage = storage;
        this.checkpoint = 0L;
        this.stateG = initialState;
    }

    public Long checkpoint;
    public CounterState state;
    public A stateG;

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

    public Mono<A> nextG(BiFunction<A, ? super Event<?>, A> accumulator) {
        // TODO: setting checkpoint and saving state should be transactional
        System.out.println("materializing from: " + checkpoint);
        return storage.getLatestEvents(checkpoint)
                .map(ep -> {
                    checkpoint = ep.eventId();
                    return Decider.deserializeEvent(ep.eventType(), ep.transactionTime(), ep.payload());
                })
                .reduce(this.stateG, accumulator)
                .flatMap(s -> {
                    this.stateG = s;
                    var template = new R2dbcEntityTemplate(storage.connectionFactory);
                    return template.update(s);
                });
    }

    public <A> Mono<A> materializeG(
            A previousState, Flux<Event<?>> events, BiFunction<A, ? super Event<?>, A> accumulator) {
        var nextState = events.reduce(previousState, accumulator);
        return nextState;
    }
}