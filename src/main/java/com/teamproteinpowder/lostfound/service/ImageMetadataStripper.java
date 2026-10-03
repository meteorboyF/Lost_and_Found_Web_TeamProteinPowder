package com.teamproteinpowder.lostfound.service;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Removes embedded metadata from uploaded photographs before they are stored.
 *
 * A photo taken on a phone carries EXIF data, usually including the GPS
 * position where it was taken. On a lost-and-found board that is often the
 * finder's room or home, published to everyone who opens the item. Camera
 * serial numbers, owner names and editing history travel the same way.
 *
 * This works on the file structure rather than decoding and re-encoding the
 * image, so pixels and quality are untouched. The one EXIF field kept is
 * orientation: without it, portrait phone photos would display sideways.
 *
 * Input has already been sniffed and validated as the given type. Anything
 * structurally unexpected is returned unchanged rather than corrupted;
 * validation has already rejected files that are not real images.
 */
final class ImageMetadataStripper {

    private ImageMetadataStripper() {
    }

    static byte[] strip(byte[] data, String type) {
        try {
            return switch (type) {
                case "image/jpeg" -> stripJpeg(data);
                case "image/png" -> stripPng(data);
                case "image/webp" -> stripWebp(data);
                default -> data; // GIF has no standard location or camera metadata
            };
        } catch (RuntimeException malformed) {
            return data;
        }
    }

    /* ------------------------------------------------------------------
       JPEG: a sequence of marker segments up to start-of-scan
       ------------------------------------------------------------------ */

    private static final int SOI = 0xD8, SOS = 0xDA, EOI = 0xD9, APP1 = 0xE1, APP13 = 0xED, COM = 0xFE;

    private static byte[] stripJpeg(byte[] in) {
        if ((in[0] & 0xFF) != 0xFF || (in[1] & 0xFF) != SOI) return in;

        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        out.write(0xFF);
        out.write(SOI);

        int orientation = 1;
        boolean wroteOrientation = false;
        int pos = 2;
        while (pos + 4 <= in.length) {
            if ((in[pos] & 0xFF) != 0xFF) return in;
            int marker = in[pos + 1] & 0xFF;
            if (marker == 0xFF) { pos++; continue; } // fill byte
            if (marker == SOS || marker == EOI) {
                // Entropy-coded image data follows; copy the rest verbatim.
                if (!wroteOrientation && orientation != 1) writeOrientationApp1(out, orientation);
                out.write(in, pos, in.length - pos);
                return out.toByteArray();
            }
            int length = ((in[pos + 2] & 0xFF) << 8) | (in[pos + 3] & 0xFF);
            if (length < 2 || pos + 2 + length > in.length) return in;

            boolean drop = false;
            if (marker == APP1) {
                // EXIF (with GPS) and XMP both live in APP1.
                int found = readExifOrientation(in, pos + 4, length - 2);
                if (found > 1) orientation = found;
                drop = true;
            } else if (marker == APP13 || marker == COM) {
                // Photoshop/IPTC (names, captions, locations) and free-text comments.
                drop = true;
            }
            if (!drop) {
                // The orientation segment goes before the frame header, as viewers expect.
                if (!wroteOrientation && orientation != 1 && isFrameOrTableMarker(marker)) {
                    writeOrientationApp1(out, orientation);
                    wroteOrientation = true;
                }
                out.write(in, pos, 2 + length);
            }
            pos += 2 + length;
        }
        return in;
    }

    private static boolean isFrameOrTableMarker(int marker) {
        // Anything that is not an APPn segment: quantisation/huffman tables, frame headers, etc.
        return marker < 0xE0 || marker > 0xEF;
    }

    /** @return the EXIF orientation (1–8), or 0 if this APP1 is not EXIF or has none */
    private static int readExifOrientation(byte[] in, int start, int len) {
        if (len < 14 || !new String(in, start, 6, StandardCharsets.ISO_8859_1).equals("Exif\0\0")) return 0;
        int tiff = start + 6;
        ByteOrder order;
        if (in[tiff] == 'I' && in[tiff + 1] == 'I') order = ByteOrder.LITTLE_ENDIAN;
        else if (in[tiff] == 'M' && in[tiff + 1] == 'M') order = ByteOrder.BIG_ENDIAN;
        else return 0;
        ByteBuffer b = ByteBuffer.wrap(in, tiff, len - 6).slice().order(order);
        long ifd = Integer.toUnsignedLong(b.getInt(4));
        if (ifd + 2 > b.limit()) return 0;
        int entries = b.getShort((int) ifd) & 0xFFFF;
        for (int i = 0; i < entries; i++) {
            int e = (int) ifd + 2 + i * 12;
            if (e + 12 > b.limit()) return 0;
            if ((b.getShort(e) & 0xFFFF) == 0x0112) {
                int value = b.getShort(e + 8) & 0xFFFF;
                return value >= 1 && value <= 8 ? value : 0;
            }
        }
        return 0;
    }

    /** A minimal EXIF block holding only the orientation tag. */
    private static void writeOrientationApp1(ByteArrayOutputStream out, int orientation) {
        byte[] exif = {
                'E', 'x', 'i', 'f', 0, 0,
                'M', 'M', 0, 42, 0, 0, 0, 8,              // big-endian TIFF header, IFD0 at offset 8
                0, 1,                                      // one entry
                0x01, 0x12, 0, 3, 0, 0, 0, 1,              // tag 0x0112 orientation, SHORT, count 1
                0, (byte) orientation, 0, 0,               // value
                0, 0, 0, 0 };                              // no next IFD
        int length = exif.length + 2;
        out.write(0xFF);
        out.write(APP1);
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.write(exif, 0, exif.length);
    }

    /* ------------------------------------------------------------------
       PNG: signature, then length-type-data-crc chunks
       ------------------------------------------------------------------ */

    /** Text, EXIF and timestamp chunks; none affect how the image renders. */
    private static final Set<String> PNG_METADATA = Set.of("eXIf", "tEXt", "zTXt", "iTXt", "tIME");

    private static byte[] stripPng(byte[] in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        out.write(in, 0, 8);
        int pos = 8;
        while (pos + 12 <= in.length) {
            long length = Integer.toUnsignedLong(ByteBuffer.wrap(in, pos, 4).getInt());
            if (pos + 12 + length > in.length) return in;
            String type = new String(in, pos + 4, 4, StandardCharsets.ISO_8859_1);
            int total = 12 + (int) length;
            if (!PNG_METADATA.contains(type)) out.write(in, pos, total);
            pos += total;
            if (type.equals("IEND")) return out.toByteArray();
        }
        return in;
    }

    /* ------------------------------------------------------------------
       WebP: a RIFF container of chunks; extended files flag EXIF/XMP
       ------------------------------------------------------------------ */

    private static byte[] stripWebp(byte[] in) {
        ByteArrayOutputStream body = new ByteArrayOutputStream(in.length);
        int pos = 12;
        boolean changed = false;
        int vp8xFlagsAt = -1;
        while (pos + 8 <= in.length) {
            String type = new String(in, pos, 4, StandardCharsets.ISO_8859_1);
            long size = Integer.toUnsignedLong(ByteBuffer.wrap(in, pos + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
            long padded = size + (size & 1);
            if (pos + 8 + padded > in.length) return in;
            if (type.equals("EXIF") || type.equals("XMP ")) {
                changed = true;
            } else {
                if (type.equals("VP8X")) vp8xFlagsAt = 12 + body.size() + 8;
                body.write(in, pos, (int) (8 + padded));
            }
            pos += (int) (8 + padded);
        }
        if (!changed) return in;

        byte[] out = new byte[12 + body.size()];
        System.arraycopy(in, 0, out, 0, 12);
        System.arraycopy(body.toByteArray(), 0, out, 12, body.size());
        ByteBuffer.wrap(out, 4, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(out.length - 8);
        if (vp8xFlagsAt >= 0) {
            out[vp8xFlagsAt] &= (byte) ~0x0C; // clear the EXIF (0x08) and XMP (0x04) presence flags
        }
        return out;
    }
}
