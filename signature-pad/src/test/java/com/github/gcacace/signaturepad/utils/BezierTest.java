package com.github.gcacace.signaturepad.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pure-JVM tests for the cubic {@link Bezier} curve math.
 */
public class BezierTest {

    private static final float DELTA = 1e-3f;

    private static TimedPoint p(float x, float y) {
        return new TimedPoint().set(x, y);
    }

    @Test
    public void set_returnsSelfAndStoresPoints() {
        Bezier bezier = new Bezier();
        TimedPoint s = p(0, 0), c1 = p(1, 1), c2 = p(2, 2), e = p(3, 3);
        Bezier returned = bezier.set(s, c1, c2, e);

        assertSame(bezier, returned);
        assertSame(s, bezier.startPoint);
        assertSame(c1, bezier.control1);
        assertSame(c2, bezier.control2);
        assertSame(e, bezier.endPoint);
    }

    @Test
    public void point_atEndpointsEqualsStartAndEnd() {
        Bezier bezier = new Bezier().set(p(0, 0), p(10, 0), p(20, 0), p(30, 0));

        // t=0 -> start component, t=1 -> end component
        assertEquals(0.0, bezier.point(0f, 0f, 10f, 20f, 30f), DELTA);
        assertEquals(30.0, bezier.point(1f, 0f, 10f, 20f, 30f), DELTA);
    }

    @Test
    public void point_onStraightLineIsLinearlyInterpolatedAtMidpoint() {
        // Control points evenly spaced on a straight line => value at t=0.5 is the midpoint.
        double mid = new Bezier().point(0.5f, 0f, 10f, 20f, 30f);
        assertEquals(15.0, mid, DELTA);
    }

    @Test
    public void length_ofStraightHorizontalCurveApproximatesDistance() {
        // A degenerate "curve" that is actually a straight line from (0,0) to (30,0).
        Bezier bezier = new Bezier().set(p(0, 0), p(10, 0), p(20, 0), p(30, 0));
        assertEquals(30f, bezier.length(), DELTA);
    }

    @Test
    public void length_ofZeroLengthCurveIsZero() {
        Bezier bezier = new Bezier().set(p(5, 5), p(5, 5), p(5, 5), p(5, 5));
        assertEquals(0f, bezier.length(), DELTA);
    }

    @Test
    public void length_ofCurvedPathIsAtLeastStraightLineDistance() {
        // A genuinely curved path is never shorter than the straight start->end distance.
        Bezier bezier = new Bezier().set(p(0, 0), p(0, 100), p(100, 100), p(100, 0));
        float straightLine = bezier.endPoint.distanceTo(bezier.startPoint); // 100
        assertTrue("curved length should exceed straight-line distance",
                bezier.length() > straightLine);
    }
}
