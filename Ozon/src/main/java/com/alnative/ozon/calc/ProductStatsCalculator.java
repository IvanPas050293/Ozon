package com.alnative.ozon.calc;

import com.alnative.ozon.catalog.ProductCostService;
import com.alnative.ozon.parser.model.Accrual;
import com.alnative.ozon.parser.model.PromoData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Расчёт «статистики по товарам», повторяющий «Отчёт по товарам» Ozon.
 * <p>
 * Для каждого SKU:
 * <ul>
 *   <li>выручка (revenue) = продажи + бонусы (типы «Выручка», «Механики лояльности»,
 *       «Программы партнёров», «Баллы за скидки») — для маржи;</li>
 *   <li>налоговая база = «Выручка» + «Программы партнёров» + «Возврат выручки» (баллы за скидки НЕ входят);</li>
 *   <li>чистый поток (total) = все строки по SKU (доходы и расходы начислений);</li>
 *   <li>налог = база × ставка (не может быть отрицательным);</li>
 *   <li>прибыль = чистый поток − себестоимость − налог − расход на рекламу (из файла продвижения).</li>
 * </ul>
 */
public class ProductStatsCalculator {

    /** Продажи и бонусы — выручка товара (для маржи). */
    private static final SetType SALE_TYPES = new SetType("Выручка", "Механики лояльности",
            "Программы партнёров", "Баллы за скидки", "За продажу или возврат до вычета комиссий и услуг");

    /** Доходы, входящие в налоговую базу (УСН Доходы): выручка, программы партнёров, возврат выручки. */
    private static final SetType TAX_BASE_TYPES = new SetType(
            "Выручка", "Программы партнёров", "Возврат выручки");

    /**
     * «Общие» сервисные удержания, которые Ozon не относит к прибыли товара
     * (не входят в колонку «Другие удержания OZON» отчёта по товарам):
     * подписки на сервисы, доставка силами Ozon, обеспечение/упаковка.
     */
    private static final SetType EXCLUDED_TYPES = new SetType(
            "Подписка Управление отзывами",
            "Доставка до места выдачи силами Ozon",
            "Обеспечение материалами для упаковки",
            "Упаковка товара партнёрами");

    private final ProductCostService costProvider;

    public ProductStatsCalculator(ProductCostService costProvider) {
        this.costProvider = costProvider;
    }

    /**
     * @param ownerChatId chat_id магазина (для себестоимостей его каталога)
     * @param accruals    строки начислений (привязка по SKU)
     * @param promoRows   строки статистики продвижения (расход на рекламу по SKU)
     * @param taxRate     налоговая ставка (доля, например 0.06)
     */
    public List<ProductStat> calculate(Long ownerChatId, List<Accrual> accruals,
                                       List<PromoData.PromoStatRow> promoRows,
                                       double taxRate) {
        Map<String, Acc> bySku = new LinkedHashMap<>();
        for (Accrual a : accruals) {
            String sku = a.sku();
            if (sku == null || sku.isBlank()) {
                continue; // строки без SKU (общие услуги, реклама) к товарам не относятся
            }
            String type = a.type() == null ? "" : a.type();
            if (EXCLUDED_TYPES.contains(type)) {
                continue; // общие сервисные удержания не относятся к прибыли товара
            }
            Acc acc = bySku.computeIfAbsent(sku, k -> new Acc(firstNonBlank(a.name(), sku)));
            double total = a.total();
            double qty = a.qty();
            acc.total += total;                      // весь поток по товару (доходы + расходы)
            if (SALE_TYPES.contains(type)) {
                acc.revenue += total;                // продажи и бонусы — для маржи
            }
            if (TAX_BASE_TYPES.contains(type)) {
                acc.taxBase += total;                // налоговая база (баллы за скидки не входят)
            }
            if (isSaleRow(a) && total > 0) {
                acc.qty += qty;                      // продано шт
            } else if (isReturnRow(a)) {
                acc.qty -= qty;                      // возвраты шт
            }
            if (type.contains("Утилизация")) {
                acc.qty += qty;
            }
        }

        // Расход на рекламу по SKU — из файла «Аналитика продвижения».
        Map<String, Double> promoBySku = new HashMap<>();
        for (PromoData.PromoStatRow r : promoRows) {
            if (r.sku() != null && !r.sku().isBlank()) {
                promoBySku.merge(r.sku(), r.expense(), Double::sum);
            }
        }

        List<ProductStat> result = new ArrayList<>();
        for (Map.Entry<String, Acc> e : bySku.entrySet()) {
            Acc acc = e.getValue();
            if (acc.qty <= 0 && acc.revenue == 0 && acc.total == 0) {
                continue; // не было продаж — в статистику не включаем
            }
            String sku = e.getKey();
            double seb = costProvider.costOf(ownerChatId, sku) * acc.qty;
            double tax = acc.taxBase > 0 ? acc.taxBase * taxRate : 0; // налог не может быть отрицательным
            double promo = promoBySku.getOrDefault(sku, 0.0);
            double profit = acc.total - seb - tax - promo;
            double profitPerUnit = acc.qty == 0 ? 0 : profit / acc.qty;
            double margin = acc.revenue == 0 ? 0 : profit / acc.revenue;
            double expenses = acc.total - acc.revenue; // удержания и возвраты (не продажи)
            result.add(new ProductStat(
                    sku, acc.name, acc.qty, acc.revenue,
                    seb, expenses, profit, profitPerUnit, margin,
                    tax, promo, costProvider.hasCost(ownerChatId, sku)));
        }
        result.sort(Comparator.comparingDouble(ProductStat::revenue).reversed());
        return result;
    }

    private static String firstNonBlank(String a, String b) {
        return a == null || a.isBlank() ? b : a;
    }

    private static boolean isSaleRow(Accrual a) {
        String type = a.type() == null ? "" : a.type();
        return type.equals("Выручка")
                || type.equals("За продажу или возврат до вычета комиссий и услуг") && a.total() > 0;
    }

    private static boolean isReturnRow(Accrual a) {
        String type = a.type() == null ? "" : a.type();
        return type.equals("Возврат выручки")
                || type.equals("Выручка") && a.total() < 0;
    }

    /** Простая обёртка над Set для читаемости. */
    private static final class SetType {
        private final java.util.Set<String> values;

        SetType(String... vals) {
            this.values = java.util.Set.of(vals);
        }

        boolean contains(String s) {
            return values.contains(s);
        }
    }

    private static final class Acc {
        final String name;
        double total;    // чистый поток: все строки по SKU
        double revenue;  // продажи и бонусы (для маржи)
        double taxBase;  // налоговая база
        double qty;      // продано шт (нетто)

        Acc(String name) {
            this.name = name;
        }
    }
}
