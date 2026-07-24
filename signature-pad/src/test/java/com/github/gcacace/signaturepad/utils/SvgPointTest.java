package com.github.gcacace.signaturepad.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

/**
 * Tests for {@link SvgPoint}. This class is package-private, so the test lives
 * in the same package.
 */
public class SvgPointTest {

    @Test
    public void constructor_roundsTimedPointCoordinates() {
        SvgPoint point = new SvgPoint(new TimedPoint().set(3.4f, 7.6f));
        assertEquals("3,8", point.toAbsoluteCoordinates());
    }

    @Test
    public void toAbsoluteCoordinates_formatsAsCommaSeparated() {
        assertEquals("10,20", new SvgPoint(10, 20).toAbsoluteCoordinates());
    }

    @Test
    public void toRelativeCoordinates_subtractsReferencePoint() {
        SvgPoint reference = new SvgPoint(100, 100);
        SvgPoint point = new SvgPoint(130, 90);

        // 130-100, 90-100
        assertEquals("30,-10", point.toRelativeCoordinates(reference));
    }

    @Test
    public void toRelativeCoordinates_sameAsReferenceIsZero() {
        SvgPoint reference = new SvgPoint(42, 42);
        assertEquals("0,0", new SvgPoint(42, 42).toRelativeCoordinates(reference));
    }

    @Test
    public void equalsAndHashCode_basedOnCoordinates() {
        SvgPoint a = new SvgPoint(5, 6);
        SvgPoint b = new SvgPoint(5, 6);
        SvgPoint c = new SvgPoint(6, 5);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
