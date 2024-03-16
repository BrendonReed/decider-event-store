package decider.event.store;

import java.util.function.BiFunction;

import decider.event.store.config.PubSubConnection;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class EventMaterializer<S, E> {
    public Storage storage;
    private SerializationMapper<E> mapper;
    private PubSubConnection pubSubConnection;
    private StatePersistance<S> persistance;

    public EventMaterializer(
            Storage storage,
            PubSubConnection pubSubConnection,
            SerializationMapper<E> mapper,
            StatePersistance<S> persistance) {
        this.storage = storage;
        this.mapper = mapper;
        this.pubSubConnection = pubSubConnection;
        this.persistance = persistance;
    }

    // in a loop -
    // find next event - checkpoint.event_id + 1
    // call a function with event for new state
    // save new state
    // update checkpoints

    public Flux<S> process(Mono<S> seedState, Mono<Long> seedCheckpoint, BiFunction<S, E, S> accumulator) {
        // TODO: join event query to processed command log to provide consistency
        // If a command emits multiple events, processes those events transactionally
        // to avoid an inconsistent view where not all events for a command are processed
        return seedState.flatMapMany(startState -> {
            return seedCheckpoint.flatMapMany(checkpoint -> {
                var listener = pubSubConnection.registerListener("event_logged");
                // var dbEvents = storage.getEvents(100);
                var dbEvents = storage.getInfiniteStreamOfUnprocessedEvents(listener, checkpoint)
                        .share();
                var mapped = dbEvents.map(mapper::toEvent);
                var newStates = mapped.scan(startState, accumulator)
                                .skip(1) // skip because scan emits for the inital state, which we don't want to process
                        ;
                var save = dbEvents.zipWith(newStates, (eventDto, nextState) -> {
                            return persistance.saveStateAndCheckpoint(eventDto.id(), nextState);
                        })
                        .concatMap(e -> e);
                return save;
            });
        });
    }
}
