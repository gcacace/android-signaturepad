package com.github.gcacace.signaturepad.utils;

public class SvgBuilder {

    /**
     * Represent a point as it would be in the generated SVG document.
     */
    private class Point {

        /**
         * string representation of the point
         */
        final String svg;

        public Point(TimedPoint point) {
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

            Point point = (Point) o;
            return svg.equals(point.svg);
        }

        @Override
        public int hashCode() {
            return svg.hashCode();
        }
    }

    private final StringBuilder mSvgBuilder = new StringBuilder();
    private Point mCurrentPathLastPoint = null;
    private Integer mCurrentPathStrokeWidth = null;

    public SvgBuilder() {
    }

    public void clear() {
        mSvgBuilder.setLength(0);
        mCurrentPathLastPoint = null;
        mCurrentPathStrokeWidth = null;
    }

    public String build(final int width, final int height) {
        if (isPathStarted()) {
            endPath();
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                "<svg xmlns=\"http://www.w3.org/2000/svg\" " +
                "height=\"" + height +
                "\" width=\"" + width +
                "\">" +
                mSvgBuilder.toString() +
                "</svg>";
    }

    public SvgBuilder append(final Bezier curve, final float strokeWidth) {
        final Integer roundedStrokeWidth = Math.round(strokeWidth);
        final Point curveStartPoint = new Point(curve.startPoint);
        final Point curveControlPoint1 = new Point(curve.control1);
        final Point curveControlPoint2 = new Point(curve.control2);
        final Point curveEndPoint = new Point(curve.endPoint);

        if (!curveStartPoint.equals(mCurrentPathLastPoint) || !roundedStrokeWidth.equals(mCurrentPathStrokeWidth)) {
            if (isPathStarted()) {
                endPath();
            }
            startNewPath(curveStartPoint, roundedStrokeWidth);
        }

        addCubicBezierCurve(curveControlPoint1, curveControlPoint2, curveEndPoint);
        return this;
    }

    private void startNewPath(final Point startPoint, final Integer strokeWidth) {
        mSvgBuilder
                .append("<path ")
                .append("stroke-width=\"")
                .append(strokeWidth)
                .append("\" ")
                .append("d=\"M ")
                .append(startPoint);
        mCurrentPathStrokeWidth = strokeWidth;
        mCurrentPathLastPoint = startPoint;
    }

    private void addCubicBezierCurve(final Point controlPoint1, final Point controlPoint2, final Point endPoint) {
        mSvgBuilder
                .append(" C ")
                .append(controlPoint1)
                .append(" ")
                .append(controlPoint2)
                .append(" ")
                .append(endPoint);
        mCurrentPathLastPoint = endPoint;
    }

    private void endPath() {
        mSvgBuilder
                .append("\" ")
                .append("stroke-linejoin=\"round\" ")
                .append("stroke-linecap=\"round\" ")
                .append("fill=\"none\" ")
                .append("stroke=\"black\" ")
                .append("/>");
    }

    private boolean isPathStarted() {
        return mCurrentPathLastPoint != null;
    }

}
