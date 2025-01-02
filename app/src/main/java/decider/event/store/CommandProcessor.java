package decider.event.store;

import com.example.eventsourcing.Decider;
import com.example.eventsourcing.infrastructure.CommandProcessingRepository;
import com.example.eventsourcing.infrastructure.DbRecordTypes.CommandLog;
import com.example.eventsourcing.infrastructure.SequentialUniqueIdObserver;
import com.example.eventsourcing.infrastructure.SerializationMapper;
import com.example.eventsourcing.infrastructure.Utils2;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

record DecisionResult<S, E>(CommandLog command, S state, List<? extends E> newEvents, boolean succeeded, RuntimeException exception) {}

@Slf4j
public class CommandProcessor<C, E, S> {
    private CommandProcessingRepository storage;
    private Decider<C, E, S> decider;
    private SerializationMapper<C, E> dtoMapper;

    public CommandProcessor(
            CommandProcessingRepository storage, Decider<C, E, S> decider, SerializationMapper<C, E> dtoMapper) {
        this.storage = storage;
        this.decider = decider;
        this.dtoMapper = dtoMapper;
    }

    public Mono<S> loadInitialState() {
        log.info("Loading initial state");
        var historicalEvents = storage.getAllEvents().map(dtoMapper::toEvent);
        return historicalEvents.reduce(decider.initialState(), (s, event) -> {
            return decider.apply(s, event);
        });
    }

    public Flux<S> process(int batchSize, int pollIntervalMilliseconds) {
        log.info("Starting!");
        return processWithDbState(batchSize, pollIntervalMilliseconds);
    }
    // public Flux<S> process(int batchSize, int pollIntervalMilliseconds) {
    //     Instant start = Instant.now();

    //     var initialState = loadInitialState();

    //     var uniqueFilter = new SequentialUniqueIdObserver(0L);
    //     var allCommands = storage.getInfiniteStreamOfUnprocessedCommands2(uniqueFilter, batchSize, pollIntervalMilliseconds)
    //             .cache();
    //     var run = initialState
    //             .doOnTerminate(() -> {
    //                 Instant end = Instant.now();
    //                 Duration duration = Duration.between(start, end);
    //                 log.info("Finished loading initial state in: {} milliseconds.", duration.toMillis());
    //             })
    //             .flatMapMany(state -> {
    //                 log.info("initial state: {}", state);

    //                 var startState = new DecisionResult<S, E>(state, new ArrayList<>(), null);
    //                 Flux<DecisionResult<S, E>> states = allCommands
    //                         .scan(startState, this::accumulate)
    //                         .skip(1); // skip because scan emits for the inital state, which we don't want to process
    //                 var eventLog = Flux.zip(allCommands, states).concatMap(tuple -> {
    //                     var commandDto = tuple.getT1();
    //                     var newEvents = tuple.getT2().newEvents();
    //                     var disposition = tuple.getT2().commandDisposition();
    //                     var nextState = tuple.getT2().state();
    //                     return saveNext(commandDto, disposition, newEvents, nextState);
    //                 });
    //                 return eventLog;
    //             });
    //     return run;
    // }

    public Flux<S> processWithDbState(int batchSize, int pollIntervalMilliseconds) {

        log.info("Starting DB State!");
        var allCommands = storage.getLatestCommandId().flatMapMany(latestCommandId -> {
            var uniqueFilter = new SequentialUniqueIdObserver(latestCommandId);

            return storage.getInfiniteStreamOfUnprocessedCommandsThroughput(uniqueFilter, batchSize, pollIntervalMilliseconds);
        });

        return allCommands.concatMap(commandDto -> {
            log.trace("Processing command: {}", commandDto);
            Mono<S> previousStateM = storage.getEventsForStream(commandDto.streamId())
                .reduce(decider.initialState(), (agg, i) -> {
                    var asEvent = dtoMapper.toEvent(i);
                    return decider.apply(agg, asEvent);
                });
            return previousStateM.flatMapMany(previousState -> {
                log.trace("Previous State: {}", previousState);
                return processCommands(previousState, Flux.just(commandDto));
            });
        });
    }

    private Flux<S> processCommands(S state, Flux<CommandLog> someCommands) {
        var startState = new DecisionResult<S, E>(null, state, new ArrayList<>(), true, null);
        Flux<DecisionResult<S, E>> states = someCommands
            .scan(startState, this::accumulate)
            .skip(1); // because scan emits the initial state
        return states.concatMap(this::saveNext);
    }

    private DecisionResult<S, E> accumulate(DecisionResult<S, E> acc, CommandLog command) {
        try {
            var domainCommand = dtoMapper.toCommand(command);
            var newEvents = decider.mutate(acc.state(), domainCommand);
            var newState = Utils2.fold(acc.state(), newEvents, decider::apply);
            log.trace("current state: {}", newState);
            return new DecisionResult<S, E>(command, newState, newEvents, true, null);
        } catch (RuntimeException e) {
            log.debug("caught business rule failure: {}", e.getLocalizedMessage());
            return new DecisionResult<S, E>(command, acc.state(), new ArrayList<>(), false, e);
        }
    }

    private Mono<S> saveNext(DecisionResult<S, E> result) {
        var streamId = result.command().streamId();
        var asOf = result.command().asOfRevisionId();
        var eventDtos = result.newEvents().stream().map(e -> dtoMapper.serialize(e)).toList();
        var nextState = result.state();
        return result.succeeded()
                ? storage.saveDtoRejectConflict(result.command().id(), eventDtos, streamId, asOf)
                        .map(pc -> nextState)
                : storage.saveFailedCommand(result.command().id()).map(pc -> nextState);
    }
}
