package sudoku;

import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;

public class Main {

    private static final int SIZE = 512;

    public static void main(final String[] args) throws IOException {
        final JFrame frame = new JFrame("Sudoku Grid");

        final var grid = new AtomicReference<GridSpec>();
        final SudokuPanel panel = new SudokuPanel(grid);
        panel.setFocusable(true);
        panel.setPreferredSize(new Dimension(SIZE, SIZE));
        panel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                super.keyReleased(e);
                final var keyChar = e.getKeyChar();
                final boolean svg = keyChar == 's', png = keyChar == 'p';
                if (svg || png) {
                    // press the 'S' key to create an SVG picture or the 'P' key to create a PNG picture
                    // in the `pictures/` subdirectory
                    final var spec = grid.get();
                    final var path = Path.of(String.format("pictures/%s_%d.%s", spec.getName(),
                            System.currentTimeMillis(), svg ? "svg" : "png"));
                    System.out.println("Exporting " + (svg ? "SVG" : "PNG") + " to " + path);

                    if (svg) {
                        final var domImpl = GenericDOMImplementation.getDOMImplementation();
                        final var document = domImpl.createDocument("http://www.w3.org/2000/svg", "svg", null);
                        final var svgGenerator = new SVGGraphics2D(document);
                        panel.paintWithSize(svgGenerator, SIZE, SIZE);
                        try (Writer out = Files.newBufferedWriter(path)) {
                            svgGenerator.stream(out, true);
                        } catch (Exception e2) {
                            e2.printStackTrace(System.err);
                        }
                    } else {
                        final var image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
                        panel.paintWithSize(image.createGraphics(), SIZE, SIZE);
                        try (final var outStream = Files.newOutputStream(path)) {
                            ImageIO.write(image, "png", outStream);
                        } catch (IOException e2) {
                            e2.printStackTrace(System.err);
                        }
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
