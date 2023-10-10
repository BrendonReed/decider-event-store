package decider.event.store;

import java.time.Instant;
import java.util.UUID;

public record Command<T>(Instant transactionTime, UUID requestId, T data) {}
