package decider.event.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.function.BiFunction;

public class Utils {
    // not sure why java streams doesn't have a fold...
    static <T, S> S fold(S state, List<T> events, BiFunction<S, T, S> evolve) {
        if (events.isEmpty()) {
            return state;
        } else {
            var newState = evolve.apply(state, events.get(0));
            return fold(newState, events.stream().skip(1).toList(), evolve);
        }
    }

    // TODO: make extracting json safe
    static String unsafeExtract(String json) {

        String streamId = null;
        ObjectMapper mapper = JsonMapper.builder().build();
        JsonNode root;
        try {
            root = mapper.readTree(json);
            streamId = root.get("stream_id").asText();
            System.out.println("stream_id: " + streamId);
        } catch (JsonMappingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return streamId;
    }

    public enum Unit {
        UNIT
    }
}
