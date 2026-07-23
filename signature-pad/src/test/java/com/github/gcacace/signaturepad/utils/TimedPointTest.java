package com.github.gcacace.signaturepad.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import org.junit.Test;

/**
 * Pure-JVM tests for {@link TimedPoint}. No Android dependencies, so these run
 * on the fast {@code test} source set.
 */
public class TimedPointTest {

    private static final float DELTA = 1e-4f;

    @Test
    public void set_storesCoordinatesAndReturnsSelf() {
        TimedPoint point = new TimedPoint();
        TimedPoint returned = point.set(3f, 4f);

        assertSame("set() should return the same instance for chaining/pooling", point, returned);
        assertEquals(3f, point.x, DELTA);
        assertEquals(4f, point.y, DELTA);
    }

    @Test
    public void distanceTo_isEuclidean() {
        TimedPoint origin = new TimedPoint().set(0f, 0f);
        TimedPoint point = new TimedPoint().set(3f, 4f);

        // classic 3-4-5 triangle
        assertEquals(5f, point.distanceTo(origin), DELTA);
        assertEquals(5f, origin.distanceTo(point), DELTA);
    }

    @Test
    public void distanceTo_sameLocationIsZero() {
        TimedPoint a = new TimedPoint().set(10f, 20f);
        TimedPoint b = new TimedPoint().set(10f, 20f);

        assertEquals(0f, a.distanceTo(b), DELTA);
    }

    @Test
    public void velocityFrom_isDistanceOverTime() {
        TimedPoint start = new TimedPoint().set(0f, 0f);
        start.timestamp = 1000L;
        TimedPoint end = new TimedPoint().set(0f, 100f);
        end.timestamp = 1010L; // 10ms later

        // 100px over 10ms => 10 px/ms
        assertEquals(10f, end.velocityFrom(start), DELTA);
    }

    @Test
    public void velocityFrom_nonPositiveTimeDeltaIsTreatedAsOneMs() {
        // Guards a real-world bug: some devices report identical (or backwards)
        // timestamps for consecutive touch events, which would divide by zero.
        TimedPoint start = new TimedPoint().set(0f, 0f);
        start.timestamp = 5000L;
        TimedPoint end = new TimedPoint().set(0f, 42f);
        end.timestamp = 5000L; // same timestamp -> diff clamped to 1ms

        assertEquals(42f, end.velocityFrom(start), DELTA);
    }

    @Test
    public void velocityFrom_backwardsTimestampDoesNotProduceNegativeVelocity() {
        TimedPoint start = new TimedPoint().set(0f, 0f);
        start.timestamp = 5000L;
        TimedPoint end = new TimedPoint().set(0f, 30f);
        end.timestamp = 4990L; // earlier than start -> diff clamped to 1ms

        assertEquals(30f, end.velocityFrom(start), DELTA);
    }

    @Test
    public void velocityFrom_zeroDistanceIsZeroVelocity() {
        TimedPoint start = new TimedPoint().set(7f, 7f);
        start.timestamp = 1000L;
        TimedPoint end = new TimedPoint().set(7f, 7f);
        end.timestamp = 1005L;

        assertEquals(0f, end.velocityFrom(start), DELTA);
    }

    @Test
    public void set_refreshesTimestamp() throws InterruptedException {
        TimedPoint point = new TimedPoint().set(0f, 0f);
        long first = point.timestamp;
        Thread.sleep(2);
        point.set(1f, 1f);

        assertNotSame(first, point.timestamp);
    }
}
