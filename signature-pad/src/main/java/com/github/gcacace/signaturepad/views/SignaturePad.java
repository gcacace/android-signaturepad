package com.github.gcacace.signaturepad.views;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.github.gcacace.signaturepad.R;


public class SignaturePad extends View {
    //View state
    private boolean mIsEmpty;

    //Configurable parameters
    private OnSignedListener mOnSignedListener;
    private boolean mClearOnDoubleClick;

    //Default attribute values
    private final int DEFAULT_ATTR_PEN_MIN_WIDTH_PX = 3;
    private final int DEFAULT_ATTR_PEN_MAX_WIDTH_PX = 7;
    private final int DEFAULT_ATTR_ERASER_STROKE_WIDTH_PX = 20;
    private final int DEFAULT_ATTR_PEN_COLOR = Color.BLACK;
    private final float DEFAULT_ATTR_VELOCITY_FILTER_WEIGHT = 0.9f;
    private final boolean DEFAULT_ATTR_CLEAR_ON_DOUBLE_CLICK = false;

    private SketchBoard sketchBoard = null;

    public SignaturePad(Context context, AttributeSet attrs) {
        super(context, attrs);

        sketchBoard = new SketchBoard(this);

        sketchBoard.setActionListener(new SketchBoard.SketchBoardActionListener() {
            @Override
            public void onDoubleClicked() {
                if (mClearOnDoubleClick) {
                    clear();
                }
            }
        });

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.SignaturePad,
                0, 0);

        //Configurable parameters
        try {
            sketchBoard.mMinWidth = a.getDimensionPixelSize(R.styleable.SignaturePad_penMinWidth, convertDpToPx(DEFAULT_ATTR_PEN_MIN_WIDTH_PX));
            sketchBoard.mMaxWidth = a.getDimensionPixelSize(R.styleable.SignaturePad_penMaxWidth, convertDpToPx(DEFAULT_ATTR_PEN_MAX_WIDTH_PX));
            sketchBoard.mEraserStrokeWidth = a.getDimensionPixelSize(R.styleable.SignaturePad_eraserStrokeWidth, convertDpToPx(DEFAULT_ATTR_ERASER_STROKE_WIDTH_PX));
            sketchBoard.mPenColor = a.getColor(R.styleable.SignaturePad_penColor, DEFAULT_ATTR_PEN_COLOR);
            sketchBoard.mVelocityFilterWeight = a.getFloat(R.styleable.SignaturePad_velocityFilterWeight, DEFAULT_ATTR_VELOCITY_FILTER_WEIGHT);
            mClearOnDoubleClick = a.getBoolean(R.styleable.SignaturePad_clearOnDoubleClick, DEFAULT_ATTR_CLEAR_ON_DOUBLE_CLICK);
        } finally {
            a.recycle();
        }

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
        sketchBoard.mPenColor = color;
    }

    /**
     * Set the minimum width of the stroke in pixel.
     *
     * @param minWidth the width in dp.
     */
    public void setMinWidth(float minWidth) {
        sketchBoard.mMinWidth = convertDpToPx(minWidth);
    }

    /**
     * Set the maximum width of the stroke in pixel.
     *
     * @param maxWidth the width in dp.
     */
    public void setMaxWidth(float maxWidth) {
        sketchBoard.mMaxWidth = convertDpToPx(maxWidth);
    }

    /**
     * Set the width of the stroke in erase mode.
     *
     * @param strokeWidth the width in dp.
     */
    public void setEraserStrokeWidth(int strokeWidth) {
        sketchBoard.mEraserStrokeWidth = strokeWidth;
    }

    /**
     * Set the velocity filter weight.
     *
     * @param velocityFilterWeight the weight.
     */
    public void setVelocityFilterWeight(float velocityFilterWeight) {
        sketchBoard.mVelocityFilterWeight = velocityFilterWeight;
    }

    public SketchBoard.Mode getMode() {
        return sketchBoard.mMode;
    }

    /**
     * set the signature pad mode
     *
     * @param mode Drawing or erasing
     */
    public void setMode(SketchBoard.Mode mode) {
        if (mode == null) {
            return;
        }
        sketchBoard.mMode = mode;
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
        sketchBoard.undo(steps);
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
        sketchBoard.redo(steps);
    }

    /**
     * remove all history(undo/redo) items
     */
    public void clearHistories() {
        sketchBoard.clearHistories();
    }

    /**
     * clear the board
     */
    public void clear() {
        sketchBoard.clear();
        setIsEmpty(true);
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // sketchBoard.getDrawState() != 0 means currently in redo/undo drawing restore
        if (!isEnabled() && sketchBoard.getDrawState() != 0) {
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                sketchBoard.begin(event);
                if (mOnSignedListener != null) {
                    mOnSignedListener.onStartSigning();
                }

            case MotionEvent.ACTION_MOVE:
                sketchBoard.moveTo(event);
                break;

            case MotionEvent.ACTION_UP:
                sketchBoard.end(event);
                getParent().requestDisallowInterceptTouchEvent(true);
                setIsEmpty(false);
                break;

            default:
                return false;
        }

        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        sketchBoard.draw(canvas);
    }

    public void setOnSignedListener(OnSignedListener listener) {
        mOnSignedListener = listener;
    }

    public boolean isEmpty() {
        return mIsEmpty;
    }

    /**
     *
     */
    public String getSignatureSvg() {
        return sketchBoard.getSignatureSvg();
    }

    /**
     *
     */
    public Bitmap getSignatureBitmap() {
        Bitmap originalBitmap = getTransparentSignatureBitmap();
        Bitmap whiteBgBitmap = Bitmap.createBitmap(originalBitmap.getWidth(), originalBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(whiteBgBitmap);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(originalBitmap, 0, 0, null);
        return whiteBgBitmap;
    }

    /**
     *
     */
    public void setSignatureBitmap(final Bitmap signature) {
        sketchBoard.setBackgroundImage(signature);
    }

    /**
     *
     */
    public Bitmap getTransparentSignatureBitmap() {
        return sketchBoard.getSignatureBitmap();
    }

    /**
     *
     */
    public Bitmap getTransparentSignatureBitmap(boolean trimBlankSpace) {
        Bitmap mSignatureBitmap = getTransparentSignatureBitmap();
        if (!trimBlankSpace) {
            return mSignatureBitmap;
        }

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

    /**
     *
     */
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

    private int convertDpToPx(float dp) {
        return Math.round(getContext().getResources().getDisplayMetrics().density * dp);
    }

    public interface OnSignedListener {
        void onStartSigning();

        void onSigned();

        void onClear();
    }
}
