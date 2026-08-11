package com.alnative.ozon.calc;

/**
 * Статистика по одному товару за период: сколько принёс выручки за минусом всех
 * расходов — за 1 шт и за все проданные, плюс маржа, налог и расход на рекламу.
 *
 * @param sku            SKU товара
 * @param name           название товара
 * @param qty            продано штук за период (нетто: выкупы − возвраты)
 * @param revenue        выручка, ₽
 * @param sebestoimost   себестоимость проданного, ₽
 * @param expenses       все расходы (удержания Ozon + нераспознанные), ₽
 * @param profit         прибыль за все проданные, ₽
 * @param profitPerUnit  прибыль за 1 шт, ₽
 * @param margin         маржа, доли (прибыль / выручка)
 * @param tax            налог по товару, ₽ (доля налога магазина пропорционально выручке)
 * @param promoExpense   расход на продвижение по товару, ₽ (из аналитики)
 * @param costSet        задана ли себестоимость товара
 */
public record ProductStat(
        String sku,
        String name,
        double qty,
        double revenue,
        double sebestoimost,
        double expenses,
        double profit,
        double profitPerUnit,
        double margin,
        double tax,
        double promoExpense,
        boolean costSet
) {
}
