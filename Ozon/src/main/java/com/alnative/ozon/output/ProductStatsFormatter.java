package com.alnative.ozon.output;

import com.alnative.ozon.calc.ProductStat;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * Форматирует «статистику по товарам» в построчный текст для Telegram:
 * на каждый товар — прибыль за 1 шт и за все проданные, маржа, налог и расход на рекламу.
 */
public class ProductStatsFormatter {

    private static final DecimalFormat MONEY = moneyFormat();

    public String format(String periodStart, String periodEnd, List<ProductStat> stats) {
        StringBuilder sb = new StringBuilder("📊 Статистика по товарам\n");
        sb.append("Период: ").append(periodStart).append(" — ").append(periodEnd).append("\n");
        if (stats.isEmpty()) {
            sb.append("\nНет данных о продажах за период.");
            return sb.toString();
        }
        for (ProductStat s : stats) {
            sb.append("\n📦 ").append(s.name()).append(" · SKU ").append(s.sku()).append("\n");
            sb.append("🛒 1 шт — ").append(rub(s.profitPerUnit()))
                    .append(" · ").append(qty(s.qty())).append(" шт — ").append(rub(s.profit())).append("\n");
            sb.append("💰 Маржа ").append(pct(s.margin()))
                    .append(" · Налог ").append(rub(s.tax()))
                    .append(" · Реклама ").append(rub(s.promoExpense())).append("\n");
            if (!s.costSet()) {
                sb.append("⚠️ себестоимость не задана — прибыль без учёта себестоимости\n");
            }
        }
        return sb.toString();
    }

    private static String rub(double v) {
        return MONEY.format(Math.round(v)) + " ₽";
    }

    private static String qty(double v) {
        return String.format(Locale.ROOT, "%,.0f", v);
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.1f", v * 100).replace('.', ',') + " %";
    }

    private static DecimalFormat moneyFormat() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(' '); // неразрывный пробел
        DecimalFormat f = new DecimalFormat("#,##0", symbols);
        f.setNegativePrefix("-");
        return f;
    }
}
