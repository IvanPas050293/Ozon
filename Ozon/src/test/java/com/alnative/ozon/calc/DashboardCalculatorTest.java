package com.alnative.ozon.calc;

import com.alnative.ozon.parser.NagruzheniyaParser;
import com.alnative.ozon.parser.PromoParser;
import com.alnative.ozon.parser.model.Accrual;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Проверяет расчёт дашборда на реальных отчётах против эталонных значений из Google-шаблона
 * (период 01–10.08.2026). Себестоимость задана по SKU, как её вводит пользователь в боте.
 */
class DashboardCalculatorTest {

    private static final double MONEY = 0.01;
    private static final double RATIO = 0.0005;

    private static Path sample(String name) {
        URL url = DashboardCalculatorTest.class.getResource("/samples/" + name);
        assertNotNull(url, "Не найден тестовый файл " + name);
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

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

    @Test
    void matchesDashboardReferenceValues() throws Exception {
        NagruzheniyaReport report = new NagruzheniyaParser().parse(sample("Отчет по начислениям_01.08.2026-10.08.2026.xlsx"));
        PromoData promo = new PromoParser().parse(sample("Аналитика продвижения_10.08.2026.xlsx"));

        List<Accrual> accruals = report.accruals();
        DashboardCalculator calc = new DashboardCalculator((owner, sku) -> COSTS.getOrDefault(sku, 0.0), 1, 0.06);
        DashboardMetrics m = calc.calculate(111L, accruals, promo.totalExpense());

        // Период
        assertEquals("01.08.2026", m.periodStart());
        assertEquals("10.08.2026", m.periodEnd());

        // Заказы и продажи
        assertEquals(155, m.vykupySht());
        assertEquals(1, m.vozvratySht());
        assertEquals(11, m.nevykupSht());
        assertEquals(154, m.prodazhSht());
        assertEquals(0.9277, m.procentVycupa(), RATIO);          // 154 / (155+11)
        assertEquals(3283.29, m.srednyayaCena(), 0.01);          // 505626 / 154

        // Финансы
        assertEquals(507316.00, m.vykupyRub(), MONEY);
        assertEquals(-1690.00, m.vozvratyRub(), MONEY);
        assertEquals(505626.00, m.vyruchka(), MONEY);
        assertEquals(99065.00, m.sebestoimost(), MONEY);
        assertEquals(-206750.63, m.komissiya(), MONEY);
        assertEquals(-4300.77, m.ekvayring(), MONEY);
        assertEquals(-16351.93, m.logistika(), MONEY);
        assertEquals(-137266.70, m.drugieUslugi(), MONEY);
        assertEquals(-360369.26, m.vsegoUderzhaniy(), MONEY);
        assertEquals(145256.74, m.oplataNaRs(), MONEY);
        assertEquals(259972.31, m.nalogovayaBaza(), MONEY);
        assertEquals(15598.34, m.nalog(), 0.01);
        assertEquals(6, m.taxRatePct());
        assertEquals(30593.40, m.chistayaPribyl(), 0.02);        // 145256.74 − 99065 − 15598.34
        assertEquals(0.0605, m.marzha(), RATIO);
        assertEquals(0.3088, m.rentabelnost(), RATIO);

        // Реклама
        assertEquals(127729.47, m.reklamaRaskhod(), MONEY);
        assertEquals(0.2518, m.drr(), RATIO);                    // 127729.47 / 507316

        assertEquals(1417, m.linesCount());
    }
}
