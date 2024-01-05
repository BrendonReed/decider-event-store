package decider.event.store;

import java.util.UUID;

public record Command<T>(Long id, UUID requestId, T data) {}
