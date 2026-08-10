package com.alnative.ozon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Бизнес-настройки расчёта дашборда.
 *
 * @param taxSystem  система налогообложения: 1=УСН Доходы, 2=УСН Доходы-Расходы, 3=не считать налог, 4=от оплаты на Р/С
 * @param taxRate    налоговая ставка (например, 0.06 для УСН Доходы)
 * @param allowedChatIds разрешённые chat_id (пусто = открыт всем)
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        int taxSystem,
        double taxRate,
        String allowedChatIds
) {
}
