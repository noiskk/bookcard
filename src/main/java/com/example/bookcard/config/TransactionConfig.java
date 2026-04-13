package com.example.bookcard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 트랜잭션 설정
 *
 * 프로그래밍적 트랜잭션 관리가 필요한 경우 사용하는 TransactionTemplate 빈을 등록한다.
 * 선언적(@Transactional)으로는 트랜잭션 범위를 세밀하게 제어할 수 없는 경우,
 * 예를 들어 장시간 외부 API 호출과 짧은 DB 작업을 분리해야 할 때 사용한다.
 */
@Configuration
public class TransactionConfig {

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
