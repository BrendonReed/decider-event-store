package decider.event.store;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

record DecisionResult<T>(T state, List<Event<?>> newEvents, String commandDisposition) {}

@Slf4j
@Component
public class CommandProcessor {
    private Storage storage;
    private PubSubConnection pubSubConnection;

    public CommandProcessor(Storage storage, PubSubConnection pubSubConnection) {
        this.storage = storage;
        this.pubSubConnection = pubSubConnection;
    }

    public <T> Flux<T> process(Decider<T> decider) {
        Instant start = Instant.now();
        var streamId = UUID.fromString("4498a039-ce94-49b2-aff9-3ca12a8623d5");

        log.info("Loading initial state");
        var historicalEvents = storage.getEventsForStream(streamId);
        var initialState = historicalEvents.reduce(decider.initialState(streamId), (s, eventP) -> {
            var event = Storage.toEvent(eventP);
            return decider.apply(s, event);
        });

        // command processor:
        // 1) listens for commands on command log
        // 2) processes command
        // 3) saves command in processed command log
        // .   stores pointer into command log
        // .   success/failure
        // .   pointer into event log for most recent event after processing
        // 4) checks state after new events for validity
        // 5) saves events
        // 6) maybe calculates next state

        // tests:
        // 1 to 3000 is 4501500.
        // for each element, an​=a1​ + (n−1) x d
        // transaction - saving a processed command fail (constraint or similar) and
        // .  the event should also not be saved

        var listener = pubSubConnection.registerListener("command_logged");
        Flux<CommandPersistance> allCommands = storage.getInifiteStreamOfUnprocessedCommands(listener);
        var run = initialState
                .doOnTerminate(() -> {
                    Instant end = Instant.now();
                    Duration duration = Duration.between(start, end);
                    log.info("Finished loading initial state in: {} milliseconds.", duration.toMillis());
                })
                .flatMapMany(state -> {
                    log.info("initial state: {}", state);

                    var startState = new DecisionResult<T>(state, new ArrayList<Event<?>>(), null);
                    var states = allCommands
                            .scan(startState, (acc, commandDto) -> {
                                var command = Storage.deserializeCommand(
                                        commandDto.commandType(), commandDto.requestId(), commandDto.command());
                                // could throw an IllegalStateException if this command violates business rules.
                                try {
                                    var newEvents = decider.mutate(acc.state(), command);
                                    var newState = Utils.fold(acc.state(), newEvents, decider::apply);
                                    log.debug("current state: {}", newEvents);
                                    return new DecisionResult<T>(newState, newEvents, "Success");
                                } catch (RuntimeException e) {
                                        log.debug("caught business rule failure: {}", e.getLocalizedMessage());
                                    return new DecisionResult<T>(acc.state(), new ArrayList<Event<?>>(), "Failure");
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
                        return disposition == "Success"
                                ? storage.save(commandDto, newEvents, streamId)
                                        .map(pc -> tuple.getT2().state())
                                : storage.saveFailedCommand(commandDto)
                                        .map(pc -> tuple.getT2().state());
                    });
                    return eventLog;
                });
        return run;
    }
}
