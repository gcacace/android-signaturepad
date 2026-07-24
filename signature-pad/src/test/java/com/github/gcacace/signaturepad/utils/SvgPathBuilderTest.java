package com.github.gcacace.signaturepad.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests for {@link SvgPathBuilder}, which serializes a single SVG {@code <path>}
 * element built from relative cubic Bezier segments.
 */
public class SvgPathBuilderTest {

    @Test
    public void toString_startsWithMoveAndCubicCommands() {
        SvgPathBuilder path = new SvgPathBuilder(new SvgPoint(0, 0), 3);
        path.append(new SvgPoint(10, 0), new SvgPoint(20, 0), new SvgPoint(30, 0));

        String s = path.toString();
        assertTrue(s.contains("stroke-width=\"3\""));
        assertTrue(s.contains("d=\"M0,0"));
        assertTrue("expected a relative cubic command 'c'", s.contains("c"));
        assertTrue(s.endsWith("\"/>"));
    }

    @Test
    public void append_updatesLastPointAndReturnsSelf() {
        SvgPathBuilder path = new SvgPathBuilder(new SvgPoint(0, 0), 1);
        SvgPathBuilder returned = path.append(new SvgPoint(1, 1), new SvgPoint(2, 2), new SvgPoint(5, 6));

        assertSame(path, returned);
        assertEquals(new SvgPoint(5, 6), path.getLastPoint());
    }

    @Test
    public void append_degenerateZeroCurve_characterizesCurrentBehavior() {
        // CHARACTERIZATION TEST — documents CURRENT (buggy) behavior, not desired.
        //
        // BUG (Phase 2/3 candidate): the "discard zero curve" guard in
        // SvgPathBuilder.makeRelativeCubicBezierCurve() compares against the
        // literal "c0 0 0 0 0 0" (space-separated, leading 'c'), but the method
        // actually emits comma-separated coordinates with a trailing space and
        // no per-segment 'c'. The strings can never match, so the guard (added
        // for #71) is effectively dead code and degenerate curves are NOT
        // discarded. This test pins the real output so a future fix will make
        // it fail loudly and force an intentional update.
        SvgPoint origin = new SvgPoint(0, 0);
        SvgPathBuilder path = new SvgPathBuilder(origin, 2);
        path.append(origin, origin, origin);

        String s = path.toString();
        assertEquals("<path stroke-width=\"2\" d=\"M0,0c0,0 0,0 0,0 \"/>", s);
    }

    @Test
    public void getStrokeWidth_returnsConstructorValue() {
        assertEquals(Integer.valueOf(7), new SvgPathBuilder(new SvgPoint(0, 0), 7).getStrokeWidth());
    }

    @Test
    public void relativeCoordinates_areRelativeToPreviousEndPoint() {
        SvgPathBuilder path = new SvgPathBuilder(new SvgPoint(100, 100), 1);
        // control1=(110,100) control2=(120,100) end=(130,100), all relative to (100,100)
        path.append(new SvgPoint(110, 100), new SvgPoint(120, 100), new SvgPoint(130, 100));

        String s = path.toString();
        assertTrue("first segment relative to start point", s.contains("c10,0 20,0 30,0"));
    }
}
