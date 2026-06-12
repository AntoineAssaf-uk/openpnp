package org.openpnp.machine.reference.gantry;

import java.util.Locale;

import org.openpnp.machine.reference.lookup.ReferenceMachineLookup;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Head;
import org.openpnp.spi.Machine;
import org.openpnp.util.MovableUtils;

public final class GantryTestSingleCycleMove {
    private static final double MOVE_SPEED = 0.20;

    private GantryTestSingleCycleMove() {
    }

    public static String moveCycleOne(GantryTestCsvInput input) throws Exception {
        if (input == null) {
            throw new Exception("Cannot move cycle 1 because CSV input is null.");
        }

        if (input.getPoints().isEmpty()) {
            throw new Exception("Cannot move cycle 1 because CSV contains no points.");
        }

        Machine machine = ReferenceMachineLookup.getMachine();
        ReferenceMachineLookup.requireMachineEnabledAndHomed(machine);

        Head head = ReferenceMachineLookup.findHead(machine, ReferenceMachineLookup.HEAD_H1);
        Camera topCamera = ReferenceMachineLookup.findDefaultHeadCamera(head);

        StringBuilder sb = new StringBuilder();

        sb.append("Gantry Test move cycle 1").append(System.lineSeparator());
        sb.append("REAL MACHINE MOTION WAS REQUESTED.").append(System.lineSeparator());
        sb.append("Only cycle 1 will be moved.").append(System.lineSeparator());
        sb.append("No image capture will be performed.").append(System.lineSeparator());
        sb.append("CSV Number of cycles is ignored in this step.").append(System.lineSeparator());
        sb.append("Point count = ").append(input.getPoints().size()).append(System.lineSeparator());
        sb.append(String.format(Locale.US,
                "Move speed = %.2f of machine max speed",
                MOVE_SPEED)).append(System.lineSeparator());

        int moveIndex = 0;

        for (GantryTestPoint point : input.getPoints()) {
            moveIndex++;

            if (!"Top".equals(point.getTopBottom())) {
                throw new Exception("Step 4.6 only supports Top camera moves. "
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
                    "Move %d / %d: point #%d \"%s\" -> Top camera X=%.6f mm, Y=%.6f mm",
                    moveIndex,
                    input.getPoints().size(),
                    point.getIndex(),
                    point.getName(),
                    point.getX(),
                    point.getY())).append(System.lineSeparator());

            MovableUtils.moveToLocationAtSafeZ(topCamera, targetLocation, MOVE_SPEED);

            Location finalLocation = topCamera.getLocation();

            sb.append(String.format(Locale.US,
                    "  Final Top camera location: X=%.6f mm, Y=%.6f mm, Z=%.6f mm, C=%.6f",
                    finalLocation.getX(),
                    finalLocation.getY(),
                    finalLocation.getZ(),
                    finalLocation.getRotation())).append(System.lineSeparator());
        }

        sb.append("Gantry Test move cycle 1 PASSED.");

        return sb.toString();
    }
}