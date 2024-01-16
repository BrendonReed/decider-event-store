package decider.event.store;

import static org.springframework.data.relational.core.query.Criteria.*;
import static org.springframework.data.relational.core.query.Query.*;

import decider.event.store.DbRecordTypes.CounterCheckpoint;
import decider.event.store.DbRecordTypes.EventLog;
import io.r2dbc.postgresql.api.Notification;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class Storage {

    public final R2dbcEntityTemplate template;

    @Autowired
    public Storage(R2dbcEntityTemplate template) {
        this.template = template;
    }

    @Transactional
    public <S> Mono<S> saveStateAndCheckpoint(Long checkpoint, S nextState) {
        return this.template
                .update(new CounterCheckpoint(1L, checkpoint))
                .flatMap(c -> this.template.update(nextState));
    }

    private Flux<EventLog> getEvents(int batchSize) {
        var sql =
                """
            SELECT event_log.*
            FROM event_log
            WHERE event_log.id > (SELECT event_log_id FROM counter_checkpoint LIMIT 1)
            limit :batchSize
            """;
        return template.getDatabaseClient()
                .sql(sql)
                .bind("batchSize", batchSize)
                .map((row, metadata) -> {
                    EventLog command = template.getConverter().read(EventLog.class, row, metadata);
                    return command;
                })
                .all();
    }

    public Flux<EventLog> getInifiteStreamOfUnprocessedEvents(Flux<Notification> sub) {

        var batchSize = 100;
        var pollingInterval = Duration.ofSeconds(2);
        var triggers = Flux.merge(Flux.interval(pollingInterval), sub);
        return getEvents(batchSize)
                .concatWith(triggers.onBackpressureDrop(data -> {
                            log.debug("dropping");
                        })
                        .concatMap(t -> getEvents(batchSize)))
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
