package com.example.bookcard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.image.storage-path:uploads/images}")
    private String storagePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 절대 경로로 변환
        String absolutePath = Paths.get(storagePath).toAbsolutePath().normalize().toString();

        // Serve images from local storage
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + absolutePath + "/");
    }
}
