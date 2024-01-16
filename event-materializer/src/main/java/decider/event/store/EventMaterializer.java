package decider.event.store;

import java.util.function.BiFunction;

import decider.event.store.config.PubSubConnection;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class EventMaterializer<S, E> {
    public Storage storage;
    private SerializationMapper<E> mapper;
    private PubSubConnection pubSubConnection;

    public EventMaterializer(
            Storage storage, PubSubConnection pubSubConnection, SerializationMapper<E> mapper, S initialState) {
        this.storage = storage;
        this.checkpoint = 0L;
        this.state = initialState;
        this.mapper = mapper;
        this.pubSubConnection = pubSubConnection;
    }

    public Long checkpoint;
    public S state;

    // in a loop -
    // find next event - checkpoint.event_id + 1
    // call a function with event for new state
    // save new state
    // update checkpoints

    public Flux<S> next(BiFunction<S, E, S> accumulator) {
        log.debug("materializing from: {}", checkpoint);
        // TODO: join event query to processed command log to provide consistency
        // If a command emits multiple events, processes those events transactionally
        // to avoid an inconsistent view where not all events for a command are processed
        var listener = pubSubConnection.registerListener("event_logged");
        var dbEvents = storage.getInifiteStreamOfUnprocessedEvents(listener);
        var mapped = dbEvents.map(mapper::toEvent);
        var newStates = mapped.scan(this.state, accumulator)
                .skip(1); // skip because scan emits for the inital state, which we don't want to process
        var save = Flux.zip(dbEvents, newStates).concatMap(tuple -> {
            var nextState = tuple.getT2();
            var eventDto = tuple.getT1();
            this.state = nextState;
            checkpoint = eventDto.id();
            return storage.saveStateAndCheckpoint(checkpoint, state);
        });
        return save;
    }
}
