package domain.ConwaysGameOfLife;

import java.util.Set;

public class GridState {
    private final int rows;
    private final int columns;
    private final Set<Cell> liveCells;
    private final int generationNumber;

    public GridState(int rows, int columns, Set<Cell> liveCells, int generationNumber) {
        this.rows = rows;
        this.columns = columns;
        this.liveCells = liveCells;
        this.generationNumber = generationNumber;
    }

    public int getRows() {
        return rows;
    }

    public int getColumns() {
        return columns;
    }

    public Set<Cell> getLiveCells() {
        return liveCells;
    }

    public int getGenerationNumber() {
        return generationNumber;
    }

    public static String display(GridState gridState) {
        StringBuilder display = new StringBuilder();

        // Iterate through each cell in the grid
        for (int row = 0; row < gridState.getRows(); row++) {
            for (int col = 0; col < gridState.getColumns(); col++) {
                Cell cell = new Cell(row, col);
                if (gridState.getLiveCells().contains(cell)) {
                    display.append("*"); // Live cell
                } else {
                    display.append("."); // Dead cell
                }
            }
            display.append("\n"); // Newline after each row
        }

        return display.toString();
    }
}
