package decider.event.store;

import decider.event.store.config.PubSubConnection;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class EventMaterializer<S, E> {
    public Storage storage;
    private SerializationMapper<E> mapper;
    private PubSubConnection pubSubConnection;
    private ReadModel<S, E> readModel;

    public EventMaterializer(
            Storage storage, 
            PubSubConnection pubSubConnection, 
            SerializationMapper<E> mapper, 
            ReadModel<S, E> readModel) {
        this.storage = storage;
        this.readModel = readModel;
        this.mapper = mapper;
        this.pubSubConnection = pubSubConnection;
    }

    // in a loop -
    // find next event - checkpoint.event_id + 1
    // call a function with event for new state
    // save new state
    // update checkpoints
    public Flux<S> process(S initialState) {
        // TODO: join event query to processed command log to provide consistency
        // If a command emits multiple events, processes those events transactionally
        // to avoid an inconsistent view where not all events for a command are processed
        var listener = pubSubConnection.registerListener("event_logged");
        var dbEvents = storage.getInfiniteStreamOfUnprocessedEvents2();
        var mapped = dbEvents.map(mapper::toEvent);
        //var run = //initialState.flatMapMany(state -> {
            var state = initialState;

            log.debug("initialState: {}", state);
            var newStates = mapped.scan(state, readModel::apply)
                    .skip(1); // skip because scan emits for the inital state, which we don't want to process
            var save = Flux.zip(dbEvents, newStates).concatMap(tuple -> {
                var nextState = tuple.getT2();
                var eventDto = tuple.getT1();
                log.debug("saving state: {}", nextState);
                return storage.saveStateAndCheckpoint(eventDto.id(), nextState);
            });
            return save;

        //});
        //return save;
    }
}
