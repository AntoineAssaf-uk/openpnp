package org.openpnp.machine.reference.gantry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class GantryTestCsvParser {
    private static final String[] EXPECTED_HEADER = {
            "#",
            "Name",
            "X",
            "Y",
            "Nozzle",
            "N_Z",
            "Crop_factor",
            "Top_Bot",
            "Ref_bmp"
    };

    private GantryTestCsvParser() {
    }

    public static GantryTestCsvInput read(Path inputFile) throws Exception {
        if (inputFile == null) {
            throw new Exception("Gantry Test CSV input file is null.");
        }

        if (!Files.exists(inputFile)) {
            throw new Exception("Gantry Test CSV input file does not exist: " + inputFile);
        }

        List<String> lines = Files.readAllLines(inputFile, StandardCharsets.UTF_8);

        if (lines.size() < 2) {
            throw new Exception("Gantry Test CSV must contain at least two lines: cycles line and header line.");
        }

        int numberOfCycles = parseNumberOfCycles(parseCsvLine(lines.get(0)));
        validateHeader(parseCsvLine(lines.get(1)));

        List<GantryTestPoint> points = new ArrayList<>();

        for (int i = 2; i < lines.size(); i++) {
            int lineNumber = i + 1;
            String line = lines.get(i);

            if (line == null || line.trim().isEmpty()) {
                continue;
            }

            List<String> cells = parseCsvLine(line);

            if (isRowEmpty(cells)) {
                continue;
            }

            points.add(parsePoint(lineNumber, cells));
        }

        return new GantryTestCsvInput(inputFile, numberOfCycles, points);
    }

    private static int parseNumberOfCycles(List<String> cells) throws Exception {
        if (cells == null || cells.isEmpty()) {
            throw new Exception("Missing Number of cycles line.");
        }

        String firstCell = cell(cells, 0);

        if (!firstCell.equalsIgnoreCase("Number of cycles:")) {
            throw new Exception("First CSV line must start with \"Number of cycles:\" but found \"" + firstCell + "\".");
        }

        for (int i = 1; i < cells.size(); i++) {
            String value = cell(cells, i);

            if (value.isEmpty()) {
                continue;
            }

            int numberOfCycles = parsePositiveInt(value, 1, "Number of cycles");

            return numberOfCycles;
        }

        throw new Exception("Cannot find Number of cycles value on first CSV line.");
    }

    private static void validateHeader(List<String> headerCells) throws Exception {
        if (headerCells.size() < EXPECTED_HEADER.length) {
            throw new Exception("CSV header has only " + headerCells.size()
                    + " columns. Expected " + EXPECTED_HEADER.length + " columns.");
        }

        for (int i = 0; i < EXPECTED_HEADER.length; i++) {
            String expected = EXPECTED_HEADER[i];
            String actual = cell(headerCells, i);

            if (!expected.equals(actual)) {
                throw new Exception("CSV header column " + (i + 1)
                        + " must be \"" + expected + "\" but found \"" + actual + "\".");
            }
        }
    }

    private static GantryTestPoint parsePoint(int lineNumber, List<String> cells) throws Exception {
        if (cells.size() < EXPECTED_HEADER.length) {
            throw new Exception("CSV line " + lineNumber + " has only " + cells.size()
                    + " columns. Expected " + EXPECTED_HEADER.length + " columns.");
        }

        int index = parsePositiveInt(cell(cells, 0), lineNumber, "#");
        String name = parseRequiredString(cell(cells, 1), lineNumber, "Name");
        double x = parseDouble(cell(cells, 2), lineNumber, "X");
        double y = parseDouble(cell(cells, 3), lineNumber, "Y");
        String nozzleName = parseRequiredString(cell(cells, 4), lineNumber, "Nozzle");
        double nozzleZ = parseDouble(cell(cells, 5), lineNumber, "N_Z");
        int cropFactor = parsePositiveInt(cell(cells, 6), lineNumber, "Crop_factor");
        String topBottom = normalizeTopBottom(cell(cells, 7), lineNumber);
        String referenceBitmap = parseRequiredString(cell(cells, 8), lineNumber, "Ref_bmp");

        return new GantryTestPoint(
                lineNumber,
                index,
                name,
                x,
                y,
                nozzleName,
                nozzleZ,
                cropFactor,
                topBottom,
                referenceBitmap);
    }

    private static String normalizeTopBottom(String value, int lineNumber) throws Exception {
        String text = parseRequiredString(value, lineNumber, "Top_Bot");

        if (text.equalsIgnoreCase("Top")) {
            return "Top";
        }

        if (text.equalsIgnoreCase("Bot") || text.equalsIgnoreCase("Bottom")) {
            return "Bot";
        }

        throw new Exception("CSV line " + lineNumber
                + " column Top_Bot must be Top, Bot, or Bottom but found \"" + text + "\".");
    }

    private static String parseRequiredString(String value, int lineNumber, String columnName) throws Exception {
        if (value == null || value.trim().isEmpty()) {
            throw new Exception("CSV line " + lineNumber + " column " + columnName + " is empty.");
        }

        return value.trim();
    }

    private static int parsePositiveInt(String value, int lineNumber, String columnName) throws Exception {
        String text = parseRequiredString(value, lineNumber, columnName);

        try {
            int parsed = Integer.parseInt(text);

            if (parsed <= 0) {
                throw new Exception("CSV line " + lineNumber + " column " + columnName
                        + " must be > 0 but found " + parsed + ".");
            }

            return parsed;
        }
        catch (NumberFormatException e) {
            throw new Exception("CSV line " + lineNumber + " column " + columnName
                    + " must be an integer but found \"" + text + "\".", e);
        }
    }

    private static double parseDouble(String value, int lineNumber, String columnName) throws Exception {
        String text = parseRequiredString(value, lineNumber, columnName);

        try {
            return Double.parseDouble(text);
        }
        catch (NumberFormatException e) {
            throw new Exception("CSV line " + lineNumber + " column " + columnName
                    + " must be a number but found \"" + text + "\".", e);
        }
    }

    private static String cell(List<String> cells, int index) {
        if (cells == null || index < 0 || index >= cells.size()) {
            return "";
        }

        String value = cells.get(index);

        if (value == null) {
            return "";
        }

        return value.trim();
    }

    private static boolean isRowEmpty(List<String> cells) {
        if (cells == null || cells.isEmpty()) {
            return true;
        }

        for (String cell : cells) {
            if (cell != null && !cell.trim().isEmpty()) {
                return false;
            }
        }

        return true;
    }

    private static List<String> parseCsvLine(String line) throws Exception {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;

        if (line == null) {
            cells.add("");
            return cells;
        }

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);

            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                }
                else {
                    inQuotes = !inQuotes;
                }
            }
            else if (ch == ',' && !inQuotes) {
                cells.add(cell.toString());
                cell.setLength(0);
            }
            else {
                cell.append(ch);
            }
        }

        if (inQuotes) {
            throw new Exception("CSV line has an opening quote without a closing quote: " + line);
        }

        cells.add(cell.toString());

        return cells;
    }
}