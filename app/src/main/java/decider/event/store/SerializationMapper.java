package decider.event.store;

import shared.DbRecordTypes.CommandLog;
import shared.DbRecordTypes.EventLog;

public interface SerializationMapper<C, E> {
    C toCommand(CommandLog dto);

    E toEvent(EventLog dto);

    EventLog serialize(E event);
}
