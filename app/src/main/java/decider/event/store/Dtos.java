package decider.event.store;

public class Dtos {
    // Commands
    public interface CommandDto {}

    public record IncrementDto(long amount) implements CommandDto {}

    public record DecrementDto(long amount) implements CommandDto {}

    // Events
    public interface EventDto {}

    public record IncrementedDto(long amount) implements EventDto {}

    public record DecrementedDto(long amount) implements EventDto {}
}
