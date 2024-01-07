package decider.event.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import decider.event.store.CounterDecider.CounterCommand;
import decider.event.store.CounterDecider.CounterEvent;
import decider.event.store.CounterDecider.Decrement;
import decider.event.store.CounterDecider.Decremented;
import decider.event.store.CounterDecider.Increment;
import decider.event.store.CounterDecider.Incremented;
import decider.event.store.DbRecordTypes.CommandLog;
import decider.event.store.DbRecordTypes.EventLog;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeciderMapper implements DtoMapper<CounterCommand, CounterEvent> {

    private final ObjectMapper objectMapper;
    private final JsonUtil jsonUtil;

    public DeciderMapper(JsonUtil jsonUtil, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.jsonUtil = jsonUtil;
    }

    public EventLog serialize(CounterEvent entity) {
        if (entity instanceof Incremented i) {
            var asJson = jsonUtil.serialize(entity);
            return new EventLog(null, null, "CounterEvent.Incremented", asJson);
        } else if (entity instanceof Decremented i) {
            var asJson = jsonUtil.serialize(entity);
            return new EventLog(null, null, "CounterEvent.Decremented", asJson);
        }
        throw new UnsupportedOperationException("invalid event");
    }

    public CounterCommand toCommand(CommandLog dto) {

        // IRL this would do validation when mapping into the domain type
        // in this case we trust the data stored in DB
        var x = jsonUtil.deSerialize(dto.command().asString(), dto.commandType());
        if (x instanceof Increment e) {
            return new Increment(e.amount());
        }
        else if (x instanceof Decrement e) {
            return new Decrement(e.amount());
        }
        throw new UnsupportedOperationException("Invalid command");
    }

    public CounterEvent toEvent(EventLog dto) {
        try {
            switch (dto.eventType()) {
                case "CounterEvent.Incremented": {
                    return objectMapper.readValue(dto.payload().asString(), Incremented.class);
                }
                case "CounterEvent.Decremented": {
                    return objectMapper.readValue(dto.payload().asString(), Decremented.class);
                }
                default: {
                    throw new UnsupportedOperationException("invalid event");
                }
            }
        } catch (JsonMappingException e) {
            throw new UnsupportedOperationException("invalid event", e);
        } catch (JsonProcessingException e) {
            throw new UnsupportedOperationException("invalid event", e);
        }
    }
}
