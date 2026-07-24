package com.github.gcacace.signaturepad.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.github.gcacace.signaturepad.views.SignaturePad;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests for {@link SignaturePadBindingAdapter}, the hand-written public
 * {@code @BindingAdapter} API that fans data-binding attributes out to a
 * {@link SignaturePad.OnSignedListener}. Robolectric is needed because the
 * adapter installs a listener on a real {@link SignaturePad} whose callbacks
 * fire from touch dispatch and {@code clear()}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SignaturePadBindingAdapterTest {

    private Activity activity;
    private FrameLayout root;
    private SignaturePad pad;

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        root = new FrameLayout(activity);
        activity.setContentView(root);
        pad = new SignaturePad(activity, null);
        pad.setId(View.generateViewId());
        root.addView(pad);
        layout(400, 300);
    }

    @Test
    public void onStartSigning_adapter_forwardsCallback() {
        Flag flag = new Flag();
        SignaturePadBindingAdapter.setOnSignedListener(
                pad, (SignaturePadBindingAdapter.OnStartSigningListener) () -> flag.fired = true);

        drawStroke();

        assertTrue("the onStartSigning binding adapter must forward the callback",
                flag.fired);
    }

    @Test
    public void onSigned_adapter_forwardsCallback() {
        Flag flag = new Flag();
        SignaturePadBindingAdapter.setOnSignedListener(
                pad, (SignaturePadBindingAdapter.OnSignedListener) () -> flag.fired = true);

        drawStroke();

        assertTrue("the onSigned binding adapter must forward the callback", flag.fired);
    }

    @Test
    public void onClear_adapter_forwardsCallback() {
        Flag flag = new Flag();
        SignaturePadBindingAdapter.setOnSignedListener(
                pad, (SignaturePadBindingAdapter.OnClearListener) () -> flag.fired = true);

        pad.clear();

        assertTrue("the onClear binding adapter must forward the callback", flag.fired);
    }

    @Test
    public void allListenersNull_areNoOp() {
        // The combined overload with all-null listeners must install a listener
        // whose bodies are safe no-ops: drawing and clearing must not throw.
        SignaturePadBindingAdapter.setOnSignedListener(pad, null, null, null);

        drawStroke();
        pad.clear();

        assertTrue("clearing leaves the pad empty", pad.isEmpty());
    }

    @Test
    public void onlyClearListener_doesNotFireForSigning() {
        // Guards the per-callback null guards: an onClear-only binding must NOT be
        // invoked while signing, only on clear.
        Flag clearFlag = new Flag();
        SignaturePadBindingAdapter.setOnSignedListener(
                pad, (SignaturePadBindingAdapter.OnClearListener) () -> clearFlag.fired = true);

        drawStroke();
        assertFalse("onClear must not fire while signing", clearFlag.fired);

        pad.clear();
        assertTrue("onClear must fire on clear()", clearFlag.fired);
    }

    /** Give the pad a real size so touch dispatch produces ink and callbacks. */
    private void layout(int width, int height) {
        pad.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        pad.layout(0, 0, width, height);
    }

    /** Draw a short multi-point stroke so the pad fires onStartSigning + onSigned. */
    private void drawStroke() {
        long t = SystemClock.uptimeMillis();
        dispatch(t, t, MotionEvent.ACTION_DOWN, 20f, 20f);
        for (int i = 1; i <= 8; i++) {
            dispatch(t, t + i * 10L, MotionEvent.ACTION_MOVE, 20f + i * 15f, 20f + i * 8f);
        }
        dispatch(t, t + 90, MotionEvent.ACTION_UP, 140f, 84f);
    }

    private void dispatch(long downTime, long eventTime, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        try {
            pad.onTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    private static final class Flag {
        boolean fired;
    }
}
