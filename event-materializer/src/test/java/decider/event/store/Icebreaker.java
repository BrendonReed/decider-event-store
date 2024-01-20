package decider.event.store;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class Icebreaker {
    @Test
    public void ice() {
        Flux<Integer> source = Flux.range(1, 5);

        Flux<Integer> scanned = source.scan(0, (acc, value) -> acc + value).skip(1);
        Flux<String> zipped =
                source.zipWith(scanned, (value, accumulated) -> value + " (Accumulated: " + accumulated + ")");

        zipped.subscribe(System.out::println);
    }
}
