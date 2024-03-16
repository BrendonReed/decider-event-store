package decider.event.store;

import decider.event.store.DbRecordTypes.CounterCheckpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class CounterStatePersistence implements StatePersistance<CounterState> {

    public final R2dbcEntityTemplate template;

    public CounterStatePersistence(R2dbcEntityTemplate template) {
        this.template = template;
    }

    @Override
    @Transactional
    public Mono<CounterState> saveStateAndCheckpoint(Long checkpoint, CounterState nextState) {
        return this.template
                .update(new CounterCheckpoint(1L, checkpoint))
                .flatMap(c -> this.template.update(nextState).onErrorResume(error -> template.insert(nextState)));
    }

    @Override
    public Mono<Long> getCheckpoint() {
        log.info("getting checkpoint");
        return this.template.select(CounterCheckpoint.class).first().map(c -> c.eventLogId());
    }

    @Override
    public Mono<CounterState> getState() {
        return this.template.select(CounterState.class).first();
    }
}
