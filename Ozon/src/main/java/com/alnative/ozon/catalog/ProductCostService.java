package com.alnative.ozon.catalog;

import com.alnative.ozon.calc.CostProvider;
import com.alnative.ozon.parser.model.Accrual;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Каталог себестоимости: автоматически пополняется названиями из отчёта начислений,
 * себестоимость вводит пользователь командами бота.
 * <p>
 * Каталог разделён по владельцу (chat_id магазина): разные магазины в одном боте
 * не видят и не перезаписывают данные друг друга.
 */
@Service
public class ProductCostService implements CostProvider {

    private static final Logger log = LoggerFactory.getLogger(ProductCostService.class);

    private final ProductCostRepository repository;

    public ProductCostService(ProductCostRepository repository) {
        this.repository = repository;
    }

    /**
     * Пополняет каталог магазина из отчёта начислений: SKU → название и артикул.
     * Существующую себестоимость не трогает.
     */
    @Transactional
    public void upsertFromReport(Long ownerChatId, List<Accrual> accruals) {
        Map<String, ProductCost> bySku = new LinkedHashMap<>();
        for (Accrual a : accruals) {
            if (a.sku() == null || a.sku().isBlank()) {
                continue;
            }
            ProductCost pc = bySku.computeIfAbsent(a.sku(), k -> new ProductCost(k, a.artikul(), a.name()));
            pc.setOwnerChatId(ownerChatId);
            if (pc.getArtikul() == null || pc.getArtikul().isBlank()) {
                pc.setArtikul(a.artikul());
            }
            if (pc.getName() == null || pc.getName().isBlank()) {
                pc.setName(a.name());
            }
        }
        int created = 0;
        int updated = 0;
        for (ProductCost pc : bySku.values()) {
            Optional<ProductCost> existing = repository.findByOwnerChatIdAndSkuIgnoreCase(ownerChatId, pc.getSku());
            if (existing.isPresent()) {
                boolean changed = false;
                ProductCost e = existing.get();
                if (isChanged(e.getArtikul(), pc.getArtikul())) {
                    e.setArtikul(pc.getArtikul());
                    changed = true;
                }
                if (isChanged(e.getName(), pc.getName())) {
                    e.setName(pc.getName());
                    changed = true;
                }
                if (changed) {
                    e.setUpdatedAt(LocalDateTime.now());
                    repository.save(e);
                    updated++;
                }
            } else {
                pc.setUpdatedAt(LocalDateTime.now());
                repository.save(pc);
                created++;
            }
        }
        log.info("Каталог (чат {}): создано {}, обновлено {}", ownerChatId, created, updated);
    }

    /**
     * Товар магазина по SKU или артикулу.
     *
     * @return товар или {@code null}, если не найден
     */
    @Transactional(readOnly = true)
    public ProductCost findByKey(Long ownerChatId, String skuOrArtikul) {
        String key = skuOrArtikul == null ? "" : skuOrArtikul.trim();
        return repository.findByOwnerChatIdAndSkuIgnoreCase(ownerChatId, key)
                .or(() -> repository.findByOwnerChatIdAndArtikulIgnoreCase(ownerChatId, key))
                .orElse(null);
    }

    /**
     * Задаёт себестоимость товара магазина по SKU или артикулу.
     *
     * @return обновлённый товар или {@code null}, если товар не найден
     */
    @Transactional
    public ProductCost setCost(Long ownerChatId, String skuOrArtikul, double cost) {
        ProductCost pc = findByKey(ownerChatId, skuOrArtikul);
        if (pc == null) {
            return null;
        }
        pc.setCost(cost);
        pc.setUpdatedAt(LocalDateTime.now());
        repository.save(pc);
        log.info("Себестоимость {} ({}) = {}", pc.getSku(), pc.getName(), cost);
        return pc;
    }

    /** Все товары каталога магазина, отсортированные по SKU. */
    @Transactional(readOnly = true)
    public List<ProductCost> list(Long ownerChatId) {
        return repository.findAllByOwnerChatIdOrderBySkuAsc(ownerChatId);
    }

    /** Себестоимость по SKU (0, если не задана) — реализация {@link CostProvider}. */
    @Override
    @Transactional(readOnly = true)
    public double costOf(Long ownerChatId, String sku) {
        if (ownerChatId == null || sku == null || sku.isBlank()) {
            return 0;
        }
        return repository.findByOwnerChatIdAndSkuIgnoreCase(ownerChatId, sku)
                .map(ProductCost::getCost)
                .orElse(0.0);
    }

    /** Задана ли себестоимость товара магазина (в отличие от 0, когда она просто не введена). */
    @Transactional(readOnly = true)
    public boolean hasCost(Long ownerChatId, String sku) {
        if (sku == null || sku.isBlank()) {
            return false;
        }
        return repository.findByOwnerChatIdAndSkuIgnoreCase(ownerChatId, sku)
                .map(ProductCost::getCost)
                .map(c -> c != null)
                .orElse(false);
    }

    private static boolean isChanged(String oldVal, String newVal) {
        return oldVal == null || oldVal.isBlank() ? newVal != null && !newVal.isBlank()
                : !oldVal.equals(newVal);
    }
}
