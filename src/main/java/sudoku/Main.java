package sudoku;

import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JFrame;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;

public class Main {
    public static void main(final String[] args) throws IOException {
        final JFrame frame = new JFrame("Sudoku Grid");

        final var grid = new AtomicReference<GridSpec>();
        final SudokuPanel panel = new SudokuPanel(grid);
        panel.setFocusable(true);
        panel.setPreferredSize(new Dimension(512, 512));
        panel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                super.keyReleased(e);
                if (e.getKeyChar() == 'p') {
                	// press the 'P' key to create an SVG picture in the `pictures/` subdirectory
                    final var spec = grid.get();
                    final var path = Path.of("pictures/" + spec.getName() + "_" + System.currentTimeMillis() + ".svg");
                    System.out.println("Exporting SVG to " + path);

                    final var domImpl = GenericDOMImplementation.getDOMImplementation();
                    final var document = domImpl.createDocument("http://www.w3.org/2000/svg", "svg", null);
                    final var svgGenerator = new SVGGraphics2D(document);
                    panel.paintWithSize(svgGenerator, 512, 512);
                    try (Writer out = Files.newBufferedWriter(path)) {
                        svgGenerator.stream(out, true);
                    } catch (Exception e2) {
                        e2.printStackTrace(System.err);
                    }
                }
            }
        });
        panel.reloadGrid();

        frame.setContentPane(panel);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        // update the grid every 500 milliseconds
        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                panel.reloadGrid();
            }
        }, 0, 500);
    }

}
