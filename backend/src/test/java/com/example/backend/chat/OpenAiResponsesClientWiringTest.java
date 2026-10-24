package com.example.backend.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.backend.quote.TossPriceConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class OpenAiResponsesClientWiringTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ChatConfiguration.class, TossPriceConfiguration.class, OpenAiResponsesClient.class)
            .withBean(ObjectMapper.class)
            .withPropertyValues(
                    "OPENAI_API_KEY=test-key",
                    "app.ai.chat.base-url=https://api.openai.com/v1",
                    "app.ai.chat.timeout-seconds=5",
                    "app.ai.chat.model=test-model",
                    "app.ai.chat.reasoning-effort=medium",
                    "app.ai.chat.max-output-tokens=100",
                    "app.toss.base-url=https://openapi.tossinvest.com");

    @Test
    void createsOpenAiClientWhenBothRestClientsExist() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("openAiRestClient");
            assertThat(context).hasBean("tossPriceRestClient");
            assertThat(context.getBeansOfType(RestClient.class)).hasSize(2);
            assertThat(context).hasSingleBean(OpenAiResponsesClient.class);
        });
    }
}
