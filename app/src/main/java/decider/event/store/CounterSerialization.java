package decider.event.store;

import decider.event.store.CounterDecider.CounterCommand;
import decider.event.store.CounterDecider.CounterEvent;
import decider.event.store.CounterDecider.Decrement;
import decider.event.store.CounterDecider.Decremented;
import decider.event.store.CounterDecider.Increment;
import decider.event.store.CounterDecider.Incremented;
import decider.event.store.DbRecordTypes.CommandLog;
import decider.event.store.DbRecordTypes.EventLog;
import lombok.extern.slf4j.Slf4j;
import java.util.UUID;

@Slf4j
public class CounterSerialization implements SerializationMapper<CounterCommand, CounterEvent> {

    private final JsonUtil jsonUtil;

    public CounterSerialization(JsonUtil jsonUtil) {
        this.jsonUtil = jsonUtil;
    }

    public EventLog serialize(CounterEvent entity) {
        var eventType = entity.getClass().getName();
        var asJson = jsonUtil.serialize(entity);
        return new EventLog(null, entity.streamId(), eventType, asJson);
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
