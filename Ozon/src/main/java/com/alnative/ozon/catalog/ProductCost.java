package com.alnative.ozon.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Каталог себестоимости: товар (SKU/Артикул/Название) и введённая пользователем себестоимость.
 */
@Entity
@Table(name = "product_cost")
public class ProductCost {

    @Id
    @Column(name = "sku", length = 32)
    private String sku;

    @Column(name = "artikul", length = 128)
    private String artikul;

    @Column(name = "name", length = 512)
    private String name;

    @Column(name = "cost")
    private Double cost;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public ProductCost() {
    }

    public ProductCost(String sku, String artikul, String name) {
        this.sku = sku;
        this.artikul = artikul;
        this.name = name;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getArtikul() {
        return artikul;
    }

    public void setArtikul(String artikul) {
        this.artikul = artikul;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getCost() {
        return cost;
    }

    public void setCost(Double cost) {
        this.cost = cost;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
