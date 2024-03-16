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
public class Storage implements StatePersistance<CounterState> {

    public final R2dbcEntityTemplate template;

    @Autowired
    public Storage(R2dbcEntityTemplate template) {
        this.template = template;
    }

    public Mono<CounterState> getState() {
        return this.template.select(CounterState.class).first();
    }

    public Mono<CounterCheckpoint> getCheckpoint() {
        log.info("getting checkpoint");
        return this.template.select(CounterCheckpoint.class).first();
    }

    @Override
    @Transactional
    public Mono<CounterState> saveStateAndCheckpoint(Long checkpoint, CounterState nextState) {
        return this.template
                .update(new CounterCheckpoint(1L, checkpoint))
                .flatMap(c -> this.template.update(nextState).onErrorResume(error -> template.insert(nextState)));
    }

    @Transactional
    public <S> Mono<S> saveStateAndCheckpoint2(Long checkpoint, S nextState) {
        // TODO: this needs to be idempotent for it to work
        // TODO: maybe embed the checkpoint or make it generic. Hardcoding it here isn't ideal
        return this.template
                .update(new CounterCheckpoint(1L, checkpoint))
                .flatMap(c -> this.template.update(nextState).onErrorResume(error -> template.insert(nextState)));
    }

    public Flux<EventLog> getEvents(int batchSize, long checkpoint) {
        log.debug("querying from checkpoint: {}", checkpoint);
        var sql =
                """
            SELECT event_log.*
            FROM event_log
            WHERE event_log.id > :checkpoint
            order by event_log.id
            limit :batchSize
            """;
        return template.getDatabaseClient()
                .sql(sql)
                .bind("batchSize", batchSize)
                .bind("checkpoint", checkpoint)
                .map((row, metadata) -> {
                    EventLog command = template.getConverter().read(EventLog.class, row, metadata);
                    return command;
                })
                .all();
    }

    public Flux<EventLog> getInfiniteStreamOfUnprocessedEvents(Flux<Notification> sub, Long seedCheckpoint) {

        var batchSize = 100;
        var pollingInterval = Duration.ofMillis(1000);
        var uniqueFilter = new SequentialUniqueIdTransform(seedCheckpoint);
        var triggers = Flux.merge(Flux.interval(pollingInterval), sub);
        // var triggers = Flux.interval(Duration.ofSeconds(10), pollingInterval);
        var events = getEvents(batchSize, uniqueFilter.max.get())
                        .concatWith(triggers.onBackpressureDrop(data -> {
                                    log.debug("dropping");
                                })
                                .concatMap(t -> getEvents(batchSize, uniqueFilter.max.get())))
                        .doOnError(error -> {
                            // Log details when an error occurs
                            log.error("Error occurred: {}", error.getMessage());
                        })
                // .retryWhen(Retry.backoff(3, Duration.ofMillis(1000)))
                // .repeatWhen(repeatSignal -> repeatSignal.delayElements(Duration.ofSeconds(1)))
                ;
        return events.filter(e -> uniqueFilter.isFirstInstance(e.id()));
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
