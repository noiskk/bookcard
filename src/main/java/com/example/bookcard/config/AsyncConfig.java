package com.example.bookcard.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class AsyncConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService sseExecutorService() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                3,                              // corePoolSize: 기본 유지 스레드 수
                10,                             // maximumPoolSize: 최대 스레드 수
                60L, TimeUnit.SECONDS,          // 유휴 스레드 종료 대기
                new LinkedBlockingQueue<>(20),  // 대기 큐 (초과 시 CallerRunsPolicy 적용)
                new ThreadPoolExecutor.CallerRunsPolicy() // 큐 꽉 차면 요청 스레드에서 직접 실행
        );
        log.info("SSE ThreadPool 초기화 — core: {}, max: {}, queue: {}",
                executor.getCorePoolSize(), executor.getMaximumPoolSize(), 20);
        return executor;
    }
}
