package decider.event.store;

import java.util.List;
import java.util.function.BiFunction;

public class Utils {
    // not sure why java streams doesn't have a fold...
    static <T, S> S fold(S state, List<T> events, BiFunction<S, T, S> evolve) {
        if (events.isEmpty()) {
            return state;
        } else {
            var newState = evolve.apply(state, events.get(0));
            return fold(newState, events.stream().skip(1).toList(), evolve);
        }
    }

    public enum Unit {UNIT}
}
