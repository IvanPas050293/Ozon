package com.alnative.ozon.parser;

import com.alnative.ozon.parser.model.Accrual;
import com.alnative.ozon.parser.model.NagruzheniyaReport;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NagruzheniyaParserTest {

    private static Path sample(String name) {
        URL url = NagruzheniyaParserTest.class.getResource("/samples/" + name);
        assertNotNull(url, "Не найден тестовый файл " + name);
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void parsesAccrualReport() throws Exception {
        NagruzheniyaReport report = new NagruzheniyaParser().parse(sample("Отчет по начислениям_01.08.2026-10.08.2026.xlsx"));

        // 1417 строк данных (1419 строк в файле минус заголовки)
        assertEquals(1417, report.accruals().size());
        assertEquals("01.08.2026", report.periodStart());
        assertEquals("10.08.2026", report.periodEnd());

        // Справочник SKU → название
        assertEquals("Набор для самокруток Mascotte, Бумага сигаретная для самокруток, Машинка для скручивания, Фильтры",
                report.skuToName().get("1792723847"));

        // Первая строка (в файле первая строка данных — «Логистика»)
        Accrual first = report.accruals().get(0);
        assertEquals("0113657612-0259-2", first.id());
        assertEquals("Логистика", first.type());
        assertEquals("Ozon", first.platform());
        assertEquals("FBO", first.scheme());
        assertTrue(Math.abs(first.total() - (-77.00)) < 0.001);
    }

    @Test
    void skuParsedWithoutExponent() throws Exception {
        NagruzheniyaReport report = new NagruzheniyaParser().parse(sample("Отчет по начислениям_01.08.2026-10.08.2026.xlsx"));
        assertTrue(report.skuToName().containsKey("2269055805"),
                "SKU должен читаться как строка 2269055805, а не 1.7927...E9");
    }
}
