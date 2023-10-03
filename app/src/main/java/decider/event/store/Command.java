package decider.event.store;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Command<T>(OffsetDateTime transactionTime, UUID requestId, T data) {}
