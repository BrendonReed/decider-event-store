package decider.event.store;

import java.util.UUID;
import org.springframework.data.annotation.Id;

// state
public record CounterState(@Id UUID id, long totalCount) {}
