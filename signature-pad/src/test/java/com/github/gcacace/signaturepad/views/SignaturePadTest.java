package com.github.gcacace.signaturepad.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Robolectric tests for {@link SignaturePad}. Lives in the view's own package so
 * it can exercise the protected {@code onSaveInstanceState()} /
 * {@code onRestoreInstanceState()} lifecycle hooks directly.
 *
 * <p>NOTE: Robolectric shadows {@code Bitmap.writeToParcel} with a JVM no-op, so
 * it cannot reproduce the native "Could not copy bitmap to parcel blob" crash
 * (issues #178/#169/#183/#187) that a real device throws. That end-to-end crash
 * is verified on an emulator. What these tests DO lock down is the structural
 * fix: no raw Bitmap ever lives in the saved-state Bundle — the signature is
 * persisted as a size-capped PNG {@code byte[]} instead.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
// NATIVE graphics rasterize Canvas.drawPoint / drawBitmap for real. LEGACY
// treats them as no-ops, which would make the dot (#41) and pixel-trim (#145)
// assertions below vacuous and the PNG save/restore round-trip fake.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SignaturePadTest {

    private Activity activity;
    private FrameLayout root;
    private SignaturePad pad;

    @Before
    public void setUp() {
        ActivityController<Activity> controller =
                Robolectric.buildActivity(Activity.class).setup();
        activity = controller.get();
        root = new FrameLayout(activity);
        activity.setContentView(root);
        pad = newPad();
    }

    /** Create a pad attached to the activity's view tree (so getParent() != null). */
    private SignaturePad newPad() {
        SignaturePad target = new SignaturePad(activity, null);
        target.setId(View.generateViewId());
        root.addView(target);
        return target;
    }

    /** Give the view a real size so it can allocate its backing bitmap. */
    private void layout() {
        layout(pad, 400, 300);
    }

    /** Lay out an arbitrary pad at the given size. */
    private void layout(SignaturePad target, int width, int height) {
        target.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        target.layout(0, 0, width, height);
    }

    /** Dispatch a single touch (down, move, up) at the given coordinates. */
    private void dispatchTouch(SignaturePad target, float x, float y) {
        long t = SystemClock.uptimeMillis();
        target.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0));
        target.onTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, x, y, 0));
    }

    /** Draw a short multi-point stroke so the pad has real content. */
    private void drawStroke(SignaturePad target) {
        long t = SystemClock.uptimeMillis();
        target.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 20f, 20f, 0));
        for (int i = 1; i <= 8; i++) {
            target.onTouchEvent(MotionEvent.obtain(
                    t, t + i * 10L, MotionEvent.ACTION_MOVE, 20f + i * 15f, 20f + i * 8f, 0));
        }
        target.onTouchEvent(MotionEvent.obtain(t, t + 90, MotionEvent.ACTION_UP, 140f, 84f, 0));
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

    // --- saved-state: crash fix (#178/#169/#183/#187) ------------------------

    @Test
    public void onSaveInstanceState_doesNotStoreBitmapInBundle() {
        // The crash root cause was a raw Bitmap in the saved-state Bundle: the
        // framework copies it to a native parcel blob during activityStopped(),
        // which throws on large signatures. The fix stores a PNG byte[] instead.
        // This test locks the structural guarantee that no Bitmap ever enters
        // the Bundle. (Robolectric can't reproduce the native throw itself.)
        layout();
        drawStroke(pad);

        Parcelable state = pad.onSaveInstanceState();

        assertTrue("saved state should be a Bundle once the view is laid out",
                state instanceof Bundle);
        Bundle bundle = (Bundle) state;

        assertNull("the legacy raw-Bitmap key must be gone",
                bundle.getParcelable("signatureBitmap"));
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            assertFalse("no value in the saved-state Bundle may be a Bitmap (key=" + key + ")",
                    value instanceof Bitmap);
        }
        assertNotNull("a drawn signature should persist as a PNG byte[]",
                bundle.getByteArray("signaturePng"));
    }

    @Test
    public void saveThenRestore_afterDrawing_restoresNonEmpty() {
        // Exercises the save -> restore path the framework runs on rotation.
        layout();
        drawStroke(pad);
        assertFalse("precondition: the pad has content after drawing", pad.isEmpty());

        Parcelable state = pad.onSaveInstanceState();

        SignaturePad restored = newPad();
        layout(restored, 400, 300);
        restored.onRestoreInstanceState(state);

        assertFalse("a drawn signature should be restored, not lost", restored.isEmpty());
    }

    @Test
    public void saveThenRestore_emptyPad_staysEmpty() {
        // An untouched pad must round-trip without persisting a signature and
        // must come up empty on the other side.
        layout();

        Parcelable state = pad.onSaveInstanceState();
        assertTrue(state instanceof Bundle);
        assertNull("nothing should be persisted for an empty pad",
                ((Bundle) state).getByteArray("signaturePng"));

        SignaturePad restored = newPad();
        layout(restored, 400, 300);
        restored.onRestoreInstanceState(state);

        assertTrue("an empty pad restores empty", restored.isEmpty());
        assertNotNull(restored.getPoints());
    }

    @Test
    public void saveThenRestore_intoRotatedDimensions_doesNotThrow() {
        // Restoring into a view with swapped (portrait<->landscape) dimensions
        // must scale into the new bounds without throwing.
        layout();
        drawStroke(pad);

        Parcelable state = pad.onSaveInstanceState();

        SignaturePad restored = newPad();
        layout(restored, 300, 400); // swapped
        restored.onRestoreInstanceState(state);

        assertFalse(restored.isEmpty());
    }

    // --- SVG survives rotation (getSignatureSvg after restore) ---------------

    @Test
    public void saveThenRestore_afterDrawing_restoresSignatureSvgPaths() {
        // Core fix: getSignatureSvg() must return the signature's <path> data after
        // a config change, not just a header-only SVG. Fails before the fix because
        // setSignatureBitmap()->clearView() wipes mSvgBuilder and nothing repopulates it.
        layout();
        drawStroke(pad);
        assertTrue("precondition: a drawn pad produces SVG paths",
                pad.getSignatureSvg().contains("<path "));

        Parcelable state = pad.onSaveInstanceState();

        SignaturePad restored = newPad();
        layout(restored, 400, 300);
        restored.onRestoreInstanceState(state);

        String svg = restored.getSignatureSvg();
        assertTrue("restored SVG should contain the signature paths", svg.contains("<path "));
        assertTrue("restored SVG should use the original viewBox", svg.contains("viewBox=\"0 0 400 300\""));
    }

    @Test
    public void saveThenRestore_svgPaths_persistedInBundle() {
        layout();
        drawStroke(pad);

        Bundle bundle = (Bundle) pad.onSaveInstanceState();

        String svgPaths = bundle.getString("signatureSvgPaths");
        assertNotNull("SVG paths should be persisted alongside the PNG", svgPaths);
        assertTrue("persisted SVG paths should contain a <path>", svgPaths.contains("<path "));
        assertEquals("original width persisted", 400, bundle.getInt("signatureSvgWidth"));
        assertEquals("original height persisted", 300, bundle.getInt("signatureSvgHeight"));
    }

    @Test
    public void saveThenRestore_intoRotatedDimensions_svgUsesOriginalViewBox() {
        // Restored paths are in the original (pre-rotation) space, so the viewBox
        // must be the original size, not the new rotated view size.
        layout();
        drawStroke(pad);

        Parcelable state = pad.onSaveInstanceState();

        SignaturePad restored = newPad();
        layout(restored, 300, 400); // swapped
        restored.onRestoreInstanceState(state);

        String svg = restored.getSignatureSvg();
        assertTrue("viewBox must be the original 400x300, not the rotated size",
                svg.contains("viewBox=\"0 0 400 300\""));
    }

    @Test
    public void resaveAfterRestore_keepsOriginalSvgDimensions() {
        // Rotate-twice guard: after restoring into a rotated view, saving AGAIN must
        // persist the ORIGINAL coordinate-space dimensions (the paths are still in
        // that space), not the current rotated view size — otherwise the viewBox
        // flip-flops and no longer matches the path coordinates.
        layout(); // 400x300
        drawStroke(pad);
        Parcelable first = pad.onSaveInstanceState();

        SignaturePad restored = newPad();
        layout(restored, 300, 400); // rotated
        restored.onRestoreInstanceState(first);

        Bundle second = (Bundle) restored.onSaveInstanceState();
        assertEquals("re-saved width must stay the original 400", 400, second.getInt("signatureSvgWidth"));
        assertEquals("re-saved height must stay the original 300", 300, second.getInt("signatureSvgHeight"));

        // And a further restore still reports the original viewBox.
        SignaturePad restored2 = newPad();
        layout(restored2, 400, 300);
        restored2.onRestoreInstanceState(second);
        assertTrue(restored2.getSignatureSvg().contains("viewBox=\"0 0 400 300\""));
    }

    @Test
    public void save_svgOverCap_dropsOnlySvg_keepsBitmap() {
        // When the SVG exceeds its independent cap, only the SVG is dropped: the
        // PNG still persists (bitmap restores) and getSignatureSvg() is empty.
        layout();
        drawStroke(pad);
        pad.mMaxSavedStateBytesSvg = 1; // force the SVG over-cap drop path

        Bundle bundle = (Bundle) pad.onSaveInstanceState();
        assertNotNull("PNG must still be persisted", bundle.getByteArray("signaturePng"));
        assertNull("over-cap SVG must not be persisted", bundle.getString("signatureSvgPaths"));

        SignaturePad restored = newPad();
        layout(restored, 400, 300);
        restored.onRestoreInstanceState(bundle);

        assertFalse("bitmap still restores", restored.isEmpty());
        assertFalse("SVG could not be restored, so no paths", restored.getSignatureSvg().contains("<path "));
    }

    @Test
    public void saveThenRestore_emptyPad_svgHasNoPaths() {
        layout();

        Bundle bundle = (Bundle) pad.onSaveInstanceState();
        assertNull("empty pad persists no SVG", bundle.getString("signatureSvgPaths"));

        SignaturePad restored = newPad();
        layout(restored, 400, 300);
        restored.onRestoreInstanceState(bundle);

        assertTrue(restored.isEmpty());
        assertFalse("empty pad restores with no SVG paths", restored.getSignatureSvg().contains("<path "));
    }

    @Test
    public void restoredSvg_afterClear_revertsToEmptyAndCurrentViewBox() {
        // After a restore, clear() must drop the restored paths AND the original
        // viewBox dims, so getSignatureSvg() reverts to the current view size.
        layout();
        drawStroke(pad);
        Parcelable state = pad.onSaveInstanceState();

        SignaturePad restored = newPad();
        layout(restored, 300, 400);
        restored.onRestoreInstanceState(state);
        assertTrue("precondition: restored SVG present", restored.getSignatureSvg().contains("<path "));

        restored.clear();

        String svg = restored.getSignatureSvg();
        assertFalse("cleared pad has no paths", svg.contains("<path "));
        assertTrue("viewBox reverts to the current view size after clear",
                svg.contains("viewBox=\"0 0 300 400\""));
    }

    @Test
    public void restoredPad_resavedBeforeLayout_stillPersistsSignature() {
        // Regression guard: restoring into a not-yet-laid-out pad leaves mIsEmpty
        // true (setSignatureBitmap defers to layout) while the signature is cached.
        // A second save before layout (e.g. recreate() storm) must NOT drop it.
        layout();
        drawStroke(pad);
        Parcelable first = pad.onSaveInstanceState();

        // Restore into a pad that is NOT laid out yet.
        SignaturePad restored = newPad();
        restored.onRestoreInstanceState(first);
        assertTrue("precondition: deferred restore leaves the pad reporting empty",
                restored.isEmpty());

        // Save again before any layout pass.
        Parcelable second = restored.onSaveInstanceState();
        assertTrue(second instanceof Bundle);
        assertNotNull("the restored signature must survive a re-save before layout",
                ((Bundle) second).getByteArray("signaturePng"));
    }

    @Test
    public void save_overCap_dropsSignatureAndRestoresEmpty() {
        // The size cap is the core crash-prevention mechanism. When the compressed
        // signature exceeds the cap, nothing is persisted and the pad restores
        // empty (rather than risking TransactionTooLargeException).
        layout();
        drawStroke(pad);
        pad.mMaxSavedStateBytes = 1; // force the over-cap drop path

        Parcelable state = pad.onSaveInstanceState();
        assertTrue(state instanceof Bundle);
        assertNull("an over-cap signature must not be persisted",
                ((Bundle) state).getByteArray("signaturePng"));

        SignaturePad restored = newPad();
        layout(restored, 400, 300);
        restored.onRestoreInstanceState(state);
        assertTrue("dropping an over-cap signature restores empty", restored.isEmpty());
    }

    @Test
    public void save_underCap_persistsSignature() {
        // Complement to the over-cap test: a normal signature stays under the
        // default cap and is persisted (guards against an inverted comparison).
        layout();
        drawStroke(pad);

        Parcelable state = pad.onSaveInstanceState();
        assertNotNull("an under-cap signature is persisted",
                ((Bundle) state).getByteArray("signaturePng"));
    }

    // --- #145: zero-bounds trim crash ---------------------------------------

    @Test
    public void getTransparentSignatureBitmap_onEmptyLaidOutPad_doesNotThrow() {
        // Blank pad: trimming finds no pixels and returns null (documented
        // behavior). The point is that neither ensureSignatureBitmap() nor the
        // crop throws "width and height must be > 0" (#145).
        layout();
        pad.getTransparentSignatureBitmap(true);
        assertNotNull("the untrimmed bitmap is always allocatable",
                pad.getTransparentSignatureBitmap());
    }

    @Test
    public void onSaveInstanceState_beforeLayout_doesNotThrow() {
        // A save issued before the first layout pass (0x0 view) must not throw.
        Parcelable state = pad.onSaveInstanceState();
        assertNotNull(state);
    }

    @Test
    public void ensureSignatureBitmap_beforeLayout_clampsToOneByOne() {
        // Directly reproduces the #145 stack trace's inner call:
        // getTransparentSignatureBitmap() -> ensureSignatureBitmap() with a 0x0
        // (never-laid-out) view, which used to throw "width and height must
        // be > 0". The dimensions must be clamped to >= 1px. This test GATES the
        // clamp: without it, Bitmap.createBitmap(0, 0, ...) throws here.
        assertEquals(0, pad.getWidth());
        assertEquals(0, pad.getHeight());

        Bitmap bitmap = pad.getTransparentSignatureBitmap();

        assertNotNull(bitmap);
        assertEquals("width clamped to 1px on a 0-width view", 1, bitmap.getWidth());
        assertEquals("height clamped to 1px on a 0-height view", 1, bitmap.getHeight());
    }

    @Test
    public void getTransparentSignatureBitmap_afterSingleDot_returnsTrimmedInk() {
        // End-to-end: a single tap renders a dot (#41), so trimming finds ink and
        // returns a non-null cropped bitmap rather than the null it returns for a
        // truly blank pad. (The zero-dimension clamp itself is gated by
        // getTransparentSignatureBitmap_afterHorizontalStroke_clampsZeroHeight.)
        layout();
        dispatchTouch(pad, 100f, 100f);
        Bitmap trimmed = pad.getTransparentSignatureBitmap(true);
        assertNotNull("the tapped dot should be trimmable to a non-null bitmap", trimmed);
    }

    @Test
    public void getTransparentSignatureBitmap_afterHorizontalStroke_clampsZeroHeight() {
        // GATES the trim-crop clamp (the single-dot test rasterizes a multi-pixel
        // blob, so its bounds are already > 1px and the clamp is a no-op there). A
        // dead-straight horizontal line exactly one row tall trims to zero height
        // (yMax == yMin); the crop must clamp to >= 1px instead of throwing (#145).
        layout();
        // Ink a single full-width row on the (blank) backing bitmap so the trim
        // bounds collapse to zero height.
        Bitmap canvasBitmap = pad.getTransparentSignatureBitmap();
        for (int x = 0; x < canvasBitmap.getWidth(); x++) {
            canvasBitmap.setPixel(x, 0, Color.BLACK);
        }

        Bitmap trimmed = pad.getTransparentSignatureBitmap(true);

        assertNotNull(trimmed);
        assertEquals("a single inked row trims to exactly 1px height", 1, trimmed.getHeight());
        assertTrue(trimmed.getWidth() >= 1);
    }

    // --- #41: single tap renders a dot --------------------------------------

    @Test
    public void singleTap_marksPadNotEmpty() {
        layout();
        dispatchTouch(pad, 100f, 100f);
        assertFalse("a single tap must mark the pad non-empty", pad.isEmpty());
    }

    @Test
    public void singleTap_atOrigin_actuallyRastersInk() {
        // The real #41 fix. A single tap builds a Bezier whose control points all
        // coincide. Away from the origin, float rounding in the Bezier basis makes
        // curve.length() a tiny NON-zero value, so ceil(length)==1 and the normal
        // loop happens to draw. AT THE ORIGIN every term is exactly 0*x==0.0, so
        // length()==0, drawSteps==0, and the pre-fix loop drew NOTHING. This is the
        // coordinate that genuinely reproduces #41, so it gates the dot branch:
        // without the fix this assertion fails (verified via mutation).
        layout();
        dispatchTouch(pad, 0f, 0f);

        Bitmap bitmap = pad.getTransparentSignatureBitmap();
        assertTrue("a tap at the origin must still render a dot (#41)", hasInk(bitmap));
    }

    /** True if any pixel in the bitmap is non-transparent. */
    private static boolean hasInk(Bitmap bitmap) {
        for (int x = 0; x < bitmap.getWidth(); x++) {
            for (int y = 0; y < bitmap.getHeight(); y++) {
                if (Color.alpha(bitmap.getPixel(x, y)) != 0) {
                    return true;
                }
            }
        }
        return false;
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
