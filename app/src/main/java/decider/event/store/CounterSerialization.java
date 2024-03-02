package decider.event.store;

import decider.event.store.DbRecordTypes.CommandLog;
import decider.event.store.DbRecordTypes.EventLog;
import domain.CounterDecider.CounterCommand;
import domain.CounterDecider.CounterEvent;
import domain.CounterDecider.Decrement;
import domain.CounterDecider.Decremented;
import domain.CounterDecider.Increment;
import domain.CounterDecider.Incremented;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CounterSerialization implements SerializationMapper<CounterCommand, CounterEvent> {

    private final JsonUtil jsonUtil;

    public CounterSerialization(JsonUtil jsonUtil) {
        this.jsonUtil = jsonUtil;
    }

    public EventLog serialize(CounterEvent entity) {
        var eventType = entity.getClass().getName();
        var asJson = jsonUtil.serialize(entity);
        return new EventLog(null, entity.tenantId(), entity.streamId(), eventType, asJson);
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
