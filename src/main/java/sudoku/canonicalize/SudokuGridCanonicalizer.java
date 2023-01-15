package sudoku.canonicalize;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Searches the set of all structurally equivalent Sudoku grids to a given initial one to find its <i>canonical</i>
 * equivalent grid. This is the the member of the set of all equivalent grids which has the puzzle string which is
 * smallest when interpreted as a 81-digit integer.
 */
public final class SudokuGridCanonicalizer {

    /** Number of digits in each house. */
    private static final int HOUSE_SIZE = 9;

    /** Number of cells in the grid. */
    private static final int GRID_SIZE = 81;

    /** Number of bits needed to store one digit. */
    private static final int DIGIT_BITS = 4;

    /** Comparator comparing two grids lexicographically. */
    private static final Comparator<BitSet> GRID_COMPARATOR = (a, b) -> {
        int pa = a.nextSetBit(0);
        int pb = b.nextSetBit(0);
        while (pa == pb && pa >= 0) {
            pa = a.nextSetBit(pa + 1);
            pb = b.nextSetBit(pb + 1);
        }
        return pa == pb ? 0 : pa < 0 ? -1 : pb < 0 ? 1 : pb - pa;
    };

    /** Minimal set of moves for rearranging a Sudoku grid (reassigning digits excluded). */
    private static final List<UnaryOperator<BitSet>> MOVES = Arrays.asList(
            // swap neighboring rows in the first floor
            new RowRearranger(1, 0, 2),
            // swap the two outer rows in the first floor
            new RowRearranger(2, 1, 0),
            // swap neighboring floors
            new RowRearranger(3, 4, 5, 0, 1, 2, 6, 7, 8),
            // swap the two outer floors
            new RowRearranger(6, 7, 8, 3, 4, 5, 0, 1, 2),
            // transpose the grid, rows become columns and vice versa
            new GridTransposer());

    /** Search space of all equivalent grids discovered until now. */
    private final Set<BitSet> searchSpace;

    /** Work queue storing all grids and transformations which have yet to be explored. */
    private final ArrayDeque<Entry<BitSet, UnaryOperator<BitSet>>> queue;

    /** Lexicographically smallest grid which has been discovered until now. */
    private BitSet leastGrid;

    /** Number of puzzles in the search space after which to print an update, {@code -1} to deactivate. */
    private int printProgress = -1;

    /**
     * Computes the Sudoku grid which is structurally equivalent to the given puzzle  while being smallest when
     * interpreted as an 81-digit number.
     *
     * @param puzzle puzzle string of the Sudoku to find the canonical representation of
     * @return canonical representation
     */
    public static String canonicalize(final String puzzle) {
        if (puzzle.length() != GRID_SIZE) {
            throw new IllegalArgumentException("Unexpected puzzle length: " + puzzle.length());
        }
        final BitSet initial = fromPuzzleString(puzzle);
        final var canonicalizer = new SudokuGridCanonicalizer(initial);
        canonicalizer.exploreSearchSpace();
        return toPuzzleString(canonicalizer.leastGrid);
    }

    /**
     * Creates a new canonicalizer for the given grid.
     *
     * @param initial grid that is being canonicalized
     */
    private SudokuGridCanonicalizer(final BitSet initial) {
        searchSpace = new HashSet<>();
        queue = new ArrayDeque<>();
        MOVES.forEach(move -> queue.addLast(new SimpleImmutableEntry<>(initial, move)));
    }

    /**
     * Explores the search space of equivalent puzzles by iteratively applying all transformations to all grids.
     */
    private void exploreSearchSpace() {
        while (!queue.isEmpty()) {
            final var current = queue.removeFirst();
            final var currentGrid = current.getKey();
            final var currentMove = current.getValue();
            final var neighbor = currentMove.apply(currentGrid);
            if (searchSpace.add(neighbor)) {
                // we found a new equivalent grid, update our canonical candidate
                final var renumbered = renumberGrid(neighbor);
                if (leastGrid == null || GRID_COMPARATOR.compare(renumbered, leastGrid) < 0) {
                    leastGrid = renumbered;
                }

                // schedule further transformations
                for (final var move : MOVES) {
                    if (move != currentMove) {
                        // applying the same move twice returns the same grid again, that's unnecessary
                        queue.addLast(new SimpleImmutableEntry<>(neighbor, move));
                    }
                }

                // report progress
                if (printProgress >= 0 && searchSpace.size() % printProgress == 0) {
                    System.err.println("Current search space size: " + searchSpace.size());
                }
            }
        }
    }

    /**
     * Renumbers the given grid so that the lexicographically minimal equivalent grid is created.
     *
     * @param grid input grid
     * @return renumbered output grid
     */
    private BitSet renumberGrid(final BitSet grid) {
        final var outGrid = new BitSet();
        final int[] digitPermutation = new int[HOUSE_SIZE];
        int nextDigit = 1;
        for (int i = 0; i < GRID_SIZE; i++) {
            final var digit = getDigit(grid, i);
            if (digit > 0) {
                if (digitPermutation[digit - 1] == 0) {
                    digitPermutation[digit - 1] = nextDigit++;
                }
                setDigit(outGrid, i, digitPermutation[digit - 1]);
            }
        }
        return outGrid;
    }

    /**
     * Converts a {@link BitSet} to a puzzle string.
     *
     * @param grid grid represented as a bit set
     * @return corresponding puzzle string
     */
    private static String toPuzzleString(final BitSet grid) {
        final var out = new StringBuilder();
        for (int i = 0; i < GRID_SIZE; i++) {
            out.append((char) ('0' + getDigit(grid, i)));
        }
        return out.toString();
    }

    /**
     * Converts a puzzle string to a {@link BitSet}.
     *
     * @param puzzle grid represented as a puzzle string
     * @return corresponding bit set
     */
    private static BitSet fromPuzzleString(final String puzzle) {
        final BitSet grid = new BitSet(DIGIT_BITS * GRID_SIZE);
        for (int i = 0; i < GRID_SIZE; i++) {
            final var c = puzzle.charAt(i);
            if ('1' <= c && c <= '9') {
                setDigit(grid, i, c - '0');
            }
        }
        return grid;
    }

    /**
     * Writes a given digit into a specific cell in the given grid.
     *
     * @param grid grid to modify
     * @param cell cell to change the contents of, must be between 0 and 80
     * @param digit digit to set, must be between 0 and 9
     */
    private static void setDigit(final BitSet grid, final int cell, final int digit) {
        final var offset = DIGIT_BITS * cell;
        grid.set(offset + 0, (digit & 0b1000) != 0);
        grid.set(offset + 1, (digit & 0b0100) != 0);
        grid.set(offset + 2, (digit & 0b0010) != 0);
        grid.set(offset + 3, (digit & 0b0001) != 0);
    }

    /**
     * Reads digit from a specific cell in the given grid.
     *
     * @param grid grid to modify
     * @param cell cell to change the contents of, must be between 0 and 80
     * @return digit in the cell, between 0 and 9
     */
    private static int getDigit(final BitSet grid, final int cell) {
        final var offset = DIGIT_BITS * cell;
        final var b0 = grid.get(offset + 0) ? 0b1000 : 0;
        final var b1 = grid.get(offset + 1) ? 0b0100 : 0;
        final var b2 = grid.get(offset + 2) ? 0b0010 : 0;
        final var b3 = grid.get(offset + 3) ? 0b0001 : 0;
        return b0 + b1 + b2 + b3;
    }

    /**
     * Command-line interface for the canonicalizer, each argument is interpreted as a puzzle string.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java -jar <path/to/this.jar> <puzzle-string>...");
            System.exit(1);
        }

        for (final var puzzle : args) {
            if (puzzle.length() != GRID_SIZE) {
                throw new IllegalArgumentException("Unexpected puzzle length for '" + puzzle + "': " + puzzle.length());
            }
            final BitSet initial = fromPuzzleString(puzzle);
            final var canonicalizer = new SudokuGridCanonicalizer(initial);
            canonicalizer.printProgress = 100_000;
            canonicalizer.exploreSearchSpace();
            System.out.println(toPuzzleString(canonicalizer.leastGrid));
        }
    }

    /**
     * Grid transformation which creates a copy of the input grid in which some or all lines are rearranged.
     */
    private static final class RowRearranger implements UnaryOperator<BitSet> {
        /** Permutation of row indexes, may be shorter than nine lines. */
        private final int[] rowPermutation;

        /**
         * Creates a new row rearranger with the given permutation sequence.
         * Row indexes in the sequence appear in the order in which the rows of the input grid are supposed to appear in
         * the output. If fewer than nine indexes are specified, the remaining rows are left untouched.
         *
         * @param rowPermutation row permutation sequence
         */
        RowRearranger(final int... rowPermutation) {
            this.rowPermutation = rowPermutation;
        }

        @Override
        public BitSet apply(final BitSet in) {
            final var out = (BitSet) in.clone();
            for (int outRow = 0; outRow < rowPermutation.length; outRow++) {
                final var inRow = rowPermutation[outRow];
                // only touch rows if they are actually being modified
                if (inRow != outRow) {
                    for (int col = 0; col < HOUSE_SIZE; col++) {
                        setDigit(out, outRow * HOUSE_SIZE + col, getDigit(in, inRow * HOUSE_SIZE + col));
                    }
                }
            }
            return out;
        }
    }

    /**
     * Grid transformation which transposes the input grid, so the rows in the input grid are columns in the output grid
     * and vice versa.
     */
    private static final class GridTransposer implements UnaryOperator<BitSet> {
        @Override
        public BitSet apply(final BitSet in) {
            final var out = new BitSet();
            for (int i = 0; i < GRID_SIZE; i++) {
                final var r = i / HOUSE_SIZE;
                final var c = i % HOUSE_SIZE;
                setDigit(out, HOUSE_SIZE * c + r, getDigit(in, i));
            }
            return out;
        }
    }
}
