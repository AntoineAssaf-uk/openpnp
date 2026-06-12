package org.openpnp.machine.reference.gantry;

import java.util.Locale;

import org.openpnp.machine.reference.lookup.ReferenceMachineLookup;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Head;
import org.openpnp.spi.Machine;
import org.openpnp.util.MovableUtils;

public final class GantryTestSingleMove {
    private static final double FIRST_MOVE_SPEED = 0.20;

    private GantryTestSingleMove() {
    }

    public static String moveFirstPoint(GantryTestCsvInput input) throws Exception {
        if (input == null) {
            throw new Exception("Cannot move first point because CSV input is null.");
        }

        if (input.getPoints().isEmpty()) {
            throw new Exception("Cannot move first point because CSV contains no points.");
        }

        GantryTestPoint point = input.getPoints().get(0);

        if (!"Top".equals(point.getTopBottom())) {
            throw new Exception("Step 4.5 only supports Top camera moves. First point Top_Bot="
                    + point.getTopBottom());
        }

        Machine machine = ReferenceMachineLookup.getMachine();
        ReferenceMachineLookup.requireMachineEnabledAndHomed(machine);

        Head head = ReferenceMachineLookup.findHead(machine, ReferenceMachineLookup.HEAD_H1);
        Camera topCamera = ReferenceMachineLookup.findDefaultHeadCamera(head);

        Location targetLocation = new Location(
                LengthUnit.Millimeters,
                point.getX(),
                point.getY(),
                Double.NaN,
                Double.NaN);

        StringBuilder sb = new StringBuilder();

        sb.append("Gantry Test single move").append(System.lineSeparator());
        sb.append("REAL MACHINE MOTION WAS REQUESTED.").append(System.lineSeparator());
        sb.append("Only first CSV point will be moved.").append(System.lineSeparator());
        sb.append("No image capture will be performed.").append(System.lineSeparator());
        sb.append(String.format(Locale.US,
                "Point: line=%d, #=%d, Name=\"%s\"",
                point.getLineNumber(),
                point.getIndex(),
                point.getName())).append(System.lineSeparator());
        sb.append(String.format(Locale.US,
                "Target Top camera location: X=%.6f mm, Y=%.6f mm",
                point.getX(),
                point.getY())).append(System.lineSeparator());
        sb.append(String.format(Locale.US,
                "Move speed = %.2f of machine max speed",
                FIRST_MOVE_SPEED)).append(System.lineSeparator());

        MovableUtils.moveToLocationAtSafeZ(topCamera, targetLocation, FIRST_MOVE_SPEED);

        Location finalLocation = topCamera.getLocation();

        sb.append(String.format(Locale.US,
                "Top camera final location: X=%.6f mm, Y=%.6f mm, Z=%.6f mm, C=%.6f",
                finalLocation.getX(),
                finalLocation.getY(),
                finalLocation.getZ(),
                finalLocation.getRotation())).append(System.lineSeparator());

        sb.append("Gantry Test single move PASSED.");

        return sb.toString();
    }
}