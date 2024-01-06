package decider.event.store;

import decider.event.store.DbRecordTypes.CommandLog;
import decider.event.store.DbRecordTypes.EventLog;

public interface DtoMapper<C, E, ED> {
    C toCommand(CommandLog dto);

    E toEvent(EventLog dto);

    ED toDTO(E event);
}