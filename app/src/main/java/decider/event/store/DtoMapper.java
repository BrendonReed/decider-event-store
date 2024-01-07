package decider.event.store;

import decider.event.store.DbRecordTypes.CommandLog;
import decider.event.store.DbRecordTypes.EventLog;

public interface DtoMapper<C, E> {
    C toCommand(CommandLog dto);

    E toEvent(EventLog dto);

    EventLog serialize(E event);
}
