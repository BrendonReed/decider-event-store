package decider.event.store;

import java.util.function.BiFunction;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class EventMaterializer<A> {
    public Storage storage;

    public EventMaterializer(Storage storage) {
        this.storage = storage;
        this.checkpoint = 0L;
    }

    public EventMaterializer(Storage storage, A initialState) {
        this.storage = storage;
        this.checkpoint = 0L;
        this.stateG = initialState;
    }

    public Long checkpoint;
    public A stateG;

    // in a loop -
    // find next event - checkpoint.event_id + 1
    // call a function with event for new state
    // save new state
    // update checkpoints

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
