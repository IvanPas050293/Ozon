package com.alnative.ozon.calc;

/**
 * Источник себестоимости товара по SKU.
 * В приложении реализуется каталогом {@code ProductCostService}, в тестах — простой картой.
 * Владелец (chat_id магазина) нужен, чтобы разные магазины не использовали чужие себестоимости.
 */
@FunctionalInterface
public interface CostProvider {

    /** Себестоимость единицы товара по SKU (0, если не задана) для магазина {@code ownerChatId}. */
    double costOf(Long ownerChatId, String sku);
}
