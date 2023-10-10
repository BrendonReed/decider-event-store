package decider.event.store;

import java.time.Instant;

record Event<T>(Instant transactionTime, T data) {}
