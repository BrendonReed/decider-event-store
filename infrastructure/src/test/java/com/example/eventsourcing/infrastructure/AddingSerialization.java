package com.example.eventsourcing.infrastructure;

import com.example.eventsourcing.infrastructure.AddingDecider.AddingCommand;
import com.example.eventsourcing.infrastructure.AddingDecider.AddingEvent;
import com.example.eventsourcing.infrastructure.AddingDecider.DiffEvent;
import com.example.eventsourcing.infrastructure.AddingDecider.GetDiff;
import com.example.eventsourcing.infrastructure.DbRecordTypes.CommandLog;
import com.example.eventsourcing.infrastructure.DbRecordTypes.EventLog;
import com.example.eventsourcing.infrastructure.JsonUtil;
import com.example.eventsourcing.infrastructure.SerializationMapper;

public class AddingSerialization implements SerializationMapper<AddingCommand, AddingEvent> {

    JsonUtil jsonUtil;

    public AddingSerialization(JsonUtil jsonUtil) {
        this.jsonUtil = jsonUtil;
    }

    public EventLog serialize(AddingEvent event) {
        var asJson = jsonUtil.serialize(event);
        var eventType = event.getClass().getName();
        return new EventLog(null, event.tenantId(), event.streamId(), eventType, asJson);
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
