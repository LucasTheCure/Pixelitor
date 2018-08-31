/*
 * Copyright 2018 Laszlo Balazs-Csiki and Contributors
 *
 * This file is part of Pixelitor. Pixelitor is free software: you
 * can redistribute it and/or modify it under the terms of the GNU
 * General Public License, version 3 as published by the Free
 * Software Foundation.
 *
 * Pixelitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Pixelitor. If not, see <http://www.gnu.org/licenses/>.
 */

package pixelitor.tools.transform;

import pixelitor.gui.View;
import pixelitor.tools.shapes.ShapeType;
import pixelitor.tools.util.PRectangle;

import javax.swing.*;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import static java.awt.RenderingHints.KEY_ANTIALIASING;
import static java.awt.RenderingHints.VALUE_ANTIALIAS_ON;

/**
 * Independent test program for a {@link TransformBox}
 */
public class TransformBoxMain {
    private TransformBoxMain() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(TransformBoxMain::buildGUI);
    }

    private static void buildGUI() {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }

        JFrame f = new JFrame("Test");
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        TestView testView = new TestView();
        f.add(testView);

        JMenuBar menuBar = new JMenuBar();
        f.setJMenuBar(menuBar);
        setupMenus(menuBar, testView);

        f.pack();
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    private static void setupMenus(JMenuBar menuBar, TestView testView) {
        JMenu zoomMenu = new JMenu("Zoom");
        menuBar.add(zoomMenu);

        zoomMenu.add(new AbstractAction("80%") {
            @Override
            public void actionPerformed(ActionEvent e) {
                testView.setViewScale(0.8);
            }
        });
        zoomMenu.add(new AbstractAction("100%") {
            @Override
            public void actionPerformed(ActionEvent e) {
                testView.setViewScale(1.0);
            }
        });
        zoomMenu.add(new AbstractAction("120%") {
            @Override
            public void actionPerformed(ActionEvent e) {
                testView.setViewScale(1.2);
            }
        });

        JMenu actionsMenu = new JMenu("Actions");
        menuBar.add(actionsMenu);
        actionsMenu.add(new AbstractAction("Reset All") {
            @Override
            public void actionPerformed(ActionEvent e) {
                testView.resetAll();
            }
        });
    }

    static class TestView extends JComponent implements View {
        private double viewScale = 1.0f;
        private double canvasStartX;
        private double canvasStartY;
        private static final int canvasWidth = 300;
        private static final int canvasHeight = 300;
        private final Dimension size = new Dimension(600, 400);

        PRectangle prect;
        TransformBox transformBox;

        Shape unTransformedShape;
        Shape transformedShape;

        public TestView() {
            init();
            addListeners();
        }

        private void init() {
            setSize(size);
            calcCanvasStart();

            prect = PRectangle.fromIm(50, 50, 200, 100, this);
            Rectangle compSpaceRect = prect.getCo();

            unTransformedShape = ShapeType.CAT.getShape(prect.toImDrag());
            transformedShape = unTransformedShape;
            transformBox = new TransformBox(compSpaceRect, this,
                    at -> transformedShape =
                            at.createTransformedShape(unTransformedShape));

            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    transformBox.viewSizeChanged(TestView.this);
                    repaint();
                }
            });
        }

        private void addListeners() {
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    transformBox.mousePressed(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    transformBox.mouseReleased(e);
                }
            });
            addMouseMotionListener(new MouseMotionListener() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    transformBox.mouseDragged(e);
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    transformBox.mouseMoved(e);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            calcCanvasStart();

            // set up image space
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);

            AffineTransform origTransform = g2.getTransform();
            g2.translate(canvasStartX, canvasStartY);
            g2.scale(viewScale, viewScale);

            // fill background with white
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, canvasWidth, canvasHeight);


            // fill the shape with pink
            g2.setColor(new Color(255, 191, 141));
            g2.fill(transformedShape);

            g2.setTransform(origTransform);

            transformBox.paint(g2);

        }

        private void calcCanvasStart() {
            canvasStartX = (getWidth() - canvasWidth) / 2.0;
            canvasStartY = (getHeight() - canvasHeight) / 2.0;
        }

        @Override
        public Dimension getPreferredSize() {
            return size;
        }

        @Override
        public Dimension getMinimumSize() {
            return size;
        }

        @Override
        public double componentXToImageSpace(double coX) {
            return ((coX - canvasStartX) / viewScale);
        }

        @Override
        public double componentYToImageSpace(double coY) {
            return ((coY - canvasStartY) / viewScale);
        }

        @Override
        public double imageXToComponentSpace(double imX) {
            return canvasStartX + imX * viewScale;
        }

        @Override
        public double imageYToComponentSpace(double imY) {
            return canvasStartY + imY * viewScale;
        }

        @Override
        public Point2D componentToImageSpace(Point2D co) {
            return new Point2D.Double(
                    componentXToImageSpace(co.getX()),
                    componentYToImageSpace(co.getY()));
        }

        @Override
        public Point2D imageToComponentSpace(Point2D im) {
            return new Point2D.Double(
                    imageXToComponentSpace(im.getX()),
                    imageYToComponentSpace(im.getY()));
        }

        @Override
        public Rectangle2D componentToImageSpace(Rectangle co) {
            return new Rectangle.Double(
                    componentXToImageSpace(co.x),
                    componentYToImageSpace(co.y),
                    (co.getWidth() / viewScale),
                    (co.getHeight() / viewScale)
            );
        }

        @Override
        public Rectangle imageToComponentSpace(Rectangle2D im) {
            return new Rectangle(
                    (int) imageXToComponentSpace(im.getX()),
                    (int) imageYToComponentSpace(im.getY()),
                    (int) (im.getWidth() * viewScale),
                    (int) (im.getHeight() * viewScale)
            );
        }

        // TODO untested
        @Override
        public AffineTransform getImageToComponentTransform() {
            AffineTransform t = new AffineTransform();
            t.translate(canvasStartX, canvasStartY);
            t.scale(viewScale, viewScale);
            return t;
        }

        // TODO untested
        @Override
        public AffineTransform getComponentToImageTransform() {
            AffineTransform t = new AffineTransform();
            double s = 1.0 / viewScale;
            t.scale(s, s);
            t.translate(-canvasStartX, -canvasStartY);
            return t;
        }

        public void setViewScale(double viewScale) {
            this.viewScale = viewScale;
            transformBox.viewSizeChanged(this);
            repaint();
        }

        public double getViewScale() {
            return viewScale;
        }

        public void resetAll() {
            init();
            repaint();
        }
    }
}
