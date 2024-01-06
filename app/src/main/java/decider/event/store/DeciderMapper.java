package decider.event.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import decider.event.store.CounterDecider.CounterCommand;
import decider.event.store.CounterDecider.CounterEvent;
import decider.event.store.CounterDecider.Decrement;
import decider.event.store.CounterDecider.Decremented;
import decider.event.store.CounterDecider.Increment;
import decider.event.store.CounterDecider.Incremented;
import decider.event.store.Dtos.CommandDto;
import decider.event.store.Dtos.EventDto;

interface DtoMapper<C, E, CD, ED> {
    C toCommand(CommandLog dto);

    E toEvent(EventLog dto);

    ED toDTO(E event);
}

public class DeciderMapper implements DtoMapper<CounterCommand, CounterEvent, CommandDto, EventDto> {
    public final ObjectMapper objectMapper;
    public final ObjectWriter objectWriter;

    public DeciderMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.objectWriter = objectMapper.writer();
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
        Object fromJson = jsonToDto(dto.command().asString(), dto.commandType());
        // IRL this would do validation when mapping into the domain type
        // in this case we trust the data stored in DB
        if (fromJson instanceof Dtos.IncrementDto e) {
            return new Increment(e.amount());
        } else if (fromJson instanceof Dtos.DecrementDto e) {
            return new Decrement(e.amount());
        }
        throw new UnsupportedOperationException("invalid event");
    }

    public CounterEvent toEvent(EventLog ep) {
        Object fromJson = jsonToDto(ep.payload().asString(), ep.eventType());
        if (fromJson instanceof Dtos.IncrementedDto e) {
            return new Incremented(e.amount());
        } else if (fromJson instanceof Dtos.DecrementedDto e) {
            return new Decremented(e.amount());
        }
        throw new UnsupportedOperationException("invalid event");
    }

    private Object jsonToDto(String payload, String type) {
        try {
            // TODO: for event versioning future preparation, this should
            // probably do explicit weak typed mapping.
            var data = objectMapper.readValue(payload, Class.forName(type));
            // IRL this would do validation when mapping into the domain type
            // in this case we trust the data stored in DB
            return data;

        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new UnsupportedOperationException();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }
}
