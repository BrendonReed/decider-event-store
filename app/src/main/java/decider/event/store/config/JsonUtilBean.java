package decider.event.store.config;

import com.example.eventsourcing.infrastructure.JsonUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class JsonUtilBean {

    @Autowired
    public ObjectMapper objectMapper;

    @Bean
    public JsonUtil create() {
        return new JsonUtil(objectMapper);
    }
}
