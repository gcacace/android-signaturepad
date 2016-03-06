package com.github.gcacace.signaturepad.utils;

/**
 * Represent a point as it would be in the generated SVG document.
 */
class SvgPoint {

    /**
     * string representation of the point
     */
    final String svg;

    public SvgPoint(TimedPoint point) {
        // one optimisation is to get rid of decimals as they are mostly non-significant in the
        // produced SVG image
        svg = (new StringBuilder())
                .append(Math.round(point.x))
                .append(",")
                .append(Math.round(point.y))
                .toString();
    }

    @Override
    public String toString() {
        return svg;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SvgPoint svgPoint = (SvgPoint) o;
        return svg.equals(svgPoint.svg);
    }

    @Override
    public int hashCode() {
        return svg.hashCode();
    }
}
