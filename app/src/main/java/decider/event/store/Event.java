package decider.event.store;

import java.time.OffsetDateTime;

record Event<T>(OffsetDateTime transactionTime, T data) { }