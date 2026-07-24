package com.github.gcacace.signaturepad.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SignaturePad extends View {
    private static final String TAG = SignaturePad.class.getName();

    /**
     * Upper bound (in bytes) on the PNG-compressed signature stored in the
     * saved-state {@link Bundle}. Android hands the whole Bundle to the system
     * over a Binder transaction whose buffer is ~1 MB, shared with everything
     * else being saved. If the compressed signature exceeds this cap we persist
     * nothing rather than risk a {@code TransactionTooLargeException}; the pad
     * simply comes up empty after the config change and the user re-signs.
     * Adjustable — a typical signature is only a few KB.
     */
    private static final int MAX_SAVED_STATE_BYTES = 256 * 1024;

    /**
     * Effective cap used by {@link #onSaveInstanceState()}. Defaults to
     * {@link #MAX_SAVED_STATE_BYTES}; package-private (not public API) so tests
     * can force the over-cap drop path deterministically without allocating a
     * multi-megabyte bitmap.
     */
    int mMaxSavedStateBytes = MAX_SAVED_STATE_BYTES;

    /**
     * Independent cap for the persisted SVG string; see {@link #mMaxSavedStateBytes}.
     * Same default value, evaluated separately so an over-cap SVG is dropped on its
     * own without dropping the (already-persisted) PNG. Package-private so tests can
     * force the drop path deterministically.
     */
    int mMaxSavedStateBytesSvg = MAX_SAVED_STATE_BYTES;

    //View state
    private List<TimedPoint> mPoints;
    private boolean mIsEmpty;
    private Boolean mHasEditState;
    private float mLastTouchX;
    private float mLastTouchY;
    private float mLastVelocity;
    private float mLastWidth;
    private RectF mDirtyRect;
    private Bitmap mBitmapSavedState;

    // SVG state staged during onRestoreInstanceState, re-injected into mSvgBuilder
    // after setSignatureBitmap()'s clearView() wipes it (see onRestoreInstanceState
    // / setSignatureBitmap). mRestoredSvgWidth/Height are the ORIGINAL view size the
    // paths were captured in, used for a self-consistent viewBox in getSignatureSvg().
    private String mRestoredSvgPaths;
    private int mRestoredSvgWidth;
    private int mRestoredSvgHeight;

    private final SvgBuilder mSvgBuilder = new SvgBuilder();

    // Cache
    private List<TimedPoint> mPointsCache = new ArrayList<>();
    private ControlTimedPoints mControlTimedPointsCached = new ControlTimedPoints();
    private Bezier mBezierCached = new Bezier();

    //Configurable parameters
    private int mMinWidth;
    private int mMaxWidth;
    private float mVelocityFilterWeight;
    private OnSignedListener mOnSignedListener;
    private boolean mClearOnDoubleClick;

    //Double click detector
    private GestureDetector mGestureDetector;

    //Default attribute values
    private final int DEFAULT_ATTR_PEN_MIN_WIDTH_PX = 3;
    private final int DEFAULT_ATTR_PEN_MAX_WIDTH_PX = 7;
    private final int DEFAULT_ATTR_PEN_COLOR = Color.BLACK;
    private final float DEFAULT_ATTR_VELOCITY_FILTER_WEIGHT = 0.9f;
    private final boolean DEFAULT_ATTR_CLEAR_ON_DOUBLE_CLICK = false;

    private Paint mPaint = new Paint();
    private Bitmap mSignatureBitmap = null;
    private Canvas mSignatureBitmapCanvas = null;

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

        clearView();

        mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                return onDoubleClick();
            }
        });
    }

    // Bitmap.compress is @WorkerThread, but onSaveInstanceState must run
    // synchronously on the UI thread by framework contract, so the compression
    // cannot be moved off-thread here. The payload is a single already-rendered
    // signature bitmap (same UI-thread cost the previous implementation already
    // paid in getTransparentSignatureBitmap), so this is safe in practice.
    @SuppressLint("WrongThread")
    @Override
    protected Parcelable onSaveInstanceState() {
        try {
            Bundle bundle = new Bundle();
            bundle.putParcelable("superState", super.onSaveInstanceState());
            // Persist when there is a signature worth keeping: either live content
            // (!mIsEmpty) OR a restored-but-not-yet-replayed signature. After
            // onRestoreInstanceState decodes into a not-yet-laid-out pad,
            // mBitmapSavedState holds the signature while mIsEmpty is still true
            // (setSignatureBitmap defers setIsEmpty(false) to layout); gating on
            // mIsEmpty alone would drop it on a second save before layout — a
            // regression vs 1.3.1. A fresh, untouched pad matches neither term and
            // stores nothing, so it correctly restores empty. When the pad has been
            // cleared via clear(), mHasEditState is true, so the re-render below
            // refreshes mBitmapSavedState to the current (blank) bitmap — this
            // preserves 1.3.1's post-clear save behavior exactly.
            if (!this.mIsEmpty || this.mBitmapSavedState != null) {
                if (this.mHasEditState == null || this.mHasEditState) {
                    this.mBitmapSavedState = this.getTransparentSignatureBitmap();
                }
                // Persist a PNG-compressed copy rather than the raw Bitmap. A raw
                // Bitmap in the Bundle is copied to a native parcel blob during the
                // framework's activityStopped() Binder transaction, which throws
                // "Could not copy bitmap to parcel blob" / TransactionTooLargeException
                // on large signatures (#178/#169/#183/#187). A byte[] never takes that
                // path, and the size cap keeps the payload well under the Binder budget.
                if (this.mBitmapSavedState != null) {
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    this.mBitmapSavedState.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    if (stream.size() <= mMaxSavedStateBytes) {
                        bundle.putByteArray("signaturePng", stream.toByteArray());
                        // Also persist the vector paths so getSignatureSvg() survives
                        // the config change: the PNG restore repaints raster ink but
                        // leaves mSvgBuilder empty. Nested here so SVG is only stored
                        // when the PNG is ("SVG present implies PNG present"), and
                        // capped independently so an oversized SVG is dropped on its
                        // own without affecting the raster restore. The paths are in
                        // the CURRENT view coordinate space; the dimensions give the
                        // restored SVG a self-consistent viewBox.
                        String svgPaths = mSvgBuilder.getInnerPaths();
                        if (svgPaths != null && !svgPaths.isEmpty()) {
                            // Measure the actual UTF-8 byte length so the comparison
                            // matches the byte-defined cap even if the SVG ever carries
                            // non-ASCII content.
                            int svgBytes = svgPaths.getBytes(StandardCharsets.UTF_8).length;
                            if (svgBytes <= mMaxSavedStateBytesSvg) {
                                bundle.putString("signatureSvgPaths", svgPaths);
                                // The dimensions identify the coordinate space the paths
                                // are in. If the paths were themselves restored from a
                                // previous save (mRestoredSvg* armed), they are still in
                                // the ORIGINAL space, so persist the original dimensions —
                                // NOT the current (possibly re-rotated) view size — so the
                                // viewBox stays consistent across multiple rotations.
                                int svgWidth = (mRestoredSvgWidth > 0) ? mRestoredSvgWidth : getWidth();
                                int svgHeight = (mRestoredSvgHeight > 0) ? mRestoredSvgHeight : getHeight();
                                bundle.putInt("signatureSvgWidth", svgWidth);
                                bundle.putInt("signatureSvgHeight", svgHeight);
                            } else {
                                Log.w(TAG, String.format(
                                        "signature SVG too large to save (%d bytes > %d cap); "
                                                + "getSignatureSvg() will be empty after the config change",
                                        svgBytes, mMaxSavedStateBytesSvg));
                            }
                        }
                    } else {
                        // Too large to persist safely; drop it. The pad restores empty
                        // and the user re-signs — strictly better than crashing.
                        Log.w(TAG, String.format(
                                "signature too large to save (%d bytes > %d cap); "
                                        + "it will not be restored after the config change",
                                stream.size(), mMaxSavedStateBytes));
                    }
                }
            }
            return bundle;
        } catch(Exception e) {
            Log.w(TAG, String.format("error saving instance state: %s", e.getMessage()));
            return super.onSaveInstanceState();
        }
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;
            byte[] png = bundle.getByteArray("signaturePng");
            if (png != null) {
                Bitmap signature = BitmapFactory.decodeByteArray(png, 0, png.length);
                if (signature != null) {
                    // Stage the restored SVG BEFORE setSignatureBitmap() -> clearView()
                    // wipes mSvgBuilder. Re-injection happens in setSignatureBitmap()'s
                    // laid-out branch, which both the laid-out and the deferred
                    // (OnGlobalLayoutListener) restore paths funnel through, so it is
                    // guaranteed to run AFTER clearView().
                    this.mRestoredSvgPaths = bundle.getString("signatureSvgPaths");
                    this.mRestoredSvgWidth = bundle.getInt("signatureSvgWidth", 0);
                    this.mRestoredSvgHeight = bundle.getInt("signatureSvgHeight", 0);
                    this.mBitmapSavedState = signature;
                    this.setSignatureBitmap(signature);
                }
            }
            // No saved signature (empty pad, or dropped for exceeding the size
            // cap) => leave the pad empty; nothing to restore.
            state = bundle.getParcelable("superState");
        }
        this.mHasEditState = false;
        super.onRestoreInstanceState(state);
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
        mLastWidth = (mMinWidth + mMaxWidth) / 2f;
    }

    /**
     * Set the maximum width of the stroke in pixel.
     *
     * @param maxWidth the width in dp.
     */
    public void setMaxWidth(float maxWidth) {
        mMaxWidth = convertDpToPx(maxWidth);
        mLastWidth = (mMinWidth + mMaxWidth) / 2f;
    }

    /**
     * Set the velocity filter weight.
     *
     * @param velocityFilterWeight the weight.
     */
    public void setVelocityFilterWeight(float velocityFilterWeight) {
        mVelocityFilterWeight = velocityFilterWeight;
    }

    public void clearView() {
        mSvgBuilder.clear();
        // Drop any staged/active restored-SVG state so clear(), a double-tap clear,
        // or a fresh setSignatureBitmap() don't resurrect stale paths or dimensions.
        mRestoredSvgPaths = null;
        mRestoredSvgWidth = 0;
        mRestoredSvgHeight = 0;
        mPoints = new ArrayList<>();
        mLastVelocity = 0;
        mLastWidth = (mMinWidth + mMaxWidth) / 2f;

        if (mSignatureBitmap != null) {
            mSignatureBitmap = null;
            ensureSignatureBitmap();
        }

        setIsEmpty(true);

        invalidate();
    }

    public void clear() {
        this.clearView();
        this.mHasEditState = true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled())
            return false;

        float eventX = event.getX();
        float eventY = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                mPoints.clear();
                if (mGestureDetector.onTouchEvent(event)) break;
                mLastTouchX = eventX;
                mLastTouchY = eventY;
                addPoint(getNewPoint(eventX, eventY));
                if (mOnSignedListener != null) mOnSignedListener.onStartSigning();

            case MotionEvent.ACTION_MOVE:
                resetDirtyRect(eventX, eventY);
                addPoint(getNewPoint(eventX, eventY));
                setIsEmpty(false);
                break;

            case MotionEvent.ACTION_UP:
                resetDirtyRect(eventX, eventY);
                addPoint(getNewPoint(eventX, eventY));
                getParent().requestDisallowInterceptTouchEvent(true);
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
        if (mSignatureBitmap != null) {
            canvas.drawBitmap(mSignatureBitmap, 0, 0, mPaint);
        }
    }

    public void setOnSignedListener(OnSignedListener listener) {
        mOnSignedListener = listener;
    }

    public boolean isEmpty() {
        return mIsEmpty;
    }

    /**
     * Returns the signature as an SVG document.
     *
     * <p>After a configuration change (e.g. rotation) the signature is restored
     * from saved state and the SVG paths are re-injected in the ORIGINAL view
     * coordinate space, so the returned document uses the original width/height as
     * its {@code viewBox} and renders as it was drawn.
     *
     * <p><b>Caveat:</b> if the user draws additional strokes after such a restore,
     * those new strokes are captured in the CURRENT (post-rotation) view space and
     * are therefore geometrically inconsistent with the restored paths in the same
     * document. The visible bitmap remains correct; only the mixed SVG is affected.
     */
    public String getSignatureSvg() {
        // Call once — getTransparentSignatureBitmap() lazily allocates the backing
        // bitmap, so reuse the result rather than invoking it per dimension.
        Bitmap bitmap = getTransparentSignatureBitmap();
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        // When paths were restored from saved state they are in the original view
        // space; pair them with the original dimensions for a self-consistent viewBox.
        if (mRestoredSvgWidth > 0 && mRestoredSvgHeight > 0) {
            width = mRestoredSvgWidth;
            height = mRestoredSvgHeight;
        }
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
            // Capture any SVG paths staged by onRestoreInstanceState BEFORE clearView()
            // resets the mRestoredSvg* fields, so they can be re-injected afterwards.
            // For ordinary external callers these are null/0 (nothing was staged), so
            // the re-injection below is a no-op and behavior is unchanged.
            final String pendingSvgPaths = mRestoredSvgPaths;
            final int pendingSvgWidth = mRestoredSvgWidth;
            final int pendingSvgHeight = mRestoredSvgHeight;

            clearView();
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

            // Re-inject SVG paths staged by onRestoreInstanceState AFTER clearView()
            // has wiped mSvgBuilder, so getSignatureSvg() returns the signature again.
            // No-op for ordinary callers (pendingSvgPaths == null).
            if (pendingSvgPaths != null) {
                mSvgBuilder.restorePaths(pendingSvgPaths);
                // Re-arm the original dimensions (cleared by clearView) so
                // getSignatureSvg() pairs the restored, original-space paths with a
                // self-consistent viewBox.
                mRestoredSvgWidth = pendingSvgWidth;
                mRestoredSvgHeight = pendingSvgHeight;
            }
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

        // Clamp to at least 1px: a single dot or a perfectly straight
        // horizontal/vertical stroke yields zero-width or zero-height bounds,
        // which would make Bitmap.createBitmap throw (#145).
        int trimmedWidth = Math.max(xMax - xMin, 1);
        int trimmedHeight = Math.max(yMax - yMin, 1);
        return Bitmap.createBitmap(mSignatureBitmap, xMin, yMin, trimmedWidth, trimmedHeight);
    }

    private boolean onDoubleClick() {
        if (mClearOnDoubleClick) {
            this.clearView();
            return true;
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
        this.mHasEditState = true;
    }

    private void addBezier(Bezier curve, float startWidth, float endWidth) {
        mSvgBuilder.append(curve, (startWidth + endWidth) / 2);
        ensureSignatureBitmap();
        float originalWidth = mPaint.getStrokeWidth();
        float widthDelta = endWidth - startWidth;
        float drawSteps = (float) Math.ceil(curve.length());

        if (drawSteps == 0) {
            // A zero-length curve (e.g. a single tap / dot) would otherwise draw
            // nothing, because the loop below never runs. Render a single dot so
            // the tap is visible (#41). The ROUND stroke cap makes drawPoint paint
            // a filled circle; use the average width to match the SVG output above.
            mPaint.setStrokeWidth((startWidth + endWidth) / 2);
            mSignatureBitmapCanvas.drawPoint(curve.startPoint.x, curve.startPoint.y, mPaint);
            expandDirtyRect(curve.startPoint.x, curve.startPoint.y);
            mPaint.setStrokeWidth(originalWidth);
            return;
        }

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

            // Set the incremental stroke width and draw.
            mPaint.setStrokeWidth(startWidth + ttt * widthDelta);
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
            // Clamp to at least 1px. The view can be asked to produce its bitmap
            // (e.g. from onSaveInstanceState) before it has been laid out, when
            // getWidth()/getHeight() are still 0 — Bitmap.createBitmap then throws
            // "width and height must be > 0" (#145).
            mSignatureBitmap = Bitmap.createBitmap(Math.max(getWidth(), 1), Math.max(getHeight(), 1),
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

    public List<TimedPoint> getPoints() {
        return mPoints;
    }
}
