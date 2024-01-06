package decider.event.store;

import java.util.List;

public interface Decider<C, E, S> {

    // aka decide
    public List<? extends E> mutate(S state, C commandWrapper);

    // aka evolve
    public S apply(S currentState, E event);

    public boolean isTerminal(S state);

    public S initialState();
}
