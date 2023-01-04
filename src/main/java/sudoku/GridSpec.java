package sudoku;

import java.awt.Color;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

public class GridSpec {

    private final String name;

    private final CellSpec[] cells;

    private final Map<Color, BitSet> highlightRegions;

    private final Map<Color, List<LineSpec>> lines;

    public GridSpec(final String name, final List<CellSpec> cells, final Map<Color, BitSet> highlightRegions,
            final Map<Color, List<LineSpec>> lines) {
        this.name = name;
        this.cells = cells.toArray(CellSpec[]::new);
        this.highlightRegions = highlightRegions;
        this.lines = lines;
    }

    public String getName() {
        return name;
    }

    public Map<Color, BitSet> getHighlightRegions() {
        return highlightRegions;
    }

    public CellSpec getCell(final int i) {
        return cells[i];
    }

    public Map<Color, List<LineSpec>> getLines() {
        return lines;
    }
}
