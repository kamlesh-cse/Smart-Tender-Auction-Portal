package com.smartportal.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/**
 * SEC-7, SEC-8: Centralised, hardened file upload utility.
 *
 * <p>Defences applied:
 * <ol>
 *   <li>Path traversal prevention — filename is sanitised; resolved path is verified
 *       to remain inside the upload root.</li>
 *   <li>Magic-byte MIME detection — {@link Files#probeContentType} checks the actual
 *       file bytes, not the client-supplied Content-Type header.</li>
 *   <li>Randomised storage name — the original filename is never used as the on-disk
 *       name; a UUID prefix makes enumeration impossible.</li>
 * </ol>
 */
public final class FileUploadUtils {

    private FileUploadUtils() {}

    /**
     * Saves {@code file} under {@code uploadDir} after validating MIME type.
     *
     * @param file          incoming multipart file
     * @param uploadDir     absolute path to the upload root directory
     * @param allowedMimes  set of allowed MIME types (magic-byte detected)
     * @return              the stored filename (relative to {@code uploadDir})
     * @throws IllegalArgumentException if MIME type is not in the allowed set
     * @throws IOException              on IO failure
     */
    public static String save(MultipartFile file, String uploadDir, Set<String> allowedMimes)
            throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty.");
        }

        // --- 1. Sanitise the original filename (SEC-7) ----------------------------
        String originalName = file.getOriginalFilename();
        String safeSuffix = sanitise(originalName);

        // --- 2. Build a UUID-prefixed storage name --------------------------------
        String storageName = UUID.randomUUID() + "_" + safeSuffix;

        // --- 3. Ensure upload directory exists ------------------------------------
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(root);

        // --- 4. Resolve the target path and verify it stays inside root (SEC-7) --
        Path target = root.resolve(storageName).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Path traversal attempt detected.");
        }

        // --- 5. Write bytes to a temp location, probe MIME, then move (SEC-8) ----
        Path temp = Files.createTempFile("upload_", "_tmp");
        try {
            file.transferTo(temp);
            String detectedMime = Files.probeContentType(temp);
            if (detectedMime == null || !allowedMimes.contains(detectedMime)) {
                throw new IllegalArgumentException(
                        "File type not allowed. Detected: " + detectedMime);
            }
            Files.move(temp, target,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }

        return storageName;
    }

    /**
     * Strips path separators and any character that is not alphanumeric, dot, hyphen,
     * or underscore, then truncates to 80 characters. Returns {@code "upload"} for blank names.
     */
    private static String sanitise(String name) {
        if (name == null || name.isBlank()) {
            return "upload";
        }
        // Remove any directory components
        int lastSep = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String basename = lastSep >= 0 ? name.substring(lastSep + 1) : name;
        // Keep only safe characters
        String clean = basename.replaceAll("[^A-Za-z0-9._\\-]", "_");
        // Truncate
        return clean.length() > 80 ? clean.substring(0, 80) : clean;
    }
}
