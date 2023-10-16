package decider.event.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.r2dbc.postgresql.codec.Json;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.annotation.Id;

// Domain functions, independent per stream
// in this case, a super simple counter domain
// all pure functions, easily testable
public class Decider {

    // aka mutate
    static List<Event<?>> decide(CounterState state, Command<?> commandWrapper) {
        var command = commandWrapper.data();
        var transactionTime = commandWrapper.transactionTime();
        if (command instanceof Increment i) {
            return List.of(new Event<>(transactionTime, i));
        } else if (command instanceof Decrement d) {
            return List.of(new Event<>(transactionTime, d));
        }
        throw new UnsupportedOperationException("invalid command");
    }

    // aka applicator
    static CounterState evolve(CounterState currentState, Event<?> event) {
        if (event.data() instanceof Increment e) {
            var newState = new CounterState(currentState.id, currentState.totalCount() + e.amount());
            return newState;
        } else if (event.data() instanceof Decrement e) {
            var newState = new CounterState(currentState.id, currentState.totalCount() - e.amount());
            return newState;
        }
        throw new UnsupportedOperationException("invalid event");
    }

    static boolean isTerminal(CounterState state) {
        return false;
    }

    static CounterState initialState(UUID id) {
        return new CounterState(id, 0);
    }

    static Event<?> deserializeEvent(String eventType, Instant transactionTime, Json jsonPayload) {

        ObjectMapper objectMapper = JsonMapper.builder().build();
        try {
            switch (eventType) {
                case "decider.event.store.Decider$Increment":
                    var increment = objectMapper.readValue(jsonPayload.asString(), Increment.class);
                    return new Event<Increment>(transactionTime, increment);
                case "decider.event.store.Decider$Decrement":
                    var decrement = objectMapper.readValue(jsonPayload.asString(), Decrement.class);
                    return new Event<Decrement>(transactionTime, decrement);
                default:
                    throw new UnsupportedOperationException();
            }
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new UnsupportedOperationException();
        }
    }

    // shared command and event types
    record Increment(long amount) {}

    record Decrement(long amount) {}

    // state
    record CounterState(@Id UUID id, long totalCount) {}
}
