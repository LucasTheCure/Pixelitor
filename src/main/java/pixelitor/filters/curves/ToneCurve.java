package pixelitor.filters.curves;

import com.jhlabs.image.Curve;
import com.jhlabs.image.ImageMath;

import java.awt.*;
import java.awt.geom.Path2D;

/**
 * Represents single tone curve
 * Points coordinates are defined from 0.0F to 1.0F on x/y axis
 * Any point (that comes from user coordinates space) must be first normalized
 * by width, height to curve coordinates space
 *
 * @author Łukasz Kurzaj lukaszkurzaj@gmail.com
 */
public class ToneCurve {
    private final static int MAX_KNOTS = 16;
    private final static int KNOT_RADIUS_PX = 6;
    private final static float KNOT_RADIUS = 0.04F;
    private final static float NEARBY_RADIUS = 0.08F;
    public Curve curve = new Curve();
    public ToneCurveType curveType;
    private int width = 255;
    private int height = 255;
    private int[] curvePlotData;
    private boolean isDirty = true;
    private boolean active = false;
    private Graphics2D gr;
    private BasicStroke curveStroke = new BasicStroke(1);
    private BasicStroke pointStroke = new BasicStroke(2);

    public ToneCurve(ToneCurveType curveType) {
        this.curveType = curveType;
    }

    public void setSize(int width, int height){
        this.width = width;
        this.height = height;
    }

    public void reset() {
        curve.x = new float[] {0, 1};
        curve.y = new float[] {0, 1};
        isDirty = true;
    }

    private void initCurvePlotData() {
        if (isDirty) {
            isDirty = false;
            curvePlotData = curve.makeTable();
        }
    }

    private boolean isClose(Point.Float p, Point.Float q)  {
        if(Math.abs(p.x - q.x) < NEARBY_RADIUS) {
            if(Math.abs(p.y - q.y) < NEARBY_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private void clampPoint(Point.Float p)  {
        p.x = ImageMath.clamp(p.x, 0, 1);
        p.y = ImageMath.clamp(p.y, 0, 1);
    }

    public int addKnot(Point.Float p, boolean allowReplace) {
        // clamp to boundaries [0,1]
        clampPoint(p);

        int lastIndex = curve.x.length - 1;
        int index = curve.findKnotPos(p.x);

        // cannot add knot at first/last position
        if (index <=0 || index > lastIndex) {
            return -1;
        }

        // if point is too close to next/prev knot -> replace the nearest
        // this protect against placing two knots too close to each other
        if (allowReplace) {
            int prevIndex = index-1;
            if (isClose(p, new Point.Float(curve.x[prevIndex], curve.y[prevIndex]))) {
                setKnotPosition(prevIndex, p);
                return prevIndex;
            } else if (isClose(p, new Point.Float(curve.x[index], curve.y[index]))) {
                setKnotPosition(index, p);
                return index;
            }
        }

        // check for max knot limit
        if (curve.x.length >= MAX_KNOTS) {
            return -1;
        }

        isDirty = true;
        return curve.addKnot(p.x, p.y);
    }

    public void deleteKnot(int index) {
        if (index < 0 || index > curve.x.length -1) {
            return;
        }

        if (curve.x.length <= 2) {
            return;
        }

        isDirty = true;
        curve.removeKnot(index);
    }

    /**
     * Set new knot location
     * @param index point index
     * @param p normalized point data
     */
    public void setKnotPosition(int index, Point.Float p){
        int lastIndex = curve.x.length - 1;

        if (index <0 || index > lastIndex) {
            return;
        }

        // check prev/next index - knots cannot change they index
        if (index > 0 && p.x < curve.x[index-1]) {
            p.x = curve.x[index-1];
        } else if (index < lastIndex && p.x > curve.x[index+1]) {
            p.x = curve.x[index+1];
        }

        curve.x[index] = ImageMath.clamp(p.x, 0, 1);
        curve.y[index] = ImageMath.clamp(p.y, 0, 1);
        isDirty = true;
    }

    /**
     * Check if point p is out of index range (check prev/next knot)
     * @param index knot index
     * @param p normalized point data
     */
    public boolean isDraggedOff(int index, Point.Float p) {
        if (index <=0 || index >= curve.x.length - 1) {
            return false;
        }

        if (p.x > curve.x[index+1] + 0.02F || p.x < curve.x[index-1] - 0.02F) {
            return true;
        }

        return false;
    }

    /**
     * Check if point p is allowed to put at given index
     * @param index knot index
     * @param p normalized point data
     */
    public boolean isDraggedIn(int index, Point.Float p) {
        if (index <=0 || index > curve.x.length - 1) {
            return false;
        }

        if (p.x < curve.x[index] && p.x > curve.x[index-1]) {
            return true;
        }

        return false;
    }

    private boolean isOver(Point.Float p, Point.Float q)  {
        if (Math.abs(p.x - q.x) < KNOT_RADIUS) {
            return Math.abs(p.y - q.y) < KNOT_RADIUS;
        }
        return false;
    }

    public boolean isOverKnot(Point.Float p) {
        return (getKnotIndexAt(p) >= 0);
    }

    public boolean isOverKnot(int index) {
        Point.Float p = new Point.Float(curve.x[index], curve.y[index]);
        for (int i = 0; i < curve.x.length; i++) {
            if (i != index && isOver(p, new Point.Float(curve.x[i], curve.y[i]))) {
                return true;
            }
        }

        return false;
    }

    public boolean isOverChart(Point.Float p) {
        return p.x >= 0 && p.x <= 1 && p.y >= 0 && p.y <= 1;
    }

    public int getKnotIndexAt(Point.Float p) {
        for (int i = 0; i < curve.x.length; i++) {
            if (isOver(p, new Point.Float(curve.x[i], curve.y[i]))) {
                return i;
            }
        }

        return -1;
    }

    public void setG2D(Graphics2D gr) {
        this.gr = gr;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void draw() {
        drawCurve();
        if (active) {
            drawKnots();
        }
    }

    private void drawCurve() {
        initCurvePlotData();
        Path2D.Float path2D = new Path2D.Float();
        path2D.moveTo(0, ((float) curvePlotData[0] / 255) * height);
        for (int i = 0; i < curvePlotData.length; i++) {
            float x = ((float) i / 255) * width;
            float y = ((float) curvePlotData[i] / 255) * height;
            path2D.lineTo(x, y);
        }

        gr.setColor(active ? this.curveType.color : this.curveType.colorInactive);
        gr.setStroke(curveStroke);
        gr.draw(path2D);
    }

    private void drawKnots() {
        gr.setColor(Color.black);
        gr.setStroke(pointStroke);
        int knotSize = 2 * KNOT_RADIUS_PX;
        for (int i = 0; i < curve.x.length; i++) {
            gr.drawOval(
                    (int)(curve.x[i] * width) - KNOT_RADIUS_PX,
                    (int)(curve.y[i] * height) - KNOT_RADIUS_PX,
                    knotSize,
                    knotSize
            );
        }
    }
}