package decider.event.store;

import decider.event.store.AddingDecider.AddingCommand;
import decider.event.store.AddingDecider.AddingEvent;
import decider.event.store.AddingDecider.DiffEvent;
import decider.event.store.AddingDecider.GetDiff;
import decider.event.store.DbRecordTypes.CommandLog;
import decider.event.store.DbRecordTypes.EventLog;

public class AddingSerialization implements SerializationMapper<AddingCommand, AddingEvent> {

    JsonUtil jsonUtil;

    public AddingSerialization(JsonUtil jsonUtil) {
        this.jsonUtil = jsonUtil;
    }

    public EventLog serialize(AddingEvent event) {
        var asJson = jsonUtil.serialize(event);
        var eventType = event.getClass().getName();
        return new EventLog(null, null, eventType, asJson);
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
        if (x instanceof DiffEvent e) {
            return e;
        }
        throw new UnsupportedOperationException("Invalid event");
    }
}
