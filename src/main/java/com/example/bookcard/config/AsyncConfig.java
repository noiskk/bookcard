package com.example.bookcard.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * SSE(Server-Sent Events) 비동기 처리를 위한 스레드풀 설정
 *
 * 북카드 생성 요청은 AI 호출로 약 21초가 소요된다.
 * 이를 별도 스레드에서 실행해 HTTP 요청 스레드를 블로킹하지 않고
 * SSE 스트림으로 진행 상황을 실시간 전달한다.
 *
 * CallerRunsPolicy: 대기 큐가 가득 찬 경우 요청 스레드(Tomcat 스레드)에서
 * 직접 실행해 요청을 자연스럽게 늦추는 배압(backpressure) 효과를 낸다.
 */
@Configuration
@Slf4j
public class AsyncConfig {

    /**
     * SSE 전용 스레드풀
     * - corePoolSize 3: 평상시 유지할 최소 스레드 수 (동시 생성 3건 기본 처리)
     * - maximumPoolSize 10: 최대 스레드 수 (피크 시 최대 10건 동시 처리)
     * - keepAliveTime 60초: 유휴 스레드가 60초 후 종료
     * - 대기 큐 20: 대기 가능한 최대 요청 수
     * - destroyMethod "shutdown": 애플리케이션 종료 시 스레드풀 정리
     */
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
