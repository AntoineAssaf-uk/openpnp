package org.openpnp;

import java.io.InputStream;
import java.util.Properties;

public final class BuildInfo {
    private static final String DEFAULT_BUILD_DATE_TIME = "unknown-build-time";
    private static final String RESOURCE_NAME = "/org/openpnp/build-info.properties";

    private BuildInfo() {
    }
    
    private static String normalizeBuildDateTime(String buildDateTime) {
    if (buildDateTime.matches("\\d{2}-\\d{2}-\\d{4} \\d{2}-\\d{2}-\\d{2}\\..+")) {
        return buildDateTime.substring(0, 19);
    }

    return buildDateTime;
}

    public static String getBuildDateTime() {
        Properties properties = new Properties();

        try (InputStream stream = BuildInfo.class.getResourceAsStream(RESOURCE_NAME)) {
            if (stream == null) {
                return DEFAULT_BUILD_DATE_TIME;
            }

            properties.load(stream);

            String buildDateTime = properties.getProperty("build.date.time");
            if (buildDateTime == null || buildDateTime.trim().isEmpty()) {
                return DEFAULT_BUILD_DATE_TIME;
            }

            return normalizeBuildDateTime(buildDateTime.trim());
        }
        catch (Exception e) {
            return DEFAULT_BUILD_DATE_TIME;
        }
    }

    public static String getWindowTitle() {
        return "OpenPnP " + getBuildDateTime();
    }
}