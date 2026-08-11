package com.alnative.ozon.calc;

import com.alnative.ozon.parser.model.Accrual;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Расчёт сводных метрик дашборда «Экономика магазина Ozon» из строк начислений.
 * <p>
 * Логика повторяет формулы листов «База» и «Dashboard» Google-шаблона и проверена
 * на реальных данных (см. тест {@code DashboardCalculatorTest}).
 */
public class DashboardCalculator {

    private static final Logger log = LoggerFactory.getLogger(DashboardCalculator.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** Типы продаж, не являющиеся расходами (не уходят в «другие услуги»). */
    private static final Set<String> SALE_TYPES = Set.of(
            "Выручка", "Механики лояльности", "Программы партнёров", "Баллы за скидки",
            "За продажу или возврат до вычета комиссий и услуг",
            "Возврат выручки", "Возврат вознаграждения");

    private final CostProvider costProvider;
    private final int taxSystem;
    private final double taxRate;

    public DashboardCalculator(CostProvider costProvider, int taxSystem, double taxRate) {
        this.costProvider = costProvider;
        this.taxSystem = taxSystem;
        this.taxRate = taxRate;
    }

    /**
     * Считает метрики за весь период (мин/макс даты начислений).
     *
     * @param ownerChatId  chat_id магазина (для себестоимостей его каталога)
     * @param accruals     строки начислений
     * @param promoExpense суммарный расход на продвижение (для ДРР)
     */
    public DashboardMetrics calculate(Long ownerChatId, List<Accrual> accruals, double promoExpense) {
        Set<String> returnIds = collectReturnIds(accruals);

        long vykupySht = 0;
        long vozvratySht = 0;
        long nevykupSht = 0;
        double vykupyRub = 0;
        double vozvratyRub = 0;
        double komissiya = 0;
        double logistika = 0;
        double ekvayring = 0;
        double sumExpense = 0;      // сумма всех «распределяемых» расходов (включая комиссию/логистику/эквайринг)
        double sumUnclassified = 0; // нераспознанные расходы (→ «Нераспределенные», CU)
        double usnDohodyBase = 0;   // налоговая база УСН «Доходы»: D + I + F + K
        Map<String, Double> netUnits = new HashMap<>();
        LocalDate min = null;
        LocalDate max = null;

        for (Accrual a : accruals) {
            String type = a.type() == null ? "" : a.type();
            double total = a.total();
            double qty = a.qty();

            if (a.date() != null) {
                min = min == null ? a.date() : min.isAfter(a.date()) ? a.date() : min;
                max = max == null ? a.date() : max.isBefore(a.date()) ? a.date() : max;
            }

            // Количества и себестоимость
            if (isSaleRow(a) && total > 0) {
                vykupySht += Math.round(qty);
                addUnits(netUnits, a.sku(), qty);
            } else if (isReturnRow(a)) {
                vozvratySht += Math.round(qty);
                addUnits(netUnits, a.sku(), -qty);
            }
            if (type.equals("Обратная логистика") && total < 0 && !returnIds.contains(a.id())) {
                nevykupSht += Math.round(qty);
            }
            if (type.contains("Утилизация")) {
                addUnits(netUnits, a.sku(), qty);
            }

            // Выручка / возвраты в рублях
            if (type.equals("Возврат выручки")) {
                vozvratyRub += total;
            } else if (saleType(type)) {
                if (total > 0) {
                    vykupyRub += total;
                } else {
                    vozvratyRub += total;
                }
            }

            // Налоговая база (УСН Доходы): D (Выручка, любой знак) + F (Программы партнёров, любой знак) + Возврат выручки
            if (type.equals("Выручка") || type.equals("Программы партнёров") || type.equals("Возврат выручки")) {
                usnDohodyBase += total;
            }

            // Расходы по категориям
            ExpenseCategory cat = ExpenseCategory.classify(type, total);
            if (cat != null) {
                sumExpense += total;
                switch (cat.kind()) {
                    case COMISSION -> komissiya += total;
                    case LOGISTICS -> logistika += total;
                    case OTHER -> {
                        if (cat == ExpenseCategory.R) {
                            ekvayring += total;
                        }
                    }
                }
            } else if (!SALE_TYPES.contains(type)) {
                sumUnclassified += total;
            }
        }

        long prodazhSht = vykupySht - vozvratySht;
        double vyruchka = vykupyRub + vozvratyRub;
        double srednyayaCena = safeDivide(vyruchka, prodazhSht);
        double procentVycupa = safeDivide(prodazhSht, vykupySht + nevykupSht);
        double drugieUslugi = (sumExpense + sumUnclassified) - komissiya - logistika;
        double vsegoUderzhaniy = sumExpense + sumUnclassified;
        double oplataNaRs = vyruchka + vsegoUderzhaniy;
        double sebestoimost = costProvider == null ? 0 : computeSebestoimost(ownerChatId, netUnits);
        double nalogovayaBaza = computeTaxBase(taxSystem, vyruchka, oplataNaRs, sebestoimost, usnDohodyBase);
        double nalog = nalogovayaBaza * taxRate;
        double chistayaPribyl = oplataNaRs - sebestoimost - nalog;
        double marzha = safeDivide(chistayaPribyl, vyruchka);
        double rentabelnost = safeDivide(chistayaPribyl, sebestoimost);
        double drr = safeDivide(promoExpense, vykupyRub);

        int taxRatePct = Math.round((float) (taxRate * 100));
        DashboardMetrics m = new DashboardMetrics(
                min == null ? "" : min.format(FMT),
                max == null ? "" : max.format(FMT),
                vykupySht, vozvratySht, nevykupSht, prodazhSht,
                procentVycupa, srednyayaCena,
                vykupyRub, vozvratyRub, vyruchka, sebestoimost, komissiya, ekvayring,
                logistika, drugieUslugi, vsegoUderzhaniy, oplataNaRs,
                nalogovayaBaza, nalog, taxRatePct, chistayaPribyl, marzha, rentabelnost,
                promoExpense, drr,
                accruals.size());
        log.debug("Дашборд: выручка {}, ЧП {}, ДРР {}", vyruchka, chistayaPribyl, drr);
        return m;
    }

    /** ID начислений, содержащие возвраты (для расчёта невыкупов). */
    private Set<String> collectReturnIds(List<Accrual> accruals) {
        Set<String> ids = new HashSet<>();
        for (Accrual a : accruals) {
            if (isReturnRow(a)) {
                ids.add(a.id());
            }
        }
        return ids;
    }

    private void addUnits(Map<String, Double> netUnits, String sku, double qty) {
        if (sku == null || sku.isBlank() || qty == 0) {
            return;
        }
        netUnits.merge(sku, qty, Double::sum);
    }

    private double computeSebestoimost(Long ownerChatId, Map<String, Double> netUnits) {
        double sum = 0;
        for (Map.Entry<String, Double> e : netUnits.entrySet()) {
            sum += e.getValue() * costProvider.costOf(ownerChatId, e.getKey());
        }
        return sum;
    }

    private double computeTaxBase(int taxSystem, double vyruchka, double oplataNaRs,
                                  double sebestoimost, double usnDohodyBase) {
        return switch (taxSystem) {
            case 2 -> oplataNaRs - sebestoimost;   // УСН Доходы-Расходы: O+CN−CO
            case 3 -> 0;                            // не считать налог
            case 4 -> oplataNaRs;                   // от оплаты на Р/С: O+CN
            default -> usnDohodyBase;               // 1 — УСН Доходы: D+I+F+K
        };
    }

    /** Тип продажи (Выручка, Механики лояльности, Программы партнёров, Баллы за скидки). */
    private static boolean saleType(String type) {
        return type.equals("Выручка") || type.equals("Механики лояльности")
                || type.equals("Программы партнёров") || type.equals("Баллы за скидки");
    }

    /** Позитивная строка продажи («выкупы»): Выручка или «За продажу до вычета…» с положительной суммой. */
    private static boolean isSaleRow(Accrual a) {
        String type = a.type() == null ? "" : a.type();
        return type.equals("Выручка")
                || type.equals("За продажу или возврат до вычета комиссий и услуг") && a.total() > 0;
    }

    /** Строка возврата: «Возврат выручки» или «Выручка» с отрицательной суммой. */
    private static boolean isReturnRow(Accrual a) {
        String type = a.type() == null ? "" : a.type();
        return type.equals("Возврат выручки")
                || type.equals("Выручка") && a.total() < 0;
    }

    private static double safeDivide(double a, double b) {
        return b == 0 ? 0 : a / b;
    }
}
