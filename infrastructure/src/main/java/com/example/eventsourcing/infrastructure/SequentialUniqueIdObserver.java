package com.example.eventsourcing.infrastructure;

import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SequentialUniqueIdObserver {

    public final AtomicReference<Long> min;
    public final AtomicReference<Long> max;

    public SequentialUniqueIdObserver(Long seed) {
        log.debug("Starting observer");
        log.info("Starting observer but with info");
        this.min = new AtomicReference<>(seed);
        this.max = new AtomicReference<>(seed);
    }

    public boolean isFirstInstance(Long value) {
        Long currentMax = max.get();

        if (currentMax == null) {
            max.updateAndGet(current -> value);
            min.updateAndGet(current -> value);
            log.info("first element: {}", value);
            return true;
        } else if (value == currentMax + 1) {
            log.info("approving: {}", value);
            max.updateAndGet(current -> value);
            return true;
        } else if (value <= currentMax) {
            log.info("denying: {}", value);
            return false;
        }
        throw new IllegalStateException("Somehow processing non-sequentially");
    }
}
