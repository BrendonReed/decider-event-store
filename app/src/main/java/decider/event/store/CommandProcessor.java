package decider.event.store;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

    public Mono<S> loadInitialState(UUID streamId) {
        log.info("Loading initial state");
        var historicalEvents = storage.getEventsForStream(streamId).map(dtoMapper::toEvent);
        return historicalEvents.reduce(decider.initialState(), (s, event) -> {
            return decider.apply(s, event);
        });
    }

    public Flux<S> process() {
        Instant start = Instant.now();
        var streamId = UUID.fromString("4498a039-ce94-49b2-aff9-3ca12a8623d5");

        var initialState = loadInitialState(streamId);

        var listener = pubSubConnection.registerListener("command_logged");
        var allCommands = storage.getInifiteStreamOfUnprocessedCommands(listener);
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
                            .scan(startState, (acc, commandDto) -> {
                                var command = commandDto;
                                // could throw an IllegalStateException if this command violates business rules.
                                try {
                                    var domainCommand = dtoMapper.toCommand(command);
                                    var newEvents = decider.mutate(acc.state(), domainCommand);
                                    var newState = Utils.fold(acc.state(), newEvents, decider::apply);
                                    log.debug("current state: {}", newState);
                                    return new DecisionResult<S, E>(newState, newEvents, "Success");
                                } catch (RuntimeException e) {
                                    log.debug("caught business rule failure: {}", e.getLocalizedMessage());
                                    return new DecisionResult<S, E>(acc.state(), new ArrayList<>(), "Failure");
                                }
                            })
                            .skip(1); // skip because scan emits for the inital state, which we don't want to process
                    var eventLog = Flux.zip(allCommands, states).concatMap(tuple -> {
                        // options -
                        // . different save call for failure, pulling event_id on demand?
                        // . use scan to track previous command?
                        var newEvents = tuple.getT2().newEvents();
                        var commandDto = tuple.getT1();
                        var disposition = tuple.getT2().commandDisposition();
                        var x = newEvents.stream();
                        var eventDtos = x.map(e -> dtoMapper.serialize(e, streamId)).toList();
                        return disposition == "Success"
                                ? storage.saveDto(commandDto.id(), eventDtos, streamId)
                                        .map(pc -> tuple.getT2().state())
                                : storage.saveFailedCommand(commandDto.id())
                                        .map(pc -> tuple.getT2().state());
                    });
                    return eventLog;
                });
        return run;
    }
}
