package decider.event.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import decider.event.store.CounterReadModel.CounterEvent;
import decider.event.store.CounterReadModel.Decremented;
import decider.event.store.CounterReadModel.Incremented;
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

    public EventLog serialize(CounterEvent entity) {
        var eventType = entity.getClass().getName();
        var asJson = jsonUtil.serialize(entity);
        return new EventLog(null, entity.tenantId(), entity.streamId(), eventType, asJson);
    }

    public CounterEvent toEvent(EventLog dto) {
        try {
            switch (dto.eventType()) {
                case "decider.event.store.CounterDecider$Incremented": {
                    var data = objectMapper.readValue(dto.payload().asString(), Incremented.class);
                    return data;
                }
                case "decider.event.store.CounterDecider$Decremented": {
                    var data = objectMapper.readValue(dto.payload().asString(), Decremented.class);
                    return data;
                }
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new UnsupportedOperationException();
        }
        throw new UnsupportedOperationException("Error deserializing event");
    }
}
