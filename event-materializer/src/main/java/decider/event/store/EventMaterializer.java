package decider.event.store;

import decider.event.store.config.PubSubConnection;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class EventMaterializer<S, E> {
    public Storage storage;
    private SerializationMapper<E> mapper;
    private PubSubConnection pubSubConnection;
    private ReadModel<S, E> readModel;
    private SequentialUniqueIdTransform sequenceObserver;

    public EventMaterializer(
            Storage storage,
            PubSubConnection pubSubConnection,
            SerializationMapper<E> mapper,
            ReadModel<S, E> readModel) {
        this.storage = storage;
        this.checkpoint = 0L;
        this.mapper = mapper;
        this.pubSubConnection = pubSubConnection;
        this.readModel = readModel;
        this.sequenceObserver = new SequentialUniqueIdTransform(0L);
    }

    public Long checkpoint;

    // in a loop -
    // find next event - checkpoint.event_id + 1
    // call a function with event for new state
    // save new state
    // update checkpoints

    public Flux<S> process(S startState) {
        log.debug("materializing from: {}", checkpoint);
        // TODO: join event query to processed command log to provide consistency
        // If a command emits multiple events, processes those events transactionally
        // to avoid an inconsistent view where not all events for a command are processed
        var listener = pubSubConnection.registerListener("event_logged");
        // var dbEvents = storage.getEvents(100);
        var dbEvents = storage.getInfiniteStreamOfUnprocessedEvents(listener).share();
        var mapped = dbEvents.map(mapper::toEvent);
        var newStates = mapped.scan(startState, readModel::apply)
                        .skip(1) // skip because scan emits for the inital state, which we don't want to process
                ;
        var save = dbEvents.zipWith(newStates, (eventDto, nextState) -> {
                    return storage.saveStateAndCheckpoint(eventDto.id(), nextState);
                })
                .concatMap(e -> e);
        return save;
    }
}
