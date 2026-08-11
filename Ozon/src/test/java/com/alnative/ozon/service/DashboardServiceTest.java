package com.alnative.ozon.service;

import com.alnative.ozon.calc.DashboardMetrics;
import com.alnative.ozon.catalog.ProductCost;
import com.alnative.ozon.catalog.ProductCostRepository;
import com.alnative.ozon.catalog.ProductCostService;
import com.alnative.ozon.config.AppProperties;
import com.alnative.ozon.parser.NagruzheniyaParser;
import com.alnative.ozon.parser.PromoParser;
import com.alnative.ozon.parser.model.NagruzheniyaReport;
import com.alnative.ozon.parser.model.PromoData;
import com.alnative.ozon.settings.ShopSettingsRepository;
import com.alnative.ozon.settings.ShopSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Регрессия: дашборд строится из распарсенных в памяти данных, а не из файлов на диске.
 * <p>
 * Раньше {@code DashboardService.build} читал файлы из временной папки по путям из сессии.
 * Если между двумя сообщениями ОС чистила %TEMP% (Storage Sense/Disk Cleanup), POI падал
 * с NoSuchFileException → «Файл не является корректным xlsx». Теперь файлы не перечитываются.
 */
class DashboardServiceTest {

    private static final AppProperties PROPS = new AppProperties(1, 0.06, "");

    @TempDir
    Path tmp;

    @Test
    void buildWorksFromMemoryAfterTempFilesDeleted() throws Exception {
        // Бот скачивает файлы во временную папку.
        Path accrualTmp = tmp.resolve("ozon-accrual.xlsx");
        Path promoTmp = tmp.resolve("ozon-promo.xlsx");
        Files.copy(sample("Отчет по начислениям_01.08.2026-10.08.2026.xlsx"), accrualTmp);
        Files.copy(sample("Аналитика продвижения_10.08.2026.xlsx"), promoTmp);

        // Парсим один раз при приёме сообщения.
        NagruzheniyaReport report = new NagruzheniyaParser().parse(accrualTmp);
        PromoData promo = new PromoParser().parse(promoTmp);

        // Файлы пропали из temp-папки между сообщениями — прежний код упал бы здесь.
        Files.delete(accrualTmp);
        Files.delete(promoTmp);
        assertTrue(Files.notExists(accrualTmp) && Files.notExists(promoTmp));

        ProductCostRepository repository = mock(ProductCostRepository.class);
        when(repository.findByOwnerChatIdAndSkuIgnoreCase(any(), any())).thenReturn(Optional.empty());
        when(repository.save(any(ProductCost.class))).thenAnswer(inv -> inv.getArgument(0));

        ShopSettingsRepository settingsRepo = mock(ShopSettingsRepository.class);
        when(settingsRepo.findById(any())).thenReturn(Optional.empty());
        ShopSettingsService settingsService = new ShopSettingsService(settingsRepo, PROPS);

        DashboardMetrics m = new DashboardService(new ProductCostService(repository), settingsService, PROPS)
                .build(111L, report, promo);
        assertNotNull(m);

        // Статистика по товарам строится из тех же данных в памяти.
        String stats = new DashboardService(new ProductCostService(repository), settingsService, PROPS)
                .productStats(111L, report, promo);
        assertNotNull(stats);
        assertTrue(stats.contains("📊 Статистика по товарам"));
    }

    private static Path sample(String name) {
        URL url = DashboardServiceTest.class.getResource("/samples/" + name);
        assertNotNull(url, "Не найден тестовый файл " + name);
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
