package org.openpnp.machine.reference.gantry;

import java.util.Locale;

public final class GantryTestDryRun {
    private static final int MAX_DETAILED_STEPS = 250;

    private GantryTestDryRun() {
    }

    public static String describe(GantryTestCsvInput input) throws Exception {
        if (input == null) {
            throw new Exception("Cannot create Gantry Test dry-run because CSV input is null.");
        }

        StringBuilder sb = new StringBuilder();

        int cycles = input.getNumberOfCycles();
        int pointCount = input.getPoints().size();
        int totalSteps = cycles * pointCount;

        sb.append("Gantry Test execution dry-run").append(System.lineSeparator());
        sb.append("NO MACHINE MOTION WILL BE PERFORMED.").append(System.lineSeparator());
        sb.append("NO IMAGE CAPTURE WILL BE PERFORMED.").append(System.lineSeparator());
        sb.append("Source file = ").append(input.getSourceFile()).append(System.lineSeparator());
        sb.append("Number of cycles = ").append(cycles).append(System.lineSeparator());
        sb.append("Point count = ").append(pointCount).append(System.lineSeparator());
        sb.append("Total planned point visits = ").append(totalSteps).append(System.lineSeparator());

        if (pointCount == 0) {
            sb.append("No gantry test points to execute.").append(System.lineSeparator());
            sb.append("Gantry Test execution dry-run PASSED.");
            return sb.toString();
        }

        sb.append(System.lineSeparator());
        sb.append("Planned sequence:").append(System.lineSeparator());

        int detailedStepsPrinted = 0;

        for (int cycle = 1; cycle <= cycles; cycle++) {
            sb.append(String.format(Locale.US,
                    "Cycle %d / %d",
                    cycle,
                    cycles)).append(System.lineSeparator());

            for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
                GantryTestPoint point = input.getPoints().get(pointIndex);

                detailedStepsPrinted++;

                if (detailedStepsPrinted > MAX_DETAILED_STEPS) {
                    sb.append("Detailed step print limit reached at ")
                            .append(MAX_DETAILED_STEPS)
                            .append(" steps. Remaining steps are not printed in the debug box.")
                            .append(System.lineSeparator());
                    sb.append("Gantry Test execution dry-run PASSED.");
                    return sb.toString();
                }

                sb.append(String.format(Locale.US,
                        "  Step %d / %d: point #%d \"%s\" -> camera=%s, X=%.6f, Y=%.6f, Nozzle=%s, N_Z=%.6f, Crop_factor=%d, Ref_bmp=\"%s\"",
                        detailedStepsPrinted,
                        totalSteps,
                        point.getIndex(),
                        point.getName(),
                        point.getTopBottom(),
                        point.getX(),
                        point.getY(),
                        point.getNozzleName(),
                        point.getNozzleZ(),
                        point.getCropFactor(),
                        point.getReferenceBitmap()))
                        .append(System.lineSeparator());
            }
        }

        sb.append("Gantry Test execution dry-run PASSED.");

        return sb.toString();
    }
}