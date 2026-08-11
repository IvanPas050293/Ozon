package com.alnative.ozon.service;

import com.alnative.ozon.calc.DashboardCalculator;
import com.alnative.ozon.calc.DashboardMetrics;
import com.alnative.ozon.catalog.ProductCostService;
import com.alnative.ozon.config.AppProperties;
import com.alnative.ozon.parser.model.NagruzheniyaReport;
import com.alnative.ozon.parser.model.PromoData;
import org.springframework.stereotype.Service;

/**
 * Собирает расчёт дашборда из двух отчётов: начислений и продвижения.
 * Отчёты приходят уже распарсенными (в памяти), файлы на диск не читаются —
 * это исключает пропажу временных файлов между сообщениями.
 * Заодно пополняет каталог себестоимости названиями товаров из отчёта.
 */
@Service
public class DashboardService {

    private final ProductCostService productCostService;
    private final AppProperties props;

    public DashboardService(ProductCostService productCostService,
                            AppProperties props) {
        this.productCostService = productCostService;
        this.props = props;
    }

    public DashboardMetrics build(Long ownerChatId, NagruzheniyaReport report, PromoData promo) {
        productCostService.upsertFromReport(ownerChatId, report.accruals());
        DashboardCalculator calc = new DashboardCalculator(productCostService, props.taxSystem(), props.taxRate());
        return calc.calculate(ownerChatId, report.accruals(), promo.totalExpense());
    }
}
