package com.alnative.ozon.service;

import com.alnative.ozon.calc.DashboardCalculator;
import com.alnative.ozon.calc.DashboardMetrics;
import com.alnative.ozon.calc.ProductStat;
import com.alnative.ozon.calc.ProductStatsCalculator;
import com.alnative.ozon.catalog.ProductCostService;
import com.alnative.ozon.config.AppProperties;
import com.alnative.ozon.output.ProductStatsFormatter;
import com.alnative.ozon.parser.model.NagruzheniyaReport;
import com.alnative.ozon.parser.model.PromoData;
import com.alnative.ozon.settings.ShopSettingsService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Собирает расчёт дашборда из двух отчётов: начислений и продвижения.
 * Отчёты приходят уже распарсенными (в памяти), файлы на диск не читаются —
 * это исключает пропажу временных файлов между сообщениями.
 * Заодно пополняет каталог себестоимости названиями товаров из отчёта.
 */
@Service
public class DashboardService {

    private final ProductCostService productCostService;
    private final ShopSettingsService shopSettingsService;
    private final AppProperties props;

    public DashboardService(ProductCostService productCostService,
                            ShopSettingsService shopSettingsService,
                            AppProperties props) {
        this.productCostService = productCostService;
        this.shopSettingsService = shopSettingsService;
        this.props = props;
    }

    public DashboardMetrics build(Long ownerChatId, NagruzheniyaReport report, PromoData promo) {
        productCostService.upsertFromReport(ownerChatId, report.accruals());
        int taxRatePct = shopSettingsService.getTaxRatePct(ownerChatId);
        DashboardCalculator calc = new DashboardCalculator(productCostService, props.taxSystem(), taxRatePct / 100.0);
        return calc.calculate(ownerChatId, report.accruals(), promo.totalExpense());
    }

    /**
     * Статистика по товарам: для каждого SKU аккумулируются продажи, бонусы и расходы;
     * прибыль = чистый поток − себестоимость − налог. Маржа, налог и расход на рекламу по товару.
     */
    public String productStats(Long ownerChatId, NagruzheniyaReport report, PromoData promo) {
        int taxRatePct = shopSettingsService.getTaxRatePct(ownerChatId);
        List<ProductStat> stats = new ProductStatsCalculator(productCostService)
                .calculate(ownerChatId, report.accruals(), promo.rows(), taxRatePct / 100.0);
        return new ProductStatsFormatter().format(report.periodStart(), report.periodEnd(), stats);
    }
}
