package org.openpnp.machine.reference.gantry;

import java.util.Locale;

import org.openpnp.machine.reference.lookup.ReferenceMachineLookup;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Head;
import org.openpnp.spi.Machine;
import org.openpnp.util.MovableUtils;

public final class GantryTestAllCyclesMove {
    private static final double MOVE_SPEED = 1.0;
    private static final int MAX_POINT_VISITS = 10000;

    private GantryTestAllCyclesMove() {
    }

    public static String moveAllCycles(GantryTestCsvInput input) throws Exception {
        if (input == null) {
            throw new Exception("Cannot move all cycles because CSV input is null.");
        }

        if (input.getPoints().isEmpty()) {
            throw new Exception("Cannot move all cycles because CSV contains no points.");
        }

        int cycles = input.getNumberOfCycles();
        int pointCount = input.getPoints().size();
        int totalPointVisits = cycles * pointCount;

        if (totalPointVisits <= 0) {
            throw new Exception("Cannot move all cycles because total point visits is invalid: "
                    + totalPointVisits);
        }

        if (totalPointVisits > MAX_POINT_VISITS) {
            throw new Exception("Refusing to move " + totalPointVisits
                    + " point visits. Current safety limit is " + MAX_POINT_VISITS
                    + ". Reduce Number of cycles or point count for this test step.");
        }

        Machine machine = ReferenceMachineLookup.getMachine();
        ReferenceMachineLookup.requireMachineEnabledAndHomed(machine);

        Head head = ReferenceMachineLookup.findHead(machine, ReferenceMachineLookup.HEAD_H1);
        Camera topCamera = ReferenceMachineLookup.findDefaultHeadCamera(head);

        StringBuilder sb = new StringBuilder();

        sb.append("Gantry Test move all cycles").append(System.lineSeparator());
        sb.append("REAL MACHINE MOTION WAS REQUESTED.").append(System.lineSeparator());
        sb.append("All CSV cycles will be moved.").append(System.lineSeparator());
        sb.append("No image capture will be performed.").append(System.lineSeparator());
        sb.append("Number of cycles = ").append(cycles).append(System.lineSeparator());
        sb.append("Point count = ").append(pointCount).append(System.lineSeparator());
        sb.append("Total point visits = ").append(totalPointVisits).append(System.lineSeparator());
        sb.append(String.format(Locale.US,
                "Move speed = %.2f of machine max speed",
                MOVE_SPEED)).append(System.lineSeparator());

        int visitIndex = 0;

        for (int cycle = 1; cycle <= cycles; cycle++) {
            sb.append(String.format(Locale.US,
                    "Cycle %d / %d",
                    cycle,
                    cycles)).append(System.lineSeparator());

            for (GantryTestPoint point : input.getPoints()) {
                visitIndex++;

                if (!"Top".equals(point.getTopBottom())) {
                    throw new Exception("Step 4.7 only supports Top camera moves. "
                            + "Line " + point.getLineNumber()
                            + " has Top_Bot=" + point.getTopBottom());
                }

                Location targetLocation = new Location(
                        LengthUnit.Millimeters,
                        point.getX(),
                        point.getY(),
                        Double.NaN,
                        Double.NaN);

                sb.append(String.format(Locale.US,
                        "  Move %d / %d: point #%d \"%s\" -> Top camera X=%.6f mm, Y=%.6f mm",
                        visitIndex,
                        totalPointVisits,
                        point.getIndex(),
                        point.getName(),
                        point.getX(),
                        point.getY())).append(System.lineSeparator());

                MovableUtils.moveToLocationAtSafeZ(topCamera, targetLocation, MOVE_SPEED);

                Location finalLocation = topCamera.getLocation();

                sb.append(String.format(Locale.US,
                        "    Final Top camera location: X=%.6f mm, Y=%.6f mm, Z=%.6f mm, C=%.6f",
                        finalLocation.getX(),
                        finalLocation.getY(),
                        finalLocation.getZ(),
                        finalLocation.getRotation())).append(System.lineSeparator());
            }
        }

        sb.append("Gantry Test move all cycles PASSED.");

        return sb.toString();
    }
}