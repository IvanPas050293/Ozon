package com.alnative.ozon.calc;

/**
 * Сводные метрики дашборда «Экономика магазина Ozon» за период.
 * Значения соответствуют листу Dashboard Google-шаблона.
 */
public record DashboardMetrics(
        String periodStart,
        String periodEnd,

        // Заказы и продажи
        long vykupySht,          // выкупы, шт
        long vozvratySht,        // возвраты, шт
        long nevykupSht,         // невыкуп/отмены, шт
        long prodazhSht,         // продаж, шт (выкупы − возвраты)
        double procentVycupa,    // % выкупа
        double srednyayaCena,    // средняя цена продажи, ₽

        // Финансы
        double vykupyRub,        // выкупы, ₽
        double vozvratyRub,      // возвраты, ₽
        double vyruchka,         // выручка, ₽
        double sebestoimost,     // себестоимость проданного, ₽
        double komissiya,        // комиссия Ozon, ₽
        double ekvayring,        // эквайринг, ₽
        double logistika,        // логистика всего, ₽
        double drugieUslugi,     // другие услуги, ₽
        double vsegoUderzhaniy,  // всего удержаний OZON, ₽
        double oplataNaRs,       // оплата на р/с, ₽
        double nalogovayaBaza,   // налоговая база, ₽
        double nalog,            // налог, ₽
        double chistayaPribyl,   // чистая прибыль, ₽
        double marzha,           // маржинальность, доли (ЧП/выручка)
        double rentabelnost,     // рентабельность, доли (ЧП/себестоимость)

        // Реклама
        double reklamaRaskhod,   // расход на продвижение, ₽
        double drr,              // ДРР по магазину, доли (расход/выкупы ₽)

        int linesCount
) {
}
