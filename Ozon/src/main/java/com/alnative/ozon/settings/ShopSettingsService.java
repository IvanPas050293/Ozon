package com.alnative.ozon.settings;

import com.alnative.ozon.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Настройки магазина. Сейчас это налоговая ставка, выбираемая пользователем кнопкой.
 * <p>
 * Пока ставка не выбрана, используется значение из конфига ({@code app.tax-rate}).
 */
@Service
public class ShopSettingsService {

    private static final Logger log = LoggerFactory.getLogger(ShopSettingsService.class);

    private final ShopSettingsRepository repository;
    private final AppProperties props;

    public ShopSettingsService(ShopSettingsRepository repository, AppProperties props) {
        this.repository = repository;
        this.props = props;
    }

    /** Налоговая ставка магазина в процентах (по умолчанию — из конфига). */
    @Transactional(readOnly = true)
    public int getTaxRatePct(Long ownerChatId) {
        return repository.findById(ownerChatId)
                .map(ShopSettings::getTaxRatePct)
                .orElse(defaultTaxRatePct());
    }

    /** Сохраняет налоговую ставку магазина (1–12). */
    @Transactional
    public void setTaxRatePct(Long ownerChatId, int pct) {
        ShopSettings s = repository.findById(ownerChatId).orElseGet(() -> {
            ShopSettings ns = new ShopSettings();
            ns.setOwnerChatId(ownerChatId);
            return ns;
        });
        s.setTaxRatePct(pct);
        s.setUpdatedAt(LocalDateTime.now());
        repository.save(s);
        log.info("Налоговая ставка магазина {} = {}%", ownerChatId, pct);
    }

    private int defaultTaxRatePct() {
        return Math.round((float) (props.taxRate() * 100));
    }
}
