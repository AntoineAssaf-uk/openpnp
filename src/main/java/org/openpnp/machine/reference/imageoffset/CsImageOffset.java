package org.openpnp.machine.reference.imageoffset;

import java.awt.image.BufferedImage;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.util.Locale;

public final class CsImageOffset {
    private CsImageOffset() {
    }

    public static CsImageOffsetResult findOffset(BufferedImage ref_bmp, BufferedImage captured_bmp) {
        long start = System.currentTimeMillis();
        StringBuilder imageOffsetInfo = new StringBuilder();

        if (ref_bmp == null || captured_bmp == null) {
            throw new IllegalArgumentException("Bitmaps cannot be null");
        }

        if (ref_bmp.getWidth() != captured_bmp.getWidth() || ref_bmp.getHeight() != captured_bmp.getHeight()) {
            throw new IllegalArgumentException("Bitmaps must have same dimensions");
        }

        int w = ref_bmp.getWidth();
        int h = ref_bmp.getHeight();

        imageOffsetInfo.append("Ref: ReadAndConvertBitmapToArray\r\n");
        double[][] ref_bmp_array = readAndConvertBitmapToArray(ref_bmp, imageOffsetInfo);

        imageOffsetInfo.append("Cap: ReadAndConvertBitmapToArray\r\n");
        double[][] cap_bmp_array = readAndConvertBitmapToArray(captured_bmp, imageOffsetInfo);

        applyHannWindow(ref_bmp_array);
        applyHannWindow(cap_bmp_array);

        ComplexStruct[][] ref_complex_array = new ComplexStruct[h][w];
        ComplexStruct[][] cap_complex_array = new ComplexStruct[h][w];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                ref_complex_array[y][x] = new ComplexStruct(ref_bmp_array[y][x], 0.0);
                cap_complex_array[y][x] = new ComplexStruct(cap_bmp_array[y][x], 0.0);
            }
        }

        fft2D(ref_complex_array, false);
        fft2D(cap_complex_array, false);

        ComplexStruct[][] cross_pwr_spectrum = new ComplexStruct[h][w];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                ComplexStruct num = ref_complex_array[y][x].multiply(cap_complex_array[y][x].conj());
                double mag = num.abs();

                if (mag == 0.0) {
                    cross_pwr_spectrum[y][x] = new ComplexStruct(0.0, 0.0);
                } else {
                    cross_pwr_spectrum[y][x] = new ComplexStruct(num.re / mag, num.im / mag);
                }
            }
        }

        fft2D(cross_pwr_spectrum, true);

        int peakX = 0;
        int peakY = 0;
        double peakVal = Double.NEGATIVE_INFINITY;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double val = cross_pwr_spectrum[y][x].re;
                if (val > peakVal) {
                    peakVal = val;
                    peakX = x;
                    peakY = y;
                }
            }
        }

        double dx = peakX;
        double dy = peakY;

        if (dx > w / 2.0) {
            dx -= w;
        }

        if (dy > h / 2.0) {
            dy -= h;
        }

        double[] subpixel = parabolicPeakSubpixel(cross_pwr_spectrum, peakX, peakY);

        double refinedDx = subpixel[0];
        double refinedDy = subpixel[1];

        if (refinedDx > w / 2.0) {
            refinedDx -= w;
        }

        if (refinedDy > h / 2.0) {
            refinedDy -= h;
        }

        long dt = System.currentTimeMillis() - start;
        imageOffsetInfo.append("dt= ").append(dt).append("\r\n");

        return new CsImageOffsetResult(-refinedDx, +refinedDy, peakVal, dt, imageOffsetInfo.toString());
    }

    // Read 8-bit grayscale/indexed bitmap into double[][] with values 0.0 or 1.0.
    //
    // Section 4.20 assumption:
    // - The input image has already been converted to grayscale by previous
    // processing.
    // - The image is stored as one byte per pixel, equivalent in intent to C#
    // PixelFormat.Format8bppIndexed.
    // - The grayscale palette/index is assumed to be 0..255, so the byte value
    // itself
    // is used directly.
    // - Threshold is fixed at 128.
    // - Output array is [y][x].
    private static double[][] readAndConvertBitmapToArray(BufferedImage bmp, StringBuilder imageOffsetInfo) {
        if (bmp == null) {
            throw new IllegalArgumentException("Bitmap cannot be null.");
        }

        final int w = bmp.getWidth();
        final int h = bmp.getHeight();

        double[][] outArr = new double[h][w];

        Raster raster = bmp.getRaster();
        SampleModel sampleModel = raster.getSampleModel();

        if (raster.getNumBands() != 1) {
            throw new IllegalArgumentException(
                    "Expected one-band 8-bit grayscale/indexed bitmap. Bands = " + raster.getNumBands());
        }

        if (raster.getDataBuffer().getDataType() != DataBuffer.TYPE_BYTE) {
            throw new IllegalArgumentException(
                    "Expected 8-bit byte bitmap data. DataBuffer type = "
                            + raster.getDataBuffer().getDataType());
        }

        if (!(sampleModel instanceof ComponentSampleModel)) {
            throw new IllegalArgumentException(
                    "Expected ComponentSampleModel for 8-bit grayscale/indexed bitmap.");
        }

        ComponentSampleModel csm = (ComponentSampleModel) sampleModel;
        DataBufferByte dataBuffer = (DataBufferByte) raster.getDataBuffer();

        byte[] pixels = dataBuffer.getData();

        final int pixelStride = csm.getPixelStride();
        final int scanlineStride = csm.getScanlineStride();
        final int bandOffset = csm.getBandOffsets()[0];

        final int x0 = raster.getMinX() - raster.getSampleModelTranslateX();
        final int y0 = raster.getMinY() - raster.getSampleModelTranslateY();

        final int baseOffset = dataBuffer.getOffset() + csm.getOffset(x0, y0, 0) + bandOffset;

        for (int y = 0; y < h; y++) {
            int row = baseOffset + y * scanlineStride;

            for (int x = 0; x < w; x++) {
                int index = row + x * pixelStride;
                int g = pixels[index] & 0xFF;

                outArr[y][x] = g >= 128 ? 1.0 : 0.0;
            }
        }

        if (imageOffsetInfo != null) {
            imageOffsetInfo.append("Read 8-bit grayscale/indexed bitmap directly\r\n");
            imageOffsetInfo.append("Threshold = 128\r\n");
        }

        return outArr;
    }

    private static void applyHannWindow(double[][] arr) {
        int h = arr.length;
        int w = arr[0].length;

        for (int y = 0; y < h; y++) {
            double wy = 0.5 * (1 - Math.cos(2 * Math.PI * y / (h - 1)));

            for (int x = 0; x < w; x++) {
                double wx = 0.5 * (1 - Math.cos(2 * Math.PI * x / (w - 1)));
                arr[y][x] *= wx * wy;
            }
        }
    }

    private static void fft2D(ComplexStruct[][] data, boolean inverse) {
        int h = data.length;
        int w = data[0].length;

        ComplexStruct[] row = new ComplexStruct[w];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                row[x] = data[y][x];
            }

            fft1D(row, inverse);

            for (int x = 0; x < w; x++) {
                data[y][x] = row[x];
            }
        }

        ComplexStruct[] col = new ComplexStruct[h];

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                col[y] = data[y][x];
            }

            fft1D(col, inverse);

            for (int y = 0; y < h; y++) {
                data[y][x] = col[y];
            }
        }

        if (inverse) {
            double scale = 1.0 / ((double) w * h);

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    data[y][x] = new ComplexStruct(data[y][x].re * scale, data[y][x].im * scale);
                }
            }
        }
    }

    private static void fft1D(ComplexStruct[] buffer, boolean inverse) {
        int n = buffer.length;

        if ((n & (n - 1)) != 0) {
            throw new IllegalArgumentException("FFT length must be power of two");
        }

        int j = 0;

        for (int i = 1; i < n; i++) {
            int bit = n >> 1;

            while ((j & bit) != 0) {
                j ^= bit;
                bit >>= 1;
            }

            j ^= bit;

            if (i < j) {
                ComplexStruct tmp = buffer[i];
                buffer[i] = buffer[j];
                buffer[j] = tmp;
            }
        }

        for (int len = 2; len <= n; len <<= 1) {
            double angle = 2 * Math.PI / len * (inverse ? -1 : 1);
            double wlenRe = Math.cos(angle);
            double wlenIm = Math.sin(angle);

            for (int i = 0; i < n; i += len) {
                double wRe = 1.0;
                double wIm = 0.0;

                for (int k = 0; k < len / 2; k++) {
                    ComplexStruct u = buffer[i + k];
                    ComplexStruct v = buffer[i + k + len / 2];

                    double vr = v.re * wRe - v.im * wIm;
                    double vi = v.re * wIm + v.im * wRe;

                    buffer[i + k] = new ComplexStruct(u.re + vr, u.im + vi);
                    buffer[i + k + len / 2] = new ComplexStruct(u.re - vr, u.im - vi);

                    double nextWRe = wRe * wlenRe - wIm * wlenIm;
                    double nextWIm = wRe * wlenIm + wIm * wlenRe;

                    wRe = nextWRe;
                    wIm = nextWIm;
                }
            }
        }
    }

    private static double[] parabolicPeakSubpixel(ComplexStruct[][] corr, int px, int py) {
        int h = corr.length;
        int w = corr[0].length;

        if (px <= 0 || px >= w - 1 || py <= 0 || py >= h - 1) {
            return new double[] { px, py };
        }

        double z10 = corr[py - 1][px].re;
        double z01 = corr[py][px - 1].re;
        double z11 = corr[py][px].re;
        double z21 = corr[py][px + 1].re;
        double z12 = corr[py + 1][px].re;

        double denomX = 2 * z11 - z21 - z01;
        double dx = 0.0;

        if (Math.abs(denomX) > 1e-12) {
            dx = (z21 - z01) / (2 * denomX);
        }

        double denomY = 2 * z11 - z12 - z10;
        double dy = 0.0;

        if (Math.abs(denomY) > 1e-12) {
            dy = (z12 - z10) / (2 * denomY);
        }

        return new double[] { px + dx, py + dy };
    }

    private static final class ComplexStruct {
        private final double re;
        private final double im;

        private ComplexStruct(double re, double im) {
            this.re = re;
            this.im = im;
        }

        private ComplexStruct multiply(ComplexStruct b) {
            return new ComplexStruct(re * b.re - im * b.im, re * b.im + im * b.re);
        }

        private ComplexStruct conj() {
            return new ComplexStruct(re, -im);
        }

        private double abs() {
            return Math.sqrt(re * re + im * im);
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "%.6f %s %.6fj", re, im >= 0 ? "+" : "-", Math.abs(im));
        }
    }
}