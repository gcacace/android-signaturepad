package com.github.gcacace.signaturepad.utils;

public class TimedPoint {
    public float x;
    public float y;
    public long timestamp;

    public TimedPoint set(float x, float y) {
        this.x = x;
        this.y = y;
        this.timestamp = System.currentTimeMillis();
        return this;
    }

    public float velocityFrom(TimedPoint start) {
        long diff = this.timestamp - start.timestamp;
        if(diff <= 0) {
            diff = 1;
        }
        float velocity = distanceTo(start) / diff;
        if (Float.isInfinite(velocity) || Float.isNaN(velocity)) {
            velocity = 0;
        }
        return velocity;
    }

    public float distanceTo(TimedPoint point) {
        return (float) Math.sqrt(Math.pow(point.x - this.x, 2) + Math.pow(point.y - this.y, 2));
    }
}
