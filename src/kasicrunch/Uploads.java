/*
 * File:    Uploads.java
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Saves photos uploaded by the admin for menu items. The browser
 *          sends the picture as a base64 "data URL"; this class checks that
 *          it really is a JPEG, PNG or WebP image (by its first bytes, not
 *          just its name), limits the size, and stores it under a random
 *          file name in public/uploads so it can be served to the website.
 */
package kasicrunch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public final class Uploads {

    /** Parameter: largest photo accepted, in bytes (2 MB). */
    public static final int MAX_PHOTO_BYTES = 2 * 1024 * 1024;
    /** Room for the base64 text (about 4/3 of the photo) plus the other form fields. */
    public static final int MAX_REQUEST_BYTES = MAX_PHOTO_BYTES * 2;

    private static final Path FOLDER = Path.of("public", "uploads");

    private Uploads() {
    }

    /**
     * Decodes and saves a data URL such as "data:image/jpeg;base64,...".
     * Returns the web path of the saved file, e.g. "uploads/3f9c....jpg".
     */
    public static String savePhoto(String dataUrl) throws IOException {
        if (dataUrl == null || !dataUrl.startsWith("data:image/") || !dataUrl.contains(";base64,")) {
            throw ApiException.badRequest("Please choose a photo (JPG, PNG or WebP).");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(",") + 1));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("That photo could not be read. Please try another image.");
        }
        if (bytes.length > MAX_PHOTO_BYTES) {
            throw ApiException.badRequest("That photo is too large. Please choose an image under 2 MB.");
        }
        String extension = detectExtension(bytes);
        if (extension == null) {
            throw ApiException.badRequest("Only JPG, PNG or WebP photos can be uploaded.");
        }
        Files.createDirectories(FOLDER);
        String name = PasswordHasher.newToken().substring(0, 24) + "." + extension;
        Files.write(FOLDER.resolve(name), bytes);
        return "uploads/" + name;
    }

    /** Recognises the image type from its "magic number" (the first few bytes). */
    static String detectExtension(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpg";
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "png";
        if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
        return null;
    }
}
