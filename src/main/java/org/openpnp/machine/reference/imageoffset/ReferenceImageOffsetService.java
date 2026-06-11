package org.openpnp.machine.reference.imageoffset;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.openpnp.machine.reference.debug.ReferenceMachineDebugLog;

public final class ReferenceImageOffsetService {
    private ReferenceImageOffsetService() {
    }

    public static CsImageOffsetResult findOffset(File referenceImageFile, File capturedImageFile) throws IOException {
        if (referenceImageFile == null) {
            throw new IllegalArgumentException("referenceImageFile cannot be null");
        }

        if (capturedImageFile == null) {
            throw new IllegalArgumentException("capturedImageFile cannot be null");
        }

        ReferenceMachineDebugLog.debugPrintf("Loading reference image: %s", referenceImageFile.getAbsolutePath());
        BufferedImage ref_bmp = ImageIO.read(referenceImageFile);

        ReferenceMachineDebugLog.debugPrintf("Loading captured image: %s", capturedImageFile.getAbsolutePath());
        BufferedImage captured_bmp = ImageIO.read(capturedImageFile);

        if (ref_bmp == null) {
            throw new IOException("Unable to read reference image: " + referenceImageFile.getAbsolutePath());
        }

        if (captured_bmp == null) {
            throw new IOException("Unable to read captured image: " + capturedImageFile.getAbsolutePath());
        }

        return findOffset(ref_bmp, captured_bmp);
    }

    public static CsImageOffsetResult findOffset(BufferedImage ref_bmp, BufferedImage captured_bmp) {
        ReferenceMachineDebugLog.debugPrintf("Image offset calculation started.");

        CsImageOffsetResult result = CsImageOffset.findOffset(ref_bmp, captured_bmp);

        String info = result.getInfo();
        if (info != null && !info.isEmpty()) {
            for (String line : info.split("\\R")) {
                if (!line.isEmpty()) {
                    ReferenceMachineDebugLog.debugPrintf("%s", line);
                }
            }
        }

        ReferenceMachineDebugLog.debugPrintf(
                "Image offset result: dx=%.3f px, dy=%.3f px, peak=%.9f, dt=%d ms",
                result.getDx(),
                result.getDy(),
                result.getPeak(),
                result.getDt());

        return result;
    }
}