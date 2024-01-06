package decider.event.store;

import io.r2dbc.postgresql.codec.Json;
import java.util.UUID;
import org.springframework.data.annotation.Id;

public class DbRecordTypes {

    record EventLog(@Id Long id, UUID streamId, String eventType, Json payload) {}

    record CommandLog(@Id Long id, UUID requestId, String commandType, Json command) {}

    record ProcessedCommand(Long commandId, Long eventLogId, String disposition) {}
}
