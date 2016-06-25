package com.github.gcacace.signaturepad.views;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.github.gcacace.signaturepad.utils.Bezier;
import com.github.gcacace.signaturepad.utils.ControlTimedPoints;
import com.github.gcacace.signaturepad.utils.SvgBuilder;
import com.github.gcacace.signaturepad.utils.TimedPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by jiaoyang
 * 16/6/24
 */
public class SketchBoard {
    // View attached
    private View view;
    // SVG builder
    private final SvgBuilder mSvgBuilder = new SvgBuilder();
    // View state
    private List<TimedPoint> mPoints;
    private float mLastTouchX;
    private float mLastTouchY;
    private float mLastVelocity;
    private float mLastWidth;
    private long mLastBeginTimestamp;
    private RectF mDirtyRect;

    //Configurable parameters
    protected int mMinWidth;
    protected int mMaxWidth;
    protected int mPenColor;
    protected int mEraserStrokeWidth;
    protected float mVelocityFilterWeight;
    protected Mode mMode = Mode.DRAW;

    // Cache
    private List<TimedPoint> mPointsCache = new ArrayList<>();
    private ControlTimedPoints mControlTimedPointsCached = new ControlTimedPoints();
    private Bezier mBezierCached = new Bezier();

    private Paint mPaint = new Paint();
    private Bitmap mSignatureBitmap = null;
    private Canvas mSignatureBitmapCanvas = null;
    private Bitmap mSignatureBitmapOnRestore = null;
    private Bitmap mSignatureBackgroundImage = null;
    private Canvas mSignatureBitmapCanvasOnRestore = null;
    private SketchesManager sketchesManager = new SketchesManager();
    // the trails we use to track the movement
    private Trails trails;
    /**
     * state: REDO(2),UNDO(1),AVAILABLE(0)
     */
    private final AtomicInteger drawState = new AtomicInteger(0);
    /**
     * use to identify double click between two touches
     */
    private final AtomicInteger lastTouchCount = new AtomicInteger(0);

    private SketchBoardActionListener actionListener = null;

    public interface SketchBoardActionListener {
        void onDoubleClicked();
    }

    /**
     * the sketch board modes
     */
    public enum Mode {
        DRAW, ERASE
    }

    /**
     * constructor
     */
    public SketchBoard(View view) {
        this.view = view;
        //Fixed parameters
        mPaint.setAntiAlias(true);
        mPaint.setColor(Color.BLACK);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        //Dirty rectangle to update only the changed portion of the view
        mDirtyRect = new RectF();

        clear();
    }

    /**
     * set an action listener
     */
    public void setActionListener(SketchBoardActionListener actionListener) {
        this.actionListener = actionListener;
    }

    /**
     * drawing state current in [ UNDO, REDO, AVAILABLE]
     */
    public int getDrawState() {
        return drawState.get();
    }

    /**
     * return the signature bitmap
     */
    public Bitmap getSignatureBitmap() {
        ensureSignatureBitmap();
        return mSignatureBitmap;
    }

    /**
     * return svg data
     */
    public String getSignatureSvg() {
        return mSvgBuilder.build(mSignatureBitmap.getWidth(), mSignatureBitmap.getHeight());
    }

    /**
     * set the sketch board background
     */
    public void setBackgroundImage(Bitmap backgroundImage) {
        this.mSignatureBackgroundImage = backgroundImage;
        if (this.mSignatureBackgroundImage != null) {
            view.postInvalidate();
        }
    }

    /**
     * clear/init params
     */
    public void clear() {

        mLastWidth = (mMinWidth + mMaxWidth) / 2;
        mPoints = new ArrayList<>();
        sketchesManager.clear();
        mSvgBuilder.clear();
        mLastVelocity = 0;
        drawState.set(0);

        if (mSignatureBitmap != null) {
            mSignatureBitmap = null;
            ensureSignatureBitmap();
        }

        if (mSignatureBitmapOnRestore != null) {
            mSignatureBitmapOnRestore.recycle();
            mSignatureBitmapOnRestore = null;
        }
    }

    /**
     * @param x
     * @param y
     * @return
     */
    private TimedPoint getNewPoint(float x, float y) {
        int mCacheSize = mPointsCache.size();
        TimedPoint timedPoint;
        if (mCacheSize == 0) {
            // Cache is empty, create a new point
            timedPoint = new TimedPoint();
        } else {
            // Get point from cache
            timedPoint = mPointsCache.remove(mCacheSize - 1);
        }

        return timedPoint.set(x, y);
    }

    /**
     * @param point
     */
    private void recyclePoint(TimedPoint point) {
        mPointsCache.add(point);
    }

    /**
     * @param newPoint
     */
    private void addPoint(TimedPoint newPoint) {
        mPoints.add(newPoint);

        int pointsCount = mPoints.size();
        if (pointsCount > 3) {

            ControlTimedPoints tmp = calculateCurveControlPoints(mPoints.get(0), mPoints.get(1), mPoints.get(2));
            TimedPoint c2 = tmp.c2;
            recyclePoint(tmp.c1);

            tmp = calculateCurveControlPoints(mPoints.get(1), mPoints.get(2), mPoints.get(3));
            TimedPoint c3 = tmp.c1;
            recyclePoint(tmp.c2);

            Bezier curve = mBezierCached.set(mPoints.get(1), c2, c3, mPoints.get(2));

            TimedPoint startPoint = curve.startPoint;
            TimedPoint endPoint = curve.endPoint;

            float velocity = endPoint.velocityFrom(startPoint);
            velocity = Float.isNaN(velocity) ? 0.0f : velocity;

            velocity = mVelocityFilterWeight * velocity
                    + (1 - mVelocityFilterWeight) * mLastVelocity;

            // The new width is a function of the velocity. Higher velocities
            // correspond to thinner strokes.
            float newWidth = strokeWidth(velocity);

            // The Bezier's width starts out as last curve's final width, and
            // gradually changes to the stroke width just calculated. The new
            // width calculation is based on the velocity between the Bezier's
            // start and end mPoints.
            addBezier(curve, mLastWidth, newWidth);

            mLastVelocity = velocity;
            mLastWidth = newWidth;

            // Remove the first element from the list,
            // so that we always have no more than 4 mPoints in mPoints array.
            recyclePoint(mPoints.remove(0));

            recyclePoint(c2);
            recyclePoint(c3);

        } else if (pointsCount == 1) {
            // To reduce the initial lag make it work with 3 mPoints
            // by duplicating the first point
            TimedPoint firstPoint = mPoints.get(0);
            mPoints.add(getNewPoint(firstPoint.x, firstPoint.y));
        }
    }

    /**
     * @param curve
     * @param startWidth
     * @param endWidth
     */
    private void addBezier(Bezier curve, float startWidth, float endWidth) {
        mSvgBuilder.append(curve, (startWidth + endWidth) / 2);

        float originalWidth = mPaint.getStrokeWidth();
        float widthDelta = endWidth - startWidth;
        float drawSteps = (float) Math.floor(curve.length());

        for (int i = 0; i < drawSteps; i++) {
            // Calculate the Bezier (x, y) coordinate for this step.
            float t = ((float) i) / drawSteps;
            float tt = t * t;
            float ttt = tt * t;
            float u = 1 - t;
            float uu = u * u;
            float uuu = uu * u;

            float x = uuu * curve.startPoint.x;
            x += 3 * uu * t * curve.control1.x;
            x += 3 * u * tt * curve.control2.x;
            x += ttt * curve.endPoint.x;

            float y = uuu * curve.startPoint.y;
            y += 3 * uu * t * curve.control1.y;
            y += 3 * u * tt * curve.control2.y;
            y += ttt * curve.endPoint.y;


            // Set the stroke width and set/clear the xfermode object
            mPaint.setStrokeWidth(mMode.equals(Mode.ERASE) ? mEraserStrokeWidth : startWidth + ttt * widthDelta);
            mPaint.setXfermode(mMode.equals(Mode.DRAW) ? null : new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

            mPaint.setColor(mPenColor);

            if (drawState.get() == 0) {
                ensureSignatureBitmap();
                mSignatureBitmapCanvas.drawPoint(x, y, mPaint);
            } else {
                mSignatureBitmapCanvasOnRestore.drawPoint(x, y, mPaint);
            }
            expandDirtyRect(x, y);
        }

        mPaint.setStrokeWidth(originalWidth);
    }

    /**
     *
     */
    private void onSignatureUpdate() {
        view.postInvalidate(
                (int) (mDirtyRect.left - mMaxWidth),
                (int) (mDirtyRect.top - mMaxWidth),
                (int) (mDirtyRect.right + mMaxWidth),
                (int) (mDirtyRect.bottom + mMaxWidth));
    }

    /**
     * @param s1
     * @param s2
     * @param s3
     * @return
     */
    private ControlTimedPoints calculateCurveControlPoints(TimedPoint s1, TimedPoint s2, TimedPoint s3) {
        float dx1 = s1.x - s2.x;
        float dy1 = s1.y - s2.y;
        float dx2 = s2.x - s3.x;
        float dy2 = s2.y - s3.y;

        float m1X = (s1.x + s2.x) / 2.0f;
        float m1Y = (s1.y + s2.y) / 2.0f;
        float m2X = (s2.x + s3.x) / 2.0f;
        float m2Y = (s2.y + s3.y) / 2.0f;

        float l1 = (float) Math.sqrt(dx1 * dx1 + dy1 * dy1);
        float l2 = (float) Math.sqrt(dx2 * dx2 + dy2 * dy2);

        float dxm = (m1X - m2X);
        float dym = (m1Y - m2Y);
        float k = l2 / (l1 + l2);
        if (Float.isNaN(k)) k = 0.0f;
        float cmX = m2X + dxm * k;
        float cmY = m2Y + dym * k;

        float tx = s2.x - cmX;
        float ty = s2.y - cmY;

        return mControlTimedPointsCached.set(getNewPoint(m1X + tx, m1Y + ty), getNewPoint(m2X + tx, m2Y + ty));
    }

    /**
     * @param velocity
     * @return
     */
    private float strokeWidth(float velocity) {
        return Math.max(mMaxWidth / (velocity + 1), mMinWidth);
    }

    /**
     * Called when replaying history to ensure the dirty region includes all
     * mPoints.
     *
     * @param historicalX the previous x coordinate.
     * @param historicalY the previous y coordinate.
     */
    private void expandDirtyRect(float historicalX, float historicalY) {
        if (historicalX < mDirtyRect.left) {
            mDirtyRect.left = historicalX;
        } else if (historicalX > mDirtyRect.right) {
            mDirtyRect.right = historicalX;
        }
        if (historicalY < mDirtyRect.top) {
            mDirtyRect.top = historicalY;
        } else if (historicalY > mDirtyRect.bottom) {
            mDirtyRect.bottom = historicalY;
        }
    }

    /**
     * Resets the dirty region when the motion event occurs.
     *
     * @param eventX the event x coordinate.
     * @param eventY the event y coordinate.
     */
    private void resetDirtyRect(float eventX, float eventY) {

        // The mLastTouchX and mLastTouchY were set when the ACTION_DOWN motion event occurred.
        mDirtyRect.left = Math.min(mLastTouchX, eventX);
        mDirtyRect.right = Math.max(mLastTouchX, eventX);
        mDirtyRect.top = Math.min(mLastTouchY, eventY);
        mDirtyRect.bottom = Math.max(mLastTouchY, eventY);
    }

    /**
     *
     */
    private void ensureSignatureBitmap() {
        if (mSignatureBitmap == null) {
            mSignatureBitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(),
                    Bitmap.Config.ARGB_8888);
            mSignatureBitmapCanvas = new Canvas(mSignatureBitmap);

            if (mSignatureBitmapOnRestore != null) {
                mSignatureBitmapCanvas.drawBitmap(mSignatureBitmapOnRestore, 0, 0, null);
            }
        }
    }

    /**
     * @param event
     */
    public void begin(MotionEvent event) {
        mPoints.clear();
        drawState.set(0);

        float eventX = event.getX();
        float eventY = event.getY();

        mLastTouchX = eventX;
        mLastTouchY = eventY;

        TimedPoint point = getNewPoint(eventX, eventY);
        addPoint(point);

        if (trails == null) {
            trails = new Trails(view.getResources().getConfiguration().orientation,
                    view.getWidth(), view.getHeight(), mMode);
        }
        trails.addNode(new TrailNode(eventX, eventY, point.timestamp));
    }

    /**
     * @param event
     */
    public void moveTo(MotionEvent event) {
        float eventX = event.getX();
        float eventY = event.getY();
        TimedPoint point = getNewPoint(eventX, eventY);

        if (trails != null && !trails.isEmpty()) {
            TrailNode firstNode = trails.firstNode();
            trails.addNode(new TrailNode(eventX, eventY, point.timestamp));
            // if two point are the same, no need to refresh paint
            if (firstNode.eventX == eventX && firstNode.eventY == eventY) {
                return;
            }
        }

        resetDirtyRect(eventX, eventY);
        addPoint(point);

        onSignatureUpdate();
    }

    /**
     * @param event
     */
    public void end(MotionEvent event) {
        // detect double click
        if (mLastBeginTimestamp != 0 && System.currentTimeMillis() - mLastBeginTimestamp < 200) {
            lastTouchCount.set(0);
            trails = null;

            if (actionListener != null) {
                actionListener.onDoubleClicked();
            }
            return;
        } else {
            mLastBeginTimestamp = System.currentTimeMillis();
        }


        if (trails != null) {
            // ignore
            if (!validTrailNodes(trails.trailNodes)) {
                trails = null;
                mPoints.clear();
                Log.w("Signature Pad", "(Single Click) No movements or movement not clear!");
                return;
            }

            float eventX = event.getX();
            float eventY = event.getY();
            resetDirtyRect(eventX, eventY);
            TimedPoint point = getNewPoint(eventX, eventY);
            addPoint(point);
            onSignatureUpdate();

            trails.addNode(new TrailNode(eventX, eventY, point.timestamp));
            sketchesManager.add(trails);
            trails = null;
        }
    }

    /**
     * check if the node has obviously movement
     */
    private boolean validTrailNodes(List<TrailNode> nodes) {
        boolean xChanged = false, yChanged = false;
        TrailNode lastNode = null;
        for (TrailNode node : nodes) {
            if (lastNode == null) {
                lastNode = node;
                continue;
            }
            xChanged = node.eventX != lastNode.eventX;
            yChanged = node.eventY != lastNode.eventY;
        }

        return xChanged || yChanged;
    }

    /**
     * draw current bitmap on canvas
     */
    public void draw(Canvas canvas) {
        if (drawState.get() == 0 && mSignatureBitmap != null) {
            // merge bg and repaint
            mergeBackground(canvas, mSignatureBitmap);
            return;
        }

        if (mSignatureBitmapOnRestore != null) {
            // add background image and repaint
            mergeBackground(canvas, mSignatureBitmapOnRestore);
        }

        // always reset to 0 (drawing mode)
        drawState.set(0);
    }

    /**
     * draw the background bitmap on bottom layer if exists
     */
    private void mergeBackground(Canvas canvas, Bitmap bitmap) {
        if (mSignatureBackgroundImage != null) {
            canvas.drawBitmap(mSignatureBackgroundImage, 0, 0, mPaint);
        }
        canvas.drawBitmap(bitmap, 0, 0, mPaint);
    }

    /**
     * undo steps
     *
     * @param steps
     */
    public void undo(int steps) {

        if (drawState.get() != 0) {
            // current in restore mode
            Log.i("Signature Pad", "Ignored, current in mode " + drawState.get());
            return;
        }

        if (sketchesManager.canUndo()) {
            drawState.set(1);
            sketchesManager.undo(steps);
            sketchesManager.buildSketch(new Callback<Void>() {

                @Override
                public void onResult(Void data) {
                    view.postInvalidate();
                }
            });
        } else {
            Log.w("Signature Pad", "no undo steps available");
        }
    }

    /**
     * redo steps
     *
     * @param steps
     */
    public void redo(int steps) {

        if (drawState.get() != 0) {
            // current in restore mode
            Log.i("Signature Pad", "Ignored, current in mode " + drawState.get());
            return;
        }

        if (sketchesManager.canRedo()) {
            drawState.set(2);
            sketchesManager.redo(steps);
            sketchesManager.buildSketch(new Callback<Void>() {

                @Override
                public void onResult(Void data) {
                    view.postInvalidate();
                }
            });
        } else {
            Log.w("Signature Pad", "no redo steps available");
        }
    }

    /**
     * clear all undo/redo histories
     */
    public void clearHistories() {
        sketchesManager.destroy();
    }

    /**
     * the point when touching the screen
     */
    public class TrailNode {
        private float eventX, eventY;
        private long timestamp;

        public TrailNode cloneTrailNode() {
            return new TrailNode(eventX, eventY, timestamp);
        }

        public TrailNode(float eventX, float eventY, long timestamp) {
            this.eventX = eventX;
            this.eventY = eventY;
            this.timestamp = timestamp;
        }

        public float getEventX() {
            return eventX;
        }

        public float getEventY() {
            return eventY;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    /**
     * Trails is a set of drawing points and the screen w/h during the drawing
     */
    public class Trails {
        private LinkedList<TrailNode> trailNodes = new LinkedList<>();
        private int screenWidth, screenHeight, orientation;
        private Mode mode;

        public Trails cloneTrails() {
            Trails trails = new Trails(orientation, screenWidth, screenHeight, mode);
            if (!trailNodes.isEmpty()) {
                for (int index = trailNodes.size() - 1; index >= 0; index--) {
                    trails.trailNodes.add(trailNodes.get(index).cloneTrailNode());
                }
            }
            return trails;
        }

        public Trails(int orientation, int screenWidth, int screenHeight, Mode mode) {
            this.orientation = orientation;
            this.screenWidth = screenWidth;
            this.screenHeight = screenHeight;
            this.mode = Mode.valueOf(mode.name());
        }

        public Trails(LinkedList<TrailNode> trailNodes, int orientation, int screenWidth, int screenHeight, Mode mode) {
            this(orientation, screenWidth, screenHeight, mode);
            this.trailNodes = trailNodes;
        }

        public Deque<TrailNode> getTrailNodes() {
            return trailNodes;
        }

        public int getOrientation() {
            return orientation;
        }

        public int getScreenWidth() {
            return screenWidth;
        }

        public int getScreenHeight() {
            return screenHeight;
        }

        public void addNode(TrailNode trailNode) {
            trailNodes.push(trailNode);
        }

        public Mode getMode() {
            return mode;
        }

        public TrailNode firstNode() {
            return trailNodes.isEmpty() ? null : trailNodes.getFirst();
        }

        public TrailNode lastNode() {
            return trailNodes.isEmpty() ? null : trailNodes.getLast();
        }

        public boolean isEmpty() {
            return trailNodes.isEmpty();
        }

        public int size() {
            return trailNodes.size();
        }
    }

    /**
     * use to flag last clear action so we can redo all drawings
     */
    public class ClearedTrails extends Trails {
        private LinkedList<Trails> referenced = new LinkedList<>();

        public ClearedTrails(int orientation, int screenWidth, int screenHeight) {
            super(orientation, screenWidth, screenHeight, Mode.DRAW);
        }

        public void pushRef(Trails refNode) {
            referenced.push(refNode);
        }

        public LinkedList<Trails> getRef() {
            return referenced;
        }
    }

    /**
     * Manage the sketch copies
     */
    public class SketchesManager {
        /**
         * store the history items
         */
        private final LinkedList<Trails> history = new LinkedList<>();
        /**
         * track the items we removed from history
         */
        private final LinkedList<Trails> retained = new LinkedList<>();

        /**
         * clear the histories
         *
         * @return actual steps removed
         */
        public int clear() {
            int steps = 0;
            int size = history.size();
            if (size != 0) {
                Trails sample = getFirstElement();
                ClearedTrails clearMarker = new ClearedTrails(sample.orientation, sample.screenWidth, sample.screenHeight);
                for (int i = 0; i < size; i++) {
                    Trails popped = history.pop();
                    retained.push(popped);
                    clearMarker.pushRef(popped);
                    ++steps;
                }
                retained.push(clearMarker);
            }
            return steps;
        }

        /**
         * destroy the histories
         */
        public void destroy() {
            history.clear();
            retained.clear();
        }

        /**
         * the first element in the history
         */
        public Trails getFirstElement() {
            if (history.isEmpty()) {
                return null;
            }

            return history.getFirst();
        }

        /**
         * add trails at the beginning of the history list
         *
         * @param trails a Trails item
         */
        public void add(Trails trails) {
            if (trails != null) {
                history.push(trails);
            }
        }

        /**
         * removes the first trails item from history and add it to the retained list so we can reuse it
         */
        public void undo() {
            if (canUndo()) {
                Trails popped = history.pop();
                retained.push(popped);
            }
        }

        /**
         * remove history by steps
         *
         * @param steps the count of history items to remove
         * @return actual steps proceed
         */
        public int undo(int steps) {
            int stepsToUndo = Math.min(steps, history.size());
            if (stepsToUndo > 0) {
                for (int i = 0; i < stepsToUndo; i++) {
                    undo();
                }
            }
            return stepsToUndo;
        }

        /**
         * reuse the last removed trails item
         */
        public void redo() {
            if (canRedo()) {
                Trails reused = retained.pop();
                history.push(reused);
            }
        }

        /**
         * reuse trails by steps
         *
         * @param steps the count of steps to be restore
         * @return actual steps proceed
         */
        public int redo(int steps) {
            int stepsToRedo = Math.min(steps, retained.size());
            if (stepsToRedo >= 1) {
                for (int i = 0; i < stepsToRedo; i++) {
                    redo();
                }
            }
            return stepsToRedo;
        }

        public boolean canUndo() {
            return !history.isEmpty();
        }

        public boolean canRedo() {
            return !retained.isEmpty();
        }

        public int countUndo() {
            return history.size();
        }

        public int countRedo() {
            return retained.size();
        }

        public void buildSketch(final Callback<Void> callback) {
            // clear last bitmap which paint on the view if last action was not undo/redo
            mSignatureBitmap = null;
            // if paint before
            if (mSignatureBitmapOnRestore != null) {
                mSignatureBitmapOnRestore.recycle();
                mSignatureBitmapOnRestore = null;
            }
            // create blank new
            mSignatureBitmapOnRestore = Bitmap.createBitmap(
                    view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888
            );
            mSignatureBitmapCanvasOnRestore = new Canvas(mSignatureBitmapOnRestore);

            if (history.isEmpty()) {
                Log.i("Signature Pad", "Ignored, no items available.");
                callback.onResult(null);
            } else {

                if (view.getResources().getConfiguration().orientation != getFirstElement().getOrientation()) {
                    Log.w("Signature Pad", "Screen orientation was changed, the image restored may not fit the view properly!");
                }

                // if this is going to restore the cleared data
                boolean restoreFormClear = getFirstElement() instanceof ClearedTrails;
                // if we head the marker, all drawings will be restored from marker referenced trails, otherwise restored from history
                final LinkedList<Trails> trails = !restoreFormClear ? sketchesManager.history : ((ClearedTrails) getFirstElement()).getRef();
                if (restoreFormClear) {
                    // remove the marker, now only support restore data from clear once
                    sketchesManager.history.pop();
                }

                final Mode originalMode = mMode;
                // build the drawings
                new TrailsDrawer() {
                    @Override
                    protected void onPostExecute(Void aVoid) {
                        mMode = originalMode;
                        callback.onResult(null);
                    }
                }.execute(new DrawerObject(trails));
            }
        }
    }

    /**
     * Object passing into the async task
     */
    class DrawerObject {
        final List<Trails> data;

        public DrawerObject(List<Trails> data) {
            this.data = Collections.unmodifiableList(data);
        }
    }

    /**
     * @param <P> input params
     * @param <R> output result
     */
    abstract class TrailsWorker<P, R> extends AsyncTask<P, Void, R> {
    }

    /**
     *
     */
    public class TrailsDrawer extends TrailsWorker<DrawerObject, Void> {

        @Override
        protected final Void doInBackground(DrawerObject... params) {

            synchronized (drawState) {
                DrawerObject drawerObject = params[0];
                List<Trails> data = drawerObject.data;
                if (!data.isEmpty()) {

                    mPoints.clear();

                    for (int index0 = data.size() - 1; index0 >= 0; index0--) {
                        Trails t = data.get(index0);

                        // mode the trails carried\
                        mMode = t.mode;

                        TrailNode head = t.firstNode();
                        mLastTouchX = head.getEventX();
                        mLastTouchY = head.getEventY();

                        TimedPoint timedPoint;
                        for (int index1 = t.trailNodes.size() - 1; index1 >= 0; index1--) {
                            TrailNode node = t.trailNodes.get(index1);
                            float eventX = node.getEventX();
                            float eventY = node.getEventY();
                            resetDirtyRect(eventX, eventY);
                            timedPoint = new TimedPoint();
                            timedPoint.x = eventX;
                            timedPoint.y = eventY;
                            timedPoint.timestamp = node.getTimestamp();
                            addPoint(timedPoint);
                        }
                        mPoints.clear();
                    }
                }
            }
            return null;
        }
    }

    /**
     * TODO: implement svg builder
     */
    public class SVGBuilder extends TrailsWorker<DrawerObject, Void> {

        @Override
        protected Void doInBackground(DrawerObject... params) {
            return null;
        }
    }

    interface Callback<T> {
        void onResult(T data);
    }
}
