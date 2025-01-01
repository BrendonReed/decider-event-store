package domain.ConwaysGameOfLife;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GameOfLife {

    // Always defined
    public interface GameOfLifeCommand {
        UUID streamId();

        List<? extends GameOfLifeEvent> mutate(GridState state);
    }

    // Events
    public interface GameOfLifeEvent {
        UUID streamId();

        Long tenantId();

        GridState apply(GridState currentState);
    }

    public record GenerationAdvanced() implements GameOfLifeEvent {

        @Override
        public UUID streamId() {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'streamId'");
        }

        @Override
        public Long tenantId() {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'tenantId'");
        }

        @Override
        public GridState apply(GridState currentState) {
            Set<Cell> nextGenerationLiveCells = new HashSet<>();

            // Map to track neighbor counts for each cell
            Map<Cell, Integer> neighborCounts = new HashMap<>();

            // Count neighbors for each live cell
            for (Cell liveCell : currentState.getLiveCells()) {
                for (Cell neighbor : liveCell.getNeighbors()) {
                    neighborCounts.put(neighbor, neighborCounts.getOrDefault(neighbor, 0) + 1);
                }
            }

            // Determine next generation live cells
            for (Map.Entry<Cell, Integer> entry : neighborCounts.entrySet()) {
                Cell cell = entry.getKey();
                int count = entry.getValue();

                if (currentState.getLiveCells().contains(cell)) {
                    // Survival rule: A live cell with 2 or 3 neighbors stays alive
                    if (count == 2 || count == 3) {
                        nextGenerationLiveCells.add(cell);
                    }
                } else {
                    // Birth rule: A dead cell with exactly 3 neighbors becomes alive
                    if (count == 3) {
                        nextGenerationLiveCells.add(cell);
                    }
                }
            }

            // Return the new GridState
            return new GridState(
                    currentState.getRows(),
                    currentState.getColumns(),
                    nextGenerationLiveCells,
                    currentState.getGenerationNumber() + 1);
        }
    }

    public List<? extends GameOfLifeEvent> mutate(GridState state, GameOfLifeCommand command) {
        return command.mutate(state);
    }
}
