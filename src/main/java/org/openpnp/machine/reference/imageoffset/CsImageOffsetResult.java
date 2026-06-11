package org.openpnp.machine.reference.imageoffset;

public class CsImageOffsetResult {
    private final double dx;
    private final double dy;
    private final double peak;
    private final long dt;
    private final String info;

    public CsImageOffsetResult(double dx, double dy, double peak, long dt, String info) {
        this.dx = dx;
        this.dy = dy;
        this.peak = peak;
        this.dt = dt;
        this.info = info;
    }

    public double getDx() {
        return dx;
    }

    public double getDy() {
        return dy;
    }

    public double getPeak() {
        return peak;
    }

    public long getDt() {
        return dt;
    }

    public String getInfo() {
        return info;
    }
}