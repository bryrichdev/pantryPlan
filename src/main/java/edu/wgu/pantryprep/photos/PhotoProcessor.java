package edu.wgu.pantryprep.photos;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * Turns an uploaded picture into what is stored: a JPEG no larger than
 * {@link #MAX_SIDE} pixels on its longest side, turned the way the camera
 * meant it, and a small thumbnail for the gallery.
 *
 * <p>Everything is re-encoded from pixels, so nothing from the original file
 * survives: no GPS position or other camera metadata, and nothing a browser
 * could mistake for anything but an image. The image's size is read from its
 * header before any pixels are decoded, and a very large photo is decoded at
 * a reduced resolution, so a huge or hostile file cannot exhaust memory.
 */
public final class PhotoProcessor {

    public static final int MAX_SIDE = 1600;
    public static final int THUMB_SIDE = 480;
    static final long MAX_PIXELS = 80_000_000L;
    private static final Set<String> FORMATS = Set.of("jpeg", "png", "gif", "bmp");

    static {
        ImageIO.setUseCache(false);
    }

    /** The stored photo and its thumbnail. */
    public record Processed(byte[] image, byte[] thumbnail, int width, int height) {
    }

    /** A file that can't be used, with a reason the cook can act on. */
    public static final class UnusablePhoto extends Exception {
        public UnusablePhoto(String message) {
            super(message);
        }
    }

    private PhotoProcessor() {
    }

    public static Processed process(byte[] bytes) throws UnusablePhoto {
        if (bytes == null || bytes.length == 0) {
            throw new UnusablePhoto("The file is empty.");
        }
        BufferedImage decoded;
        String format;
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new UnusablePhoto(notAPhoto(bytes));
            }
            ImageReader reader = readers.next();
            try {
                format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!FORMATS.contains(format)) {
                    throw new UnusablePhoto(notAPhoto(bytes));
                }
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1) {
                    throw new UnusablePhoto("The picture has no size.");
                }
                if ((long) width * height > MAX_PIXELS) {
                    throw new UnusablePhoto("The picture is too large (" + width + " × " + height
                            + " pixels). Use one under 80 megapixels.");
                }
                ImageReadParam param = reader.getDefaultReadParam();
                // Decode no more than twice the stored size, then scale smoothly from there.
                int step = Math.max(1, Math.max(width, height) / (MAX_SIDE * 2));
                param.setSourceSubsampling(step, step, 0, 0);
                decoded = reader.read(0, param);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            throw new UnusablePhoto("The picture couldn't be read. Save it again as a JPEG or PNG and upload that.");
        }

        BufferedImage photo = fit(flatten(decoded), MAX_SIDE);
        if (format.equals("jpeg")) {
            photo = orient(photo, ExifOrientation.read(bytes));
        }
        BufferedImage thumbnail = fit(photo, THUMB_SIDE);
        return new Processed(jpeg(photo, 0.85f), jpeg(thumbnail, 0.8f), photo.getWidth(), photo.getHeight());
    }

    private static String notAPhoto(byte[] bytes) {
        if (looksLikeHeic(bytes)) {
            return "This is an HEIC photo, which can't be read here. Upload it from your phone's photo picker, "
                    + "which sends a JPEG, or export it as a JPEG first.";
        }
        return "This isn't a photo PantryPrep can read. Use a JPEG or PNG.";
    }

    /** HEIC/HEIF files carry "ftyp" then a brand such as "heic" near the start. */
    static boolean looksLikeHeic(byte[] bytes) {
        if (bytes.length < 12) {
            return false;
        }
        String head = new String(bytes, 4, 8, java.nio.charset.StandardCharsets.ISO_8859_1);
        return head.startsWith("ftyp") && Set.of("heic", "heix", "hevc", "heim", "heis", "mif1", "msf1", "avif")
                .contains(head.substring(4));
    }

    /** Onto an opaque white background, so transparent PNGs don't turn black as JPEGs. */
    private static BufferedImage flatten(BufferedImage source) {
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    /** Scales down to fit maxSide, halving first so the result stays smooth. Never scales up. */
    static BufferedImage fit(BufferedImage source, int maxSide) {
        int width = source.getWidth();
        int height = source.getHeight();
        double scale = Math.min(1.0, (double) maxSide / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        BufferedImage current = source;
        while (current.getWidth() / 2 >= targetWidth && current.getHeight() / 2 >= targetHeight) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        if (current.getWidth() != targetWidth || current.getHeight() != targetHeight) {
            current = resize(current, targetWidth, targetHeight);
        }
        return current;
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /**
     * Applies an EXIF orientation (1 to 8): phones store the sensor's pixels
     * as they came and record how to turn them, which a re-encoded JPEG would
     * otherwise forget.
     */
    static BufferedImage orient(BufferedImage source, int orientation) {
        if (orientation < 2 || orientation > 8) {
            return source;
        }
        int w = source.getWidth();
        int h = source.getHeight();
        boolean swap = orientation >= 5;
        int outWidth = swap ? h : w;
        int outHeight = swap ? w : h;
        int[] in = source.getRGB(0, 0, w, h, null, 0, w);
        int[] out = new int[in.length];
        for (int y = 0; y < outHeight; y++) {
            for (int x = 0; x < outWidth; x++) {
                int sx;
                int sy;
                switch (orientation) {
                    case 2 -> { sx = w - 1 - x; sy = y; }
                    case 3 -> { sx = w - 1 - x; sy = h - 1 - y; }
                    case 4 -> { sx = x; sy = h - 1 - y; }
                    case 5 -> { sx = y; sy = x; }
                    case 6 -> { sx = y; sy = h - 1 - x; }
                    case 7 -> { sx = w - 1 - y; sy = h - 1 - x; }
                    default -> { sx = w - 1 - y; sy = x; }
                }
                out[y * outWidth + x] = in[sy * w + sx];
            }
        }
        BufferedImage result = new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_RGB);
        result.setRGB(0, 0, outWidth, outHeight, out, 0, outWidth);
        return result;
    }

    private static byte[] jpeg(BufferedImage image, float quality) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
