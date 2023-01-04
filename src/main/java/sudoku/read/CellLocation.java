package sudoku.read;

public final class CellLocation {

    private final int row;
    private final int column;
    private final int candidate;

    public CellLocation(final int row, final int column, final int candidate) {
        this.row = row;
        this.column = column;
        this.candidate = candidate;
    }

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }

    public int getCandidate() {
        return candidate;
    }
}
