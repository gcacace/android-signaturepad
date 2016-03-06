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

    private final StringBuilder mSvgPathsBuilder = new StringBuilder();
    private Point mCurrentPathLastPoint = null;
    private Integer mCurrentPathStrokeWidth = null;

    public SvgBuilder() {
    }

    public void clear() {
        mSvgPathsBuilder.setLength(0);
        mCurrentPathLastPoint = null;
        mCurrentPathStrokeWidth = null;
    }

    public String build(final int width, final int height) {
        if (isPathStarted()) {
            endPath();
        }
        return (new StringBuilder())
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n")
                .append("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" ")
                .append("\"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">")
                .append("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" ")
                .append("height=\"")
                .append(height)
                .append("\" ")
                .append("width=\"")
                .append(width)
                .append("\">")
                .append("<g ")
                .append("stroke-linejoin=\"round\" ")
                .append("stroke-linecap=\"round\" ")
                .append("fill=\"none\" ")
                .append("stroke=\"black\"")
                .append(">")
                .append(mSvgPathsBuilder)
                .append("</g>")
                .append("</svg>")
                .toString();
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
        mSvgPathsBuilder
                .append("<path ")
                .append("stroke-width=\"")
                .append(strokeWidth)
                .append("\" ")
                .append("d=\"M")
                .append(startPoint);
        mCurrentPathStrokeWidth = strokeWidth;
        mCurrentPathLastPoint = startPoint;
    }

    private void addCubicBezierCurve(final Point controlPoint1, final Point controlPoint2, final Point endPoint) {
        mSvgPathsBuilder
                .append("C")
                .append(controlPoint1)
                .append(" ")
                .append(controlPoint2)
                .append(" ")
                .append(endPoint);
        mCurrentPathLastPoint = endPoint;
    }

    private void endPath() {
        mSvgPathsBuilder.append("\"/>");
    }

    private boolean isPathStarted() {
        return mCurrentPathLastPoint != null;
    }

}
