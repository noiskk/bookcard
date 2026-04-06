package com.example.bookcard.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.UUID;

@Service
@Slf4j
public class ImageStorageService {

    @Value("${app.image.storage-path:uploads/images}")
    private String storagePath;

    @Value("${app.image.base-url:/images}")
    private String baseUrl;

    private Path storageLocation;

    @PostConstruct
    public void init() {
        storageLocation = Paths.get(storagePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageLocation);
            log.info("Image storage initialized at: {}", storageLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not create image storage directory", e);
        }
    }

    /**
     * Download image from URL and save locally
     * @param imageUrl The remote image URL (e.g., from OpenAI)
     * @return Local URL path to access the saved image
     */
    public String downloadAndSave(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }

        try {
            String fileName = UUID.randomUUID().toString() + ".png";
            Path targetPath = storageLocation.resolve(fileName);

            log.info("Downloading image from: {}", imageUrl);

            try (InputStream in = new URL(imageUrl).openStream()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            String localUrl = baseUrl + "/" + fileName;
            log.info("Image saved locally: {}", localUrl);

            return localUrl;

        } catch (IOException e) {
            log.error("Failed to download and save image: {}", e.getMessage(), e);
            return imageUrl; // Fallback to original URL
        }
    }

    /**
     * Save base64-encoded image data directly to local storage
     * @param base64Data Base64-encoded image data
     * @param mimeType MIME type (e.g., "image/png")
     * @return Local URL path to access the saved image
     */
    public String saveBase64Image(String base64Data, String mimeType) {
        if (base64Data == null || base64Data.isEmpty()) {
            return null;
        }

        try {
            String extension = mimeType != null && mimeType.contains("jpeg") ? ".jpg" : ".png";
            String fileName = UUID.randomUUID().toString() + extension;
            Path targetPath = storageLocation.resolve(fileName);

            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            Files.write(targetPath, imageBytes);

            String localUrl = baseUrl + "/" + fileName;
            log.info("Base64 image saved locally: {} ({} bytes)", localUrl, imageBytes.length);

            return localUrl;

        } catch (IOException e) {
            log.error("Failed to save base64 image: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Delete an image file
     */
    public void delete(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(baseUrl)) {
            return;
        }

        try {
            String fileName = imageUrl.substring(baseUrl.length() + 1);
            Path filePath = storageLocation.resolve(fileName);
            Files.deleteIfExists(filePath);
            log.info("Deleted image: {}", fileName);
        } catch (IOException e) {
            log.error("Failed to delete image: {}", e.getMessage());
        }
    }
}
