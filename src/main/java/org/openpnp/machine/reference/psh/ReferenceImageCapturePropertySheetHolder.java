package org.openpnp.machine.reference.psh;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.util.Locale;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import org.openpnp.model.Configuration;
import org.openpnp.model.Location;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Head;
import org.openpnp.spi.Machine;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.PropertySheetHolder;
import org.openpnp.util.UiUtils;

public class ReferenceImageCapturePropertySheetHolder implements PropertySheetHolder {
    private static final String TITLE = "Capture Reference Images";

    private static final String TOP_HEAD_NAME = "ReferenceHead H1";
    private static final String BOTTOM_CAMERA_NAME = "OpenPnpCaptureCamera Bottom";
    private static final String NOZZLE_1_NAME = "ReferenceNozzle N1";
    private static final String NOZZLE_2_NAME = "ReferenceNozzle N2";

    @Override
    public String getPropertySheetHolderTitle() {
        return TITLE;
    }

    @Override
    public PropertySheetHolder[] getChildPropertySheetHolders() {
        return null;
    }

    @Override
    public PropertySheet[] getPropertySheets() {
        return new PropertySheet[] {
                new PropertySheet() {
                    @Override
                    public String getPropertySheetTitle() {
                        return TITLE;
                    }

                    @Override
                    public JPanel getPropertySheetPanel() {
                        return createPanel();
                    }
                }
        };
    }

    @Override
    public Action[] getPropertySheetHolderActions() {
        return null;
    }

    @Override
    public Icon getPropertySheetHolderIcon() {
        return null;
    }

    private JPanel createPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel savedOkLabel = new JLabel("Image Saved Ok..");
        savedOkLabel.setForeground(new Color(0, 128, 0));
        savedOkLabel.setFont(savedOkLabel.getFont().deriveFont(Font.BOLD));
        savedOkLabel.setVisible(false);

        JButton imageCaptureButton = new JButton("Image Capture");
        imageCaptureButton.setToolTipText("Image capture by camera");

        imageCaptureButton.addActionListener((ActionEvent e) -> {
            savedOkLabel.setVisible(false);
            imageCaptureButton.setEnabled(false);

            UiUtils.submitUiMachineTask(() -> {
                validateCaptureSetup();
                return null;
            }, (result) -> {
                imageCaptureButton.setEnabled(true);
                savedOkLabel.setVisible(true);
            }, (throwable) -> {
                imageCaptureButton.setEnabled(true);
                savedOkLabel.setVisible(false);
                UiUtils.showError(throwable);
            });
        });

        panel.add(imageCaptureButton);
        panel.add(Box.createVerticalStrut(12));
        panel.add(savedOkLabel);

        return panel;
    }

    private void validateCaptureSetup() throws Exception {
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

        String debugMessage = String.format(Locale.US,
                "Reference image capture setup OK. Top camera X=%.3f Y=%.3f, N1 Z=%.3f, N2 Z=%.3f, bottom camera=%s",
                topLocation.getX(),
                topLocation.getY(),
                nozzle1Location.getZ(),
                nozzle2Location.getZ(),
                bottomCamera.getName());

        System.out.println(debugMessage);
    }

  private Head findHead(Machine machine, String nameOrId) throws Exception {
    StringBuilder availableHeads = new StringBuilder();

    for (Head head : machine.getHeads()) {
        String headName = head.getName();
        String headId = head.getId();

        String displayNameFromName = head.getClass().getSimpleName() + " " + headName;
        String displayNameFromId = head.getClass().getSimpleName() + " " + headId;

        if (nameOrId.equals(headName)
                || nameOrId.equals(headId)
                || nameOrId.equals(displayNameFromName)
                || nameOrId.equals(displayNameFromId)) {
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

    private Camera findTopCamera(Head head) throws Exception {
        Camera camera = head.getDefaultCamera();

        if (camera == null) {
            throw new Exception("Head \"" + head.getName() + "\" does not have a default camera.");
        }

        return camera;
    }

private Camera findMachineCamera(Machine machine, String nameOrId) throws Exception {
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

private boolean matchesName(String requestedName, String candidateName) {
    if (requestedName == null || candidateName == null) {
        return false;
    }

    if (requestedName.equals(candidateName)) {
        return true;
    }

    return normalizeName(requestedName).equals(normalizeName(candidateName));
}

private String normalizeName(String name) {
    return name.replaceAll("\\s+", "").toLowerCase(Locale.US);
}

private Nozzle findNozzle(Head head, String nameOrId) throws Exception {
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
}