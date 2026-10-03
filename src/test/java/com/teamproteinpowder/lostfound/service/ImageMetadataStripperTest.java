package com.teamproteinpowder.lostfound.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Photos are published to anyone who opens an item. These tests plant
 * location and identity metadata in each supported format and check that
 * none of it survives storage, while the image itself is unharmed.
 */
class ImageMetadataStripperTest {

    private static final String GPS = "GPS 23.8103N 90.4125E";
    private static final String OWNER = "Owner: Jane Student";

    private static boolean contains(byte[] haystack, String needle) {
        return new String(haystack, StandardCharsets.ISO_8859_1).contains(needle);
    }

    private static byte[] encode(String format) throws Exception {
        BufferedImage img = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        img.setRGB(5, 5, 0xFF0000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    /* ---------------------------------------------------------------- JPEG */

    /** An EXIF APP1 with an orientation tag and a GPS IFD, as a phone writes. */
    private static byte[] exifSegment(int orientation) {
        byte[] gps = GPS.getBytes(StandardCharsets.ISO_8859_1);
        ByteBuffer tiff = ByteBuffer.allocate(8 + 2 + 24 + 4 + gps.length).order(ByteOrder.BIG_ENDIAN);
        tiff.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(8);
        tiff.putShort((short) 2);
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        tiff.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(38); // GPS IFD pointer
        tiff.putInt(0);
        tiff.put(gps);
        byte[] payload = new byte[6 + tiff.capacity()];
        System.arraycopy("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1), 0, payload, 0, 6);
        System.arraycopy(tiff.array(), 0, payload, 6, tiff.capacity());
        return segment(0xE1, payload);
    }

    private static byte[] segment(int marker, byte[] payload) {
        int length = payload.length + 2;
        byte[] seg = new byte[2 + length];
        seg[0] = (byte) 0xFF;
        seg[1] = (byte) marker;
        seg[2] = (byte) (length >> 8);
        seg[3] = (byte) length;
        System.arraycopy(payload, 0, seg, 4, payload.length);
        return seg;
    }

    /** Insert segments straight after the SOI marker. */
    private static byte[] withSegments(byte[] jpeg, byte[]... segments) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        for (byte[] s : segments) out.write(s, 0, s.length);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    /** Orientation as a viewer would read it from the first EXIF block, or 1. */
    private static int orientationOf(byte[] jpeg) {
        int pos = 2;
        while (pos + 4 < jpeg.length && (jpeg[pos + 1] & 0xFF) != 0xDA) {
            int len = ((jpeg[pos + 2] & 0xFF) << 8) | (jpeg[pos + 3] & 0xFF);
            if ((jpeg[pos + 1] & 0xFF) == 0xE1
                    && new String(jpeg, pos + 4, 6, StandardCharsets.ISO_8859_1).equals("Exif\0\0")) {
                ByteBuffer b = ByteBuffer.wrap(jpeg, pos + 10, len - 8).slice()
                        .order(jpeg[pos + 10] == 'I' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
                int ifd = b.getInt(4);
                for (int i = 0; i < (b.getShort(ifd) & 0xFFFF); i++) {
                    int e = ifd + 2 + i * 12;
                    if ((b.getShort(e) & 0xFFFF) == 0x0112) return b.getShort(e + 8);
                }
            }
            pos += 2 + len;
        }
        return 1;
    }

    @Test
    void jpegLosesGpsAndCommentsButKeepsOrientationAndPixels() throws Exception {
        byte[] clean = encode("jpg");
        byte[] phone = withSegments(clean, exifSegment(6),
                segment(0xFE, OWNER.getBytes(StandardCharsets.ISO_8859_1)));
        assertTrue(contains(phone, GPS), "precondition: the planted GPS is present");

        byte[] stripped = ImageMetadataStripper.strip(phone, "image/jpeg");

        assertFalse(contains(stripped, GPS), "GPS position must not survive");
        assertFalse(contains(stripped, OWNER), "comment segments must not survive");
        assertEquals(6, orientationOf(stripped), "a rotated phone photo must still display upright");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(stripped));
        assertNotNull(decoded);
        assertEquals(40, decoded.getWidth());
        assertEquals(30, decoded.getHeight());
    }

    @Test
    void uprightJpegGetsNoExifAtAll() throws Exception {
        byte[] stripped = ImageMetadataStripper.strip(withSegments(encode("jpg"), exifSegment(1)), "image/jpeg");
        assertFalse(contains(stripped, "Exif\0\0"));
        assertFalse(contains(stripped, GPS));
    }

    @Test
    void jpegWithoutMetadataIsUnchanged() throws Exception {
        byte[] clean = encode("jpg");
        assertArrayEquals(clean, ImageMetadataStripper.strip(clean, "image/jpeg"));
    }

    /* ----------------------------------------------------------------- PNG */

    private static byte[] chunk(String type, byte[] data) {
        ByteBuffer b = ByteBuffer.allocate(12 + data.length);
        b.putInt(data.length).put(type.getBytes(StandardCharsets.ISO_8859_1)).put(data);
        CRC32 crc = new CRC32();
        crc.update(type.getBytes(StandardCharsets.ISO_8859_1));
        crc.update(data);
        b.putInt((int) crc.getValue());
        return b.array();
    }

    @Test
    void pngLosesTextAndExifChunks() throws Exception {
        byte[] clean = encode("png");
        int iend = clean.length - 12;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(clean, 0, iend);
        out.write(chunk("tEXt", ("Author\0" + OWNER).getBytes(StandardCharsets.ISO_8859_1)));
        out.write(chunk("eXIf", GPS.getBytes(StandardCharsets.ISO_8859_1)));
        out.write(clean, iend, 12);
        byte[] tagged = out.toByteArray();

        byte[] stripped = ImageMetadataStripper.strip(tagged, "image/png");

        assertFalse(contains(stripped, OWNER));
        assertFalse(contains(stripped, GPS));
        assertArrayEquals(clean, stripped, "only the metadata chunks are removed");
    }

    /* ---------------------------------------------------------------- WebP */

    private static byte[] riffChunk(String type, byte[] data) {
        int padded = data.length + (data.length & 1);
        ByteBuffer b = ByteBuffer.allocate(8 + padded).order(ByteOrder.LITTLE_ENDIAN);
        b.put(type.getBytes(StandardCharsets.ISO_8859_1)).putInt(data.length).put(data);
        return b.array();
    }

    @Test
    void webpLosesExifAndXmpAndStaysAConsistentContainer() throws Exception {
        byte[] vp8x = new byte[10];
        vp8x[0] = 0x0C; // EXIF + XMP flags set
        byte[] image = new byte[] {0x2F, 1, 2, 3, 4, 5, 6, 7, 8};
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(riffChunk("VP8X", vp8x));
        body.write(riffChunk("VP8L", image));
        body.write(riffChunk("EXIF", GPS.getBytes(StandardCharsets.ISO_8859_1)));
        body.write(riffChunk("XMP ", (OWNER + "!").getBytes(StandardCharsets.ISO_8859_1))); // odd length, padded
        ByteBuffer file = ByteBuffer.allocate(12 + body.size()).order(ByteOrder.LITTLE_ENDIAN);
        file.put("RIFF".getBytes(StandardCharsets.ISO_8859_1)).putInt(4 + body.size())
                .put("WEBP".getBytes(StandardCharsets.ISO_8859_1)).put(body.toByteArray());

        byte[] stripped = ImageMetadataStripper.strip(file.array(), "image/webp");

        assertFalse(contains(stripped, GPS));
        assertFalse(contains(stripped, OWNER));
        assertEquals(stripped.length - 8,
                ByteBuffer.wrap(stripped, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt(), "RIFF size must match");
        assertEquals(0, stripped[20] & 0x0C, "the VP8X flags must no longer claim EXIF or XMP");
        assertTrue(contains(stripped, "VP8L"), "the image data itself is kept");
    }

    /* ------------------------------------------------------- end to end */

    @Test
    void storedUploadsCarryNoLocation(@TempDir Path dir) throws Exception {
        StorageService storage = new StorageService(dir.toString());
        byte[] phone = withSegments(encode("jpg"), exifSegment(6));

        String url = storage.store(new MockMultipartFile("photo", "IMG_0001.jpg", "image/jpeg", phone));
        String privateName = storage.storePrivate(new MockMultipartFile("evidence", "x.jpg", "image/jpeg", phone));

        assertFalse(contains(Files.readAllBytes(dir.resolve(url.substring("/uploads/".length()))), GPS),
                "public photo leaked GPS");
        assertFalse(contains(Files.readAllBytes(dir.resolve("private").resolve(privateName)), GPS),
                "private evidence leaked the claimant's GPS to the poster");
    }

    @Test
    void malformedInputIsReturnedUntouchedRatherThanCorrupted() {
        byte[] junk = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0x7F, 0x7F, 1, 2};
        assertArrayEquals(junk, ImageMetadataStripper.strip(junk, "image/jpeg"));
    }
}
