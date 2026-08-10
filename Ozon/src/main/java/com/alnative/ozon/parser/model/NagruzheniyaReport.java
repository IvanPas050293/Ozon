package com.alnative.ozon.parser.model;

import java.util.List;
import java.util.Map;

/**
 * Результат разбора файла «Отчет по начислениям».
 *
 * @param accruals   все строки начислений
 * @param skuToName  справочник SKU → название товара (для каталога себестоимости)
 * @param periodStart начало периода (мин. дата начисления), dd.MM.yyyy
 * @param periodEnd   конец периода (макс. дата начисления), dd.MM.yyyy
 */
public record NagruzheniyaReport(
        List<Accrual> accruals,
        Map<String, String> skuToName,
        String periodStart,
        String periodEnd
) {
}
