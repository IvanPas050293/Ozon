package com.alnative.ozon.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductCostRepository extends JpaRepository<ProductCost, String> {

    Optional<ProductCost> findBySkuIgnoreCase(String sku);

    Optional<ProductCost> findByArtikulIgnoreCase(String artikul);

    List<ProductCost> findAllByOrderBySkuAsc();
}
