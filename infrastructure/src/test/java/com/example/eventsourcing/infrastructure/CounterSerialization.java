package com.example.eventsourcing.infrastructure;

import com.example.eventsourcing.infrastructure.CounterDecider.CounterCommand;
import com.example.eventsourcing.infrastructure.CounterDecider.CounterEvent;
import com.example.eventsourcing.infrastructure.CounterDecider.Decrement;
import com.example.eventsourcing.infrastructure.CounterDecider.Decremented;
import com.example.eventsourcing.infrastructure.CounterDecider.Increment;
import com.example.eventsourcing.infrastructure.CounterDecider.Incremented;
import com.example.eventsourcing.infrastructure.DbRecordTypes.CommandLog;
import com.example.eventsourcing.infrastructure.DbRecordTypes.EventLog;
import com.example.eventsourcing.infrastructure.JsonUtil;
import com.example.eventsourcing.infrastructure.SerializationMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CounterSerialization implements SerializationMapper<CounterCommand, CounterEvent> {

    // should this live in the domain?
    // it could live in the domain
    // or it could live in the command processor.
    // if it lived in the command processor, materializer would also need one
    // if it lives in the domain, then it can be shared in command processor and
    // in order for it to be in the domain library though, the domain library would need
    // references to json and db libraries as well as the types we're serializing to/from - eventLog and commandLog
    private final JsonUtil jsonUtil;

    public CounterSerialization(JsonUtil jsonUtil) {
        this.jsonUtil = jsonUtil;
    }

    public EventLog serialize(CounterEvent entity) {
        var eventType = entity.getClass().getName();
        var asJson = jsonUtil.serialize(entity);
        return new EventLog(null, entity.tenantId(), entity.streamId().toString(), eventType, asJson);
    }

    public CounterCommand toCommand(CommandLog dto) {

        // IRL this would do validation when mapping into the domain type
        // in this case we trust the data stored in DB
        var x = jsonUtil.deSerialize(dto.command().asString(), dto.commandType());
        if (x instanceof Increment e) {
            return e;
        } else if (x instanceof Decrement e) {
            return e;
        }
        throw new UnsupportedOperationException("Invalid command");
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
