package edu.wgu.pantryprep.photos;

/**
 * Reads the orientation tag (0x0112) from a JPEG's EXIF block. Only that one
 * value is wanted, so this walks the file's segments by hand rather than
 * pulling in a metadata library. Every offset is bounds-checked: a damaged or
 * hostile file gives 1, "already upright", and never an exception.
 */
final class ExifOrientation {

    private static final int ORIENTATION_TAG = 0x0112;

    private ExifOrientation() {
    }

    static int read(byte[] jpeg) {
        try {
            return find(jpeg);
        } catch (RuntimeException ex) {
            return 1;
        }
    }

    private static int find(byte[] b) {
        if (b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) {
            return 1;
        }
        int pos = 2;
        while (pos + 4 <= b.length) {
            if ((b[pos] & 0xFF) != 0xFF) {
                return 1;
            }
            int marker = b[pos + 1] & 0xFF;
            if (marker == 0xFF) {
                pos++;
                continue;
            }
            if (marker == 0xDA || marker == 0xD9) {
                return 1;
            }
            int length = ((b[pos + 2] & 0xFF) << 8) | (b[pos + 3] & 0xFF);
            if (length < 2 || pos + 2 + length > b.length) {
                return 1;
            }
            int start = pos + 4;
            if (marker == 0xE1 && length >= 8 && b[start] == 'E' && b[start + 1] == 'x' && b[start + 2] == 'i'
                    && b[start + 3] == 'f' && b[start + 4] == 0 && b[start + 5] == 0) {
                return fromTiff(b, start + 6, pos + 2 + length);
            }
            pos += 2 + length;
        }
        return 1;
    }

    private static int fromTiff(byte[] b, int tiff, int end) {
        if (tiff + 8 > end) {
            return 1;
        }
        boolean little;
        if (b[tiff] == 'I' && b[tiff + 1] == 'I') {
            little = true;
        } else if (b[tiff] == 'M' && b[tiff + 1] == 'M') {
            little = false;
        } else {
            return 1;
        }
        if (u16(b, tiff + 2, little) != 42) {
            return 1;
        }
        long ifd = u32(b, tiff + 4, little);
        int dir = tiff + (int) Math.min(ifd, Integer.MAX_VALUE - tiff);
        if (ifd < 8 || dir + 2 > end) {
            return 1;
        }
        int entries = u16(b, dir, little);
        for (int i = 0; i < entries; i++) {
            int entry = dir + 2 + i * 12;
            if (entry + 12 > end) {
                return 1;
            }
            if (u16(b, entry, little) == ORIENTATION_TAG) {
                int value = u16(b, entry + 8, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static int u16(byte[] b, int at, boolean little) {
        int first = b[at] & 0xFF;
        int second = b[at + 1] & 0xFF;
        return little ? (second << 8) | first : (first << 8) | second;
    }

    private static long u32(byte[] b, int at, boolean little) {
        long value = 0;
        for (int i = 0; i < 4; i++) {
            int index = little ? at + 3 - i : at + i;
            value = (value << 8) | (b[index] & 0xFF);
        }
        return value;
    }
}
