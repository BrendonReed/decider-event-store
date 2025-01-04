package com.example.eventsourcing.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.r2dbc.postgresql.codec.Json;
import org.junit.jupiter.api.Test;

record EventV2(Long amount, Long amount2) {}

class SerializationTests {
    @Test
    public void weakTypeDeserialize() {
        // looks like this will default values that aren't in the json,
        // but unexpected values in the json will cause a JsonMappingException,
        // unless configured to not fail on unknown properties.
        var jsonString = "{\"amount\": 5, \"amount3\": 7}";
        var jsonPayload = Json.of(jsonString);
        ObjectMapper objectMapper = JsonMapper.builder().build();
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        try {
            System.out.println("deserialized: before");
            var event2 = objectMapper.readValue(jsonPayload.asString(), EventV2.class);
            System.out.println("deserialized: " + event2);
        } catch (JsonMappingException e) {
            // TODO Auto-generated catch block
            System.out.println("JsonMappingException");
            e.printStackTrace();
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            System.out.println("JsonProcessingException");
            e.printStackTrace();
        }
    }
}
