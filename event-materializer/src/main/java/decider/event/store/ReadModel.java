package decider.event.store;

public interface ReadModel<E, S> {
    // aka evolve
    public S apply(S currentState, E event);
}
