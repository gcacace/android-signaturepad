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
    public void build_calledTwice_characterizesCurrentBehavior() {
        // CHARACTERIZATION TEST — documents CURRENT (buggy) behavior, not desired.
        //
        // BUG (Phase 2/3 candidate): SvgBuilder.build() flushes the in-progress
        // path via appendCurrentPath() but never resets mCurrentPathBuilder.
        // Calling build() a second time therefore appends the last path AGAIN,
        // duplicating a stroke in the output. A caller that reads the SVG twice
        // (e.g. logging then saving) would get different documents. This test
        // pins the duplication so a future fix will fail it intentionally.
        builder.append(curve(0, 0, 10, 10, 20, 20, 30, 30), 5f);

        String first = builder.build(100, 100);
        String second = builder.build(100, 100);

        int firstPathCount = first.split("<path ", -1).length - 1;
        int secondPathCount = second.split("<path ", -1).length - 1;
        assertEquals("first build() emits one path", 1, firstPathCount);
        assertEquals("second build() currently DUPLICATES the path (bug)", 2, secondPathCount);
    }
}
