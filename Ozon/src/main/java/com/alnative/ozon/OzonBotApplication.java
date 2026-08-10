package com.alnative.ozon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Точка входа Telegram-бота «Экономика магазина Ozon».
 * Принимает от пользователя два xlsx-отчёта (начисления и продвижение),
 * парсит их и выдаёт сводные метрики дашборда.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class OzonBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(OzonBotApplication.class, args);
    }
}
