package com.example.bookcard.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI ChatClient 빈 설정
 *
 * OpenAI API와의 통신을 추상화하는 ChatClient를 빈으로 등록한다.
 * application.properties의 spring.ai.openai.api-key를 읽어 자동 구성된 ChatModel을 주입받는다.
 */
@Configuration
public class AiConfig {

    /**
     * ChatClient 빈 생성
     * OpenAiService에서 프롬프트 체이닝에 사용한다.
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
