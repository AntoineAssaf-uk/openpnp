package org.openpnp.machine.photon;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.openpnp.machine.reference.ReferenceNozzle;
import org.openpnp.machine.reference.ReferenceNozzleTip;
import org.openpnp.machine.reference.capture.ReferenceImageCaptureService;
import org.openpnp.machine.reference.imageoffset.CsImageOffsetResult;
import org.openpnp.machine.reference.imageoffset.ReferenceImageOffsetService;
import org.openpnp.model.Configuration;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Feeder;
import org.openpnp.spi.Head;
import org.openpnp.spi.Machine;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.MotionPlanner.CompletionType;
import org.openpnp.util.MovableUtils;
import org.pmw.tinylog.Logger;

public class PhotonFeederAutomaticSetup {
    private static final String HEAD_NAME = "H1";

    private static final double MOVE_SPEED = 1.00;

    private static final double AUTOMATIC_FEEDER_HEIGHT_APPROACH_Z_MM = 8.5;
    private static final double AUTOMATIC_FEEDER_HEIGHT_PROBE_STEP_MM = 0.5;
    private static final double AUTOMATIC_FEEDER_HEIGHT_RETRACT_STEP_MM = 0.1;
    private static final double AUTOMATIC_FEEDER_HEIGHT_MIN_Z_MM = 5.0;
    private static final double AUTOMATIC_FEEDER_HEIGHT_MAX_RETRACT_MM = 2.0;
    private static final double AUTOMATIC_FEEDER_HEIGHT_APPROACH_SPEED = 0.25;
    private static final double AUTOMATIC_FEEDER_HEIGHT_PROBE_SPEED = 0.10;
    private static final double AUTOMATIC_FEEDER_HEIGHT_RETRACT_SPEED = 0.10;
    private static final int AUTOMATIC_FEEDER_HEIGHT_VACUUM_SETTLE_MS = 500;
    private static final int AUTOMATIC_FEEDER_HEIGHT_STEP_SETTLE_MS = 500;

    private static final Path CALIBRATION_FOLDER = Paths.get("C:\\Opulo\\Data\\Calibration");

    private static final DateTimeFormatter OUTPUT_FOLDER_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss",
            Locale.US);

    private PhotonFeederAutomaticSetup() {
    }

    public static SearchResult searchAndCollectValidFeeders(
            PhotonFeeder.FeederSearchProgressConsumer progressUpdate) throws Exception {
        PhotonFeeder.findAllFeeders(progressUpdate);
        return collectValidFeeders();
    }

    public static FirstFeederOffsetMeasurementResult searchAndMeasureFirstValidFeederOffset(
            PhotonFeederCalibrationCsv.CalibrationData calibrationData,
            PhotonFeeder.FeederSearchProgressConsumer progressUpdate) throws Exception {
        if (calibrationData == null) {
            throw new Exception("Calibration CSV data is null.");
        }

        SearchResult searchResult = searchAndCollectValidFeeders(progressUpdate);

        FeederSummary firstValidFeeder = findFirstValidFeeder(searchResult);

        Path outputFolder = createOutputFolderIfNeeded(calibrationData);

        OffsetMeasurementResult offsetMeasurementResult = measureFeederFiducialOffset(firstValidFeeder, calibrationData,
                1, outputFolder);

        return new FirstFeederOffsetMeasurementResult(
                searchResult,
                firstValidFeeder,
                offsetMeasurementResult);
    }

    public static FirstFeederXyCorrectionResult searchAndCorrectFirstValidFeederXy(
            PhotonFeederCalibrationCsv.CalibrationData calibrationData,
            PhotonFeeder.FeederSearchProgressConsumer progressUpdate) throws Exception {
        if (calibrationData == null) {
            throw new Exception("Calibration CSV data is null.");
        }

        SearchResult searchResult = searchAndCollectValidFeeders(progressUpdate);

        FeederSummary firstValidFeeder = findFirstValidFeeder(searchResult);

        FirstFeederXyCorrectionResult result = correctFeederXy(firstValidFeeder, searchResult, calibrationData);

        if (result.isSuccess()) {
            Configuration.get().save();
            result.setConfigurationSaved(true);
        }

        return result;
    }

    public static FirstFeederXyAndZCorrectionResult searchAndCorrectFirstValidFeederXyAndZ(
            PhotonFeederCalibrationCsv.CalibrationData calibrationData,
            PhotonFeeder.FeederSearchProgressConsumer progressUpdate) throws Exception {
        if (calibrationData == null) {
            throw new Exception("Calibration CSV data is null.");
        }

        SearchResult searchResult = searchAndCollectValidFeeders(progressUpdate);

        FeederSummary firstValidFeeder = findFirstValidFeeder(searchResult);

        FirstFeederXyCorrectionResult xyCorrectionResult = correctFeederXy(firstValidFeeder, searchResult,
                calibrationData);

        if (!xyCorrectionResult.isSuccess()) {
            throw new Exception("First feeder XY correction did not reach requested precision. "
                    + "Z probing was not executed.");
        }

        FeederZProbeResult zProbeResult = detectAndUpdateFeederSlotZ(firstValidFeeder, calibrationData);

        Location finalSlotLocation = getCurrentSlotLocation(firstValidFeeder);

        Configuration.get().save();

        return new FirstFeederXyAndZCorrectionResult(
                xyCorrectionResult,
                zProbeResult,
                finalSlotLocation,
                true);
    }

    public static AllFeedersXyAndZCorrectionResult searchAndCorrectAllValidFeedersXyAndZ(
            PhotonFeederCalibrationCsv.CalibrationData calibrationData,
            PhotonFeeder.FeederSearchProgressConsumer progressUpdate) throws Exception {
        if (calibrationData == null) {
            throw new Exception("Calibration CSV data is null.");
        }

        String issuedOnTimestamp = PhotonFeederCalibrationCsv.createIssuedOnTimestamp();

        SearchResult searchResult = searchAndCollectValidFeeders(progressUpdate);

        if (searchResult.getValidCount() <= 0) {
            throw new Exception("No valid Photon feeders were found for automatic setup.");
        }

        Path outputFolder = createOutputFolderIfNeeded(calibrationData, issuedOnTimestamp);

        List<FirstFeederXyAndZCorrectionResult> feederResults = new ArrayList<>();
        List<PhotonFeederCalibrationCsv.FeederResultLine> csvResultLines = new ArrayList<>();

        boolean configurationSaved = false;

        for (FeederSummary feederSummary : searchResult.getFeederSummaries()) {
            if (!feederSummary.isValid()) {
                continue;
            }

            try {
                FirstFeederXyCorrectionResult xyCorrectionResult = correctFeederXy(
                        feederSummary,
                        searchResult,
                        calibrationData,
                        outputFolder);

                if (!xyCorrectionResult.isSuccess()) {
                    throw new Exception("XY correction did not reach requested precision within "
                            + calibrationData.getTentatives()
                            + " tentatives.");
                }

                FeederZProbeResult zProbeResult = detectAndUpdateFeederSlotZ(feederSummary, calibrationData);

                Location finalSlotLocation = getCurrentSlotLocation(feederSummary);

                Configuration.get().save();
                configurationSaved = true;

                feederResults.add(new FirstFeederXyAndZCorrectionResult(
                        xyCorrectionResult,
                        zProbeResult,
                        finalSlotLocation,
                        true));

                csvResultLines.add(PhotonFeederCalibrationCsv.FeederResultLine.create(
                        feederSummary.getSlotAddress(),
                        finalSlotLocation,
                        false));
            } catch (Exception e) {
                Location failedSlotLocation = null;
                try {
                    failedSlotLocation = getCurrentSlotLocation(feederSummary);
                    csvResultLines.add(PhotonFeederCalibrationCsv.FeederResultLine.create(
                            feederSummary.getSlotAddress(),
                            failedSlotLocation,
                            true));
                } catch (Exception locationException) {
                    Logger.warn(locationException,
                            "Automatic feeder setup failed to read failed feeder slot location.");
                }

                PhotonFeederCalibrationCsv.rewriteResults(
                        calibrationData,
                        issuedOnTimestamp,
                        csvResultLines);

                return new AllFeedersXyAndZCorrectionResult(
                        searchResult,
                        feederResults,
                        outputFolder,
                        configurationSaved,
                        false,
                        feederSummary,
                        failedSlotLocation,
                        String.format(Locale.US,
                                "Automatic setup failed for Slot %d, hardware %s.%n%n%s",
                                feederSummary.getSlotAddress(),
                                feederSummary.getHardwareId(),
                                e.getMessage()),
                        true,
                        calibrationData.getCsvPath(),
                        issuedOnTimestamp);
            }
        }

        PhotonFeederCalibrationCsv.rewriteResults(
                calibrationData,
                issuedOnTimestamp,
                csvResultLines);

        return new AllFeedersXyAndZCorrectionResult(
                searchResult,
                feederResults,
                outputFolder,
                configurationSaved,
                true,
                null,
                null,
                null,
                true,
                calibrationData.getCsvPath(),
                issuedOnTimestamp);
    }

    public static SearchResult collectValidFeeders() {
        List<FeederSummary> feederSummaries = new ArrayList<>();

        for (Feeder feeder : Configuration.get().getMachine().getFeeders()) {
            if (!(feeder instanceof PhotonFeeder)) {
                continue;
            }

            PhotonFeeder photonFeeder = (PhotonFeeder) feeder;
            feederSummaries.add(createFeederSummary(photonFeeder));
        }

        Collections.sort(feederSummaries, (left, right) -> {
            Integer leftSlot = left.getSlotAddress();
            Integer rightSlot = right.getSlotAddress();

            if (leftSlot == null && rightSlot == null) {
                return left.getDisplayName().compareToIgnoreCase(right.getDisplayName());
            }
            if (leftSlot == null) {
                return 1;
            }
            if (rightSlot == null) {
                return -1;
            }
            return leftSlot.compareTo(rightSlot);
        });

        int validCount = 0;
        for (FeederSummary feederSummary : feederSummaries) {
            if (feederSummary.isValid()) {
                validCount++;
            }
        }

        return new SearchResult(feederSummaries, validCount);
    }

    private static FeederSummary findFirstValidFeeder(SearchResult searchResult) throws Exception {
        for (FeederSummary feederSummary : searchResult.getFeederSummaries()) {
            if (feederSummary.isValid()) {
                return feederSummary;
            }
        }

        throw new Exception("No valid Photon feeders were found for automatic setup.");
    }

    private static FirstFeederXyCorrectionResult correctFeederXy(
            FeederSummary feederSummary,
            SearchResult searchResult,
            PhotonFeederCalibrationCsv.CalibrationData calibrationData) throws Exception {
        Path outputFolder = createOutputFolderIfNeeded(calibrationData);

        return correctFeederXy(
                feederSummary,
                searchResult,
                calibrationData,
                outputFolder);
    }

    private static FirstFeederXyCorrectionResult correctFeederXy(
            FeederSummary feederSummary,
            SearchResult searchResult,
            PhotonFeederCalibrationCsv.CalibrationData calibrationData,
            Path outputFolder) throws Exception {
        List<OffsetMeasurementResult> measurements = new ArrayList<>();
        int correctionsApplied = 0;
        boolean success = false;

        for (int tentative = 1; tentative <= calibrationData.getTentatives(); tentative++) {
            OffsetMeasurementResult measurement = measureFeederFiducialOffset(
                    feederSummary,
                    calibrationData,
                    tentative,
                    outputFolder);

            measurements.add(measurement);

            if (measurement.isWithinPrecision()) {
                success = true;
                break;
            }

            applyFeederSlotXyCorrection(feederSummary, measurement);
            correctionsApplied++;
        }

        Location finalSlotLocation = getCurrentSlotLocation(feederSummary);

        return new FirstFeederXyCorrectionResult(
                searchResult,
                feederSummary,
                measurements,
                success,
                correctionsApplied,
                finalSlotLocation,
                outputFolder,
                false);
    }

    private static OffsetMeasurementResult measureFeederFiducialOffset(
            FeederSummary feederSummary,
            PhotonFeederCalibrationCsv.CalibrationData calibrationData,
            int tentative,
            Path outputFolder) throws Exception {
        Machine machine = Configuration.get().getMachine();
        if (machine == null) {
            throw new Exception("No OpenPnP machine configuration is loaded.");
        }
        if (!machine.isEnabled()) {
            throw new Exception("Machine is not enabled. Enable the machine before automatic feeder setup.");
        }
        if (!machine.isHomed()) {
            throw new Exception("Machine is not homed. Home the machine before automatic feeder setup.");
        }

        Head head = machine.getHeadByName(HEAD_NAME);
        if (head == null) {
            throw new Exception("Machine head " + HEAD_NAME + " was not found.");
        }

        Camera topCamera = head.getDefaultCamera();
        if (topCamera == null) {
            throw new Exception("Head " + HEAD_NAME + " does not have a default Top camera.");
        }

        Location slotLocationMm = getCurrentSlotLocation(feederSummary);

        Location cameraTargetLocation = new Location(
                LengthUnit.Millimeters,
                slotLocationMm.getX(),
                slotLocationMm.getY(),
                Double.NaN,
                Double.NaN);

        head.moveToSafeZ();
        MovableUtils.moveToLocationAtSafeZ(topCamera, cameraTargetLocation, MOVE_SPEED);
        MovableUtils.fireTargetedUserAction(topCamera);

        BufferedImage originalImage = topCamera.lightSettleAndCapture();
        if (originalImage == null) {
            throw new Exception("Top camera capture returned null image for slot "
                    + feederSummary.getSlotAddress() + ".");
        }

        BufferedImage monoImage = ReferenceImageCaptureService.createGrayscaleLuminosityImage(originalImage);

        BufferedImage cropImage = ReferenceImageCaptureService.cropCentered(
                monoImage,
                calibrationData.getCrop());

        if (cropImage == null) {
            throw new Exception(String.format(Locale.US,
                    "Cannot create %d x %d crop from Top camera image %d x %d.",
                    calibrationData.getCrop(),
                    calibrationData.getCrop(),
                    monoImage.getWidth(),
                    monoImage.getHeight()));
        }

        BufferedImage referenceImage = ImageIO.read(calibrationData.getReferenceImagePath().toFile());
        if (referenceImage == null) {
            throw new Exception("Unable to read reference image: "
                    + calibrationData.getReferenceImagePath());
        }

        CsImageOffsetResult imageOffsetResult = ReferenceImageOffsetService.findOffset(referenceImage, cropImage);

        Location unitsPerPixel = topCamera.getUnitsPerPixelAtZ()
                .convertToUnits(LengthUnit.Millimeters);

        double dxMm = imageOffsetResult.getDx() * unitsPerPixel.getX();
        double dyMm = imageOffsetResult.getDy() * unitsPerPixel.getY();
        double dxUm = dxMm * 1000.0;
        double dyUm = dyMm * 1000.0;

        Path savedCropFile = null;

        if (outputFolder != null) {
            savedCropFile = outputFolder.resolve(String.format(Locale.US,
                    "Slot_%d_Tentative_%d_Crop_%d.bmp",
                    feederSummary.getSlotAddress(),
                    tentative,
                    calibrationData.getCrop()));

            saveBmp(cropImage, savedCropFile);
        }

        boolean withinPrecision = Math.abs(dxUm) <= calibrationData.getPrecisionUm()
                && Math.abs(dyUm) <= calibrationData.getPrecisionUm();

        return new OffsetMeasurementResult(
                tentative,
                calibrationData.getCrop(),
                slotLocationMm,
                topCamera.getLocation().convertToUnits(LengthUnit.Millimeters),
                unitsPerPixel,
                imageOffsetResult.getDx(),
                imageOffsetResult.getDy(),
                dxMm,
                dyMm,
                dxUm,
                dyUm,
                imageOffsetResult.getPeak(),
                imageOffsetResult.getDt(),
                withinPrecision,
                outputFolder,
                savedCropFile);
    }

    private static FeederZProbeResult detectAndUpdateFeederSlotZ(
            FeederSummary feederSummary,
            PhotonFeederCalibrationCsv.CalibrationData calibrationData) throws Exception {
        Machine machine = Configuration.get().getMachine();
        if (machine == null) {
            throw new Exception("No OpenPnP machine configuration is loaded.");
        }
        if (!machine.isEnabled()) {
            throw new Exception("Machine is not enabled. Enable the machine before automatic feeder setup.");
        }
        if (!machine.isHomed()) {
            throw new Exception("Machine is not homed. Home the machine before automatic feeder setup.");
        }

        Head head = machine.getHeadByName(HEAD_NAME);
        if (head == null) {
            throw new Exception("Machine head " + HEAD_NAME + " was not found.");
        }

        Nozzle nozzle = head.getNozzleByName(calibrationData.getNozzleName());
        if (nozzle == null) {
            throw new Exception("Nozzle \"" + calibrationData.getNozzleName()
                    + "\" was not found on head " + HEAD_NAME + ".");
        }

        if (!(nozzle instanceof ReferenceNozzle)) {
            throw new Exception("Automatic feeder setup Z probing currently requires a ReferenceNozzle.");
        }

        ReferenceNozzle referenceNozzle = (ReferenceNozzle) nozzle;
        if (referenceNozzle.getVacuumActuator() == null) {
            throw new Exception("The selected nozzle has no vacuum actuator configured.");
        }
        if (referenceNozzle.getVacuumSenseActuator() == null) {
            throw new Exception("The selected nozzle has no vacuum sense actuator configured.");
        }
        if (!(nozzle.getNozzleTip() instanceof ReferenceNozzleTip)) {
            throw new Exception("The selected nozzle tip is not a ReferenceNozzleTip.");
        }

        Location oldSlotLocation = getCurrentSlotLocation(feederSummary);

        Length safeZ = nozzle.getEffectiveSafeZ();
        if (safeZ == null) {
            throw new Exception("The selected nozzle has no effective Safe Z.");
        }

        double targetZ = safeZ.convertToUnits(LengthUnit.Millimeters).getValue();

        Location nozzleTargetLocation = new Location(
                LengthUnit.Millimeters,
                oldSlotLocation.getX(),
                oldSlotLocation.getY(),
                targetZ,
                Double.NaN);

        FeederZProbeResult zProbeResult = null;

        try {
            MovableUtils.moveToLocationAtSafeZ(nozzle, nozzleTargetLocation);
            MovableUtils.fireTargetedUserAction(nozzle);

            double threshold = getFeederHeightVacuumThreshold(nozzle);

            setFeederHeightVacuum(nozzle, true);
            Thread.sleep(AUTOMATIC_FEEDER_HEIGHT_VACUUM_SETTLE_MS);

            zProbeResult = probeFeederSlotZ(
                    nozzle,
                    nozzleTargetLocation,
                    threshold,
                    oldSlotLocation.getZ());
        } finally {
            cleanupFeederHeightNozzle(nozzle);
        }

        Location updatedSlotLocation = oldSlotLocation.derive(
                null,
                null,
                zProbeResult.getEstimatedSlotZ(),
                null);

        feederSummary.getFeeder().getSlot().setLocation(updatedSlotLocation);

        zProbeResult.setUpdatedSlotLocation(updatedSlotLocation);

        return zProbeResult;
    }

    private static FeederZProbeResult probeFeederSlotZ(
            Nozzle nozzle,
            Location startLocation,
            double threshold,
            double oldSlotZ) throws Exception {
        double probeStepZ = new Length(
                AUTOMATIC_FEEDER_HEIGHT_PROBE_STEP_MM,
                LengthUnit.Millimeters)
                .convertToUnits(startLocation.getUnits())
                .getValue();

        double retractStepZ = new Length(
                AUTOMATIC_FEEDER_HEIGHT_RETRACT_STEP_MM,
                LengthUnit.Millimeters)
                .convertToUnits(startLocation.getUnits())
                .getValue();

        double approachZ = new Length(
                AUTOMATIC_FEEDER_HEIGHT_APPROACH_Z_MM,
                LengthUnit.Millimeters)
                .convertToUnits(startLocation.getUnits())
                .getValue();

        double minZ = new Length(
                AUTOMATIC_FEEDER_HEIGHT_MIN_Z_MM,
                LengthUnit.Millimeters)
                .convertToUnits(startLocation.getUnits())
                .getValue();

        double maxRetractZ = new Length(
                AUTOMATIC_FEEDER_HEIGHT_MAX_RETRACT_MM,
                LengthUnit.Millimeters)
                .convertToUnits(startLocation.getUnits())
                .getValue();

        double startZ = startLocation.getZ();

        if (approachZ < minZ) {
            throw new Exception(String.format(Locale.US,
                    "Invalid automatic feeder Z probing settings.%n%n"
                            + "Approach Z = %.3f mm%n"
                            + "Minimum allowed Z = %.3f mm",
                    approachZ,
                    minZ));
        }

        Location approachLocation = startLocation.derive(
                null,
                null,
                approachZ,
                null);

        Logger.info(String.format(Locale.US,
                "Automatic feeder Z probing: fast approach from Z=%.3f to Z=%.3f",
                startZ,
                approachZ));

        nozzle.moveTo(approachLocation, AUTOMATIC_FEEDER_HEIGHT_APPROACH_SPEED);
        nozzle.waitForCompletion(CompletionType.WaitForStillstand);

        double z = approachZ - probeStepZ;
        int probeSteps = 0;
        double contactZ;
        double contactReading;

        while (true) {
            if (z < minZ) {
                throw new Exception(String.format(Locale.US,
                        "Feeder contact was not detected before the hard safety limit.%n%n"
                                + "Next requested Z = %.3f mm%n"
                                + "Minimum allowed Z = %.3f mm",
                        z,
                        minZ));
            }

            Location probeLocation = startLocation.derive(
                    null,
                    null,
                    z,
                    null);

            nozzle.moveTo(probeLocation, AUTOMATIC_FEEDER_HEIGHT_PROBE_SPEED);
            nozzle.waitForCompletion(CompletionType.WaitForStillstand);

            Thread.sleep(AUTOMATIC_FEEDER_HEIGHT_STEP_SETTLE_MS);

            double reading = readFeederHeightVacuum(nozzle);
            probeSteps++;

            Logger.info(String.format(Locale.US,
                    "Automatic feeder Z probe: step=%d Z=%.3f vacuum=%.3f threshold=%.3f",
                    probeSteps,
                    z,
                    reading,
                    threshold));

            if (reading <= threshold) {
                contactZ = z;
                contactReading = reading;
                break;
            }

            z -= probeStepZ;
        }

        double releaseZ = contactZ;
        double releaseReading = contactReading;
        int retractSteps = 0;

        while (true) {
            releaseZ += retractStepZ;

            if (releaseZ > contactZ + maxRetractZ) {
                throw new Exception(String.format(Locale.US,
                        "Feeder contact release was not detected within the allowed retract distance.%n%n"
                                + "Contact Z = %.3f mm%n"
                                + "Last requested release Z = %.3f mm%n"
                                + "Maximum allowed release Z = %.3f mm",
                        contactZ,
                        releaseZ,
                        contactZ + maxRetractZ));
            }

            Location releaseLocation = startLocation.derive(
                    null,
                    null,
                    releaseZ,
                    null);

            nozzle.moveTo(releaseLocation, AUTOMATIC_FEEDER_HEIGHT_RETRACT_SPEED);
            nozzle.waitForCompletion(CompletionType.WaitForStillstand);

            Thread.sleep(AUTOMATIC_FEEDER_HEIGHT_STEP_SETTLE_MS);

            releaseReading = readFeederHeightVacuum(nozzle);
            retractSteps++;

            Logger.info(String.format(Locale.US,
                    "Automatic feeder Z retract: step=%d Z=%.3f vacuum=%.3f threshold=%.3f",
                    retractSteps,
                    releaseZ,
                    releaseReading,
                    threshold));

            if (releaseReading > threshold) {
                break;
            }
        }

        double estimatedSlotZ = 0.5 * (contactZ + releaseZ);

        return new FeederZProbeResult(
                nozzle.getName(),
                oldSlotZ,
                startZ,
                approachZ,
                contactZ,
                releaseZ,
                estimatedSlotZ,
                minZ,
                threshold,
                contactReading,
                releaseReading,
                probeSteps,
                retractSteps);
    }

    private static void setFeederHeightVacuum(Nozzle nozzle, boolean on) throws Exception {
        ReferenceNozzle referenceNozzle = (ReferenceNozzle) nozzle;

        if (on) {
            nozzle.getHead().actuatePumpRequest(nozzle, true);
        }

        referenceNozzle.getExpectedVacuumActuator().actuate(on);

        if (!on) {
            nozzle.getHead().actuatePumpRequest(nozzle, false);
        }
    }

    private static double readFeederHeightVacuum(Nozzle nozzle) throws Exception {
        ReferenceNozzle referenceNozzle = (ReferenceNozzle) nozzle;
        return referenceNozzle.readVacuumLevel();
    }

    private static void cleanupFeederHeightNozzle(Nozzle nozzle) {
        try {
            nozzle.getHead().moveToSafeZ();
        } catch (Exception e) {
            Logger.warn(e, "Automatic feeder Z probing: failed to park nozzle Z.");
        }

        try {
            setFeederHeightVacuum(nozzle, false);
        } catch (Exception e) {
            Logger.warn(e, "Automatic feeder Z probing: failed to turn vacuum off.");
        }
    }

    private static double getFeederHeightVacuumThreshold(Nozzle nozzle) throws Exception {
        if (!(nozzle.getNozzleTip() instanceof ReferenceNozzleTip)) {
            throw new Exception("The selected nozzle tip is not a ReferenceNozzleTip.");
        }

        ReferenceNozzleTip nozzleTip = (ReferenceNozzleTip) nozzle.getNozzleTip();

        double low = nozzleTip.getVacuumLevelPartOnLow();
        double high = nozzleTip.getVacuumLevelPartOnHigh();

        if (low == high) {
            throw new Exception("The selected nozzle tip has invalid vacuum thresholds.");
        }

        double min = Math.min(low, high);
        double max = Math.max(low, high);

        return min + 0.5 * (max - min);
    }

    private static Path createOutputFolderIfNeeded(
            PhotonFeederCalibrationCsv.CalibrationData calibrationData) throws Exception {
        return createOutputFolderIfNeeded(
                calibrationData,
                LocalDateTime.now().format(OUTPUT_FOLDER_TIMESTAMP));
    }

    private static Path createOutputFolderIfNeeded(
            PhotonFeederCalibrationCsv.CalibrationData calibrationData,
            String timestamp) throws Exception {
        if (!calibrationData.isSaveImages()) {
            return null;
        }

        Path outputFolder = CALIBRATION_FOLDER.resolve(timestamp);
        Files.createDirectories(outputFolder);
        return outputFolder;
    }

    private static Location getCurrentSlotLocation(FeederSummary feederSummary) throws Exception {
        if (feederSummary == null || feederSummary.getFeeder() == null) {
            throw new Exception("Feeder summary is missing.");
        }

        if (feederSummary.getFeeder().getSlot() == null) {
            throw new Exception("Slot object is missing for slot "
                    + feederSummary.getSlotAddress() + ".");
        }

        Location slotLocation = feederSummary.getFeeder().getSlot().getLocation();
        if (slotLocation == null) {
            throw new Exception("Slot location is null for slot "
                    + feederSummary.getSlotAddress() + ".");
        }

        return slotLocation.convertToUnits(LengthUnit.Millimeters);
    }

    private static Location applyFeederSlotXyCorrection(
            FeederSummary feederSummary,
            OffsetMeasurementResult measurement) throws Exception {
        Location oldSlotLocation = getCurrentSlotLocation(feederSummary);

        Location correctedSlotLocation = oldSlotLocation.derive(
                oldSlotLocation.getX() + measurement.getDxMm(),
                oldSlotLocation.getY() + measurement.getDyMm(),
                null,
                null);

        feederSummary.getFeeder().getSlot().setLocation(correctedSlotLocation);

        return correctedSlotLocation;
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

    private static FeederSummary createFeederSummary(PhotonFeeder feeder) {
        String displayName = feeder.getName();
        String hardwareId = feeder.getHardwareId();
        Integer slotAddress = feeder.getSlotAddress();
        Location slotLocation = null;

        if (slotAddress != null && feeder.getSlot() != null) {
            slotLocation = feeder.getSlot().getLocation();
        }

        boolean valid = true;
        String invalidReason = "";

        if (hardwareId == null || hardwareId.trim().isEmpty()) {
            valid = false;
            invalidReason = "unconfigured feeder";
        } else if (slotAddress == null) {
            valid = false;
            invalidReason = "slot is None";
        } else if (feeder.getSlot() == null) {
            valid = false;
            invalidReason = "slot object is missing";
        } else if (slotLocation == null) {
            valid = false;
            invalidReason = "slot location is not configured";
        }

        return new FeederSummary(
                feeder,
                displayName,
                hardwareId,
                slotAddress,
                slotLocation,
                valid,
                invalidReason);
    }

    public static class AllFeedersXyAndZCorrectionResult {
        private final SearchResult searchResult;
        private final List<FirstFeederXyAndZCorrectionResult> feederResults;
        private final Path outputFolder;
        private final boolean configurationSaved;
        private final boolean success;
        private final FeederSummary failedFeederSummary;
        private final Location failedSlotLocation;
        private final String errorMessage;
        private final boolean csvRewritten;
        private final Path csvPath;
        private final String issuedOnTimestamp;

        private AllFeedersXyAndZCorrectionResult(
                SearchResult searchResult,
                List<FirstFeederXyAndZCorrectionResult> feederResults,
                Path outputFolder,
                boolean configurationSaved,
                boolean success,
                FeederSummary failedFeederSummary,
                Location failedSlotLocation,
                String errorMessage,
                boolean csvRewritten,
                Path csvPath,
                String issuedOnTimestamp) {
            this.searchResult = searchResult;
            this.feederResults = new ArrayList<>(feederResults);
            this.outputFolder = outputFolder;
            this.configurationSaved = configurationSaved;
            this.success = success;
            this.failedFeederSummary = failedFeederSummary;
            this.failedSlotLocation = failedSlotLocation;
            this.errorMessage = errorMessage;
            this.csvRewritten = csvRewritten;
            this.csvPath = csvPath;
            this.issuedOnTimestamp = issuedOnTimestamp;
        }

        public SearchResult getSearchResult() {
            return searchResult;
        }

        public List<FirstFeederXyAndZCorrectionResult> getFeederResults() {
            return Collections.unmodifiableList(feederResults);
        }

        public int getProcessedFeederCount() {
            return feederResults.size();
        }

        public int getTotalImageOffsetMeasurements() {
            int total = 0;

            for (FirstFeederXyAndZCorrectionResult feederResult : feederResults) {
                total += feederResult.getXyCorrectionResult().getMeasurements().size();
            }

            return total;
        }

        public int getTotalXyCorrectionsApplied() {
            int total = 0;

            for (FirstFeederXyAndZCorrectionResult feederResult : feederResults) {
                total += feederResult.getXyCorrectionResult().getCorrectionsApplied();
            }

            return total;
        }

        public double getMaximumFinalAbsErrorUm() {
            double maximum = 0.0;

            for (FirstFeederXyAndZCorrectionResult feederResult : feederResults) {
                List<OffsetMeasurementResult> measurements = feederResult.getXyCorrectionResult().getMeasurements();

                if (measurements.isEmpty()) {
                    continue;
                }

                OffsetMeasurementResult finalMeasurement = measurements.get(measurements.size() - 1);

                maximum = Math.max(maximum, Math.abs(finalMeasurement.getDxUm()));
                maximum = Math.max(maximum, Math.abs(finalMeasurement.getDyUm()));
            }

            return maximum;
        }

        public Path getOutputFolder() {
            return outputFolder;
        }

        public boolean isConfigurationSaved() {
            return configurationSaved;
        }

        public boolean isSuccess() {
            return success;
        }

        public FeederSummary getFailedFeederSummary() {
            return failedFeederSummary;
        }

        public Location getFailedSlotLocation() {
            return failedSlotLocation;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean isCsvRewritten() {
            return csvRewritten;
        }

        public Path getCsvPath() {
            return csvPath;
        }

        public String getIssuedOnTimestamp() {
            return issuedOnTimestamp;
        }
    }

    public static class FirstFeederXyAndZCorrectionResult {
        private final FirstFeederXyCorrectionResult xyCorrectionResult;
        private final FeederZProbeResult zProbeResult;
        private final Location finalSlotLocation;
        private final boolean configurationSaved;

        private FirstFeederXyAndZCorrectionResult(
                FirstFeederXyCorrectionResult xyCorrectionResult,
                FeederZProbeResult zProbeResult,
                Location finalSlotLocation,
                boolean configurationSaved) {
            this.xyCorrectionResult = xyCorrectionResult;
            this.zProbeResult = zProbeResult;
            this.finalSlotLocation = finalSlotLocation;
            this.configurationSaved = configurationSaved;
        }

        public FirstFeederXyCorrectionResult getXyCorrectionResult() {
            return xyCorrectionResult;
        }

        public FeederZProbeResult getZProbeResult() {
            return zProbeResult;
        }

        public Location getFinalSlotLocation() {
            return finalSlotLocation;
        }

        public boolean isConfigurationSaved() {
            return configurationSaved;
        }
    }

    public static class FeederZProbeResult {
        private final String nozzleName;
        private final double oldSlotZ;
        private final double startZ;
        private final double approachZ;
        private final double contactZ;
        private final double releaseZ;
        private final double estimatedSlotZ;
        private final double minZ;
        private final double threshold;
        private final double contactReading;
        private final double releaseReading;
        private final int probeSteps;
        private final int retractSteps;
        private Location updatedSlotLocation;

        private FeederZProbeResult(
                String nozzleName,
                double oldSlotZ,
                double startZ,
                double approachZ,
                double contactZ,
                double releaseZ,
                double estimatedSlotZ,
                double minZ,
                double threshold,
                double contactReading,
                double releaseReading,
                int probeSteps,
                int retractSteps) {
            this.nozzleName = nozzleName;
            this.oldSlotZ = oldSlotZ;
            this.startZ = startZ;
            this.approachZ = approachZ;
            this.contactZ = contactZ;
            this.releaseZ = releaseZ;
            this.estimatedSlotZ = estimatedSlotZ;
            this.minZ = minZ;
            this.threshold = threshold;
            this.contactReading = contactReading;
            this.releaseReading = releaseReading;
            this.probeSteps = probeSteps;
            this.retractSteps = retractSteps;
        }

        private void setUpdatedSlotLocation(Location updatedSlotLocation) {
            this.updatedSlotLocation = updatedSlotLocation;
        }

        public String getNozzleName() {
            return nozzleName;
        }

        public double getOldSlotZ() {
            return oldSlotZ;
        }

        public double getStartZ() {
            return startZ;
        }

        public double getApproachZ() {
            return approachZ;
        }

        public double getContactZ() {
            return contactZ;
        }

        public double getReleaseZ() {
            return releaseZ;
        }

        public double getEstimatedSlotZ() {
            return estimatedSlotZ;
        }

        public double getMinZ() {
            return minZ;
        }

        public double getThreshold() {
            return threshold;
        }

        public double getContactReading() {
            return contactReading;
        }

        public double getReleaseReading() {
            return releaseReading;
        }

        public int getProbeSteps() {
            return probeSteps;
        }

        public int getRetractSteps() {
            return retractSteps;
        }

        public Location getUpdatedSlotLocation() {
            return updatedSlotLocation;
        }
    }

    public static class FirstFeederXyCorrectionResult {
        private final SearchResult searchResult;
        private final FeederSummary feederSummary;
        private final List<OffsetMeasurementResult> measurements;
        private final boolean success;
        private final int correctionsApplied;
        private final Location finalSlotLocation;
        private final Path outputFolder;
        private boolean configurationSaved;

        private FirstFeederXyCorrectionResult(
                SearchResult searchResult,
                FeederSummary feederSummary,
                List<OffsetMeasurementResult> measurements,
                boolean success,
                int correctionsApplied,
                Location finalSlotLocation,
                Path outputFolder,
                boolean configurationSaved) {
            this.searchResult = searchResult;
            this.feederSummary = feederSummary;
            this.measurements = new ArrayList<>(measurements);
            this.success = success;
            this.correctionsApplied = correctionsApplied;
            this.finalSlotLocation = finalSlotLocation;
            this.outputFolder = outputFolder;
            this.configurationSaved = configurationSaved;
        }

        private void setConfigurationSaved(boolean configurationSaved) {
            this.configurationSaved = configurationSaved;
        }

        public SearchResult getSearchResult() {
            return searchResult;
        }

        public FeederSummary getFeederSummary() {
            return feederSummary;
        }

        public List<OffsetMeasurementResult> getMeasurements() {
            return Collections.unmodifiableList(measurements);
        }

        public boolean isSuccess() {
            return success;
        }

        public int getCorrectionsApplied() {
            return correctionsApplied;
        }

        public Location getFinalSlotLocation() {
            return finalSlotLocation;
        }

        public Path getOutputFolder() {
            return outputFolder;
        }

        public boolean isConfigurationSaved() {
            return configurationSaved;
        }
    }

    public static class FirstFeederOffsetMeasurementResult {
        private final SearchResult searchResult;
        private final FeederSummary feederSummary;
        private final OffsetMeasurementResult offsetMeasurementResult;

        private FirstFeederOffsetMeasurementResult(
                SearchResult searchResult,
                FeederSummary feederSummary,
                OffsetMeasurementResult offsetMeasurementResult) {
            this.searchResult = searchResult;
            this.feederSummary = feederSummary;
            this.offsetMeasurementResult = offsetMeasurementResult;
        }

        public SearchResult getSearchResult() {
            return searchResult;
        }

        public FeederSummary getFeederSummary() {
            return feederSummary;
        }

        public OffsetMeasurementResult getOffsetMeasurementResult() {
            return offsetMeasurementResult;
        }
    }

    public static class OffsetMeasurementResult {
        private final int tentative;
        private final int crop;
        private final Location slotLocation;
        private final Location finalCameraLocation;
        private final Location cameraUnitsPerPixel;
        private final double dxPixels;
        private final double dyPixels;
        private final double dxMm;
        private final double dyMm;
        private final double dxUm;
        private final double dyUm;
        private final double peak;
        private final long dtMs;
        private final boolean withinPrecision;
        private final Path outputFolder;
        private final Path savedCropFile;

        private OffsetMeasurementResult(
                int tentative,
                int crop,
                Location slotLocation,
                Location finalCameraLocation,
                Location cameraUnitsPerPixel,
                double dxPixels,
                double dyPixels,
                double dxMm,
                double dyMm,
                double dxUm,
                double dyUm,
                double peak,
                long dtMs,
                boolean withinPrecision,
                Path outputFolder,
                Path savedCropFile) {
            this.tentative = tentative;
            this.crop = crop;
            this.slotLocation = slotLocation;
            this.finalCameraLocation = finalCameraLocation;
            this.cameraUnitsPerPixel = cameraUnitsPerPixel;
            this.dxPixels = dxPixels;
            this.dyPixels = dyPixels;
            this.dxMm = dxMm;
            this.dyMm = dyMm;
            this.dxUm = dxUm;
            this.dyUm = dyUm;
            this.peak = peak;
            this.dtMs = dtMs;
            this.withinPrecision = withinPrecision;
            this.outputFolder = outputFolder;
            this.savedCropFile = savedCropFile;
        }

        public int getTentative() {
            return tentative;
        }

        public int getCrop() {
            return crop;
        }

        public Location getSlotLocation() {
            return slotLocation;
        }

        public Location getFinalCameraLocation() {
            return finalCameraLocation;
        }

        public Location getCameraUnitsPerPixel() {
            return cameraUnitsPerPixel;
        }

        public double getDxPixels() {
            return dxPixels;
        }

        public double getDyPixels() {
            return dyPixels;
        }

        public double getDxMm() {
            return dxMm;
        }

        public double getDyMm() {
            return dyMm;
        }

        public double getDxUm() {
            return dxUm;
        }

        public double getDyUm() {
            return dyUm;
        }

        public double getPeak() {
            return peak;
        }

        public long getDtMs() {
            return dtMs;
        }

        public boolean isWithinPrecision() {
            return withinPrecision;
        }

        public Path getOutputFolder() {
            return outputFolder;
        }

        public Path getSavedCropFile() {
            return savedCropFile;
        }
    }

    public static class SearchResult {
        private final List<FeederSummary> feederSummaries;
        private final int validCount;

        private SearchResult(
                List<FeederSummary> feederSummaries,
                int validCount) {
            this.feederSummaries = new ArrayList<>(feederSummaries);
            this.validCount = validCount;
        }

        public List<FeederSummary> getFeederSummaries() {
            return Collections.unmodifiableList(feederSummaries);
        }

        public int getTotalCount() {
            return feederSummaries.size();
        }

        public int getValidCount() {
            return validCount;
        }

        public int getInvalidCount() {
            return feederSummaries.size() - validCount;
        }
    }

    public static class FeederSummary {
        private final PhotonFeeder feeder;
        private final String displayName;
        private final String hardwareId;
        private final Integer slotAddress;
        private final Location slotLocation;
        private final boolean valid;
        private final String invalidReason;

        private FeederSummary(
                PhotonFeeder feeder,
                String displayName,
                String hardwareId,
                Integer slotAddress,
                Location slotLocation,
                boolean valid,
                String invalidReason) {
            this.feeder = feeder;
            this.displayName = displayName;
            this.hardwareId = hardwareId;
            this.slotAddress = slotAddress;
            this.slotLocation = slotLocation;
            this.valid = valid;
            this.invalidReason = invalidReason;
        }

        public PhotonFeeder getFeeder() {
            return feeder;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getHardwareId() {
            return hardwareId;
        }

        public Integer getSlotAddress() {
            return slotAddress;
        }

        public Location getSlotLocation() {
            return slotLocation;
        }

        public boolean isValid() {
            return valid;
        }

        public String getInvalidReason() {
            return invalidReason;
        }
    }
}