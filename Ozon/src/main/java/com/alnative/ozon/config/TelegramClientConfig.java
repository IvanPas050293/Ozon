package com.alnative.ozon.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Telegram-клиент для отправки сообщений (одна инстанция на токен).
 */
@Configuration
public class TelegramClientConfig {

    @Bean
    public TelegramClient telegramClient(BotProperties props) {
        return new OkHttpTelegramClient(props.token());
    }
}
