package com.github.gcacace.signaturepad.utils;

/**
 * Represent a point as it would be in the generated SVG document.
 */
class SvgPoint {

    private final Integer x, y;

    SvgPoint(TimedPoint point) {
        // one optimisation is to get rid of decimals as they are mostly non-significant in the
        // produced SVG image
        x = Math.round(point.x);
        y = Math.round(point.y);
    }

    private SvgPoint(int x, int y) {
        this.x = x;
        this.y = y;
    }

    private String toAbsoluteCoordinates() {
        return String.valueOf(x) + "," + y;
    }

    public String toRelativeCoordinates(final SvgPoint referencePoint) {
        return (new SvgPoint(x - referencePoint.x, y - referencePoint.y)).toString();
    }

    @Override
    public String toString() {
        return toAbsoluteCoordinates();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SvgPoint svgPoint = (SvgPoint) o;

        return x.equals(svgPoint.x) && y.equals(svgPoint.y);

    }

    @Override
    public int hashCode() {
        int result = x.hashCode();
        result = 31 * result + y.hashCode();
        return result;
    }
}
