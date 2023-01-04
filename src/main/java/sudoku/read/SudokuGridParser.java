package sudoku.read;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.apache.commons.lang3.tuple.Pair;

import sudoku.CellSpec;
import sudoku.GridSpec;
import sudoku.LineSpec;

public class SudokuGridParser {

    private static final Pattern SELECTOR_PATTERN = Pattern.compile("^(\\([1-9]+\\))?r([1-9]+)c([1-9]+)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern COLOR_PATTERN = Pattern.compile("#([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})", Pattern.CASE_INSENSITIVE);

    private static final Pattern LINE_PATTERN = Pattern.compile("^(D)?LINE(?:\\[(-?\\d+(?:\\.\\d+)?),(\\d+(?:\\.\\d+)?)\\])?\\s+");

    public static GridSpec parse(final String name, final Path file) throws IOException {
        final List<CellSpec> cells;
        try (BufferedReader r = Files.newBufferedReader(file)) {
            final var gridRes = readGrid(r);
            cells = gridRes.getLeft();
            int lineNo = gridRes.getRight();

            final var regions = new HashMap<Color, BitSet>();
            final var lines = new HashMap<Color, List<LineSpec>>();
            for (String line; (line = r.readLine()) != null;) {
                lineNo++;
                final var colorMatcher = COLOR_PATTERN.matcher(line);
                final var lineMatcher = LINE_PATTERN.matcher(line);
                if (line.startsWith("GROUP ") && colorMatcher.find(6) && colorMatcher.start() == 6) {
                    readBackgroundColor(regions, line, colorMatcher);
                } else if (line.startsWith("LINE") || line.startsWith("DLINE")) {
                    if (!lineMatcher.find()) {
                        throw new IllegalArgumentException("Broken LINE specifier in line " + lineNo + ": '" + line + "'");
                    }
                    if (!colorMatcher.find(lineMatcher.end()) || colorMatcher.start() != lineMatcher.end()) {
                        throw new IllegalArgumentException("LINE specified must be followed by a color, found '" + line + "' in line " + lineNo);
                    }
                    readLineSpec(lines, line, lineMatcher, colorMatcher);
                } else if (colorMatcher.find() && colorMatcher.start() == 0) {
                    readOutlineColor(cells, line, colorMatcher);
                }
            }

            return new GridSpec(name, cells, regions, lines);
        }
    }

    private static void readLineSpec(final Map<Color, List<LineSpec>> lines, final String line,
            final Matcher lineMatcher, final Matcher colorMatcher) {
        final var curve = lineMatcher.end(2) < 0 ? null
                : Pair.of(Double.parseDouble(lineMatcher.group(2)), Double.parseDouble(lineMatcher.group(3)));
        final Color color = extractColor(colorMatcher);
        final String[] rest = line.substring(colorMatcher.end()).trim().split("\\s+");
        if (rest.length != 2) {
            throw new IllegalArgumentException("Line needs exactly two pencilmarks (start and end), found '" + line + "'");
        }
        final var startSelector = parseSelector(rest[0])
                .filter(s -> s.rows.cardinality() == 1 && s.columns.cardinality() == 1 && s.candidates != null && s.candidates.cardinality() == 1)
                .orElseThrow(() -> new IllegalArgumentException("Line start must be a single pencilmark, found '" + rest[0] + "'"));
        final var start = new CellLocation(startSelector.rows.nextSetBit(0), startSelector.columns.nextSetBit(0), startSelector.candidates.nextSetBit(0));
        final var endSelector = parseSelector(rest[1])
                .filter(s -> s.rows.cardinality() == 1 && s.columns.cardinality() == 1 && s.candidates != null && s.candidates.cardinality() == 1)
                .orElseThrow(() -> new IllegalArgumentException("Line end must be a single pencilmark, found '" + rest[1] + "'"));
        final var end = new CellLocation(endSelector.rows.nextSetBit(0), endSelector.columns.nextSetBit(0), endSelector.candidates.nextSetBit(0));
        lines.computeIfAbsent(color, k -> new ArrayList<>()).add(new LineSpec(start, end, curve, lineMatcher.group(1) != null));
    }

    private static Optional<Selector> parseSelector(final String str) {
        final Matcher matcher = SELECTOR_PATTERN.matcher(str);
        if (matcher.find() && matcher.start() == 0 && matcher.end() == str.length()) {
            return Optional.of(toSelector(matcher));
        }
        return Optional.empty();
    }

    private static void readOutlineColor(final List<CellSpec> cells, String line, final Matcher colorMatcher) {
        final Color color = extractColor(colorMatcher);
        for (final String desc : line.substring(colorMatcher.end()).trim().split("\\s+")) {
            assignColors(cells, color, desc);
        }
    }

    private static void readBackgroundColor(final HashMap<Color, BitSet> regions, String line,
            final Matcher colorMatcher) {
        final Color color = extractColor(colorMatcher);
        final BitSet cellSet = new BitSet();
        for (final String desc : line.substring(colorMatcher.end()).trim().split("\\s+")) {
            final var matcher = SELECTOR_PATTERN.matcher(desc);
            if (!matcher.find()) {
                throw new IllegalArgumentException("Not a cell or pencilmark selector: " + desc);
            } else if (matcher.end(1) >= 0) {
                throw new IllegalArgumentException("Pencilmark selectors not allowed in groups: " + line);
            }
            toSelector(matcher).forEachCell((r, c) -> cellSet.set(9 * r + c));
        }
        if (cellSet.isEmpty()) {
            throw new IllegalArgumentException("Empty group: " + line);
        }
        if (regions.put(color, cellSet) != null) {
            throw new IllegalArgumentException("Duplicate highlight group color: "
                    + line.substring(colorMatcher.start(), colorMatcher.end()));
        }
    }

    private static Pair<List<CellSpec>, Integer> readGrid(BufferedReader r) throws IOException {
        final var grid = new ArrayList<CellSpec>(81);
        int lineNo = 0;
        for (String line; grid.size() < 81 && (line = r.readLine()) != null;) {
            lineNo++;
            final String cleaned = line.replaceAll("[^0-9 \t*-]+", "").trim();
            if (cleaned.matches(".*[0-9].*")) {
                final String[] parts = cleaned.split("\\s+");
                if (parts.length != 9) {
                    throw new IllegalArgumentException("Row + " + lineNo + " is not 9 cells long: " + line);
                }
                for (int p = 0; p < 9; p++) {
                    final char[] chars = parts[p].toCharArray();
                    final CellSpec currentCell = new CellSpec();
                    grid.add(currentCell);
                    if (chars.length == 1) {
                        if (chars[0] != '0') {
                            if (chars[0] > '0' && chars[0] <= '9') {
                                currentCell.setDigit((chars[0] - '0') - 1);
                            } else {
                                throw new IllegalArgumentException("Nonsensical cell specification '" + parts[p] + "' in line " + lineNo);
                            }
                        }
                    } else if (chars.length == 2 && chars[0] == '*') {
                        if (chars[1] > '0' && chars[1] <= '9') {
                            currentCell.setDigit((chars[1] - '0') - 1);
                            currentCell.setGiven(false);
                        } else {
                            throw new IllegalArgumentException("Nonsensical cell specification '" + parts[p] + "' in line " + lineNo);
                        }
                    } else {
                        boolean eliminate = false;
                        for (int i = 0; i < chars.length; i++) {
                            if (chars[i] == '-') {
                                eliminate = true;
                            } else if (chars[i] == '*') {
                                throw new IllegalArgumentException("Pencilmarks are never given, nonsensical '*' in line " + lineNo + "': '" + parts[p] + "'");
                            } else {
                                final int val = chars[i] - '0';
                                if (val != 0) {
                                    currentCell.setPencilmark(val - 1);
                                    if (eliminate) {
                                        currentCell.setElimination(val - 1);
                                    }
                                }
                                eliminate = false;
                            }
                        }
                    }
                }
            }
        }
        if (grid.size() < 81) {
            throw new IllegalArgumentException("Puzzle is less than 9 rows long.");
        }
        return Pair.of(grid, lineNo);
    }

    private static Color extractColor(final Matcher colorMatcher) {
        final int[] rgba = IntStream.range(1, 5).map(i -> Integer.parseInt(colorMatcher.group(i), 16)).toArray();
        return new Color(rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    private static void assignColors(List<CellSpec> cells, Color color, String desc) {
        final var matcher = SELECTOR_PATTERN.matcher(desc);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Not a cell or pencilmark selector: " + desc);
        }
        final var selector = toSelector(matcher);
        selector.forEachCell((r, c) -> {
            final CellSpec cell = cells.get(9 * r + c);
            if (selector.candidates == null) {
                cell.setBackground(color);
            } else {
                selector.candidates.stream().forEach(pm -> {
                    cell.setPencilmarkBackground(pm, color);
                });
            }
        });
    }

    private static Selector toSelector(final Matcher matcher) {
        final var pms = matcher.end(1) < 0 ? null : digitsToBitSet(matcher.group(1).replaceAll("[()]", ""));
        final var rows = digitsToBitSet(matcher.group(2));
        final var cols = digitsToBitSet(matcher.group(3));
        return new Selector(pms, rows, cols);
    }

    private static final BitSet digitsToBitSet(final String digits) {
        final var set = new BitSet();
        digits.chars().map(c -> c - '0').forEach(d -> set.set(d - 1));
        return set;
    }
}
