package com.teamproteinpowder.lostfound.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Writes uploaded photographs to disk and hands back the public URL.
 *
 * Filenames are generated, never taken from the upload: a client-supplied name
 * is attacker-controlled and is the usual way a path traversal or an executable
 * extension gets written into a served directory.
 */
@Service
public class StorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final Path root;

    public StorageService(@Value("${app.upload.dir}") String uploadDir) throws IOException {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    public Path getRoot() {
        return root;
    }

    /**
     * @return the public URL of the stored file, or null when no file was sent
     */
    public String store(MultipartFile file) {
        String name = storeFile(file, root);
        return name == null ? null : "/uploads/" + name;
    }

    public String storePrivate(MultipartFile file) {
        return storeFile(file, root.resolve("private"));
    }

    private String storeFile(MultipartFile file, Path directory) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Photographs must be 5 MB or smaller");
        }

        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Upload a JPEG, PNG, WebP, or GIF image");
        }

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the photograph", e);
        }
        String detected = detectType(data);
        if (data.length > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Photographs must be 5 MB or smaller");
        if (!contentType.equals(detected)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The file contents must match its image type");
        }
        /* Strip GPS and other identifying metadata, then validate what will
           actually be stored, so a stripping fault can never save a broken file. */
        data = ImageMetadataStripper.strip(data, detected);
        validateImage(data, detected);
        String extension = switch (detected) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpg";
        };

        String name = UUID.randomUUID() + "." + extension;
        Path target = directory.resolve(name).normalize();

        /* Belt and braces: even with a generated name, refuse anything that
           resolves outside the upload root. */
        if (!target.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid upload path");
        }

        try {
            Files.createDirectories(directory);
            Files.write(target, data, java.nio.file.StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not save the photograph", e);
        }

        return name;
    }

    public org.springframework.core.io.Resource resource(String name, boolean privatePhoto) {
        if (name == null || !name.matches("[a-zA-Z0-9_-]+\\.(jpg|jpeg|png|webp|gif)")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photograph not found");
        }
        Path target = (privatePhoto ? root.resolve("private") : root).resolve(name).normalize();
        if (!Files.isRegularFile(target) || Files.isSymbolicLink(target)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photograph not found");
        }
        return new org.springframework.core.io.FileSystemResource(target);
    }

    /** Remove only the files allocated for a failed submission. */
    public void deleteStored(String publicUrl, String privateName) {
        try {
            if (publicUrl != null) Files.deleteIfExists(root.resolve(publicUrl.substring("/uploads/".length())));
            if (privateName != null) Files.deleteIfExists(root.resolve("private").resolve(privateName));
        } catch (IOException ex) {
            org.slf4j.LoggerFactory.getLogger(StorageService.class).warn("Could not clean up a failed upload", ex);
        }
    }

    private static String detectType(byte[] data) {
        if (data.length < 12) return "";
        if ((data[0] & 255) == 255 && (data[1] & 255) == 216 && (data[2] & 255) == 255) return "image/jpeg";
        if (java.util.Arrays.equals(java.util.Arrays.copyOf(data, 8),
                new byte[] {(byte)137, 80, 78, 71, 13, 10, 26, 10})) return "image/png";
        String head = new String(data, 0, 12, java.nio.charset.StandardCharsets.ISO_8859_1);
        if (head.startsWith("GIF87a") || head.startsWith("GIF89a")) return "image/gif";
        if (head.startsWith("RIFF") && head.substring(8).equals("WEBP")) return "image/webp";
        return "";
    }

    private static void validateImage(byte[] data, String type) {
        // The JDK has no WebP decoder; validate its container and chunk signature.
        if (type.equals("image/webp")) {
            if (data.length < 30) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid WebP image");
            String chunk = new String(data, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
            long declared = Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(data, 4, 4)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt()) + 8;
            if (!Set.of("VP8 ", "VP8L", "VP8X").contains(chunk) || declared != data.length) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid WebP image");
            }
            return;
        }
        try (var input = javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(data))) {
            var readers = javax.imageio.ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported image");
            var reader = readers.next();
            try {
                reader.setInput(input);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > 20_000_000 || pixels < 1) throw new IOException("Image dimensions too large");
                if (reader.read(0) == null) throw new IOException("Invalid image");
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a valid image of at most 20 megapixels", ex);
        }
    }

}
