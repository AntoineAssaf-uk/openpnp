package org.openpnp.machine.reference.lookup;

import java.util.Locale;

import org.openpnp.model.Configuration;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Head;
import org.openpnp.spi.Machine;
import org.openpnp.spi.Nozzle;

public final class ReferenceMachineLookup {
    public static final String HEAD_H1 = "H1";
    public static final String TOP_CAMERA = "Top PnP";
    public static final String BOTTOM_CAMERA = "Bottom";
    public static final String NOZZLE_N1 = "N1";
    public static final String NOZZLE_N2 = "N2";
    public static final String ACTUATOR_LED = "LED";
    public static final String ACTUATOR_VAC1 = "VAC1";
    public static final String ACTUATOR_VAC2 = "VAC2";

    private ReferenceMachineLookup() {
    }

    public static Machine getMachine() throws Exception {
        Machine machine = Configuration.get().getMachine();

        if (machine == null) {
            throw new Exception("No OpenPnP machine configuration is loaded.");
        }

        return machine;
    }

    public static void requireMachineEnabledAndHomed(Machine machine) throws Exception {
        if (machine == null) {
            throw new Exception("No OpenPnP machine configuration is loaded.");
        }

        if (!machine.isEnabled()) {
            throw new Exception("Machine is not enabled.");
        }

        if (!machine.isHomed()) {
            throw new Exception("Machine is not homed.");
        }
    }

    public static Head findHead(String nameOrId) throws Exception {
        return findHead(getMachine(), nameOrId);
    }

    public static Head findHead(Machine machine, String nameOrId) throws Exception {
        if (machine == null) {
            throw new Exception("Cannot find head because machine is null.");
        }

        Head head = machine.getHeadByName(nameOrId);
        if (head != null) {
            return head;
        }

        head = machine.getHead(nameOrId);
        if (head != null) {
            return head;
        }

        StringBuilder available = new StringBuilder();

        for (Head candidate : machine.getHeads()) {
            if (matchesIdentifiedObject(nameOrId,
                    candidate.getClass().getSimpleName(),
                    candidate.getName(),
                    candidate.getId())) {
                return candidate;
            }

            appendAvailable(available,
                    candidate.getClass().getSimpleName(),
                    candidate.getName(),
                    candidate.getId());
        }

        throw new Exception("Cannot find head \"" + nameOrId + "\". Available heads: " + available);
    }

    public static Camera findDefaultHeadCamera(Head head) throws Exception {
        if (head == null) {
            throw new Exception("Cannot find default camera because head is null.");
        }

        Camera camera = head.getDefaultCamera();

        if (camera == null) {
            throw new Exception("Head \"" + head.getName() + "\" does not have a default camera.");
        }

        return camera;
    }

    public static Camera findHeadCamera(Head head, String nameOrId) throws Exception {
        if (head == null) {
            throw new Exception("Cannot find head camera because head is null.");
        }

        Camera camera = head.getCamera(nameOrId);
        if (camera != null) {
            return camera;
        }

        StringBuilder available = new StringBuilder();

        for (Camera candidate : head.getCameras()) {
            if (matchesIdentifiedObject(nameOrId,
                    candidate.getClass().getSimpleName(),
                    candidate.getName(),
                    candidate.getId())) {
                return candidate;
            }

            appendAvailable(available,
                    candidate.getClass().getSimpleName(),
                    candidate.getName(),
                    candidate.getId());
        }

        throw new Exception("Cannot find camera \"" + nameOrId + "\" on head \"" + head.getName()
                + "\". Available head cameras: " + available);
    }

    public static Camera findMachineCamera(String nameOrId) throws Exception {
        return findMachineCamera(getMachine(), nameOrId);
    }

    public static Camera findMachineCamera(Machine machine, String nameOrId) throws Exception {
        if (machine == null) {
            throw new Exception("Cannot find machine camera because machine is null.");
        }

        Camera camera = machine.getCamera(nameOrId);
        if (camera != null) {
            return camera;
        }

        StringBuilder available = new StringBuilder();

        for (Camera candidate : machine.getCameras()) {
            if (matchesIdentifiedObject(nameOrId,
                    candidate.getClass().getSimpleName(),
                    candidate.getName(),
                    candidate.getId())) {
                return candidate;
            }

            appendAvailable(available,
                    candidate.getClass().getSimpleName(),
                    candidate.getName(),
                    candidate.getId());
        }

        throw new Exception("Cannot find machine camera \"" + nameOrId
                + "\". Available machine cameras: " + available);
    }

    public static Nozzle findNozzle(Head head, String nameOrId) throws Exception {
        if (head == null) {
            throw new Exception("Cannot find nozzle because head is null.");
        }

        Nozzle nozzle = head.getNozzleByName(nameOrId);
        if (nozzle != null) {
            return nozzle;
        }

        nozzle = head.getNozzle(nameOrId);
        if (nozzle != null) {
            return nozzle;
        }

        StringBuilder available = new StringBuilder();

        for (Nozzle candidate : head.getNozzles()) {
            if (matchesIdentifiedObject(nameOrId,
                    candidate.getClass().getSimpleName(),
                    candidate.getName(),
                    candidate.getId())) {
                return candidate;
            }

            appendAvailable(available,
                    candidate.getClass().getSimpleName(),
                    candidate.getName(),
                    candidate.getId());
        }

        throw new Exception("Cannot find nozzle \"" + nameOrId + "\" on head \"" + head.getName()
                + "\". Available nozzles: " + available);
    }

    public static Actuator findHeadActuator(Head head, String nameOrId) throws Exception {
        if (head == null) {
            throw new Exception("Cannot find head actuator because head is null.");
        }

        Actuator actuator = head.getActuatorByName(nameOrId);
        if (actuator != null) {
            return actuator;
        }

        actuator = head.getActuator(nameOrId);
        if (actuator != null) {
            return actuator;
        }

        StringBuilder available = new StringBuilder();

        for (Actuator candidate : head.getActuators()) {
            if (matchesIdentifiedObject(nameOrId,
                    candidate.getClass().getSimpleName(),
                    candidate.getName(),
                    candidate.getId())) {
                return candidate;
            }

            appendAvailable(available,
                    candidate.getClass().getSimpleName(),
                    candidate.getName(),
                    candidate.getId());
        }

        throw new Exception("Cannot find actuator \"" + nameOrId + "\" on head \"" + head.getName()
                + "\". Available head actuators: " + available);
    }

    public static Actuator findMachineActuator(String nameOrId) throws Exception {
        return findMachineActuator(getMachine(), nameOrId);
    }

    public static Actuator findMachineActuator(Machine machine, String nameOrId) throws Exception {
        if (machine == null) {
            throw new Exception("Cannot find machine actuator because machine is null.");
        }

        Actuator actuator = machine.getActuatorByName(nameOrId);
        if (actuator != null) {
            return actuator;
        }

        actuator = machine.getActuator(nameOrId);
        if (actuator != null) {
            return actuator;
        }

        StringBuilder available = new StringBuilder();

        for (Actuator candidate : machine.getActuators()) {
            if (matchesIdentifiedObject(nameOrId,
                    candidate.getClass().getSimpleName(),
                    candidate.getName(),
                    candidate.getId())) {
                return candidate;
            }

            appendAvailable(available,
                    candidate.getClass().getSimpleName(),
                    candidate.getName(),
                    candidate.getId());
        }

        throw new Exception("Cannot find machine actuator \"" + nameOrId
                + "\". Available machine actuators: " + available);
    }

    public static String describeStandardGantryObjects() throws Exception {
        StringBuilder sb = new StringBuilder();

        Machine machine = getMachine();

        appendLine(sb, "ReferenceMachineLookup standard gantry object test");
        appendLine(sb, "Machine class = " + machine.getClass().getSimpleName());
        appendLine(sb, "Machine enabled = " + machine.isEnabled());
        appendLine(sb, "Machine homed = " + machine.isHomed());

        Head head = findHead(machine, HEAD_H1);
        Camera topCamera = findDefaultHeadCamera(head);
        Camera bottomCamera = findMachineCamera(machine, BOTTOM_CAMERA);
        Nozzle nozzle1 = findNozzle(head, NOZZLE_N1);
        Nozzle nozzle2 = findNozzle(head, NOZZLE_N2);
        Actuator led = findMachineActuator(machine, ACTUATOR_LED);
        Actuator vac1 = findHeadActuator(head, ACTUATOR_VAC1);
        Actuator vac2 = findHeadActuator(head, ACTUATOR_VAC2);

        appendLine(sb, "Head H1 = " + describe(head));
        appendLine(sb, "Top camera default = " + describe(topCamera));
        appendLine(sb, "Bottom camera = " + describe(bottomCamera));
        appendLine(sb, "Nozzle N1 = " + describe(nozzle1));
        appendLine(sb, "Nozzle N2 = " + describe(nozzle2));
        appendLine(sb, "Machine actuator LED = " + describe(led));
        appendLine(sb, "Head actuator VAC1 = " + describe(vac1));
        appendLine(sb, "Head actuator VAC2 = " + describe(vac2));
        appendLine(sb, "ReferenceMachineLookup test PASSED.");

        return sb.toString();
    }

    private static String describe(Head head) {
        return describe(head.getClass().getSimpleName(), head.getName(), head.getId());
    }

    private static String describe(Camera camera) {
        return describe(camera.getClass().getSimpleName(), camera.getName(), camera.getId());
    }

    private static String describe(Nozzle nozzle) {
        return describe(nozzle.getClass().getSimpleName(), nozzle.getName(), nozzle.getId());
    }

    private static String describe(Actuator actuator) {
        return describe(actuator.getClass().getSimpleName(), actuator.getName(), actuator.getId());
    }

    private static String describe(String className, String name, String id) {
        return className + " [name=" + name + ", id=" + id + "]";
    }

    private static void appendLine(StringBuilder sb, String line) {
        sb.append(line).append(System.lineSeparator());
    }

    private static void appendAvailable(StringBuilder sb, String className, String name, String id) {
        if (sb.length() > 0) {
            sb.append(", ");
        }

        sb.append(describe(className, name, id));
    }

    private static boolean matchesIdentifiedObject(String requestedName, String className, String name, String id) {
        return matchesName(requestedName, name)
                || matchesName(requestedName, id)
                || matchesName(requestedName, className + " " + name)
                || matchesName(requestedName, className + name)
                || matchesName(requestedName, className + " " + id)
                || matchesName(requestedName, className + id);
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
}