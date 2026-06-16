package org.openpnp.machine.reference.gantry;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
import org.openpnp.machine.reference.imageoffset.CsImageOffsetResult;
import org.openpnp.machine.reference.imageoffset.ReferenceImageOffsetService;

public final class GantryTestAllCyclesCapture {
    private static final double MOVE_SPEED = 0.20;
    private static final int MAX_POINT_VISITS = 500;
    private static final DateTimeFormatter OUTPUT_FOLDER_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss",
            Locale.US);

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

        Path outputCsvFile = outputFolder.resolve("Gantry Test output.csv");

        List<String> resultCsvLines = new ArrayList<>();
        resultCsvLines.add("Cycle,Visit,Point,Line,Name,X,Y,Nozzle,N_Z,Crop_factor,Top_Bot,"
                + "Ref_bmp,Reference_File,Captured_Crop_File,d_x_pixel,d_y_pixel,peak,dt_ms");

        StringBuilder sb = new StringBuilder();

        sb.append("Gantry Test all cycles capture").append(System.lineSeparator());
        sb.append("REAL MACHINE MOTION WAS REQUESTED.").append(System.lineSeparator());
        sb.append("All CSV cycles will be moved and captured.").append(System.lineSeparator());
        sb.append("Only Top camera capture is supported in this step.").append(System.lineSeparator());
        sb.append("Image offset calculation will be performed for every captured mono crop.")
                .append(System.lineSeparator());
        sb.append("Number of cycles = ").append(cycles).append(System.lineSeparator());
        sb.append("Point count = ").append(pointCount).append(System.lineSeparator());
        sb.append("Total point visits = ").append(totalPointVisits).append(System.lineSeparator());
        sb.append("Total BMP files to save = ").append(totalPointVisits * 3).append(System.lineSeparator());
        sb.append(String.format(Locale.US,
                "Move speed = %.2f of machine max speed",
                MOVE_SPEED)).append(System.lineSeparator());
        sb.append("Output folder = ").append(outputFolder).append(System.lineSeparator());
        sb.append("Output CSV = ").append(outputCsvFile).append(System.lineSeparator());

        int visitIndex = 0;

        for (int cycle = 1; cycle <= cycles; cycle++) {
            sb.append(String.format(Locale.US,
                    "Cycle %d / %d",
                    cycle,
                    cycles)).append(System.lineSeparator());

            for (GantryTestPoint point : input.getPoints()) {
                visitIndex++;

                if (!"Top".equals(point.getTopBottom())) {
                    throw new Exception("Step 4.12 only supports Top camera capture. "
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

                Path referenceBitmapFile = GantryTestReferenceImageResolver.findReferenceBitmap(input, point);

                if (referenceBitmapFile == null) {
                    throw new Exception("Cannot calculate image offset because Ref_bmp file was not found at cycle "
                            + cycle
                            + ", line "
                            + point.getLineNumber()
                            + ". Ref_bmp=\""
                            + point.getReferenceBitmap()
                            + "\".");
                }

                sb.append("    Reference BMP = ").append(referenceBitmapFile).append(System.lineSeparator());
                sb.append("    Calculating image offset...").append(System.lineSeparator());

                CsImageOffsetResult offsetResult = ReferenceImageOffsetService.findOffset(
                        referenceBitmapFile.toFile(),
                        cropFile.toFile());

                sb.append(String.format(Locale.US,
                        "    Image offset result: dx=%.6f px, dy=%.6f px, peak=%.9f, dt=%d ms",
                        offsetResult.getDx(),
                        offsetResult.getDy(),
                        offsetResult.getPeak(),
                        offsetResult.getDt())).append(System.lineSeparator());

                resultCsvLines.add(buildResultCsvLine(
                        cycle,
                        visitIndex,
                        point,
                        referenceBitmapFile,
                        cropFile,
                        offsetResult));
            }
        }

        Files.write(outputCsvFile, resultCsvLines, StandardCharsets.UTF_8);

        sb.append("Saved output CSV = ").append(outputCsvFile).append(System.lineSeparator());
        sb.append("Gantry Test all cycles capture PASSED.");

        return sb.toString();
    }

    private static Path createOutputFolder(GantryTestCsvInput input) throws Exception {
        Path baseFolder;

        if (input.getSourceFile() != null && input.getSourceFile().getParent() != null) {
            baseFolder = input.getSourceFile().getParent().resolve("Gantry Test Captures");
        } else {
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

    private static String buildResultCsvLine(
            int cycle,
            int visit,
            GantryTestPoint point,
            Path referenceBitmapFile,
            Path capturedCropFile,
            CsImageOffsetResult offsetResult) {
        return String.join(",",
                Integer.toString(cycle),
                Integer.toString(visit),
                Integer.toString(point.getIndex()),
                Integer.toString(point.getLineNumber()),
                csv(point.getName()),
                formatDouble(point.getX()),
                formatDouble(point.getY()),
                csv(point.getNozzleName()),
                formatDouble(point.getNozzleZ()),
                Integer.toString(point.getCropFactor()),
                csv(point.getTopBottom()),
                csv(point.getReferenceBitmap()),
                csv(referenceBitmapFile.toString()),
                csv(capturedCropFile.toString()),
                formatDouble(offsetResult.getDx()),
                formatDouble(offsetResult.getDy()),
                formatDouble(offsetResult.getPeak()),
                Long.toString(offsetResult.getDt()));
    }

    private static String formatDouble(double value) {
        return String.format(Locale.US, "%.9f", value);
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }

        boolean mustQuote = value.contains(",")
                || value.contains("\"")
                || value.contains("\r")
                || value.contains("\n");

        if (!mustQuote) {
            return value;
        }

        return "\"" + value.replace("\"", "\"\"") + "\"";
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