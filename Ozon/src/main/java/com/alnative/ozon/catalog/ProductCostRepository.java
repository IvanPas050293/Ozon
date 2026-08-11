package com.alnative.ozon.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductCostRepository extends JpaRepository<ProductCost, String> {

    Optional<ProductCost> findByOwnerChatIdAndSkuIgnoreCase(Long ownerChatId, String sku);

    Optional<ProductCost> findByOwnerChatIdAndArtikulIgnoreCase(Long ownerChatId, String artikul);

    List<ProductCost> findAllByOwnerChatIdOrderBySkuAsc(Long ownerChatId);
}
