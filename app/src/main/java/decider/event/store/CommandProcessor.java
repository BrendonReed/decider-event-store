package decider.event.store;

import decider.event.store.DbRecordTypes.CommandLog;
import domain.Decider;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import shared.Utils2;

record DecisionResult<S, E>(S state, List<? extends E> newEvents, String commandDisposition) {}

@Slf4j
public class CommandProcessor<C, E, S> {
    private Storage storage;
    private PubSubConnection pubSubConnection;
    private Decider<C, E, S> decider;
    private SerializationMapper<C, E> dtoMapper;

    public CommandProcessor(
            Storage storage,
            PubSubConnection pubSubConnection,
            Decider<C, E, S> decider,
            SerializationMapper<C, E> dtoMapper) {
        this.storage = storage;
        this.pubSubConnection = pubSubConnection;
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
        Instant start = Instant.now();

        var initialState = loadInitialState();

        var listener = pubSubConnection.registerListener("command_logged");
        var allCommands = storage.getInfiniteStreamOfUnprocessedCommands(listener, batchSize, pollIntervalMilliseconds)
                .share();
        var run = initialState
                .doOnTerminate(() -> {
                    Instant end = Instant.now();
                    Duration duration = Duration.between(start, end);
                    log.info("Finished loading initial state in: {} milliseconds.", duration.toMillis());
                })
                .flatMapMany(state -> {
                    log.info("initial state: {}", state);

                    var startState = new DecisionResult<S, E>(state, new ArrayList<>(), null);
                    Flux<DecisionResult<S, E>> states = allCommands
                            .scan(startState, this::accumulate)
                            .skip(1); // skip because scan emits for the inital state, which we don't want to process
                    var eventLog = Flux.zip(allCommands, states).concatMap(tuple -> {
                        var commandDto = tuple.getT1();
                        var newEvents = tuple.getT2().newEvents();
                        var disposition = tuple.getT2().commandDisposition();
                        var nextState = tuple.getT2().state();
                        return saveNext(commandDto, disposition, newEvents, nextState);
                    });
                    return eventLog;
                });
        return run;
    }

    private DecisionResult<S, E> accumulate(DecisionResult<S, E> acc, CommandLog command) {
        try {
            var domainCommand = dtoMapper.toCommand(command);
            var newEvents = decider.mutate(acc.state(), domainCommand);
            var newState = Utils2.fold(acc.state(), newEvents, decider::apply);
            log.debug("current state: {}", newState);
            return new DecisionResult<S, E>(newState, newEvents, "Success");
        } catch (RuntimeException e) {
            log.debug("caught business rule failure: {}", e.getLocalizedMessage());
            return new DecisionResult<S, E>(acc.state(), new ArrayList<>(), "Failure");
        }
    }

    private Mono<S> saveNext(CommandLog commandDto, String disposition, List<? extends E> newEvents, S nextState) {
        var x = newEvents.stream();
        var streamId = commandDto.streamId();
        var asOf = commandDto.asOfRevisionId();
        var eventDtos = x.map(e -> dtoMapper.serialize(e)).toList();
        return disposition == "Success"
                ? storage.saveDtoRejectConflict(commandDto.id(), eventDtos, streamId, asOf)
                        .map(pc -> nextState)
                : storage.saveFailedCommand(commandDto.id()).map(pc -> nextState);
    }
}
