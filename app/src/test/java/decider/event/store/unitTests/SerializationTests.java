package decider.event.store.unitTests;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import decider.event.store.DeciderMapper;
import decider.event.store.Dtos.IncrementDto;
import decider.event.store.JsonUtil;
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

    @Test
    public void deserializeEvent() {

        String classString = decider.event.store.Dtos.IncrementDto.class.toString();
        System.out.println("classString: " + classString);
        var className = "decider.event.store.Dtos$IncrementDto";
        var classNama = "decider.event.store.Dtos$IncrementDto";
        var jsonString = "{\"amount\": 5}";
        var jsonPayload = Json.of(jsonString);
        ObjectMapper objectMapper = JsonMapper.builder().build();
        var jsonUtil = new JsonUtil(objectMapper);
        var mapper = new DeciderMapper(jsonUtil);
        // get a IncrementedDto
        try {
            // byte[] json = objectWriter.writeValueAsBytes(jsonString);
            // var asx = Json.of(json);

            var incrememnt1 = objectMapper.readValue(jsonPayload.asString(), IncrementDto.class);
            System.out.println("deserialized: " + incrememnt1);
            var incrememnt2 = objectMapper.readValue(jsonPayload.asString(), Class.forName(className));
            System.out.println("deserialized: " + incrememnt2);
        } catch (JsonMappingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
