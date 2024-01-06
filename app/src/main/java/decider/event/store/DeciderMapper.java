package decider.event.store;

import decider.event.store.CounterDecider.CounterCommand;
import decider.event.store.CounterDecider.CounterEvent;
import decider.event.store.CounterDecider.Decrement;
import decider.event.store.CounterDecider.Decremented;
import decider.event.store.CounterDecider.Increment;
import decider.event.store.CounterDecider.Incremented;
import decider.event.store.DbRecordTypes.CommandLog;
import decider.event.store.DbRecordTypes.EventLog;
import decider.event.store.Dtos.DecrementedDto;
import decider.event.store.Dtos.EventDto;
import decider.event.store.Dtos.IncrementedDto;

public class DeciderMapper implements DtoMapper<CounterCommand, CounterEvent, EventDto> {

    private JsonUtil jsonUtil;

    public DeciderMapper(JsonUtil jsonUtil) {
        this.jsonUtil = jsonUtil;
    }

    public EventDto toDTO(CounterEvent entity) {
        if (entity instanceof Incremented i) {
            return new Dtos.IncrementedDto(i.amount());
        } else if (entity instanceof Decremented i) {
            return new Dtos.IncrementedDto(i.amount());
        }
        throw new UnsupportedOperationException("invalid event");
    }

    public CounterCommand toCommand(CommandLog dto) {

        var fromJson = jsonUtil.deSerialize(dto.command().asString(), dto.commandType());
        // IRL this would do validation when mapping into the domain type
        // in this case we trust the data stored in DB
        if (fromJson instanceof Dtos.IncrementDto e) {
            return new Increment(e.amount());
        } else if (fromJson instanceof Dtos.DecrementDto e) {
            return new Decrement(e.amount());
        }
        throw new UnsupportedOperationException("invalid event");
    }

    public CounterEvent toEvent(EventLog dto) {
        var fromJson = jsonUtil.deSerialize(dto.payload().asString(), dto.eventType());
        if (fromJson instanceof IncrementedDto e) {
            return new Incremented(e.amount());
        } else if (fromJson instanceof DecrementedDto e) {
            return new Decremented(e.amount());
        }
        throw new UnsupportedOperationException("invalid event");
    }
}
