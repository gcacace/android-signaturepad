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

    /** Obtain, dispatch, and recycle a MotionEvent so the shared pool isn't leaked. */
    private void dispatch(SignaturePad target, long downTime, long eventTime, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        try {
            target.onTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    /** Dispatch a single touch (down, up) at the given coordinates. */
    private void dispatchTouch(SignaturePad target, float x, float y) {
        long t = SystemClock.uptimeMillis();
        dispatch(target, t, t, MotionEvent.ACTION_DOWN, x, y);
        dispatch(target, t, t + 10, MotionEvent.ACTION_UP, x, y);
    }

    /** Draw a short multi-point stroke so the pad has real content. */
    private void drawStroke(SignaturePad target) {
        long t = SystemClock.uptimeMillis();
        dispatch(target, t, t, MotionEvent.ACTION_DOWN, 20f, 20f);
        for (int i = 1; i <= 8; i++) {
            dispatch(target, t, t + i * 10L, MotionEvent.ACTION_MOVE, 20f + i * 15f, 20f + i * 8f);
        }
        dispatch(target, t, t + 90, MotionEvent.ACTION_UP, 140f, 84f);
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

    // --- pen configuration ---------------------------------------------------

    @Test
    public void setPenColor_rendersDotInThatColor() {
        // setPenColor's effect was never asserted. Set red, tap at the origin (the
        // zero-length-curve dot branch), and confirm a red pixel was painted.
        layout();
        pad.setPenColor(Color.RED);

        dispatchTouch(pad, 50f, 50f);

        Bitmap bitmap = pad.getTransparentSignatureBitmap();
        assertTrue("a red pen must paint red ink", hasPixelOfColor(bitmap, Color.RED));
    }

    @Test
    public void setPenColorRes_invalidResource_fallsBackToBlackWithoutThrowing() {
        // Resource id 0 is never valid, so getColor throws Resources.NotFoundException;
        // the catch must fall back to black (#000000) rather than propagate. This
        // gates the otherwise-uncovered catch branch.
        layout();

        pad.setPenColorRes(0);

        dispatchTouch(pad, 0f, 0f);
        Bitmap bitmap = pad.getTransparentSignatureBitmap();
        assertTrue("the fallback pen must paint black ink",
                hasPixelOfColor(bitmap, Color.BLACK));
    }

    @Test
    public void setMinMaxAndVelocityWeight_thenDraw_stillRenders() {
        // The three stroke-shaping setters were untested. Exercise them (they also
        // run convertDpToPx and recompute mLastWidth) and confirm a stroke still
        // renders afterwards.
        pad.setMinWidth(2f);
        pad.setMaxWidth(9f);
        pad.setVelocityFilterWeight(0.5f);
        layout();

        drawStroke(pad);

        assertFalse("the pad should have content after drawing", pad.isEmpty());
        assertTrue("ink should be rendered with the reconfigured widths",
                hasInk(pad.getTransparentSignatureBitmap()));
    }

    @Test
    public void onTouchEvent_whenDisabled_ignoresTouchesAndStaysEmpty() {
        // The !isEnabled() early return was uncovered. A disabled pad must ignore
        // touches (onTouchEvent returns false and the pad stays empty).
        layout();
        pad.setEnabled(false);

        dispatchTouch(pad, 50f, 50f);

        assertTrue("a disabled pad must ignore touches and stay empty", pad.isEmpty());
    }

    @Test
    public void getSignatureBitmap_afterDraw_hasWhiteBackgroundAndInk() {
        // The white-background composite variant (getSignatureBitmap) had no test.
        // After drawing it must be non-null, carry the drawn ink, and be painted on
        // an opaque white background.
        layout();
        drawStroke(pad);

        Bitmap bitmap = pad.getSignatureBitmap();

        assertNotNull(bitmap);
        assertTrue("the white-background bitmap must contain the drawn ink",
                hasInk(bitmap));
        assertTrue("the background must be composited white",
                hasPixelOfColor(bitmap, Color.WHITE));
    }

    // --- listener callbacks --------------------------------------------------

    @Test
    public void clear_notifiesListenerOnClear() {
        RecordingListener listener = new RecordingListener();
        pad.setOnSignedListener(listener);

        pad.clear();

        assertTrue("onClear should fire when the pad is cleared", listener.onClearCalled);
    }

    @Test
    public void drawStroke_notifiesOnStartSigningAndOnSigned() {
        // The onStartSigning (ACTION_DOWN) and onSigned (setIsEmpty(false)) callbacks
        // were captured by RecordingListener but never asserted anywhere. Drawing a
        // real stroke must fire both.
        RecordingListener listener = new RecordingListener();
        pad.setOnSignedListener(listener);
        layout();

        drawStroke(pad);

        assertTrue("onStartSigning should fire when the pad is first touched",
                listener.onStartSigningCalled);
        assertTrue("onSigned should fire once the pad has content",
                listener.onSignedCalled);
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
    public void restoredPad_resavedBeforeLayout_stillPersistsSvgPaths() {
        // Regression guard (PR #190): after a restore into a not-yet-laid-out pad,
        // mSvgBuilder is still empty (re-injection is deferred to layout) while the
        // staged mRestoredSvgPaths holds the signature. A second save before layout
        // must persist the SVG from the staged paths, not drop it.
        layout();
        drawStroke(pad);
        Parcelable first = pad.onSaveInstanceState();
        assertNotNull("precondition: first save has SVG paths",
                ((Bundle) first).getString("signatureSvgPaths"));

        // Restore into a pad that is NOT laid out yet, then save again immediately.
        SignaturePad restored = newPad();
        restored.onRestoreInstanceState(first);
        Parcelable second = restored.onSaveInstanceState();

        assertNotNull("SVG paths must survive a re-save before layout",
                ((Bundle) second).getString("signatureSvgPaths"));

        // And they must still restore into a real pad as a populated SVG.
        SignaturePad restored2 = newPad();
        layout(restored2, 400, 300);
        restored2.onRestoreInstanceState(second);
        assertTrue("re-saved SVG restores with paths",
                restored2.getSignatureSvg().contains("<path "));
    }

    @Test
    public void restore_legacyBitmapKey_isHonored() {
        // Backwards compatibility (PR #190): saved state produced by an older library
        // version stored the signature as a raw Bitmap Parcelable under
        // "signatureBitmap". Restoring must honour that legacy key rather than
        // silently losing the signature (the old crash was at save time, not restore).
        layout();
        drawStroke(pad);
        Bitmap legacyBitmap = pad.getTransparentSignatureBitmap();

        // Build an old-format saved-state Bundle by taking a real one and rewriting
        // it to the legacy shape: drop the new "signaturePng" key and store the raw
        // Bitmap under "signatureBitmap", exactly as older library versions did.
        Bundle legacyState = (Bundle) pad.onSaveInstanceState();
        legacyState.remove("signaturePng");
        legacyState.remove("signatureSvgPaths");
        legacyState.putParcelable("signatureBitmap", legacyBitmap);

        SignaturePad restored = newPad();
        layout(restored, 400, 300);
        restored.onRestoreInstanceState(legacyState);

        assertFalse("a legacy Bitmap-key signature must be restored, not lost",
                restored.isEmpty());
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
        // truly blank pad.
        layout();
        dispatchTouch(pad, 100f, 100f);
        Bitmap trimmed = pad.getTransparentSignatureBitmap(true);
        assertNotNull("the tapped dot should be trimmable to a non-null bitmap", trimmed);
    }

    @Test
    public void getTransparentSignatureBitmap_singleInkedRow_trimsToOnePxTall() {
        // A dead-straight horizontal line exactly one row tall: yMin == yMax, so the
        // INCLUSIVE height is (yMax - yMin + 1) == 1 — the single inked row is kept,
        // not dropped to zero. (Also exercises the >=1 safety clamp for #145.)
        layout();
        Bitmap canvasBitmap = pad.getTransparentSignatureBitmap();
        for (int x = 0; x < canvasBitmap.getWidth(); x++) {
            canvasBitmap.setPixel(x, 0, Color.BLACK);
        }

        Bitmap trimmed = pad.getTransparentSignatureBitmap(true);

        assertNotNull(trimmed);
        assertEquals("a single inked row trims to exactly 1px height", 1, trimmed.getHeight());
        assertTrue(trimmed.getWidth() >= 1);
    }

    @Test
    public void getTransparentSignatureBitmap_keepsLastInkedRowAndColumn() {
        // GATES the inclusive-bounds fix (#64): a filled block spanning rows/cols
        // [10..12] (3px each way) must trim to exactly 3x3. The previous
        // `xMax - xMin` / `yMax - yMin` math dropped the last row+column, yielding
        // 2x2 and clipping a pixel line. This test fails under that off-by-one.
        layout();
        Bitmap canvasBitmap = pad.getTransparentSignatureBitmap();
        for (int x = 10; x <= 12; x++) {
            for (int y = 10; y <= 12; y++) {
                canvasBitmap.setPixel(x, y, Color.BLACK);
            }
        }

        Bitmap trimmed = pad.getTransparentSignatureBitmap(true);

        assertNotNull(trimmed);
        assertEquals("inclusive width must keep the last inked column", 3, trimmed.getWidth());
        assertEquals("inclusive height must keep the last inked row", 3, trimmed.getHeight());
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

    // --- #147: setClearOnDoubleClick setter ---------------------------------

    @Test
    public void setClearOnDoubleClick_togglesTheFlag() {
        // The flag was previously only settable via the XML attribute at
        // construction; the runtime setter must flip it both ways.
        assertFalse("default is off", pad.isClearOnDoubleClick());

        pad.setClearOnDoubleClick(true);
        assertTrue("setter enables the flag", pad.isClearOnDoubleClick());

        pad.setClearOnDoubleClick(false);
        assertFalse("setter disables the flag", pad.isClearOnDoubleClick());
    }

    @Test
    public void setClearOnDoubleClick_enabled_doubleTapClearsSignature() {
        // End-to-end: with the flag set at runtime, a double tap must clear the pad.
        layout();
        drawStroke(pad);
        assertFalse("precondition: pad has content", pad.isEmpty());

        pad.setClearOnDoubleClick(true);
        doubleTap(pad, 50f, 50f);

        assertTrue("a double tap clears the pad once the runtime flag is on",
                pad.isEmpty());
    }

    @Test
    public void doubleTap_whenDisabled_keepsSignature() {
        // Guards against the setter being ignored / inverted: with the flag off
        // (the default) a double tap must NOT clear the drawn signature.
        layout();
        drawStroke(pad);

        pad.setClearOnDoubleClick(false);
        doubleTap(pad, 50f, 50f);

        assertFalse("a double tap must not clear when the flag is off",
                pad.isEmpty());
    }

    @Test
    public void doubleTapClear_afterRestore_isNotUndoneByNextRotation() {
        // Regression for the state bug flagged on PR #192: a double-tap clear must
        // set mHasEditState (via clear(), not clearView()). Otherwise, after a
        // restore (mHasEditState=false, mBitmapSavedState still holding the drawn
        // signature) the next onSaveInstanceState() skips refreshing
        // mBitmapSavedState and re-persists the STALE pre-clear signature, so the
        // cleared signature reappears on the following rotation.
        //
        // Sequence: draw -> rotate(save/restore) -> double-tap clear ->
        //           rotate(save/restore) -> must still be empty.
        layout();
        drawStroke(pad);

        // Rotation 1: save and restore into a fresh laid-out pad.
        Parcelable afterDraw = pad.onSaveInstanceState();
        SignaturePad restored = newPad();
        layout(restored, 400, 300);
        restored.onRestoreInstanceState(afterDraw);
        assertFalse("precondition: signature restored after first rotation",
                restored.isEmpty());

        // Double-tap clear on the restored pad.
        restored.setClearOnDoubleClick(true);
        doubleTap(restored, 50f, 50f);
        assertTrue("double tap clears the restored pad", restored.isEmpty());

        // Rotation 2: save the cleared pad and restore again.
        Parcelable afterClear = restored.onSaveInstanceState();
        SignaturePad restored2 = newPad();
        layout(restored2, 400, 300);
        restored2.onRestoreInstanceState(afterClear);

        assertTrue("a double-tap clear must survive the next rotation, "
                + "not resurrect the pre-clear signature", restored2.isEmpty());
    }

    // --- #94: setSignatureBitmap(null) clears instead of crashing ------------

    @Test
    public void setSignatureBitmap_null_clearsPadWithoutThrowing() {
        // Passing null used to NPE (clearView() -> signature.getWidth()). It must
        // now clear the pad gracefully. Gates the null guard: without it, the
        // laid-out branch dereferences the null bitmap and throws.
        layout();
        drawStroke(pad);
        assertFalse("precondition: pad has content", pad.isEmpty());

        pad.setSignatureBitmap(null);

        assertTrue("setSignatureBitmap(null) clears the pad", pad.isEmpty());
    }

    @Test
    public void setSignatureBitmap_null_beforeLayout_doesNotThrow() {
        // The not-laid-out branch also dereferenced the bitmap (deferred via the
        // layout listener). The guard runs before the isLaidOut() check, so a null
        // passed on a 0x0 view is handled without throwing too.
        assertEquals(0, pad.getWidth());
        pad.setSignatureBitmap(null);
        assertTrue("a null bitmap on an un-laid-out pad leaves it empty",
                pad.isEmpty());
    }

    /**
     * Dispatch a double tap so the GestureDetector fires onDoubleTap(). The
     * second tap's down-time must fall within [DOUBLE_TAP_MIN_TIME (40ms),
     * DOUBLE_TAP_TIMEOUT (300ms)] after the first tap's up-time, so the event
     * times are spaced explicitly rather than read from the (frozen) test clock.
     */
    private void doubleTap(SignaturePad target, float x, float y) {
        long t = SystemClock.uptimeMillis();
        dispatch(target, t, t, MotionEvent.ACTION_DOWN, x, y);
        dispatch(target, t, t + 10, MotionEvent.ACTION_UP, x, y);
        long t2 = t + 110; // 100ms after the first up: inside the double-tap window
        dispatch(target, t2, t2, MotionEvent.ACTION_DOWN, x, y);
        dispatch(target, t2, t2 + 10, MotionEvent.ACTION_UP, x, y);
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

    /** True if any non-transparent pixel in the bitmap approximately matches the given color. */
    private static boolean hasPixelOfColor(Bitmap bitmap, int color) {
        final int expectedR = Color.red(color);
        final int expectedG = Color.green(color);
        final int expectedB = Color.blue(color);
        final int maxDelta = 10; // tolerate anti-aliasing / blending

        for (int x = 0; x < bitmap.getWidth(); x++) {
            for (int y = 0; y < bitmap.getHeight(); y++) {
                int pixel = bitmap.getPixel(x, y);
                if (Color.alpha(pixel) == 0) {
                    continue;
                }
                if (Math.abs(Color.red(pixel) - expectedR) <= maxDelta
                        && Math.abs(Color.green(pixel) - expectedG) <= maxDelta
                        && Math.abs(Color.blue(pixel) - expectedB) <= maxDelta) {
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
