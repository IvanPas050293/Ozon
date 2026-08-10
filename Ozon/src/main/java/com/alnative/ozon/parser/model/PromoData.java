package com.alnative.ozon.parser.model;

import java.util.List;

/**
 * Данные файла «Аналитика продвижения»: статистика рекламных кампаний.
 *
 * @param periodStart   начало периода
 * @param periodEnd     конец периода
 * @param totalExpense  суммарный расход на продвижение (колонка «Расход, ₽» листа Statistics)
 * @param rows          строки статистики
 * @param unionRows     строки листа Union (связка SKU продвижения → SKU объединённой карточки)
 */
public record PromoData(
        String periodStart,
        String periodEnd,
        double totalExpense,
        List<PromoStatRow> rows,
        List<PromoUnionRow> unionRows
) {

    /** Одна строка листа Statistics. */
    public record PromoStatRow(
            String sku,
            String name,
            String tool,
            String placement,
            String campaignId,
            double expense,
            double drr,
            double sales,
            double soldUnits,
            double ctr,
            double impressions,
            double clicks,
            double cartAdds,
            double cartConversion,
            double costPerOrder,
            double costPerClick
    ) {
    }

    /** Одна строка листа Union. */
    public record PromoUnionRow(
            String sku,
            String name,
            String tool,
            String placement,
            String campaignId,
            String unionSku,
            String unionName,
            double sales,
            double soldUnits
    ) {
    }
}
