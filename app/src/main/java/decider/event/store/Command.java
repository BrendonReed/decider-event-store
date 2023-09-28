package decider.event.store;

import java.time.OffsetDateTime;

public record Command<T>(OffsetDateTime transactionTime, T data) {}
