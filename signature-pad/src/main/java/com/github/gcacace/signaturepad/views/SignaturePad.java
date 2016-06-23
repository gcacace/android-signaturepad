package com.github.gcacace.signaturepad.views;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;

import com.github.gcacace.signaturepad.R;
import com.github.gcacace.signaturepad.utils.Bezier;
import com.github.gcacace.signaturepad.utils.ControlTimedPoints;
import com.github.gcacace.signaturepad.utils.SvgBuilder;
import com.github.gcacace.signaturepad.utils.TimedPoint;
import com.github.gcacace.signaturepad.view.ViewCompat;
import com.github.gcacace.signaturepad.view.ViewTreeObserverCompat;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class SignaturePad extends View {
    //View state
    private List<TimedPoint> mPoints;
    private boolean mIsEmpty;
    private float mLastTouchX;
    private float mLastTouchY;
    private float mLastVelocity;
    private float mLastWidth;
    private RectF mDirtyRect;

    private final SvgBuilder mSvgBuilder = new SvgBuilder();

    // Cache
    private List<TimedPoint> mPointsCache = new ArrayList<>();
    private ControlTimedPoints mControlTimedPointsCached = new ControlTimedPoints();
    private Bezier mBezierCached = new Bezier();

    //Configurable parameters
    private int mMinWidth;
    private int mMaxWidth;
    private int mEraserStrokeWidth;
    private float mVelocityFilterWeight;
    private OnSignedListener mOnSignedListener;
    private OnRestoreListener mOnRestoreListener;
    private boolean mClearOnDoubleClick;

    //Click values
    private long mFirstClick;
    private int mCountClick;
    private static final int DOUBLE_CLICK_DELAY_MS = 200;

    //Default attribute values
    private final int DEFAULT_ATTR_PEN_MIN_WIDTH_PX = 3;
    private final int DEFAULT_ATTR_PEN_MAX_WIDTH_PX = 7;
    private final int DEFAULT_ATTR_ERASER_STROKE_WIDTH_PX = 20;
    private final int DEFAULT_ATTR_PEN_COLOR = Color.BLACK;
    private final float DEFAULT_ATTR_VELOCITY_FILTER_WEIGHT = 0.9f;
    private final boolean DEFAULT_ATTR_CLEAR_ON_DOUBLE_CLICK = false;

    private Paint mPaint = new Paint();
    private Bitmap mSignatureBitmap = null;
    private Canvas mSignatureBitmapCanvas = null;
    private SignatureMode mMode = SignatureMode.DRAW;
    private PathManager pathManager = new PathManager();
    private Path mLastPath = null;

    public enum SignatureMode {
        DRAW, ERASE
    }

    public SignaturePad(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.SignaturePad,
                0, 0);

        //Configurable parameters
        try {
            mMinWidth = a.getDimensionPixelSize(R.styleable.SignaturePad_penMinWidth, convertDpToPx(DEFAULT_ATTR_PEN_MIN_WIDTH_PX));
            mMaxWidth = a.getDimensionPixelSize(R.styleable.SignaturePad_penMaxWidth, convertDpToPx(DEFAULT_ATTR_PEN_MAX_WIDTH_PX));
            mEraserStrokeWidth = a.getDimensionPixelSize(R.styleable.SignaturePad_eraserStrokeWidth, convertDpToPx(DEFAULT_ATTR_ERASER_STROKE_WIDTH_PX));
            mPaint.setColor(a.getColor(R.styleable.SignaturePad_penColor, DEFAULT_ATTR_PEN_COLOR));
            mVelocityFilterWeight = a.getFloat(R.styleable.SignaturePad_velocityFilterWeight, DEFAULT_ATTR_VELOCITY_FILTER_WEIGHT);
            mClearOnDoubleClick = a.getBoolean(R.styleable.SignaturePad_clearOnDoubleClick, DEFAULT_ATTR_CLEAR_ON_DOUBLE_CLICK);
        } finally {
            a.recycle();
        }

        //Fixed parameters
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);

        //Dirty rectangle to update only the changed portion of the view
        mDirtyRect = new RectF();

        clear();
    }

    /**
     * Set the pen color from a given resource.
     * If the resource is not found, {@link android.graphics.Color#BLACK} is assumed.
     *
     * @param colorRes the color resource.
     */
    public void setPenColorRes(int colorRes) {
        try {
            setPenColor(getResources().getColor(colorRes));
        } catch (Resources.NotFoundException ex) {
            setPenColor(Color.parseColor("#000000"));
        }
    }

    /**
     * Set the pen color from a given color.
     *
     * @param color the color.
     */
    public void setPenColor(int color) {
        mPaint.setColor(color);
    }

    /**
     * Set the minimum width of the stroke in pixel.
     *
     * @param minWidth the width in dp.
     */
    public void setMinWidth(float minWidth) {
        mMinWidth = convertDpToPx(minWidth);
    }

    /**
     * Set the maximum width of the stroke in pixel.
     *
     * @param maxWidth the width in dp.
     */
    public void setMaxWidth(float maxWidth) {
        mMaxWidth = convertDpToPx(maxWidth);
    }

    /**
     * Set the width of the stroke in erase mode.
     *
     * @param strokeWidth the width in dp.
     */
    public void setEraserStrokeWidth(int strokeWidth) {
        this.mEraserStrokeWidth = strokeWidth;
    }

    /**
     * Set the velocity filter weight.
     *
     * @param velocityFilterWeight the weight.
     */
    public void setVelocityFilterWeight(float velocityFilterWeight) {
        mVelocityFilterWeight = velocityFilterWeight;
    }

    public SignatureMode getMode() {
        return mMode;
    }

    /**
     * set the signature pad mode
     *
     * @param mode Drawing or erasing
     */
    public void setMode(SignatureMode mode) {
        if (mode == null) {
            return;
        }
        mMode = mode;
    }

    /**
     * undo the last path
     */
    public void undo() {
        undo(1);
    }

    /**
     * undo steps
     *
     * @param steps how many steps to be undone
     */
    public void undo(int steps) {
        undoInternal(steps);
    }

    /**
     * redo the last path
     */
    public void redo() {
        redo(1);
    }

    /**
     * redo steps
     *
     * @param steps how many steps to be redo
     */
    public void redo(int steps) {
        redoInternal(steps);
    }

    /**
     * remove all history items
     */
    public void clearHistories() {
        pathManager.destroy();
    }

    public void clear() {
        clearInternal();
        pathManager.clear();
    }

    private void clearInternal() {
        mSvgBuilder.clear();
        mPoints = new ArrayList<>();
        mLastVelocity = 0;
        mLastWidth = (mMinWidth + mMaxWidth) / 2;

        if (mSignatureBitmap != null) {
            mSignatureBitmap = null;
            ensureSignatureBitmap();
        }

        setIsEmpty(true);

        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled())
            return false;

        float eventX = event.getX();
        float eventY = event.getY();

        if (mLastPath == null) {
            mLastPath = new Path();
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                mPoints.clear();
                if (isDoubleClick()) {
                    break;
                }
                mLastTouchX = eventX;
                mLastTouchY = eventY;
                mLastPath.begin(mMode, eventX, eventY);
                addPoint(getNewPoint(eventX, eventY));
                if (mOnSignedListener != null) {
                    mOnSignedListener.onStartSigning();
                }

            case MotionEvent.ACTION_MOVE:
                mLastPath.move(eventX, eventY);
                resetDirtyRect(eventX, eventY);
                addPoint(getNewPoint(eventX, eventY));
                break;

            case MotionEvent.ACTION_UP:
                mLastPath.move(eventX, eventY);
                resetDirtyRect(eventX, eventY);
                addPoint(getNewPoint(eventX, eventY));
                getParent().requestDisallowInterceptTouchEvent(true);
                setIsEmpty(false);

                pathManager.add(mLastPath);
                mLastPath = null;
                break;

            default:
                return false;
        }

        //invalidate();
        invalidate(
                (int) (mDirtyRect.left - mMaxWidth),
                (int) (mDirtyRect.top - mMaxWidth),
                (int) (mDirtyRect.right + mMaxWidth),
                (int) (mDirtyRect.bottom + mMaxWidth));

        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (pathManager.pathRedraw()) {
            restoreSignatures();
            return;
        }

        if (mSignatureBitmap != null) {
            canvas.drawBitmap(mSignatureBitmap, 0, 0, mPaint);
        }

        if (pathManager.pendingOnDraw()) {
            restoreSignaturesComplete();
        }
    }

    /**
     * restore signatures according to the history
     */
    private void restoreSignatures() {
        for (int index = pathManager.getHistory().size() - 1; index >= 0; index--) {
            pathManager.getHistory().get(index).draw();
        }

        pathManager.pathRedraw(false);
        pathManager.pendingOnDraw(true);
        postInvalidate();
    }

    /**
     * signatures restore complete
     */
    private void restoreSignaturesComplete() {
        post(new Runnable() {
            @Override
            public void run() {
                pathManager.pendingOnDraw(false);
                if (mOnRestoreListener != null) {
                    mOnRestoreListener.onSignatureRestored();
                }
            }
        });
    }

    public void setOnRestoreListener(OnRestoreListener listener) {
        this.mOnRestoreListener = listener;
    }

    public void setOnSignedListener(OnSignedListener listener) {
        mOnSignedListener = listener;
    }

    public boolean isEmpty() {
        return mIsEmpty;
    }

    public String getSignatureSvg() {
        int width = getTransparentSignatureBitmap().getWidth();
        int height = getTransparentSignatureBitmap().getHeight();
        return mSvgBuilder.build(width, height);
    }

    public Bitmap getSignatureBitmap() {
        Bitmap originalBitmap = getTransparentSignatureBitmap();
        Bitmap whiteBgBitmap = Bitmap.createBitmap(originalBitmap.getWidth(), originalBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(whiteBgBitmap);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(originalBitmap, 0, 0, null);
        return whiteBgBitmap;
    }

    public void setSignatureBitmap(final Bitmap signature) {
        // View was laid out...
        if (ViewCompat.isLaidOut(this)) {
            clear();
            ensureSignatureBitmap();

            RectF tempSrc = new RectF();
            RectF tempDst = new RectF();

            int dWidth = signature.getWidth();
            int dHeight = signature.getHeight();
            int vWidth = getWidth();
            int vHeight = getHeight();

            // Generate the required transform.
            tempSrc.set(0, 0, dWidth, dHeight);
            tempDst.set(0, 0, vWidth, vHeight);

            Matrix drawMatrix = new Matrix();
            drawMatrix.setRectToRect(tempSrc, tempDst, Matrix.ScaleToFit.CENTER);

            Canvas canvas = new Canvas(mSignatureBitmap);
            canvas.drawBitmap(signature, drawMatrix, null);
            setIsEmpty(false);
            invalidate();
        }
        // View not laid out yet e.g. called from onCreate(), onRestoreInstanceState()...
        else {
            getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    // Remove layout listener...
                    ViewTreeObserverCompat.removeOnGlobalLayoutListener(getViewTreeObserver(), this);

                    // Signature bitmap...
                    setSignatureBitmap(signature);
                }
            });
        }
    }

    public Bitmap getTransparentSignatureBitmap() {
        ensureSignatureBitmap();
        return mSignatureBitmap;
    }

    public Bitmap getTransparentSignatureBitmap(boolean trimBlankSpace) {

        if (!trimBlankSpace) {
            return getTransparentSignatureBitmap();
        }

        ensureSignatureBitmap();

        int imgHeight = mSignatureBitmap.getHeight();
        int imgWidth = mSignatureBitmap.getWidth();

        int backgroundColor = Color.TRANSPARENT;

        int xMin = Integer.MAX_VALUE,
                xMax = Integer.MIN_VALUE,
                yMin = Integer.MAX_VALUE,
                yMax = Integer.MIN_VALUE;

        boolean foundPixel = false;

        // Find xMin
        for (int x = 0; x < imgWidth; x++) {
            boolean stop = false;
            for (int y = 0; y < imgHeight; y++) {
                if (mSignatureBitmap.getPixel(x, y) != backgroundColor) {
                    xMin = x;
                    stop = true;
                    foundPixel = true;
                    break;
                }
            }
            if (stop)
                break;
        }

        // Image is empty...
        if (!foundPixel)
            return null;

        // Find yMin
        for (int y = 0; y < imgHeight; y++) {
            boolean stop = false;
            for (int x = xMin; x < imgWidth; x++) {
                if (mSignatureBitmap.getPixel(x, y) != backgroundColor) {
                    yMin = y;
                    stop = true;
                    break;
                }
            }
            if (stop)
                break;
        }

        // Find xMax
        for (int x = imgWidth - 1; x >= xMin; x--) {
            boolean stop = false;
            for (int y = yMin; y < imgHeight; y++) {
                if (mSignatureBitmap.getPixel(x, y) != backgroundColor) {
                    xMax = x;
                    stop = true;
                    break;
                }
            }
            if (stop)
                break;
        }

        // Find yMax
        for (int y = imgHeight - 1; y >= yMin; y--) {
            boolean stop = false;
            for (int x = xMin; x <= xMax; x++) {
                if (mSignatureBitmap.getPixel(x, y) != backgroundColor) {
                    yMax = y;
                    stop = true;
                    break;
                }
            }
            if (stop)
                break;
        }

        return Bitmap.createBitmap(mSignatureBitmap, xMin, yMin, xMax - xMin, yMax - yMin);
    }

    private boolean isDoubleClick() {
        if (mClearOnDoubleClick) {
            if (mFirstClick != 0 && System.currentTimeMillis() - mFirstClick > DOUBLE_CLICK_DELAY_MS) {
                mCountClick = 0;
            }
            mCountClick++;
            if (mCountClick == 1) {
                mFirstClick = System.currentTimeMillis();
            } else if (mCountClick == 2) {
                long lastClick = System.currentTimeMillis();
                if (lastClick - mFirstClick < DOUBLE_CLICK_DELAY_MS) {
                    this.clear();
                    return true;
                }
            }
        }
        return false;
    }

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

    private void recyclePoint(TimedPoint point) {
        mPointsCache.add(point);
    }

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

    private void addBezier(Bezier curve, float startWidth, float endWidth) {
        mSvgBuilder.append(curve, (startWidth + endWidth) / 2);
        ensureSignatureBitmap();
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
            mPaint.setStrokeWidth(mMode.equals(SignatureMode.ERASE) ? mEraserStrokeWidth : startWidth + ttt * widthDelta);
            mPaint.setXfermode(mMode.equals(SignatureMode.DRAW) ? null : new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

            mSignatureBitmapCanvas.drawPoint(x, y, mPaint);
            expandDirtyRect(x, y);
        }

        mPaint.setStrokeWidth(originalWidth);
    }

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

    private void setIsEmpty(boolean newValue) {
        mIsEmpty = newValue;
        if (mOnSignedListener != null) {
            if (mIsEmpty) {
                mOnSignedListener.onClear();
            } else {
                mOnSignedListener.onSigned();
            }
        }
    }

    private void ensureSignatureBitmap() {
        if (mSignatureBitmap == null) {
            mSignatureBitmap = Bitmap.createBitmap(getWidth(), getHeight(),
                    Bitmap.Config.ARGB_8888);
            mSignatureBitmapCanvas = new Canvas(mSignatureBitmap);
        }
    }

    private int convertDpToPx(float dp) {
        return Math.round(getContext().getResources().getDisplayMetrics().density * dp);
    }

    public interface OnSignedListener {
        void onStartSigning();

        void onSigned();

        void onClear();
    }

    public interface OnRestoreListener {

        void onRestoreFromHistory();

        void onSignatureRestored();
    }

    private void undoInternal(final int steps) {
        if (!pathManager.canUndo()) {
            Log.w("Signature Pad", "No items in the undo list!");
            return;
        }

        if (mOnRestoreListener != null) {
            mOnRestoreListener.onRestoreFromHistory();
        }

        pathManager.undo(steps);
        pathManager.pathRedraw(true);

        postDelayed(new Runnable() {
            @Override
            public void run() {
                // trigger redraw
                clearInternal();
            }
        }, 500);
    }

    private void redoInternal(final int steps) {
        if (!pathManager.canRedo()) {
            Log.w("Signature Pad", "No items in the redo list!");
            return;
        }

        if (mOnRestoreListener != null) {
            mOnRestoreListener.onRestoreFromHistory();
        }

        pathManager.redo(steps);
        pathManager.pathRedraw(true);

        postDelayed(new Runnable() {
            @Override
            public void run() {
                // trigger redraw
                clearInternal();
            }
        }, 500);
    }

    /**
     * trail of touch path
     */
    private class Path {
        SignatureMode mode;
        float touchPointX, touchPointY;
        LinkedList<Trail> points = new LinkedList<>();

        protected void begin(SignatureMode mode, float eventX, float eventY) {
            this.mode = mode;
            this.touchPointX = eventX;
            this.touchPointY = eventY;
        }

        protected void move(float eventX, float eventY) {
            points.push(new Trail(eventX, eventY));
        }

        public void draw() {
            if (!points.isEmpty()) {
                post(new Runnable() {
                    @Override
                    public void run() {

                        final SignatureMode originalMode = mMode;
                        setMode(mode);

                        TimedPoint timedPoint;
                        getParent().requestDisallowInterceptTouchEvent(true);
                        mPoints.clear();
                        Trail head = points.getLast();
                        mLastTouchX = head.eventX;
                        mLastTouchY = head.eventY;
                        timedPoint = getNewPoint(head.eventX, head.eventY);
                        timedPoint.timestamp = head.timestamp;
                        addPoint(timedPoint);
                        for (int index = points.size() - 1; index >= 0; index--) {
                            Trail trail = points.get(index);
                            timedPoint = getNewPoint(trail.eventX, trail.eventY);
                            timedPoint.timestamp = trail.timestamp;
                            resetDirtyRect(trail.eventX, trail.eventY);
                            addPoint(timedPoint);
                        }
                        getParent().requestDisallowInterceptTouchEvent(true);
                        setIsEmpty(false);
                        setMode(originalMode);
                    }
                });
            }
        }
    }

    private class Trail {
        float eventX, eventY;
        long timestamp;

        public Trail(float eventX, float eventY) {
            this.eventX = eventX;
            this.eventY = eventY;
            timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Manage the trails
     */
    private class PathManager {

        /**
         * store the history items
         */
        private LinkedList<Path> history = new LinkedList<>();
        /**
         * track the items we removed from history
         */
        private LinkedList<Path> retained = new LinkedList<>();
        /**
         * redraw flag
         */
        private AtomicBoolean pathRedraw = new AtomicBoolean(false);
        /**
         * Wait View onDraw to finish
         */
        private AtomicBoolean pendingOnDraw = new AtomicBoolean(false);

        /**
         * clear the histories
         *
         * @return actual steps removed
         */
        public int clear() {
            return undo(history.size());
        }

        /**
         * destroy the histories
         */
        public void destroy() {
            history.clear();
            retained.clear();
        }

        /**
         * @return the first element in the history
         */
        public Path getLast() {
            if (history.isEmpty()) {
                return null;
            }
            return history.getFirst();
        }

        /**
         * add Path at the beginning of the history
         *
         * @param item an history item
         */
        public void add(Path item) {
            if (item != null) {
                Path path = new Path();
                path.points = new LinkedList<>(item.points);
                path.touchPointX = item.touchPointX;
                path.touchPointY = item.touchPointY;
                path.mode = item.mode;
                history.push(path);
            }
        }

        /**
         * removes the first Path from history and add it to the retained list so we can reuse it
         */
        public void undo() {
            Path popped = history.pop();
            retained.push(popped);
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
         * reuse the last removed history item
         */
        public void redo() {
            if (!retained.isEmpty()) {
                Path reused = retained.pop();
                history.push(reused);
            }
        }

        /**
         * reuse history items by steps
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

        /**
         * @return the path we can use to rebuild signatures
         */
        public LinkedList<Path> getHistory() {
            return history;
        }

        /**
         * @return the path we can redo
         */
        public LinkedList<Path> getRetained() {
            return retained;
        }

        public boolean pathRedraw() {
            return pathRedraw.get();
        }

        public void pathRedraw(boolean redraw) {
            this.pathRedraw.set(redraw);
        }

        public boolean pendingOnDraw() {
            return pendingOnDraw.get();
        }

        public void pendingOnDraw(boolean pendingOnDraw) {
            this.pendingOnDraw.set(pendingOnDraw);
        }
    }
}
