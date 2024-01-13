package decider.event.store;

import static org.springframework.data.relational.core.query.Criteria.*;
import static org.springframework.data.relational.core.query.Query.*;

import decider.event.store.DbRecordTypes.EventLog;
import io.r2dbc.postgresql.api.Notification;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@Slf4j
public class Storage {

    public final R2dbcEntityTemplate template;
    private final JsonUtil jsonUtil;

    @Autowired
    public Storage(R2dbcEntityTemplate template, JsonUtil jsonUtil) {
        this.template = template;
        this.jsonUtil = jsonUtil;
    }

    public Flux<EventLog> getAllEvents() {
        return template.select(EventLog.class)
                .from("event_log")
                // .matching(query(where("stream_id").is(streamId)))
                .all();
    }

    public Flux<EventLog> getLatestEvents(Long latestEvent) {
        List<Criteria> criteriaList = new ArrayList<>();

        criteriaList.add(where("id").greaterThan(latestEvent));
        Criteria combinedCriteria = criteriaList.stream().reduce(Criteria.empty(), Criteria::and, Criteria::and);
        return template.select(EventLog.class)
                .from("event_log")
                // .matching(query(combinedCriteria))
                .all();
    }

    public Flux<EventLog> getInifiteStreamOfUnprocessedEvents(Flux<Notification> sub, Long latestEvent) {

        var pollingInterval = Duration.ofSeconds(2);
        var triggers = Flux.merge(Flux.interval(pollingInterval), sub);
        return getLatestEvents(latestEvent)
                .concatWith(triggers.onBackpressureDrop(data -> {
                            log.debug("dropping");
                        })
                        .concatMap(t -> getLatestEvents(latestEvent)))
                .doOnError(error -> {
                    // Log details when an error occurs
                    log.error("Error occurred: {}", error.getMessage());
                })
        // .retryWhen(Retry.backoff(3, Duration.ofMillis(1000)))
        ;
    }

    public Flux<LocalDateTime> queryCurrentTime() {
        var sql = "select now() current_time";
        return template.getDatabaseClient()
                .sql(sql)
                .map(row -> {
                    return row.get("current_time", LocalDateTime.class);
                })
                .all();
    }
}
