package com.example.eventsourcing.infrastructure;

import com.example.eventsourcing.infrastructure.DbRecordTypes.CommandLog;
import com.example.eventsourcing.infrastructure.DbRecordTypes.EventLog;

public interface SerializationMapper<C, E> {
    C toCommand(CommandLog dto);

    E toEvent(EventLog dto);

    EventLog serialize(E event);
}
