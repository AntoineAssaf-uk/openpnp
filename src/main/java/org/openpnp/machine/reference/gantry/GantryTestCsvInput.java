package org.openpnp.machine.reference.gantry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GantryTestCsvInput {
    private final Path sourceFile;
    private final int numberOfCycles;
    private final List<GantryTestPoint> points;

    public GantryTestCsvInput(Path sourceFile, int numberOfCycles, List<GantryTestPoint> points) {
        this.sourceFile = sourceFile;
        this.numberOfCycles = numberOfCycles;
        this.points = Collections.unmodifiableList(new ArrayList<>(points));
    }

    public Path getSourceFile() {
        return sourceFile;
    }

    public int getNumberOfCycles() {
        return numberOfCycles;
    }

    public List<GantryTestPoint> getPoints() {
        return points;
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();

        sb.append("Gantry Test CSV parse result").append(System.lineSeparator());
        sb.append("Source file = ").append(sourceFile).append(System.lineSeparator());
        sb.append("Number of cycles = ").append(numberOfCycles).append(System.lineSeparator());
        sb.append("Point count = ").append(points.size()).append(System.lineSeparator());

        if (points.isEmpty()) {
            sb.append("No gantry test points found after the header. This is valid for parser testing.")
                    .append(System.lineSeparator());
        }
        else {
            for (GantryTestPoint point : points) {
                sb.append(point.describe()).append(System.lineSeparator());
            }
        }

        sb.append("Gantry Test CSV parse PASSED.");

        return sb.toString();
    }
}