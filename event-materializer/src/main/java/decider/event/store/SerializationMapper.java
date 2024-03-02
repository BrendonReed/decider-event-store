package decider.event.store;

import decider.event.store.DbRecordTypes.EventLog;

public interface SerializationMapper<E> {
    E toEvent(EventLog dto);
}
