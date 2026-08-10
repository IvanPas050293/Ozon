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
 */
@Service
public class ProductCostService implements CostProvider {

    private static final Logger log = LoggerFactory.getLogger(ProductCostService.class);

    private final ProductCostRepository repository;

    public ProductCostService(ProductCostRepository repository) {
        this.repository = repository;
    }

    /**
     * Пополняет каталог из отчёта начислений: SKU → название и артикул.
     * Существующую себестоимость не трогает.
     */
    @Transactional
    public void upsertFromReport(List<Accrual> accruals) {
        Map<String, ProductCost> bySku = new LinkedHashMap<>();
        for (Accrual a : accruals) {
            if (a.sku() == null || a.sku().isBlank()) {
                continue;
            }
            ProductCost pc = bySku.computeIfAbsent(a.sku(), k -> new ProductCost(k, a.artikul(), a.name()));
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
            Optional<ProductCost> existing = repository.findBySkuIgnoreCase(pc.getSku());
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
        log.info("Каталог: создано {}, обновлено {}", created, updated);
    }

    /**
     * Задаёт себестоимость товара по SKU или артикулу.
     *
     * @return обновлённый товар или {@code null}, если товар не найден
     */
    @Transactional
    public ProductCost setCost(String skuOrArtikul, double cost) {
        String key = skuOrArtikul == null ? "" : skuOrArtikul.trim();
        ProductCost pc = repository.findBySkuIgnoreCase(key)
                .or(() -> repository.findByArtikulIgnoreCase(key))
                .orElse(null);
        if (pc == null) {
            return null;
        }
        pc.setCost(cost);
        pc.setUpdatedAt(LocalDateTime.now());
        repository.save(pc);
        log.info("Себестоимость {} ({}) = {}", pc.getSku(), pc.getName(), cost);
        return pc;
    }

    /** Все товары каталога, отсортированные по SKU. */
    @Transactional(readOnly = true)
    public List<ProductCost> list() {
        return repository.findAllByOrderBySkuAsc();
    }

    /** Себестоимость по SKU (0, если не задана) — реализация {@link CostProvider}. */
    @Override
    @Transactional(readOnly = true)
    public double costOf(String sku) {
        if (sku == null || sku.isBlank()) {
            return 0;
        }
        return repository.findBySkuIgnoreCase(sku)
                .map(ProductCost::getCost)
                .orElse(0.0);
    }

    private static boolean isChanged(String oldVal, String newVal) {
        return oldVal == null || oldVal.isBlank() ? newVal != null && !newVal.isBlank()
                : !oldVal.equals(newVal);
    }
}
