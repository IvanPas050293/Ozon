package com.alnative.ozon.settings;

import com.alnative.ozon.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Налоговая ставка магазина: хранится отдельно на каждый магазин,
 * пока не задана — берётся из конфига ({@code app.tax-rate}).
 */
class ShopSettingsServiceTest {

    private static final AppProperties PROPS = new AppProperties(1, 0.06, "");

    private ShopSettingsRepository repository;
    private ShopSettingsService service;

    @BeforeEach
    void setUp() {
        repository = mock(ShopSettingsRepository.class);
        service = new ShopSettingsService(repository, PROPS);
    }

    @Test
    void defaultRateFromConfigWhenNotSet() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertEquals(6, service.getTaxRatePct(1L));
    }

    @Test
    void getReturnsStoredRate() {
        ShopSettings s = new ShopSettings();
        s.setOwnerChatId(1L);
        s.setTaxRatePct(9);
        when(repository.findById(1L)).thenReturn(Optional.of(s));

        assertEquals(9, service.getTaxRatePct(1L));
    }

    @Test
    void setStoresRateForShop() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        when(repository.save(any(ShopSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        service.setTaxRatePct(1L, 8);

        org.mockito.Mockito.verify(repository).save(org.mockito.ArgumentMatchers.argThat(
                s -> s.getOwnerChatId().equals(1L) && s.getTaxRatePct() == 8));
    }

    /** Два магазина хранят независимые ставки: изменение одного не влияет на другого. */
    @Test
    void shopsHaveIndependentRates() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        when(repository.findById(2L)).thenReturn(Optional.empty());

        service.setTaxRatePct(1L, 8);

        // Магазин 1 сохранил свою ставку, магазин 2 всё ещё использует дефолт из конфига.
        org.mockito.Mockito.verify(repository).save(org.mockito.ArgumentMatchers.argThat(
                s -> s.getOwnerChatId().equals(1L) && s.getTaxRatePct() == 8));
        assertEquals(6, service.getTaxRatePct(2L));
    }
}
