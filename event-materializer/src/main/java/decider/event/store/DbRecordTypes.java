package decider.event.store;

import io.r2dbc.postgresql.codec.Json;
import java.util.UUID;
import org.springframework.data.annotation.Id;

public class DbRecordTypes {

    public record EventLog(@Id Long id, Long tenantId, UUID streamId, String eventType, Json payload) {}

    public record CounterCheckpoint(@Id Long id, Long eventLogId) {}
}
