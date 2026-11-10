package edu.wgu.pantryprep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.wgu.pantryprep.photos.PhotoProcessor;
import edu.wgu.pantryprep.photos.PhotoProcessor.Processed;
import edu.wgu.pantryprep.photos.PhotoProcessor.UnusablePhoto;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Shrinking, turning and cleaning photos, without a database. */
class PhotoProcessorTests {

    @Test
    void aLargePhotoIsShrunkWithAThumbnail() throws Exception {
        Processed photo = PhotoProcessor.process(TestPhotos.jpeg(4000, 3000));
        assertEquals(1600, photo.width());
        assertEquals(1200, photo.height());

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(photo.image()));
        assertEquals(1600, image.getWidth());
        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(photo.thumbnail()));
        assertEquals(480, thumb.getWidth());
        assertEquals(360, thumb.getHeight());
        assertTrue(photo.image().length < 400_000, "a few hundred kilobytes at most: " + photo.image().length);
    }

    @Test
    void aSmallPhotoIsNeverEnlarged() throws Exception {
        Processed photo = PhotoProcessor.process(TestPhotos.png(300, 200));
        assertEquals(300, photo.width());
        assertEquals(200, photo.height());
    }

    @Test
    void transparencyBecomesWhiteNotBlack() throws Exception {
        BufferedImage clear = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        Processed photo = PhotoProcessor.process(TestPhotos.encode(clear, "png"));
        Color corner = new Color(ImageIO.read(new ByteArrayInputStream(photo.image())).getRGB(5, 5));
        assertTrue(corner.getRed() > 240 && corner.getGreen() > 240 && corner.getBlue() > 240, corner.toString());
    }

    @Test
    void aPhoneCameraTurnIsAppliedAndItsMetadataIsGone() throws Exception {
        byte[] sideways = TestPhotos.withExifOrientation(TestPhotos.jpeg(400, 200), 6);
        assertTrue(new String(sideways, StandardCharsets.ISO_8859_1).contains("SECRET-GPS"));

        Processed photo = PhotoProcessor.process(sideways);
        assertEquals(200, photo.width(), "turned a quarter, so width and height swap");
        assertEquals(400, photo.height());

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(photo.image()));
        Color top = new Color(image.getRGB(100, 20));
        Color bottom = new Color(image.getRGB(100, 380));
        assertTrue(top.getRed() > 200 && top.getBlue() < 60, "red half turned to the top: " + top);
        assertTrue(bottom.getBlue() > 200 && bottom.getRed() < 60, "blue half at the bottom: " + bottom);

        String stored = new String(photo.image(), StandardCharsets.ISO_8859_1);
        assertFalse(stored.contains("SECRET-GPS"), "camera metadata isn't kept");
        assertFalse(stored.contains("Exif"), "no EXIF block at all");
    }

    @Test
    void anUpsideDownPhotoIsTurnedRightWayUp() throws Exception {
        Processed photo = PhotoProcessor.process(TestPhotos.withExifOrientation(TestPhotos.jpeg(400, 200), 3));
        assertEquals(400, photo.width());
        Color left = new Color(ImageIO.read(new ByteArrayInputStream(photo.image())).getRGB(20, 100));
        assertTrue(left.getBlue() > 200, "turned half a circle, so blue is now on the left: " + left);
    }

    @Test
    void filesThatAreNotPhotosAreRefusedWithAReason() {
        UnusablePhoto text = assertThrows(UnusablePhoto.class,
                () -> PhotoProcessor.process("name,serves\nchili,4".getBytes(StandardCharsets.UTF_8)));
        assertTrue(text.getMessage().contains("JPEG or PNG"), text.getMessage());

        byte[] heic = new byte[64];
        System.arraycopy("\0\0\0\u0018ftypheic".getBytes(StandardCharsets.ISO_8859_1), 0, heic, 0, 12);
        UnusablePhoto iphone = assertThrows(UnusablePhoto.class, () -> PhotoProcessor.process(heic));
        assertTrue(iphone.getMessage().contains("HEIC"), iphone.getMessage());

        byte[] broken = TestPhotos.jpeg(200, 200);
        byte[] truncated = java.util.Arrays.copyOf(broken, 120);
        assertThrows(UnusablePhoto.class, () -> PhotoProcessor.process(truncated));
        assertThrows(UnusablePhoto.class, () -> PhotoProcessor.process(new byte[0]));
    }

    @Test
    void aPictureClaimingEnormousSizeIsRefusedBeforeItIsDecoded() {
        UnusablePhoto huge = assertThrows(UnusablePhoto.class, () -> PhotoProcessor.process(pngHeader(50_000, 50_000)));
        assertTrue(huge.getMessage().contains("too large"), huge.getMessage());
    }

    /** A PNG that is only a signature and a header, claiming the given size. */
    private static byte[] pngHeader(int width, int height) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
        byte[] ihdr = {'I', 'H', 'D', 'R',
                (byte) (width >> 24), (byte) (width >> 16), (byte) (width >> 8), (byte) width,
                (byte) (height >> 24), (byte) (height >> 16), (byte) (height >> 8), (byte) height,
                8, 2, 0, 0, 0};
        out.write(new byte[] {0, 0, 0, 13});
        out.write(ihdr);
        CRC32 crc = new CRC32();
        crc.update(ihdr);
        long value = crc.getValue();
        out.write(new byte[] {(byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value});
        return out.toByteArray();
    }
}
