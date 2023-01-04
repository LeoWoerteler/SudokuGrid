package sudoku;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CellSpec {

    private Integer digit;

    private boolean given = true;

    private Color background;

    private final Set<Integer> pencilmarks = new HashSet<>();

    private final Set<Integer> eliminations = new HashSet<>();

    private final Map<Integer, Color> pencilmarkBackgrounds = new HashMap<>();

    public void setDigit(Integer d) {
        digit = d;
    }

    public void setGiven(final boolean isGiven) {
        given = isGiven;
    }

    public boolean isGiven() {
        return this.given;
    }

    public void setPencilmark(Integer pm) {
        pencilmarks.add(pm);
    }

    public void setElimination(Integer elim) {
        eliminations.add(elim);
    }

    public void setBackground(Color bg) {
        background = bg;
    }

    public void setPencilmarkBackground(Integer pm, Color bg) {
        pencilmarkBackgrounds.put(pm, bg);
    }

    public boolean hasPencilmark(int pm) {
        return digit == null && pencilmarks.contains(pm);
    }

    public boolean hasElimination(int pm) {
        return digit == null && eliminations.contains(pm);
    }

    public Color getBackground() {
        return background;
    }

    public Color getPencilmarkBackground(int pm) {
        return digit == null ? pencilmarkBackgrounds.get(pm) : null;
    }

    public Integer getDigit() {
        return digit;
    }
}
