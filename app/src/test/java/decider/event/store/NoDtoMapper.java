package decider.event.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import decider.event.store.AddingDecider.AddingCommand;
import decider.event.store.AddingDecider.AddingEvent;
import decider.event.store.AddingDecider.DiffEvent;
import decider.event.store.AddingDecider.GetDiff;
import decider.event.store.DbRecordTypes.CommandLog;
import decider.event.store.DbRecordTypes.EventLog;

public class NoDtoMapper implements DtoMapper<AddingCommand, AddingEvent> {

    JsonUtil jsonUtil;
    ObjectMapper objectMapper;

    public NoDtoMapper(JsonUtil jsonUtil, ObjectMapper objectMapper) {
        this.jsonUtil = jsonUtil;
        this.objectMapper = objectMapper;
    }

    public EventLog serialize(AddingEvent event) {
        return new EventLog(null, null, "AddingEvent.DiffEvent", jsonUtil.serialize(event));
    }

    @Override
    public AddingCommand toCommand(CommandLog dto) {
        var x = jsonUtil.deSerialize(dto.command().asString(), dto.commandType());
        if (x instanceof GetDiff e) {
            return new GetDiff(e.toMatch());
        }
        throw new UnsupportedOperationException("Invalid command");
    }

    @Override
    public AddingEvent toEvent(EventLog dto) {
        try {
            switch (dto.eventType()) {
                case "AddingEvent.DiffEvent": {
                    return objectMapper.readValue(dto.payload().asString(), DiffEvent.class);
                }
                default:
                    throw new UnsupportedOperationException("Invalid event");
            }
        } catch (JsonMappingException e) {
            throw new UnsupportedOperationException("invalid event", e);
        } catch (JsonProcessingException e) {
            throw new UnsupportedOperationException("invalid event", e);
        }
    }
}
