package decider.event.store;

import reactor.core.publisher.Mono;

public interface StatePersistance<S> {
    public Mono<S> getState();

    public Mono<S> saveStateAndCheckpoint(Long checkpoint, S nextState);

    public Mono<Long> getCheckpoint();
}
