package decider.event.store;

import decider.event.store.AddingDecider.AddingCommand;
import decider.event.store.AddingDecider.AddingEvent;
import decider.event.store.AddingDecider.DiffEvent;
import decider.event.store.AddingDecider.GetDiff;
import decider.event.store.DbRecordTypes.CommandLog;
import decider.event.store.DbRecordTypes.EventLog;

public class NoDtoMapper implements DtoMapper<AddingCommand, AddingEvent, AddingEvent> {

    JsonUtil jsonUtil;

    public NoDtoMapper(JsonUtil jsonUtil) {
        this.jsonUtil = jsonUtil;
    }

    public AddingEvent toDTO(AddingEvent event) {
        return event;
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
        var x = jsonUtil.deSerialize(dto.payload().asString(), dto.eventType());
        if (x instanceof GetDiff e) {
            return new DiffEvent(e.toMatch());
        }
        throw new UnsupportedOperationException("Invalid event");
    }
}
