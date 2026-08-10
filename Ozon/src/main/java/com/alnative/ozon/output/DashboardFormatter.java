package com.alnative.ozon.output;

import com.alnative.ozon.calc.DashboardMetrics;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Форматирует метрики дашборда в сводный текст для отправки в Telegram.
 */
@Component
public class DashboardFormatter {

    private static final DecimalFormat MONEY = moneyFormat();

    public String format(DashboardMetrics m) {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 Экономика магазина Ozon\n");
        sb.append("Период: ").append(m.periodStart()).append(" — ").append(m.periodEnd()).append("\n\n");

        sb.append("🛍️ Заказы и продажи\n");
        sb.append("▫️ Продаж шт.: ").append(m.prodazhSht()).append("\n");
        sb.append("▫️ Выкупы: ").append(m.vykupySht())
                .append(" · Возвраты: ").append(m.vozvratySht())
                .append(" · Невыкуп/Отмены: ").append(m.nevykupSht()).append("\n");
        sb.append("▫️ % выкупа: ").append(pct(m.procentVycupa())).append("\n");
        sb.append("▫️ Средняя цена: ").append(rub(m.srednyayaCena())).append("\n\n");

        sb.append("💰 Финансы\n");
        sb.append("▫️ Выручка: ").append(rub(m.vyruchka())).append("\n");
        sb.append("▫️ Себестоимость: ").append(rub(m.sebestoimost())).append("\n");
        sb.append("▫️ Комиссия Ozon: ").append(rub(m.komissiya())).append("\n");
        sb.append("▫️ Эквайринг: ").append(rub(m.ekvayring())).append("\n");
        sb.append("▫️ Логистика: ").append(rub(m.logistika())).append("\n");
        sb.append("▫️ Другие услуги: ").append(rub(m.drugieUslugi())).append("\n");
        sb.append("▫️ Всего удержаний OZON: ").append(rub(m.vsegoUderzhaniy())).append("\n");
        sb.append("▫️ Оплата на р/с: ").append(rub(m.oplataNaRs())).append("\n");
        sb.append("▫️ Налог: ").append(rub(m.nalog())).append("\n");
        sb.append("▫️ Чистая прибыль: ").append(rub(m.chistayaPribyl())).append("\n");
        sb.append("▫️ Маржинальность: ").append(pct(m.marzha()))
                .append(" · Рентабельность: ").append(pct(m.rentabelnost())).append("\n\n");

        sb.append("📣 Реклама\n");
        sb.append("▫️ Расход на продвижение: ").append(rub(m.reklamaRaskhod())).append("\n");
        sb.append("▫️ ДРР по магазину: ").append(pct(m.drr())).append("\n");
        return sb.toString();
    }

    /** Деньги: «-360 369» (в рублях, ₽ добавляется вызывающим). */
    private static String rub(double v) {
        return MONEY.format(Math.round(v)) + " ₽";
    }

    /** Проценты: доли → «92,8 %». */
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
