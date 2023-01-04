package sudoku;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JPanel;

import org.apache.commons.lang3.tuple.Pair;

import sudoku.read.SudokuGridParser;

public final class SudokuPanel extends JPanel {

    private static final long serialVersionUID = 6739744032920633128L;

    private final AtomicReference<GridSpec> grid;

    public SudokuPanel(final AtomicReference<GridSpec> grid) {
        this.grid = grid;
    }

    void reloadGrid() {
    	// TODO make this changeable via the GUI
        final String fileName = "grid";
        try {
            grid.set(SudokuGridParser.parse(fileName, Path.of("input").resolve(fileName + ".txt")));
            repaint();
        } catch (final IOException | IllegalArgumentException e) {
            e.printStackTrace(System.err);
        }
    }

    private static final class PaintContext {

        final Color majorGridColor = new Color(0x444444);
        final Color minorGridColor = new Color(0x666666);
        final Color givenColor = new Color(0x000000);
        final Color enteredColor = new Color(0x1d00bb);

        final GridSpec gridSpec;
        final Font digitFont;
        final Font pmFont;

        PaintContext(final GridSpec gridSpec, final Font digitFont, final Font pmFont) {
            this.gridSpec = gridSpec;
            this.digitFont = digitFont;
            this.pmFont = pmFont;
        }

        String getPencilmark(final int r, final int c, final int pm) {
            return gridSpec.getCell(9 * r + c).hasPencilmark(pm) ? Integer.toString(pm + 1) : null;
        }

        boolean isPencilmarkEliminated(final int r, final int c, final int pm) {
            return gridSpec.getCell(9 * r + c).hasElimination(pm);
        }

        Color getPencilmarkBackground(final int r, final int c, final int pm) {
            return gridSpec.getCell(9 * r + c).getPencilmarkBackground(pm);
        }

        public CellSpec getCellSpec(int row, int col) {
            return gridSpec.getCell(9 * row + col);
        }

        Color cellBackgroundColor(final int r, final int c) {
            final var color = gridSpec.getCell(9 * r + c).getBackground();
            if (color != null) {
                return color;
            }
            return Color.WHITE;
        }
    }

    private static GridLayout calculateLayout(int width, int height) {
        final int gridSide = Math.min(width, height);
        final double gridBorderWidth = 0.006 * gridSide;
        final double boxSide = (gridSide - 4 * gridBorderWidth) / 3;
        final double boxBorderWidth = 0.003 * gridSide;
        final double cellSide = (boxSide - 2 * boxBorderWidth) / 3;
        final double cellBorderWidth = 0.004 * gridSide;
        final double pencilmarkSide = (cellSide - 4 * cellBorderWidth) / 3;
        final float elimWidth = (float) (0.12 * pencilmarkSide);

        final double gy = 0.5 * (height - gridSide);
        final double gx = 0.5 * (width - gridSide);
        final BoxLayout[] boxes = new BoxLayout[9];
        for (int bId = 0; bId < boxes.length; bId++) {
            final int floor = bId / 3;
            final int stack = bId % 3;
            final double by = gy + (bId / 3) * (gridBorderWidth + boxSide) + gridBorderWidth;
            final double bx = gx + (bId % 3) * (gridBorderWidth + boxSide) + gridBorderWidth;
            final CellLayout[] cells = new CellLayout[9];
            for (int cId = 0; cId < cells.length; cId++) {
                final int row = 3 * floor + (cId / 3);
                final int col = 3 * stack + (cId % 3);
                final double cy = by + (cId / 3) * (boxBorderWidth + cellSide);
                final double cx = bx + (cId % 3) * (boxBorderWidth + cellSide);
                final PencilmarkLayout[] pencilmarks = new PencilmarkLayout[9];
                for (int pId = 0; pId < pencilmarks.length; pId++) {
                    final double py = cy + (pId / 3) * (cellBorderWidth + pencilmarkSide) + cellBorderWidth;
                    final double px = cx + (pId % 3) * (cellBorderWidth + pencilmarkSide) + cellBorderWidth;
                    pencilmarks[pId] = new PencilmarkLayout(pId, px, py, pencilmarkSide, elimWidth);
                }
                cells[cId] = new CellLayout(row, col, cx, cy, cellSide, pencilmarks);
            }
            boxes[bId] = new BoxLayout(bx, by, boxSide, cells);
        }
        return new GridLayout(gx, gy, gridSide, boxes);
    }

    @Override
    protected void paintComponent(final Graphics g) {
        setBackground(Color.BLACK);
        super.paintComponent(g);
        final int width = getWidth();
        final int height = getHeight();
        paintWithSize(g, width, height);
    }

    protected void paintWithSize(final Graphics g, final int width, final int height) {
        final GridLayout layout = calculateLayout(width, height);

        final int digitSize = (int) Math.round(0.075 * layout.outline.width);
        final var digitFont = new Font("Arial", Font.PLAIN, digitSize);
        final var pmFont = digitFont.deriveFont(digitFont.getSize2D() / 3);
        final var context = new PaintContext(grid.get(), digitFont, pmFont);


        final Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        paintGrid(g2d, context, layout);

        final float outlineWidth = (float) (0.007 * layout.outline.width);
        for (final var group : context.gridSpec.getHighlightRegions().entrySet()) {
            final var color = group.getKey();
            printHighlightRegion(g2d, layout, context, color, group.getValue(), outlineWidth);
        }

        final float lineWidth = (float) (0.004 * layout.outline.width);
        for (final var group : context.gridSpec.getLines().entrySet()) {
            final var color = group.getKey();
            for (final var line : group.getValue()) {
                paintLine(g2d, layout, context, color, line, lineWidth);
            }
        }
    }

    private void paintLine(final Graphics2D g2d, final GridLayout layout, final PaintContext context,
            final Color color, final LineSpec line, final float lineWidth) {

        final var fromCell = layout.getCell(line.from.getRow(), line.from.getColumn());
        final var fromCandidate = fromCell.pencilmarks[line.from.getCandidate()];
        final var fromOutline = fromCandidate.outline;
        final var fromCenter = new Point2D.Double(fromOutline.getCenterX(), fromOutline.getCenterY());

        final var toCell = layout.getCell(line.to.getRow(), line.to.getColumn());
        final var toCandidate = toCell.pencilmarks[line.to.getCandidate()];
        final var toOutline = toCandidate.outline;
        final var toCenter = new Point2D.Double(toOutline.getCenterX(), toOutline.getCenterY());

        final var direct = new Line2D.Double(fromCenter, toCenter);
        g2d.setColor(color);
        final var stroke = new BasicStroke(lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        final var dblStroke = new BasicStroke(1.5f * lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        final Shape path = line.curve == null ? direct : curvedPath(g2d, direct, line.curve);
        final var shape = line.doubleStroke ? dblStroke.createStrokedShape(path) : path;
        final Area area = new Area(stroke.createStrokedShape(shape));
        area.subtract(new Area(fromOutline));
        area.subtract(new Area(toOutline));
        g2d.fill(area);
    }

    private Path2D.Double curvedPath(final Graphics2D g2d, final Line2D.Double line, final Pair<Double, Double> curve) {
        final AffineTransform tx = new AffineTransform();
        tx.translate(line.x1, line.y1);
        tx.rotate(Math.atan2(line.y2 - line.y1, line.x2 - line.x1));
        final var len = Point2D.distance(line.x1, line.y1, line.x2, line.y2);
        tx.scale(len, len);

        final var temp = new Point2D.Double();
        temp.setLocation(0.5 - curve.getRight(),  curve.getLeft());
        final var mid1 = tx.transform(temp, null);
        temp.setLocation(0.5 + curve.getRight(), curve.getLeft());
        final var mid2 = tx.transform(temp, null);

        final var path = new Path2D.Double();
        path.moveTo(line.x1, line.y1);
        path.curveTo(mid1.getX(), mid1.getY(), mid2.getX(), mid2.getY(), line.x2, line.y2);
        return path;
    }

    private void paintGrid(final Graphics2D g2d, final PaintContext ctx, final GridLayout grid) {
        g2d.setColor(ctx.majorGridColor);
        g2d.fill(grid.outline);
        for (final var box : grid.boxes) {
            paintBox(g2d, ctx, box);
        }
    }

    private void paintBox(final Graphics2D g2d, final PaintContext ctx, final BoxLayout box) {
        g2d.setColor(ctx.minorGridColor);
        g2d.fill(box.outline);
        for (final var cell : box.cells) {
            g2d.setColor(Color.WHITE);
            g2d.fill(cell.outline);
            paintCell(g2d, ctx, cell);
        }
    }

    private void paintCell(final Graphics2D g2d, final PaintContext ctx, final CellLayout cell) {
        g2d.setColor(ctx.cellBackgroundColor(cell.row, cell.col));
        final var outline = cell.outline;
        g2d.fill(outline);

        final var cellSpec = ctx.getCellSpec(cell.row, cell.col);
        final var digit = Optional.ofNullable(cellSpec.getDigit())
                .map(i -> Integer.toString(i + 1))
                .orElse(null);
        if (digit != null) {
            g2d.setColor(cellSpec.isGiven() ? ctx.givenColor : ctx.enteredColor);
            g2d.setFont(ctx.digitFont);
            final var bounds = g2d.getFontMetrics(ctx.digitFont).getStringBounds(digit, g2d);
            g2d.drawString(digit, (float) (outline.getCenterX() - bounds.getCenterX()),
                    (float) (outline.getCenterY() - bounds.getCenterY()));
        } else {
            for (final var pencilmark : cell.pencilmarks) {
                paintPencilmark(g2d, ctx, cell.row, cell.col, pencilmark);
            }
        }
    }

    private void paintPencilmark(final Graphics2D g2d, final PaintContext ctx,
            final int row, final int col, final PencilmarkLayout pencilmark) {
        final var outline = pencilmark.outline;
        final var bg = ctx.getPencilmarkBackground(row, col, pencilmark.digit);
        if (bg != null) {
            g2d.setColor(bg);
            g2d.fill(outline);
        }
        final var str = ctx.getPencilmark(row, col, pencilmark.digit);
        if (str != null) {
            final var bounds = g2d.getFontMetrics(ctx.pmFont).getStringBounds(str, g2d);
            g2d.setColor(ctx.givenColor);
            g2d.setFont(ctx.pmFont);
            g2d.drawString(str, (float) (outline.getCenterX() - bounds.getCenterX()),
                    (float) (outline.getCenterY() - bounds.getCenterY()));
        }
        if (ctx.isPencilmarkEliminated(row, col, pencilmark.digit)) {
            final var oldStroke = g2d.getStroke();
            g2d.setColor(Color.RED);
            final var width = (float) pencilmark.elimWidth;
            g2d.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER));
            g2d.draw(new Line2D.Double(outline.getMinX() + width, outline.getMaxY() - width,
                    outline.getMaxX() - width, outline.getMinY() + width));
            g2d.setStroke(oldStroke);
        }
    }

    private void printHighlightRegion(final Graphics2D g2d, final GridLayout layout,
            final PaintContext context, final Color color, final BitSet cells, final float strokeWidth) {
        final var area = new Area();
        cells.stream().forEach(cellId -> {
            final int r = cellId / 9;
            final int c = cellId % 9;
            final int floor = r / 3;
            final int stack = c / 3;
            final int boxId = 3 * floor + stack;
            final var box = layout.boxes[boxId];
            final var boxRect = box.outline;
            final int br = r - 3 * floor;
            final int bc = c - 3 * stack;
            final var cell = box.cells[3 * br + bc];
            final var cellRect = cell.outline;
            final double padding = 1 * (boxRect.width - 3 * cellRect.width);
            final double side = cellRect.width + 2 * padding;
            final var rect = new Rectangle2D.Double(cellRect.x - padding, cellRect.y - padding, side, side);
            area.add(new Area(rect));
        });
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.draw(area);
    }

    private static abstract class GridObject {

        final Rectangle2D.Double outline;

        GridObject(final double x, final double y, final double side) {
            this.outline = new Rectangle2D.Double(x, y, side, side);
        }
    }

    private static final class GridLayout extends GridObject {

        final BoxLayout[] boxes;

        GridLayout(final double x, final double y, final double side, final BoxLayout[] boxes) {
            super(x, y, side);
            this.boxes = boxes;
        }

        public CellLayout getCell(int row, int column) {
            final int floor = row / 3;
            final int stack = column / 3;
            final var box = boxes[3 * floor + stack];
            final int boxRow = row - 3 * floor;
            final int boxColumn = column - 3 * stack;
            return box.cells[3 * boxRow + boxColumn];
        }
    }

    private static final class BoxLayout extends GridObject {

        final CellLayout[] cells;

        BoxLayout(final double x, final double y, final double side, final CellLayout[] cells) {
            super(x, y, side);
            this.cells = cells;
        }
    }

    private static final class CellLayout extends GridObject {

        final int row;

        final int col;

        final PencilmarkLayout[] pencilmarks;

        CellLayout(final int row, final int col, final double x, final double y, final double side,
                final PencilmarkLayout[] pencilmarks) {
            super(x, y, side);
            this.row = row;
            this.col = col;
            this.pencilmarks = pencilmarks;
        }
    }

    private static final class PencilmarkLayout extends GridObject {

        final int digit;

        final float elimWidth;

        PencilmarkLayout(final int digit, final double x, final double y, final double side,
                final float elimWidth) {
            super(x, y, side);
            this.digit = digit;
            this.elimWidth = elimWidth;
        }
    }
}
