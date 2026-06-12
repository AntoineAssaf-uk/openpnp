package org.openpnp.machine.reference.gantry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GantryTestValidationResult {
    private final List<String> messages = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    public void addMessage(String message) {
        messages.add(message);
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    public void addError(String error) {
        errors.add(error);
    }

    public boolean isPassed() {
        return errors.isEmpty();
    }

    public List<String> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();

        sb.append("Gantry Test CSV validation result").append(System.lineSeparator());

        for (String message : messages) {
            sb.append("INFO: ").append(message).append(System.lineSeparator());
        }

        for (String warning : warnings) {
            sb.append("WARNING: ").append(warning).append(System.lineSeparator());
        }

        for (String error : errors) {
            sb.append("ERROR: ").append(error).append(System.lineSeparator());
        }

        sb.append("Warnings = ").append(warnings.size()).append(System.lineSeparator());
        sb.append("Errors = ").append(errors.size()).append(System.lineSeparator());

        if (isPassed()) {
            sb.append("Gantry Test CSV validation PASSED.");
        }
        else {
            sb.append("Gantry Test CSV validation FAILED.");
        }

        return sb.toString();
    }
}