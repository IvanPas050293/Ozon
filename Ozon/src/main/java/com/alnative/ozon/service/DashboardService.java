package com.alnative.ozon.service;

import com.alnative.ozon.calc.DashboardCalculator;
import com.alnative.ozon.calc.DashboardMetrics;
import com.alnative.ozon.catalog.ProductCostService;
import com.alnative.ozon.config.AppProperties;
import com.alnative.ozon.parser.NagruzheniyaParser;
import com.alnative.ozon.parser.PromoParser;
import com.alnative.ozon.parser.model.NagruzheniyaReport;
import com.alnative.ozon.parser.model.PromoData;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Собирает расчёт дашборда из двух файлов: начислений и продвижения.
 * Заодно пополняет каталог себестоимости названиями товаров из отчёта.
 */
@Service
public class DashboardService {

    private final NagruzheniyaParser nagruzheniyaParser;
    private final PromoParser promoParser;
    private final ProductCostService productCostService;
    private final AppProperties props;

    public DashboardService(NagruzheniyaParser nagruzheniyaParser,
                            PromoParser promoParser,
                            ProductCostService productCostService,
                            AppProperties props) {
        this.nagruzheniyaParser = nagruzheniyaParser;
        this.promoParser = promoParser;
        this.productCostService = productCostService;
        this.props = props;
    }

    public DashboardMetrics build(Path accrualFile, Path promoFile) throws IOException {
        NagruzheniyaReport report = nagruzheniyaParser.parse(accrualFile);
        productCostService.upsertFromReport(report.accruals());
        PromoData promo = promoParser.parse(promoFile);
        DashboardCalculator calc = new DashboardCalculator(productCostService, props.taxSystem(), props.taxRate());
        return calc.calculate(report.accruals(), promo.totalExpense());
    }
}
