package shared;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.r2dbc.postgresql.codec.Json;

public class JsonUtil {

    public final ObjectMapper objectMapper;
    public final ObjectWriter objectWriter;

    public JsonUtil(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.objectWriter = objectMapper.writer();
    }

    public Object deSerialize(String payload, String type) {
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

    public <ED> Json serialize(ED object) {
        try {
            byte[] jsonBytes = objectWriter.writeValueAsBytes(object);
            var json = Json.of(jsonBytes);
            return json;
        } catch (JsonProcessingException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public <ED> ToSerialize toJson(ED object) {
        try {
            byte[] jsonBytes = objectWriter.writeValueAsBytes(object);
            var json = Json.of(jsonBytes);
            var eventType = object.getClass().getName();
            return new ToSerialize(eventType, json);
        } catch (JsonProcessingException e) {
            throw new UnsupportedOperationException();
        }
    }

    record ToSerialize(String objectType, Json json) {}
}
