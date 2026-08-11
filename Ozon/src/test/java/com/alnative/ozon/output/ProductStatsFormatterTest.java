package com.alnative.ozon.output;

import com.alnative.ozon.calc.ProductStat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductStatsFormatterTest {

    private static final String NBSP = " ";

    @Test
    void formatsProductLines() {
        List<ProductStat> stats = List.of(
                new ProductStat("123", "Триммер", 70, 70_000, 20_000, -40_000,
                        56_000, 800, 0.8, 10_000, 20_000, true),
                new ProductStat("456", "Футболка", 3, 3_000, 0, -1_500,
                        1_000, 333.33, 0.3333, 500, 0, false));

        String text = new ProductStatsFormatter().format("01.08.2026", "10.08.2026", stats);

        assertTrue(text.contains("📊 Статистика по товарам"));
        assertTrue(text.contains("Период: 01.08.2026 — 10.08.2026"));
        assertTrue(text.contains("📦 Триммер · SKU 123"));
        assertTrue(text.contains("🛒 1 шт — 800 ₽ · 70 шт — 56" + NBSP + "000 ₽"));
        assertTrue(text.contains("💰 Маржа 80,0 % · Налог 10" + NBSP + "000 ₽ · Реклама 20" + NBSP + "000 ₽"));
        assertTrue(text.contains("📦 Футболка · SKU 456"));
        assertTrue(text.contains("себестоимость не задана"));
    }

    @Test
    void emptyStats() {
        String text = new ProductStatsFormatter().format("01.08.2026", "10.08.2026", List.of());
        assertTrue(text.contains("Нет данных о продажах"));
        assertFalse(text.contains("SKU"));
    }
}
