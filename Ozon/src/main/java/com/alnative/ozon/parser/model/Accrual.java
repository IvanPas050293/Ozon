package com.alnative.ozon.parser.model;

import java.time.LocalDate;

/**
 * Одна строка отчёта «Отчет по начислениям» (финансы → экономика магазина).
 * Соответствует строке данных xlsx-файла.
 *
 * @param id            ID начисления
 * @param date          Дата начисления
 * @param group         Группа услуг (Продажи, Услуги доставки, Вознаграждение Ozon, …)
 * @param type          Тип начисления (Выручка, Логистика, Оплата за клик, …)
 * @param artikul       Артикул продавца
 * @param sku           SKU товара (число из отчёта приводится к строке без экспоненты)
 * @param name          Название товара или услуги
 * @param qty           Количество
 * @param sellerPrice   Цена продавца
 * @param acceptDate    Дата принятия заказа в обработку или оказания услуги
 * @param platform      Платформа продажи (Ozon)
 * @param scheme        Схема работы (FBO)
 * @param rewardPct     Вознаграждение Ozon, %
 * @param total         Сумма итого, руб. (с учётом знака: расход — отрицательная)
 */
public record Accrual(
        String id,
        LocalDate date,
        String group,
        String type,
        String artikul,
        String sku,
        String name,
        double qty,
        double sellerPrice,
        LocalDate acceptDate,
        String platform,
        String scheme,
        double rewardPct,
        double total
) {
}
