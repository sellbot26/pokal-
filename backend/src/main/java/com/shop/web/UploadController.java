package com.shop.web;

import com.shop.config.ShopProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UploadController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp");

    private final ShopProperties props;

    /** Bild-Upload für Produkte/Embeds — jeder eingeloggte Nutzer darf hochladen, nicht nur der Site-Admin. */
    @PostMapping("/api/upload")
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("Keine Datei erhalten.");
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase()
                : "";
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Nur Bilder erlaubt (" + String.join(", ", ALLOWED_EXTENSIONS) + ").");
        }
        Path dir = Paths.get(props.getUploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String filename = UUID.randomUUID() + "." + ext;
        file.transferTo(dir.resolve(filename));
        // Absolute URL — Discord-Embeds (JDA) akzeptieren nur http(s)/attachment-URLs, keine relativen Pfade
        return Map.of("url", props.getBaseUrl() + "/uploads/" + filename);
    }
}
