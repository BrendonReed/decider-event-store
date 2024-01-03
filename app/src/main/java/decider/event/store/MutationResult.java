package decider.event.store;

import java.util.List;

public interface MutationResult {
    record Success(List<Event<?>> events) implements MutationResult {}

    record Failure(String message) implements MutationResult {}
}
