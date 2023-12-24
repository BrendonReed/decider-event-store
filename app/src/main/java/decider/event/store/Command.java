package decider.event.store;

import java.util.UUID;

public record Command<T>(UUID requestId, T data) {}
