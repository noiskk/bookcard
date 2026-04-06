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

/**
 * 이미지 파일 저장 서비스
 *
 * Gemini API가 Base64 바이너리로 반환한 이미지를 로컬 파일시스템에 저장하고,
 * WebConfig의 리소스 핸들러를 통해 /images/** URL로 서빙할 수 있도록 관리한다.
 *
 * 파일명: UUID 기반으로 생성해 중복을 방지한다.
 * 저장 경로: app.image.storage-path (기본값: uploads/images)
 * 접근 URL: app.image.base-url (기본값: /images)
 */
@Service
@Slf4j
public class ImageStorageService {

    @Value("${app.image.storage-path:uploads/images}")
    private String storagePath;

    @Value("${app.image.base-url:/images}")
    private String baseUrl;

    /** 정규화된 절대 경로의 저장 디렉토리 */
    private Path storageLocation;

    /**
     * 빈 초기화 시 저장 디렉토리 생성
     * 디렉토리가 없으면 생성하고, 생성 실패 시 애플리케이션 기동을 중단한다.
     */
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
     * 외부 URL의 이미지를 다운로드해 로컬에 저장
     *
     * @param imageUrl 다운로드할 외부 이미지 URL
     * @return 저장된 이미지의 로컬 URL 경로 (예: /images/uuid.png)
     *         다운로드 실패 시 원본 URL을 폴백으로 반환
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
            return imageUrl; // 다운로드 실패 시 원본 URL 반환 (폴백)
        }
    }

    /**
     * Base64로 인코딩된 이미지 데이터를 디코딩해 로컬에 저장
     *
     * Gemini API는 이미지를 URL이 아닌 Base64 바이너리로 반환하기 때문에
     * 이 메서드로 파일에 직접 쓴다.
     *
     * @param base64Data Base64로 인코딩된 이미지 데이터
     * @param mimeType   이미지 MIME 타입 (예: "image/png", "image/jpeg")
     * @return 저장된 이미지의 로컬 URL 경로 (예: /images/uuid.png)
     *         저장 실패 시 null 반환
     */
    public String saveBase64Image(String base64Data, String mimeType) {
        if (base64Data == null || base64Data.isEmpty()) {
            return null;
        }

        try {
            // MIME 타입에 따라 파일 확장자 결정
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
     * 로컬에 저장된 이미지 파일 삭제
     *
     * 북카드 삭제 시 연결된 생성 이미지 파일도 함께 제거하기 위해 호출된다.
     * baseUrl로 시작하지 않는 외부 URL은 삭제하지 않는다.
     *
     * @param imageUrl 삭제할 이미지의 로컬 URL 경로 (예: /images/uuid.png)
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
