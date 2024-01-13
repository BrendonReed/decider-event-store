package decider.event.store;

import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Slf4j
public class EventMaterializer<S, E> {
    public Storage storage;
    private SerializationMapper<E> mapper;

    public EventMaterializer(Storage storage, SerializationMapper<E> mapper, S initialState) {
        this.storage = storage;
        this.checkpoint = 0L;
        this.state = initialState;
        this.mapper = mapper;
    }

    public Long checkpoint;
    public S state;

    // in a loop -
    // find next event - checkpoint.event_id + 1
    // call a function with event for new state
    // save new state
    // update checkpoints

    @Transactional
    public Mono<S> next(BiFunction<S, E, S> accumulator) {
        // TODO: setting checkpoint and saving state should be transactional
        log.debug("materializing from: {}", checkpoint);
        var dbEvents = storage.getLatestEvents(checkpoint);
        var mapped = dbEvents.map(mapper::toEvent);
        var newState = mapped.reduce(this.state, accumulator).flatMap(s -> {
            this.state = s;
            return storage.template.update(s);
        });
        return newState;
    }
}
