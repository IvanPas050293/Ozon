package com.alnative.ozon.catalog;

import com.alnative.ozon.parser.model.Accrual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductCostServiceTest {

    private static final long SHOP_A = 111L;
    private static final long SHOP_B = 222L;

    private ProductCostRepository repository;
    private ProductCostService service;

    @BeforeEach
    void setUp() {
        repository = mock(ProductCostRepository.class);
        service = new ProductCostService(repository);
    }

    private static Accrual accrual(String sku, String artikul, String name) {
        return new Accrual("id", LocalDate.of(2026, 8, 1), "Продажи", "Выручка",
                artikul, sku, name, 1, 100, null, "Ozon", "FBO", 0, 100);
    }

    @Test
    void upsertCreatesProductsFromReport() {
        when(repository.findByOwnerChatIdAndSkuIgnoreCase(eq(SHOP_A), any())).thenReturn(Optional.empty());
        when(repository.save(any(ProductCost.class))).thenAnswer(inv -> inv.getArgument(0));

        service.upsertFromReport(SHOP_A, List.of(
                accrual("SKU1", "АРТ1", "Товар 1"),
                accrual("SKU2", "АРТ2", "Товар 2")));

        org.mockito.Mockito.verify(repository).save(org.mockito.ArgumentMatchers.argThat(
                pc -> SHOP_A == pc.getOwnerChatId()
                        && "SKU1".equals(pc.getSku()) && "АРТ1".equals(pc.getArtikul()) && "Товар 1".equals(pc.getName())));
        org.mockito.Mockito.verify(repository).save(org.mockito.ArgumentMatchers.argThat(
                pc -> "SKU2".equals(pc.getSku())));
    }

    @Test
    void upsertDoesNotOverwriteExistingCost() {
        ProductCost existing = new ProductCost("SKU1", "АРТ1", "Старое имя");
        existing.setOwnerChatId(SHOP_A);
        existing.setCost(555.0);
        when(repository.findByOwnerChatIdAndSkuIgnoreCase(SHOP_A, "SKU1")).thenReturn(Optional.of(existing));
        when(repository.save(any(ProductCost.class))).thenAnswer(inv -> inv.getArgument(0));

        service.upsertFromReport(SHOP_A, List.of(accrual("SKU1", "АРТ1", "Новое имя")));

        assertEquals("Новое имя", existing.getName());
        assertEquals(555.0, existing.getCost(), 0.001);
    }

    @Test
    void setCostBySkuAndArtikul() {
        ProductCost bySku = new ProductCost("SKU1", "АРТ1", "Товар 1");
        bySku.setOwnerChatId(SHOP_A);
        when(repository.findByOwnerChatIdAndSkuIgnoreCase(SHOP_A, "SKU1")).thenReturn(Optional.of(bySku));
        when(repository.findByOwnerChatIdAndSkuIgnoreCase(SHOP_A, "АРТ2")).thenReturn(Optional.empty());
        when(repository.findByOwnerChatIdAndArtikulIgnoreCase(SHOP_A, "АРТ2"))
                .thenReturn(Optional.of(new ProductCost("SKU2", "АРТ2", "Товар 2")));
        when(repository.save(any(ProductCost.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductCost updated = service.setCost(SHOP_A, "SKU1", 123.5);
        assertNotNull(updated);
        assertEquals(123.5, updated.getCost(), 0.001);

        updated = service.setCost(SHOP_A, "АРТ2", 999);
        assertNotNull(updated);
        assertEquals("SKU2", updated.getSku());

        when(repository.findByOwnerChatIdAndSkuIgnoreCase(SHOP_A, "NOPE")).thenReturn(Optional.empty());
        when(repository.findByOwnerChatIdAndArtikulIgnoreCase(SHOP_A, "NOPE")).thenReturn(Optional.empty());
        assertNull(service.setCost(SHOP_A, "NOPE", 100));
    }

    @Test
    void costOfReturnsZeroForUnknown() {
        when(repository.findByOwnerChatIdAndSkuIgnoreCase(SHOP_A, "UNKNOWN")).thenReturn(Optional.empty());
        assertEquals(0.0, service.costOf(SHOP_A, "UNKNOWN"), 0.001);
    }

    /** Два магазина в одном боте не видят и не меняют каталог друг друга. */
    @Test
    void shopsDoNotMixCatalogs() {
        ProductCost shopAProduct = new ProductCost("SKU1", "АРТ1", "Товар магазина A");
        shopAProduct.setOwnerChatId(SHOP_A);
        shopAProduct.setCost(500.0);
        when(repository.findByOwnerChatIdAndSkuIgnoreCase(SHOP_A, "SKU1")).thenReturn(Optional.of(shopAProduct));
        when(repository.findByOwnerChatIdAndSkuIgnoreCase(SHOP_B, "SKU1")).thenReturn(Optional.empty());
        when(repository.findByOwnerChatIdAndArtikulIgnoreCase(SHOP_B, "SKU1")).thenReturn(Optional.empty());

        // Магазин A видит свою себестоимость.
        assertEquals(500.0, service.costOf(SHOP_A, "SKU1"), 0.001);

        // Магазин B не видит чужую себестоимость и не может её перезаписать.
        assertEquals(0.0, service.costOf(SHOP_B, "SKU1"), 0.001);
        assertNull(service.setCost(SHOP_B, "SKU1", 100));
        assertEquals(500.0, shopAProduct.getCost(), 0.001);
    }
}
