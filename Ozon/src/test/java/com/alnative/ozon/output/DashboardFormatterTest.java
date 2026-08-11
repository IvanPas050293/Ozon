package com.alnative.ozon.output;

import com.alnative.ozon.calc.DashboardMetrics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardFormatterTest {

    @Test
    void formatsReferenceMetrics() {
        DashboardMetrics m = new DashboardMetrics(
                "01.08.2026", "10.08.2026",
                155, 1, 11, 154, 0.9277, 3283.29,
                507316, -1690, 505626, 99065, -206750.63, -4300.77,
                -16351.93, -137266.70, -360369.26, 145256.74,
                259972.31, 15598.34, 6, 30593.40, 0.0605, 0.3088,
                127729.47, 0.2518, 1417);

        String text = new DashboardFormatter().format(m);

        assertTrue(text.contains("Налог (6%): 15 598 ₽"));
        assertTrue(text.contains("01.08.2026 — 10.08.2026"));
        assertTrue(text.contains("Выручка: 505 626 ₽"));
        assertTrue(text.contains("Комиссия Ozon: -206 751 ₽"));
        assertTrue(text.contains("Всего удержаний OZON: -360 369 ₽"));
        assertTrue(text.contains("Оплата на р/с: 145 257 ₽"));
        assertTrue(text.contains("Чистая прибыль: 30 593 ₽"));
        assertTrue(text.contains("Маржинальность: 6,1 %"));
        assertTrue(text.contains("ДРР по магазину: 25,2 %"));
    }
}
