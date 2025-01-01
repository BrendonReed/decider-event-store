package shared;

import io.r2dbc.postgresql.codec.Json;
import java.util.UUID;
import org.springframework.data.annotation.Id;

public class DbRecordTypes {

    public record EventLog(@Id Long id, Long tenantId, UUID streamId, String eventType, Json payload) {}

    public record CommandLog(
            @Id Long id,
            UUID requestId,
            Long tenantId,
            UUID streamId,
            Long asOfRevisionId,
            String commandType,
            Json command) {}

    public record ProcessedCommand(Long commandId, Long eventLogId, String disposition) {}
}
