package sudoku.read;

import java.util.BitSet;
import java.util.function.BiConsumer;

public final class Selector {

    final BitSet candidates;
    final BitSet rows;
    final BitSet columns;

    public Selector(final BitSet candidates, final BitSet rows, final BitSet columns) {
        this.candidates = candidates;
        this.rows = rows;
        this.columns = columns;
    }

    public void forEachCell(final BiConsumer<Integer, Integer> consumer) {
        rows.stream().forEach(r -> columns.stream().forEach(c -> consumer.accept(r, c)));
    }
}
