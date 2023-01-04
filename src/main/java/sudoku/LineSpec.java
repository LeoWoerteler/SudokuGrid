package sudoku;

import org.apache.commons.lang3.tuple.Pair;

import sudoku.read.CellLocation;

public class LineSpec {

    final CellLocation from;

    final CellLocation to;

    final Pair<Double, Double> curve;

    final boolean doubleStroke;

    public LineSpec(final CellLocation from, final CellLocation to, final Pair<Double, Double> curve,
            final boolean doubleStroke) {
        this.from = from;
        this.to = to;
        this.curve = curve;
        this.doubleStroke = doubleStroke;
    }
}
