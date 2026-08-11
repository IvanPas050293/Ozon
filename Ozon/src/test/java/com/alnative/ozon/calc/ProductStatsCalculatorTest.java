package com.alnative.ozon.calc;

import com.alnative.ozon.catalog.ProductCostService;
import com.alnative.ozon.parser.NagruzheniyaParser;
import com.alnative.ozon.parser.PromoParser;
import com.alnative.ozon.parser.model.NagruzheniyaReport;
import com.alnative.ozon.parser.model.PromoData;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * «Статистика по товарам» на реальных отчётах: прибыль за 1 шт и за все проданные,
 * маржа, налог (доля от налога магазина) и расход на рекламу по каждому SKU.
 */
class ProductStatsCalculatorTest {

    private static final long SHOP = 111L;

    private static final Map<String, Double> COSTS = Map.of(
            "1792723847", 260.0,
            "1901934201", 155.0,
            "2269055805", 1200.0,
            "3128882913", 155.0,
            "3753195826", 550.0,
            "4406356192", 1000.0,
            "4406190024", 1200.0,
            "5276856672", 600.0
    );

    private static Path sample(String name) {
        URL url = ProductStatsCalculatorTest.class.getResource("/samples/" + name);
        assertNotNull(url, "Не найден тестовый файл " + name);
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static ProductCostService costs() {
        ProductCostService costs = mock(ProductCostService.class);
        when(costs.costOf(eq(SHOP), any())).thenReturn(0.0);
        when(costs.hasCost(eq(SHOP), any())).thenReturn(false);
        COSTS.forEach((sku, cost) -> {
            when(costs.costOf(SHOP, sku)).thenReturn(cost);
            when(costs.hasCost(SHOP, sku)).thenReturn(true);
        });
        return costs;
    }

    @Test
    void calculatesProductStats() throws Exception {
        NagruzheniyaReport report = new NagruzheniyaParser().parse(sample("Отчет по начислениям_01.08.2026-10.08.2026.xlsx"));
        PromoData promo = new PromoParser().parse(sample("Аналитика продвижения_10.08.2026.xlsx"));

        List<ProductStat> stats = new ProductStatsCalculator(costs())
                .calculate(SHOP, report.accruals(), promo.rows(), 0.06);

        assertFalse(stats.isEmpty());
        // Сортировка по выручке убыванию.
        for (int i = 1; i < stats.size(); i++) {
            assertTrue(stats.get(i - 1).revenue() >= stats.get(i).revenue());
        }
        for (ProductStat s : stats) {
            // Прибыль за 1 шт = прибыль за все / количество.
            assertEquals(s.profitPerUnit(), s.profit() / s.qty(), 0.01);
            // Маржа = прибыль / выручка.
            assertEquals(s.margin(), s.profit() / s.revenue(), 0.0001);
            // Прибыль = чистый поток (выручка+расходы) − себестоимость − налог − реклама.
            double expectedProfit = s.revenue() + s.expenses() - s.sebestoimost() - s.tax() - s.promoExpense();
            assertEquals(expectedProfit, s.profit(), 0.01);
            // Налог неотрицательный.
            assertTrue(s.tax() >= 0, s.sku());
        }

        // Товары с известной себестоимостью помечаются costSet=true, остальные — нет.
        assertTrue(stats.stream().anyMatch(ProductStat::costSet));
        for (ProductStat s : stats) {
            if (COSTS.containsKey(s.sku())) {
                assertTrue(s.costSet(), s.sku());
            }
        }
    }

    @Test
    void promoExpenseIsAttributedToSku() throws Exception {
        NagruzheniyaReport report = new NagruzheniyaParser().parse(sample("Отчет по начислениям_01.08.2026-10.08.2026.xlsx"));
        PromoData promo = new PromoParser().parse(sample("Аналитика продвижения_10.08.2026.xlsx"));

        double totalPromo = promo.totalExpense();
        List<ProductStat> stats = new ProductStatsCalculator(costs())
                .calculate(SHOP, report.accruals(), promo.rows(), 0);

        double sumPromo = stats.stream().mapToDouble(ProductStat::promoExpense).sum();
        // Расход по товарам может быть меньше totalExpense (строки без SKU / объединённые карточки),
        // но должен быть положительным, если реклама была.
        assertTrue(sumPromo > 0 && sumPromo <= totalPromo + 0.01);
        assertTrue(stats.stream().anyMatch(s -> s.promoExpense() > 0));
    }
}
