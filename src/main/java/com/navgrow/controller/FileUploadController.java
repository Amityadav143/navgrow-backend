/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.controller;

import com.navgrow.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Direct file uploads for the admin panel (product photos, gallery images,
 * article covers, tender documents, …). Files are stored on local disk under
 * {@code app.upload-dir} and served back at {@code /uploads/**} (see
 * {@link com.navgrow.config.WebConfig}).
 *
 * The returned {@code url} is absolute (built from the current request), so it
 * works unchanged from the Vite dev server, behind nginx in production, and in
 * emails.
 */
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Slf4j
public class FileUploadController {

    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final Set<String> DOC_EXT   = Set.of("pdf");
    private static final long MAX_BYTES = 8L * 1024 * 1024; // 8 MB

    private final com.navgrow.service.AuditService audit;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @PostMapping("/upload")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('EDITOR')")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new BadRequestException("No file received. Choose a file to upload.");
        if (file.getSize() > MAX_BYTES)     throw new BadRequestException("File is too large. Maximum size is 8 MB.");

        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String ext = extensionOf(original);
        boolean isImage = IMAGE_EXT.contains(ext);
        boolean isDoc   = DOC_EXT.contains(ext);
        if (!isImage && !isDoc)
            throw new BadRequestException("Unsupported file type '." + ext + "'. Allowed: "
                + String.join(", ", IMAGE_EXT) + ", " + String.join(", ", DOC_EXT) + ".");

        // Read the upload ONCE into memory. A MultipartFile's InputStream must not be
        // consumed twice: validating with ImageIO.read(getInputStream()) and then
        // copying from getInputStream() again can write a 0-byte / corrupt file for
        // small (in-memory) uploads. Validate and persist from the same byte[].
        byte[] bytes = file.getBytes();
        if (bytes.length == 0) throw new BadRequestException("The uploaded file is empty.");

        // Content sniffing — the extension alone is not trusted.
        if (isImage) {
            java.awt.image.BufferedImage img =
                ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (img == null)
                throw new BadRequestException("The file does not appear to be a valid image.");
        } else {
            if (bytes.length < 5
                || !"%PDF-".equals(new String(Arrays.copyOfRange(bytes, 0, 5),
                                              java.nio.charset.StandardCharsets.US_ASCII)))
                throw new BadRequestException("The file does not appear to be a valid PDF.");
        }

        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String storedName = UUID.randomUUID() + "." + ext;
        Path target = dir.resolve(storedName).normalize();
        if (!target.startsWith(dir)) throw new BadRequestException("Invalid file name.");
        Files.write(target, bytes, StandardOpenOption.CREATE_NEW);

        // Build an absolute URL from the *incoming request* so it points at the
        // API's public host (e.g. https://api.navgrow.org/api/uploads/<name>).
        // nginx forwards Host + X-Forwarded-Proto, so this resolves correctly in
        // production; it also works on the Vite dev server. We build it explicitly
        // from forwarded headers to be robust behind the proxy.
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/uploads/").path(storedName).toUriString();

        log.info("File uploaded: {} ({} bytes) -> {}", original, bytes.length, storedName);
        audit.log("FILE_UPLOAD", "File", storedName, original);
        return ResponseEntity.ok(Map.of(
            "url", url,
            "fileName", storedName,
            "originalName", original,
            "size", bytes.length,
            "kind", isImage ? "image" : "document"
        ));
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT).trim();
    }
}
