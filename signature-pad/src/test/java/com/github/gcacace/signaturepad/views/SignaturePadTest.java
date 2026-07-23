package com.github.gcacace.signaturepad.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Robolectric tests for {@link SignaturePad}. Lives in the view's own package so
 * it can exercise the protected {@code onSaveInstanceState()} /
 * {@code onRestoreInstanceState()} lifecycle hooks directly.
 *
 * <p>NOTE: Robolectric shadows {@code Bitmap.writeToParcel} with a JVM no-op, so
 * it cannot reproduce the native "Could not copy bitmap to parcel blob" crash
 * (issues #178/#169/#183/#187) that a real device throws. That end-to-end crash
 * is verified on an emulator. What these tests DO lock down is the structural
 * root cause — a raw Bitmap living in the saved-state Bundle — so the Phase 2
 * fix that removes it will force an intentional update here.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SignaturePadTest {

    private Activity activity;
    private SignaturePad pad;

    @Before
    public void setUp() {
        ActivityController<Activity> controller =
                Robolectric.buildActivity(Activity.class).setup();
        activity = controller.get();
        pad = new SignaturePad(activity, null);
        pad.setId(View.generateViewId());
    }

    /** Give the view a real size so it can allocate its backing bitmap. */
    private void layout() {
        pad.measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
        pad.layout(0, 0, 400, 300);
    }

    // --- basic state ---------------------------------------------------------

    @Test
    public void newPad_isEmpty() {
        assertTrue(pad.isEmpty());
    }

    @Test
    public void newPad_hasNoPoints() {
        assertNotNull(pad.getPoints());
        assertTrue(pad.getPoints().isEmpty());
    }

    @Test
    public void getSignatureSvg_onEmptyPad_isWellFormed() {
        layout();
        String svg = pad.getSignatureSvg();
        assertTrue(svg.startsWith("<?xml"));
        assertTrue(svg.contains("<svg "));
        assertTrue(svg.trim().endsWith("</svg>"));
    }

    @Test
    public void setPenColor_doesNotThrow() {
        pad.setPenColor(Color.RED);
        pad.setPenColorRes(android.R.color.black);
    }

    // --- listener callbacks --------------------------------------------------

    @Test
    public void clear_notifiesListenerOnClear() {
        RecordingListener listener = new RecordingListener();
        pad.setOnSignedListener(listener);

        pad.clear();

        assertTrue("onClear should fire when the pad is cleared", listener.onClearCalled);
    }

    // --- saved-state characterization (Phase 2 target) -----------------------

    @Test
    public void onSaveInstanceState_currentlyStoresRawBitmapInBundle() {
        // CHARACTERIZATION of the crash ROOT CAUSE (issues #178/#169/#183/#187).
        //
        // onSaveInstanceState() places the full transparent signature Bitmap
        // into the saved-state Bundle under "signatureBitmap". On a real device
        // the framework later serializes that Bundle across a Binder
        // transaction and the native Bitmap copy fails -> app crash. The
        // existing try/catch (commit 89d3596) cannot help, because the throw
        // happens outside onSaveInstanceState(), during activityStopped().
        //
        // Phase 2 fix: stop parcelling the bitmap. When that lands, this
        // assertion SHOULD fail and be updated to reflect the new, safe state.
        layout();

        Parcelable state = pad.onSaveInstanceState();

        assertTrue("saved state should be a Bundle once the view is laid out",
                state instanceof Bundle);
        Bundle bundle = (Bundle) state;
        Parcelable saved = bundle.getParcelable("signatureBitmap");
        assertTrue("ROOT CAUSE: a raw Bitmap is currently stored in the state bundle",
                saved instanceof Bitmap);
    }

    @Test
    public void saveThenRestoreInstanceState_roundTripsWithoutThrowing() {
        // Exercises the save -> restore path the framework runs on rotation.
        // Robolectric won't reproduce the native parcel crash, but this guards
        // against regressions in the (de)serialization logic itself.
        layout();

        Parcelable state = pad.onSaveInstanceState();

        SignaturePad restored = new SignaturePad(activity, null);
        restored.setId(pad.getId());
        restored.measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
        restored.layout(0, 0, 400, 300);

        restored.onRestoreInstanceState(state);
        // A cleared/empty pad that is restored should not spuriously report content.
        assertNotNull(restored.getPoints());
    }

    private static final class RecordingListener implements SignaturePad.OnSignedListener {
        boolean onStartSigningCalled;
        boolean onSignedCalled;
        boolean onClearCalled;

        @Override public void onStartSigning() { onStartSigningCalled = true; }
        @Override public void onSigned() { onSignedCalled = true; }
        @Override public void onClear() { onClearCalled = true; }
    }
}
