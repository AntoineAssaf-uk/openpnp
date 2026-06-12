package org.openpnp.machine.reference.gantry;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.openpnp.machine.reference.capture.ReferenceImageCaptureService;
import org.openpnp.machine.reference.lookup.ReferenceMachineLookup;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Head;
import org.openpnp.spi.Machine;
import org.openpnp.util.MovableUtils;

public final class GantryTestAllCyclesCapture {
    private static final double MOVE_SPEED = 0.20;
    private static final int MAX_POINT_VISITS = 500;
    private static final DateTimeFormatter OUTPUT_FOLDER_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US);

    private GantryTestAllCyclesCapture() {
    }

    public static String moveAndCaptureAllCycles(GantryTestCsvInput input) throws Exception {
        if (input == null) {
            throw new Exception("Cannot capture all cycles because CSV input is null.");
        }

        if (input.getPoints().isEmpty()) {
            throw new Exception("Cannot capture all cycles because CSV contains no points.");
        }

        int cycles = input.getNumberOfCycles();
        int pointCount = input.getPoints().size();
        int totalPointVisits = cycles * pointCount;

        if (totalPointVisits <= 0) {
            throw new Exception("Cannot capture all cycles because total point visits is invalid: "
                    + totalPointVisits);
        }

        if (totalPointVisits > MAX_POINT_VISITS) {
            throw new Exception("Refusing to capture " + totalPointVisits
                    + " point visits. Current safety limit is " + MAX_POINT_VISITS
                    + ". Reduce Number of cycles or point count for this test step.");
        }

        Machine machine = ReferenceMachineLookup.getMachine();
        ReferenceMachineLookup.requireMachineEnabledAndHomed(machine);

        Head head = ReferenceMachineLookup.findHead(machine, ReferenceMachineLookup.HEAD_H1);
        Camera topCamera = ReferenceMachineLookup.findDefaultHeadCamera(head);

        Path outputFolder = createOutputFolder(input);

        StringBuilder sb = new StringBuilder();

        sb.append("Gantry Test all cycles capture").append(System.lineSeparator());
        sb.append("REAL MACHINE MOTION WAS REQUESTED.").append(System.lineSeparator());
        sb.append("All CSV cycles will be moved and captured.").append(System.lineSeparator());
        sb.append("Only Top camera capture is supported in this step.").append(System.lineSeparator());
        sb.append("No image offset calculation will be performed.").append(System.lineSeparator());
        sb.append("Number of cycles = ").append(cycles).append(System.lineSeparator());
        sb.append("Point count = ").append(pointCount).append(System.lineSeparator());
        sb.append("Total point visits = ").append(totalPointVisits).append(System.lineSeparator());
        sb.append("Total BMP files to save = ").append(totalPointVisits * 3).append(System.lineSeparator());
        sb.append(String.format(Locale.US,
                "Move speed = %.2f of machine max speed",
                MOVE_SPEED)).append(System.lineSeparator());
        sb.append("Output folder = ").append(outputFolder).append(System.lineSeparator());

        int visitIndex = 0;

        for (int cycle = 1; cycle <= cycles; cycle++) {
            sb.append(String.format(Locale.US,
                    "Cycle %d / %d",
                    cycle,
                    cycles)).append(System.lineSeparator());

            for (GantryTestPoint point : input.getPoints()) {
                visitIndex++;

                if (!"Top".equals(point.getTopBottom())) {
                    throw new Exception("Step 4.10 only supports Top camera capture. "
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
                        "  Visit %d / %d: cycle=%d, line=%d, #=%d, Name=\"%s\"",
                        visitIndex,
                        totalPointVisits,
                        cycle,
                        point.getLineNumber(),
                        point.getIndex(),
                        point.getName())).append(System.lineSeparator());

                sb.append(String.format(Locale.US,
                        "    Target Top camera location: X=%.6f mm, Y=%.6f mm",
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

                sb.append("    Capturing Top camera image...").append(System.lineSeparator());

                BufferedImage originalImage = topCamera.lightSettleAndCapture();

                if (originalImage == null) {
                    throw new Exception("Top camera capture returned null image at cycle "
                            + cycle + ", line " + point.getLineNumber() + ".");
                }

                BufferedImage monoImage = ReferenceImageCaptureService.createGrayscaleLuminosityImage(originalImage);
                BufferedImage cropImage = ReferenceImageCaptureService.cropCentered(monoImage, point.getCropFactor());

                if (cropImage == null) {
                    throw new Exception("Cannot create centered mono crop at cycle "
                            + cycle
                            + ", line "
                            + point.getLineNumber()
                            + ". Crop_factor="
                            + point.getCropFactor()
                            + ", image width="
                            + monoImage.getWidth()
                            + ", image height="
                            + monoImage.getHeight());
                }

                String baseFileName = buildBaseFileName(cycle, point);

                Path originalFile = outputFolder.resolve(baseFileName + "_Original.bmp");
                Path monoFile = outputFolder.resolve(baseFileName + "_Mono.bmp");
                Path cropFile = outputFolder.resolve(baseFileName + "_Mono_Crop_" + point.getCropFactor() + ".bmp");

                saveBmp(originalImage, originalFile);
                saveBmp(monoImage, monoFile);
                saveBmp(cropImage, cropFile);

                sb.append("    Saved original BMP = ").append(originalFile).append(System.lineSeparator());
                sb.append("    Saved mono BMP = ").append(monoFile).append(System.lineSeparator());
                sb.append("    Saved mono crop BMP = ").append(cropFile).append(System.lineSeparator());
            }
        }

        sb.append("Gantry Test all cycles capture PASSED.");

        return sb.toString();
    }

    private static Path createOutputFolder(GantryTestCsvInput input) throws Exception {
        Path baseFolder;

        if (input.getSourceFile() != null && input.getSourceFile().getParent() != null) {
            baseFolder = input.getSourceFile().getParent().resolve("Gantry Test Captures");
        }
        else {
            baseFolder = Paths.get("C:\\Opulo\\Tests\\Gantry Test\\Gantry Test Captures");
        }

        Path outputFolder = baseFolder.resolve("All_Cycles_" + LocalDateTime.now().format(OUTPUT_FOLDER_TIMESTAMP));

        Files.createDirectories(outputFolder);

        return outputFolder;
    }

    private static String buildBaseFileName(int cycle, GantryTestPoint point) {
        return String.format(Locale.US,
                "Cycle_%03d_Point_%03d_Line_%03d_Top",
                cycle,
                point.getIndex(),
                point.getLineNumber());
    }

    private static void saveBmp(BufferedImage image, Path file) throws Exception {
        if (image == null) {
            throw new Exception("Cannot save BMP file because image is null: " + file);
        }

        boolean ok = ImageIO.write(image, "bmp", file.toFile());

        if (!ok) {
            throw new Exception("No BMP image writer is available for file: " + file);
        }
    }
}