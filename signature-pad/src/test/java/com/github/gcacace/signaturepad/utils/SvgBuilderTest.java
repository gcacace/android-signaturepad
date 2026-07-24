package com.github.gcacace.signaturepad.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link SvgBuilder}, which assembles the SVG document returned by
 * {@code SignaturePad.getSignatureSvg()}.
 */
public class SvgBuilderTest {

    private SvgBuilder builder;

    @Before
    public void setUp() {
        builder = new SvgBuilder();
    }

    private static Bezier curve(float sx, float sy, float c1x, float c1y,
                                float c2x, float c2y, float ex, float ey) {
        return new Bezier().set(
                new TimedPoint().set(sx, sy),
                new TimedPoint().set(c1x, c1y),
                new TimedPoint().set(c2x, c2y),
                new TimedPoint().set(ex, ey));
    }

    @Test
    public void build_emptyProducesWellFormedSvgHeader() {
        String svg = builder.build(400, 300);

        assertTrue(svg.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>"));
        assertTrue(svg.contains("<svg "));
        assertTrue(svg.endsWith("</svg>"));
    }

    @Test
    public void build_includesWidthHeightAndViewBox() {
        // Regression guard for #78 / #111: the SVG must carry width, height AND a
        // viewBox so it scales correctly when rendered.
        String svg = builder.build(400, 300);

        assertTrue("missing width", svg.contains("width=\"400\""));
        assertTrue("missing height", svg.contains("height=\"300\""));
        assertTrue("missing viewBox", svg.contains("viewBox=\"0 0 400 300\""));
    }

    @Test
    public void build_withCurveEmitsPathElement() {
        builder.append(curve(0, 0, 10, 10, 20, 20, 30, 30), 5f);
        String svg = builder.build(100, 100);

        assertTrue("expected a <path> element", svg.contains("<path "));
        assertTrue("expected stroke-width from the curve", svg.contains("stroke-width=\"5\""));
        assertTrue("path should start with an absolute move", svg.contains("d=\"M0,0"));
    }

    @Test
    public void clear_resetsAccumulatedPaths() {
        builder.append(curve(0, 0, 10, 10, 20, 20, 30, 30), 5f);
        builder.clear();
        String svg = builder.build(100, 100);

        assertFalse("cleared builder should emit no path", svg.contains("<path "));
    }

    @Test
    public void append_returnsSelfForChaining() {
        SvgBuilder returned = builder.append(curve(0, 0, 1, 1, 2, 2, 3, 3), 2f);
        assertEquals(builder, returned);
    }

    @Test
    public void build_calledTwice_isIdempotent() {
        // Previously build() flushed the in-progress path via appendCurrentPath()
        // but never reset mCurrentPathBuilder, so a second build() duplicated the
        // last path. appendCurrentPath() now nulls the builder, making build()
        // idempotent — required because getSignatureSvg()/getInnerPaths() may be
        // read more than once (e.g. logging then persisting).
        builder.append(curve(0, 0, 10, 10, 20, 20, 30, 30), 5f);

        String first = builder.build(100, 100);
        String second = builder.build(100, 100);

        int firstPathCount = first.split("<path ", -1).length - 1;
        int secondPathCount = second.split("<path ", -1).length - 1;
        assertEquals("first build() emits one path", 1, firstPathCount);
        assertEquals("second build() must not duplicate the path", 1, secondPathCount);
        assertEquals("repeated build() must return identical output", first, second);
    }

    @Test
    public void getInnerPaths_flushesInProgressPath_andIsIdempotent() {
        builder.append(curve(0, 0, 10, 10, 20, 20, 30, 30), 5f);

        String first = builder.getInnerPaths();
        String second = builder.getInnerPaths();

        assertTrue("inner paths should contain the flushed <path>", first.contains("<path "));
        assertFalse("inner paths must NOT include the <svg> document wrapper", first.contains("<svg"));
        assertEquals("repeated getInnerPaths() must be idempotent", first, second);
        assertEquals("exactly one <path> fragment", 1, first.split("<path ", -1).length - 1);
    }

    @Test
    public void restorePaths_thenBuild_emitsRestoredPaths() {
        builder.append(curve(0, 0, 10, 10, 20, 20, 30, 30), 5f);
        String exported = builder.getInnerPaths();

        SvgBuilder restored = new SvgBuilder();
        restored.restorePaths(exported);
        String svg = restored.build(100, 100);

        assertTrue("restored builder should emit the path", svg.contains("<path "));
        assertTrue("restored SVG should embed the exported fragment verbatim", svg.contains(exported));
    }

    @Test
    public void restorePaths_thenAppend_keepsRestoredAndAddsNew() {
        // Locks the "keep restored SVG and append new strokes" behavior.
        builder.append(curve(0, 0, 10, 10, 20, 20, 30, 30), 5f);
        String exported = builder.getInnerPaths();

        SvgBuilder restored = new SvgBuilder();
        restored.restorePaths(exported);
        restored.append(curve(50, 50, 60, 60, 70, 70, 80, 80), 3f);
        String svg = restored.build(100, 100);

        assertEquals("restored path plus one appended path", 2, svg.split("<path ", -1).length - 1);
    }

    @Test
    public void restorePaths_null_isNoOp() {
        builder.restorePaths(null);
        String svg = builder.build(100, 100);

        assertFalse("restoring null should add no path", svg.contains("<path "));
    }
}
