package decider.event.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import domain.CounterDecider.CounterEvent;
import domain.CounterDecider.Decremented;
import domain.CounterDecider.Incremented;
import decider.event.store.DbRecordTypes.EventLog;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CounterReadModelSerialization implements SerializationMapper<CounterEvent> {

    private final JsonUtil jsonUtil;
    public final ObjectMapper objectMapper;

    public CounterReadModelSerialization(JsonUtil jsonUtil, ObjectMapper objectMapper) {
        this.jsonUtil = jsonUtil;
        this.objectMapper = objectMapper;
    }

    public CounterEvent toEvent(EventLog dto) {
        var x = jsonUtil.deSerialize(dto.payload().asString(), dto.eventType());
        if (x instanceof Incremented e) {
            return e;
        } else if (x instanceof Decremented e) {
            return e;
        }
        throw new UnsupportedOperationException("Error deserializing event");
    }
}
