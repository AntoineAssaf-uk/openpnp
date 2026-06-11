package org.openpnp.machine.reference.debug;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public final class ReferenceMachineDebugLog {
    private static final int MAX_BUFFER_CHARS = 50000;

    private static final StringBuilder buffer = new StringBuilder();
    private static JTextArea textArea;

    private ReferenceMachineDebugLog() {
    }

    public static void setTextArea(JTextArea area) {
        synchronized (ReferenceMachineDebugLog.class) {
            textArea = area;
        }

        if (area != null) {
            String text = getText();
            SwingUtilities.invokeLater(() -> {
                area.setText(text);
                area.setCaretPosition(area.getDocument().getLength());
            });
        }
    }

    public static void debugPrintf(String format, Object... args) {
        String line;
        try {
            line = String.format(format, args);
        }
        catch (Exception e) {
            line = format;
        }

        appendLine(line);
    }

    public static void debugPrintln(String line) {
        appendLine(line);
    }

    public static void debugException(String message, Throwable throwable) {
        if (throwable == null) {
            debugPrintf("%s", message);
            return;
        }

        debugPrintf("%s: %s", message, throwable.toString());
        for (StackTraceElement element : throwable.getStackTrace()) {
            debugPrintf("    at %s", element.toString());
        }
    }

    public static void clear() {
        JTextArea area;
        synchronized (ReferenceMachineDebugLog.class) {
            buffer.setLength(0);
            area = textArea;
        }

        if (area != null) {
            SwingUtilities.invokeLater(() -> {
                area.setText("");
                area.setCaretPosition(0);
            });
        }
    }

    private static void appendLine(String line) {
        if (line == null) {
            line = "";
        }

        String textToAppend = line + System.lineSeparator();
        JTextArea area;

        synchronized (ReferenceMachineDebugLog.class) {
            buffer.append(textToAppend);

            if (buffer.length() > MAX_BUFFER_CHARS) {
                buffer.delete(0, buffer.length() - MAX_BUFFER_CHARS);
            }

            area = textArea;
        }

        if (area != null) {
            final String finalTextToAppend = textToAppend;
            SwingUtilities.invokeLater(() -> {
                area.append(finalTextToAppend);
                area.setCaretPosition(area.getDocument().getLength());
            });
        }
    }

    private static String getText() {
        synchronized (ReferenceMachineDebugLog.class) {
            return buffer.toString();
        }
    }
}