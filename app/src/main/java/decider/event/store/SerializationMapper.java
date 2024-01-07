package decider.event.store;

import decider.event.store.DbRecordTypes.CommandLog;
import decider.event.store.DbRecordTypes.EventLog;
import java.util.UUID;

public interface SerializationMapper<C, E> {
    C toCommand(CommandLog dto);

    E toEvent(EventLog dto);

    EventLog serialize(E event, UUID streamId);
}
