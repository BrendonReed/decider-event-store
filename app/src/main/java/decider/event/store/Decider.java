package decider.event.store;

import java.util.List;
import java.util.UUID;

public interface Decider<S> {

    // aka mutator
    public List<Event<?>> mutate(S state, Command<?> commandWrapper);

    // aka applicator
    public S apply(S currentState, Event<?> event);

    public boolean isTerminal(S state);

    public S initialState(UUID id);
}
