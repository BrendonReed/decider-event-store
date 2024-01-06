package decider.event.store;

public class EventMaterializer<A> {
    public Storage storage;

    public EventMaterializer(Storage storage) {
        this.storage = storage;
        this.checkpoint = 0L;
    }

    public EventMaterializer(Storage storage, A initialState) {
        this.storage = storage;
        this.checkpoint = 0L;
        this.state = initialState;
    }

    public Long checkpoint;
    public A state;

    // in a loop -
    // find next event - checkpoint.event_id + 1
    // call a function with event for new state
    // save new state
    // update checkpoints

    // @Transactional
    // public Mono<A> next(BiFunction<A, ? super Event<?>, A> accumulator) {
    //     // TODO: setting checkpoint and saving state should be transactional
    //     System.out.println("materializing from: " + checkpoint);
    //     return storage.getLatestEvents(checkpoint)
    //             .reduce(this.state, accumulator)
    //             .flatMap(s -> {
    //                 this.state = s;
    //                 return storage.template.update(s);
    //             });
    // }
}
