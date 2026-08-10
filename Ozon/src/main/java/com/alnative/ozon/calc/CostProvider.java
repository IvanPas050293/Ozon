package com.alnative.ozon.calc;

/**
 * Источник себестоимости товара по SKU.
 * В приложении реализуется каталогом {@code ProductCostService}, в тестах — простой картой.
 */
@FunctionalInterface
public interface CostProvider {

    /** Себестоимость единицы товара по SKU (0, если не задана). */
    double costOf(String sku);
}
