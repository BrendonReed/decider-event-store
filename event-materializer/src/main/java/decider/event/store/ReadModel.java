package decider.event.store;

public interface ReadModel<S, E> {
    // aka evolve
    public S apply(S currentState, E event);
}
