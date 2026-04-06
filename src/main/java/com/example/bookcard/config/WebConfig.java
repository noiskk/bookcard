package com.example.bookcard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 정적 리소스 핸들러 설정
 *
 * Gemini가 생성한 이미지를 로컬 파일시스템에 저장하고,
 * /images/** URL로 서빙할 수 있도록 리소스 경로를 매핑한다.
 *
 * Gemini는 URL이 아닌 Base64 바이너리로 이미지를 반환하기 때문에
 * ImageStorageService가 로컬 파일로 저장한 후 이 핸들러를 통해 접근한다.
 *
 * 예: /images/550e8400-e29b-41d4-a716-446655440000.png
 *  → uploads/images/550e8400-e29b-41d4-a716-446655440000.png (로컬 파일)
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.image.storage-path:uploads/images}")
    private String storagePath;

    /**
     * /images/** 요청을 로컬 스토리지 디렉토리로 매핑
     * 절대 경로를 사용해 현재 작업 디렉토리에 관계없이 동일하게 동작한다.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 절대 경로로 변환
        String absolutePath = Paths.get(storagePath).toAbsolutePath().normalize().toString();

        // 로컬 파일시스템의 이미지 파일을 HTTP로 서빙
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + absolutePath + "/");
    }
}
