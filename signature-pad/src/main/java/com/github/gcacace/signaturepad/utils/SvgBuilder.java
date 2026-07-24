package com.github.gcacace.signaturepad.utils;

public class SvgBuilder {

    private final StringBuilder mSvgPathsBuilder = new StringBuilder();
    private SvgPathBuilder mCurrentPathBuilder = null;

    public SvgBuilder() {
    }

    public void clear() {
        mSvgPathsBuilder.setLength(0);
        mCurrentPathBuilder = null;
    }

    public String build(final int width, final int height) {
        if (isPathStarted()) {
            appendCurrentPath();
        }
        return (new StringBuilder())
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n")
                .append("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.2\" baseProfile=\"tiny\" ")
                .append("height=\"")
                .append(height)
                .append("\" ")
                .append("width=\"")
                .append(width)
                .append("\" ")
                .append("viewBox=\"")
                .append(0)
                .append(" ")
                .append(0)
                .append(" ")
                .append(width)
                .append(" ")
                .append(height)
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

    /**
     * Returns only the accumulated inner {@code <path .../>} fragments — NOT the
     * wrapping {@code <svg>}/{@code <g>} document — flushing any in-progress path
     * first. Because it carries no {@code <svg>} chrome it has no embedded
     * width/height/viewBox, so it can be persisted across a configuration change
     * and re-rendered later at whatever dimensions {@link #build(int, int)} is
     * given. Idempotent: repeated calls return the same string (see
     * {@link #appendCurrentPath()}).
     */
    public String getInnerPaths() {
        if (isPathStarted()) {
            appendCurrentPath();
        }
        return mSvgPathsBuilder.toString();
    }

    /**
     * Re-injects inner path fragments previously produced by {@link #getInnerPaths()}
     * so a later {@link #build(int, int)} emits them again. The fragments are
     * appended to whatever is already accumulated, so strokes added afterwards via
     * {@link #append(Bezier, float)} follow the restored paths. A no-op for
     * {@code null} input.
     */
    public void restorePaths(final String innerPaths) {
        if (innerPaths != null) {
            mSvgPathsBuilder.append(innerPaths);
        }
    }

    public SvgBuilder append(final Bezier curve, final float strokeWidth) {
        final Integer roundedStrokeWidth = Math.round(strokeWidth);
        final SvgPoint curveStartSvgPoint = new SvgPoint(curve.startPoint);
        final SvgPoint curveControlSvgPoint1 = new SvgPoint(curve.control1);
        final SvgPoint curveControlSvgPoint2 = new SvgPoint(curve.control2);
        final SvgPoint curveEndSvgPoint = new SvgPoint(curve.endPoint);

        if (!isPathStarted()) {
            startNewPath(roundedStrokeWidth, curveStartSvgPoint);
        }

        if (!curveStartSvgPoint.equals(mCurrentPathBuilder.getLastPoint())
                || !roundedStrokeWidth.equals(mCurrentPathBuilder.getStrokeWidth())) {
            appendCurrentPath();
            startNewPath(roundedStrokeWidth, curveStartSvgPoint);
        }

        mCurrentPathBuilder.append(curveControlSvgPoint1, curveControlSvgPoint2, curveEndSvgPoint);
        return this;
    }

    private void startNewPath(Integer roundedStrokeWidth, SvgPoint curveStartSvgPoint) {
        mCurrentPathBuilder = new SvgPathBuilder(curveStartSvgPoint, roundedStrokeWidth);
    }

    private void appendCurrentPath() {
        mSvgPathsBuilder.append(mCurrentPathBuilder);
        // Null out so build()/getInnerPaths() are idempotent: without this, a
        // second call would flush (and thus duplicate) the same in-progress path
        // again. The append() path re-assigns mCurrentPathBuilder via
        // startNewPath() before its next use, so this is safe there.
        mCurrentPathBuilder = null;
    }

    private boolean isPathStarted() {
        return mCurrentPathBuilder != null;
    }

}
