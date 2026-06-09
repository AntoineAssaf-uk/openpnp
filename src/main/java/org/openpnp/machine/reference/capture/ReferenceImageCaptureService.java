package org.openpnp.machine.reference.capture;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.openpnp.model.Configuration;
import org.openpnp.model.Location;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Head;
import org.openpnp.spi.Machine;
import org.openpnp.spi.Nozzle;

public final class ReferenceImageCaptureService {
    public static final Path REFERENCE_IMAGE_FOLDER =
            Paths.get("C:\\", "Opulo", "Data", "Reference Images");

    private static final String TOP_HEAD_NAME = "ReferenceHead H1";
    private static final String BOTTOM_CAMERA_NAME = "OpenPnpCaptureCamera Bottom";
    private static final String NOZZLE_1_NAME = "ReferenceNozzle N1";
    private static final String NOZZLE_2_NAME = "ReferenceNozzle N2";

    private ReferenceImageCaptureService() {
    }

    public static CaptureResult captureAndSaveOriginalReferenceImages(String optionalFolderName) throws Exception {
        Machine machine = Configuration.get().getMachine();

        if (machine == null) {
            throw new Exception("No OpenPnP machine configuration is loaded.");
        }

        if (!machine.isEnabled()) {
            throw new Exception("Machine is not enabled. Enable the machine before capturing reference images.");
        }

        if (!machine.isHomed()) {
            throw new Exception("Machine is not homed. Home the machine before capturing reference images.");
        }

        Head topHead = findHead(machine, TOP_HEAD_NAME);
        Camera topCamera = findTopCamera(topHead);
        Camera bottomCamera = findMachineCamera(machine, BOTTOM_CAMERA_NAME);
        Nozzle nozzle1 = findNozzle(topHead, NOZZLE_1_NAME);
        Nozzle nozzle2 = findNozzle(topHead, NOZZLE_2_NAME);

        Location topLocation = topCamera.getLocation();
        Location nozzle1Location = nozzle1.getLocation();
        Location nozzle2Location = nozzle2.getLocation();

        double topX = topLocation.getX();
        double topY = topLocation.getY();
        double nozzle1Z = nozzle1Location.getZ();
        double nozzle2Z = nozzle2Location.getZ();

        String folderName = buildReferenceFolderName(topX, topY, optionalFolderName);
        Path outputFolder = REFERENCE_IMAGE_FOLDER.resolve(folderName);

        Files.createDirectories(outputFolder);

        BufferedImage topImage = topCamera.lightSettleAndCapture();
        BufferedImage bottomImage = bottomCamera.lightSettleAndCapture();

        String topFileName = buildOriginalImageFileName("Top", nozzle1Z, nozzle2Z);
        String bottomFileName = buildOriginalImageFileName("Bot", nozzle1Z, nozzle2Z);

        Path topFile = outputFolder.resolve(topFileName);
        Path bottomFile = outputFolder.resolve(bottomFileName);

        saveBmp(topImage, topFile);
        saveBmp(bottomImage, bottomFile);

        return new CaptureResult(folderName, outputFolder, topFile, bottomFile);
    }

    public static String buildReferenceFolderName(double x, double y, String optionalFolderName) {
        String suffix = sanitizeFolderSuffix(optionalFolderName);

        return String.format(Locale.US,
                "%s_%s_%s",
                formatCoordinate(x),
                formatCoordinate(y),
                suffix);
    }

    public static String buildOriginalImageFileName(String prefix, double nozzle1Z, double nozzle2Z) {
        return String.format(Locale.US,
                "%s_N1_%s_N2_%s.bmp",
                prefix,
                formatZ(nozzle1Z),
                formatZ(nozzle2Z));
    }

    public static String formatCoordinate(double value) {
        return String.format(Locale.US, "%07.3f", value);
    }

    public static String formatZ(double value) {
        return String.format(Locale.US, "%04.1f", value);
    }

    public static String sanitizeFolderSuffix(String suffix) {
        if (suffix == null) {
            return "";
        }

        String sanitized = suffix.trim();

        sanitized = sanitized.replaceAll("[<>:\"/\\\\|?*]", "_");

        if (sanitized.length() > 32) {
            sanitized = sanitized.substring(0, 32);
        }

        return sanitized;
    }

    private static void saveBmp(BufferedImage image, Path file) throws Exception {
        if (image == null) {
            throw new Exception("Cannot save BMP file because captured image is null: " + file);
        }

        boolean ok = ImageIO.write(image, "bmp", file.toFile());

        if (!ok) {
            throw new Exception("No BMP image writer is available for file: " + file);
        }
    }

    private static Head findHead(Machine machine, String nameOrId) throws Exception {
        StringBuilder availableHeads = new StringBuilder();

        for (Head head : machine.getHeads()) {
            String headName = head.getName();
            String headId = head.getId();

            String displayNameFromName = head.getClass().getSimpleName() + " " + headName;
            String displayNameFromId = head.getClass().getSimpleName() + " " + headId;

            if (matchesName(nameOrId, headName)
                    || matchesName(nameOrId, headId)
                    || matchesName(nameOrId, displayNameFromName)
                    || matchesName(nameOrId, displayNameFromId)) {
                return head;
            }

            if (availableHeads.length() > 0) {
                availableHeads.append(", ");
            }

            availableHeads.append("[name=")
                    .append(headName)
                    .append(", id=")
                    .append(headId)
                    .append(", display=")
                    .append(displayNameFromName)
                    .append("]");
        }

        throw new Exception("Cannot find head \"" + nameOrId + "\". Available heads: " + availableHeads);
    }

    private static Camera findTopCamera(Head head) throws Exception {
        Camera camera = head.getDefaultCamera();

        if (camera == null) {
            throw new Exception("Head \"" + head.getName() + "\" does not have a default camera.");
        }

        return camera;
    }

    private static Camera findMachineCamera(Machine machine, String nameOrId) throws Exception {
        StringBuilder availableCameras = new StringBuilder();

        for (Camera camera : machine.getCameras()) {
            String cameraName = camera.getName();
            String cameraId = camera.getId();
            String cameraClassName = camera.getClass().getSimpleName();

            String displayNameFromName = cameraClassName + " " + cameraName;
            String compactDisplayNameFromName = cameraClassName + cameraName;

            String displayNameFromId = cameraClassName + " " + cameraId;
            String compactDisplayNameFromId = cameraClassName + cameraId;

            if (matchesName(nameOrId, cameraName)
                    || matchesName(nameOrId, cameraId)
                    || matchesName(nameOrId, displayNameFromName)
                    || matchesName(nameOrId, compactDisplayNameFromName)
                    || matchesName(nameOrId, displayNameFromId)
                    || matchesName(nameOrId, compactDisplayNameFromId)) {
                return camera;
            }

            if (availableCameras.length() > 0) {
                availableCameras.append(", ");
            }

            availableCameras.append("[name=")
                    .append(cameraName)
                    .append(", id=")
                    .append(cameraId)
                    .append(", class=")
                    .append(cameraClassName)
                    .append(", display=")
                    .append(displayNameFromName)
                    .append(", compactDisplay=")
                    .append(compactDisplayNameFromName)
                    .append("]");
        }

        throw new Exception("Cannot find machine camera \"" + nameOrId + "\". Available machine cameras: "
                + availableCameras);
    }

    private static Nozzle findNozzle(Head head, String nameOrId) throws Exception {
        Nozzle nozzle = head.getNozzleByName(nameOrId);

        if (nozzle != null) {
            return nozzle;
        }

        StringBuilder availableNozzles = new StringBuilder();

        for (Nozzle candidate : head.getNozzles()) {
            String nozzleName = candidate.getName();
            String nozzleId = candidate.getId();
            String nozzleClassName = candidate.getClass().getSimpleName();

            String displayNameFromName = nozzleClassName + " " + nozzleName;
            String compactDisplayNameFromName = nozzleClassName + nozzleName;

            String displayNameFromId = nozzleClassName + " " + nozzleId;
            String compactDisplayNameFromId = nozzleClassName + nozzleId;

            if (matchesName(nameOrId, nozzleName)
                    || matchesName(nameOrId, nozzleId)
                    || matchesName(nameOrId, displayNameFromName)
                    || matchesName(nameOrId, compactDisplayNameFromName)
                    || matchesName(nameOrId, displayNameFromId)
                    || matchesName(nameOrId, compactDisplayNameFromId)) {
                return candidate;
            }

            if (availableNozzles.length() > 0) {
                availableNozzles.append(", ");
            }

            availableNozzles.append("[name=")
                    .append(nozzleName)
                    .append(", id=")
                    .append(nozzleId)
                    .append(", class=")
                    .append(nozzleClassName)
                    .append(", display=")
                    .append(displayNameFromName)
                    .append(", compactDisplay=")
                    .append(compactDisplayNameFromName)
                    .append("]");
        }

        throw new Exception("Cannot find nozzle \"" + nameOrId + "\" on head \"" + head.getName()
                + "\". Available nozzles: " + availableNozzles);
    }

    private static boolean matchesName(String requestedName, String candidateName) {
        if (requestedName == null || candidateName == null) {
            return false;
        }

        if (requestedName.equals(candidateName)) {
            return true;
        }

        return normalizeName(requestedName).equals(normalizeName(candidateName));
    }

    private static String normalizeName(String name) {
        return name.replaceAll("\\s+", "").toLowerCase(Locale.US);
    }

    public static final class CaptureResult {
        private final String folderName;
        private final Path folder;
        private final Path topOriginalFile;
        private final Path bottomOriginalFile;

        private CaptureResult(String folderName, Path folder, Path topOriginalFile, Path bottomOriginalFile) {
            this.folderName = folderName;
            this.folder = folder;
            this.topOriginalFile = topOriginalFile;
            this.bottomOriginalFile = bottomOriginalFile;
        }

        public String getFolderName() {
            return folderName;
        }

        public Path getFolder() {
            return folder;
        }

        public Path getTopOriginalFile() {
            return topOriginalFile;
        }

        public Path getBottomOriginalFile() {
            return bottomOriginalFile;
        }
    }
}