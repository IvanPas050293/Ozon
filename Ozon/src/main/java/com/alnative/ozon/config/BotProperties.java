package com.alnative.ozon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки Telegram-бота: имя и токен.
 */
@ConfigurationProperties(prefix = "telegram.bot")
public record BotProperties(
        String username,
        String token
) {
}
