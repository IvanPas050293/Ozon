package com.alnative.ozon.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Настройки магазина (chat_id): налоговая ставка, выбранная пользователем кнопкой.
 * Каждый магазин хранит свою ставку независимо от других.
 */
@Entity
@Table(name = "shop_settings")
public class ShopSettings {

    /** Владелец — chat_id магазина. */
    @Id
    @Column(name = "owner_chat_id")
    private Long ownerChatId;

    /** Налоговая ставка в процентах (1–12). */
    @Column(name = "tax_rate_pct")
    private Integer taxRatePct;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public ShopSettings() {
    }

    public Long getOwnerChatId() {
        return ownerChatId;
    }

    public void setOwnerChatId(Long ownerChatId) {
        this.ownerChatId = ownerChatId;
    }

    public Integer getTaxRatePct() {
        return taxRatePct;
    }

    public void setTaxRatePct(Integer taxRatePct) {
        this.taxRatePct = taxRatePct;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
