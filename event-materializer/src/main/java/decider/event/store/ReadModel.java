package decider.event.store;

public interface ReadModel<S, E> {
    // aka evolve
    S apply(S currentState, E event);
    S initialState();
}
