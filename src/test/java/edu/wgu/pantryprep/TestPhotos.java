package edu.wgu.pantryprep;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/** Small generated pictures for photo tests. */
final class TestPhotos {

    private TestPhotos() {
    }

    /** A picture whose left half is red and right half blue, so turning it shows. */
    static BufferedImage halves(int width, int height, boolean transparent) {
        BufferedImage image = new BufferedImage(width, height,
                transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, width / 2, height);
        g.setColor(Color.BLUE);
        g.fillRect(width / 2, 0, width - width / 2, height);
        g.dispose();
        return image;
    }

    static byte[] encode(BufferedImage image, String format) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("No writer for " + format);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return out.toByteArray();
    }

    static byte[] jpeg(int width, int height) {
        return encode(halves(width, height, false), "jpeg");
    }

    static byte[] png(int width, int height) {
        return encode(halves(width, height, true), "png");
    }

    /**
     * The JPEG with an EXIF block saying how to turn it (big-endian TIFF, one
     * IFD entry for orientation) and some text that stands in for camera
     * metadata such as a GPS position.
     */
    static byte[] withExifOrientation(byte[] jpeg, int orientation) {
        byte[] marker = "SECRET-GPS-48.8584N".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] tiff = {
                'M', 'M', 0, 42, 0, 0, 0, 8,
                0, 1,
                0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0,
                0, 0, 0, 0};
        byte[] exif = new byte[6 + tiff.length + marker.length];
        System.arraycopy(new byte[] {'E', 'x', 'i', 'f', 0, 0}, 0, exif, 0, 6);
        System.arraycopy(tiff, 0, exif, 6, tiff.length);
        System.arraycopy(marker, 0, exif, 6 + tiff.length, marker.length);
        int length = exif.length + 2;
        byte[] out = new byte[jpeg.length + 4 + exif.length];
        out[0] = (byte) 0xFF;
        out[1] = (byte) 0xD8;
        out[2] = (byte) 0xFF;
        out[3] = (byte) 0xE1;
        out[4] = (byte) (length >> 8);
        out[5] = (byte) length;
        System.arraycopy(exif, 0, out, 6, exif.length);
        System.arraycopy(jpeg, 2, out, 6 + exif.length, jpeg.length - 2);
        return out;
    }
}
