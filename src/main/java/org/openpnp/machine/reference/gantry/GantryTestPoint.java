package org.openpnp.machine.reference.gantry;

import java.util.Locale;

public final class GantryTestPoint {
    private final int lineNumber;
    private final int index;
    private final String name;
    private final double x;
    private final double y;
    private final String nozzleName;
    private final double nozzleZ;
    private final int cropFactor;
    private final String topBottom;
    private final String referenceBitmap;

    public GantryTestPoint(
            int lineNumber,
            int index,
            String name,
            double x,
            double y,
            String nozzleName,
            double nozzleZ,
            int cropFactor,
            String topBottom,
            String referenceBitmap) {
        this.lineNumber = lineNumber;
        this.index = index;
        this.name = name;
        this.x = x;
        this.y = y;
        this.nozzleName = nozzleName;
        this.nozzleZ = nozzleZ;
        this.cropFactor = cropFactor;
        this.topBottom = topBottom;
        this.referenceBitmap = referenceBitmap;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public int getIndex() {
        return index;
    }

    public String getName() {
        return name;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public String getNozzleName() {
        return nozzleName;
    }

    public double getNozzleZ() {
        return nozzleZ;
    }

    public int getCropFactor() {
        return cropFactor;
    }

    public String getTopBottom() {
        return topBottom;
    }

    public String getReferenceBitmap() {
        return referenceBitmap;
    }

    public String describe() {
        return String.format(Locale.US,
                "line=%d, #=%d, Name=\"%s\", X=%.6f, Y=%.6f, Nozzle=%s, N_Z=%.6f, Crop_factor=%d, Top_Bot=%s, Ref_bmp=\"%s\"",
                lineNumber,
                index,
                name,
                x,
                y,
                nozzleName,
                nozzleZ,
                cropFactor,
                topBottom,
                referenceBitmap);
    }
}