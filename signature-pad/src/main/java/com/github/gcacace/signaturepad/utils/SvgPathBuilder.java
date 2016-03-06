package com.github.gcacace.signaturepad.utils;



/**
 * Build a SVG path as a string.
 *
 * https://www.w3.org/TR/SVGTiny12/paths.html
 */
public class SvgPathBuilder {

    public static final char SVG_ABSOLUTE_CUBIC_BEZIER_CURVE = 'C';
    public static final char SVG_MOVE = 'M';
    private final StringBuilder mStringBuilder;
    private final Integer mStrokeWidth;
    private final SvgPoint mStartPoint;
    private SvgPoint mLastPoint;

    public SvgPathBuilder(final SvgPoint startPoint, final Integer strokeWidth) {
        mStrokeWidth = strokeWidth;
        mStartPoint = startPoint;
        mLastPoint = startPoint;
        mStringBuilder = new StringBuilder();
    }

    public final Integer getStrokeWidth() {
        return mStrokeWidth;
    }

    public final SvgPoint getLastPoint() {
        return mLastPoint;
    }

    public SvgPathBuilder append(final SvgPoint controlPoint1, final SvgPoint controlPoint2, final SvgPoint endPoint) {
        mStringBuilder.append(SVG_ABSOLUTE_CUBIC_BEZIER_CURVE);
        mStringBuilder.append(makeAbsoluteCubicBezierCurve(controlPoint1, controlPoint2, endPoint));
        mLastPoint = endPoint;
        return this;
    }

    @Override
    public String toString() {
        return (new StringBuilder())
                .append("<path ")
                .append("stroke-width=\"")
                .append(mStrokeWidth)
                .append("\" ")
                .append("d=\"")
                .append(SVG_MOVE)
                .append(mStartPoint)
                .append(mStringBuilder)
                .append("\"/>")
                .toString();
    }

    private String makeAbsoluteCubicBezierCurve(final SvgPoint controlPoint1, final SvgPoint controlPoint2, final SvgPoint endPoint) {
        return (new StringBuilder())
                .append(controlPoint1)
                .append(" ")
                .append(controlPoint2)
                .append(" ")
                .append(endPoint)
                .toString();
    }
}