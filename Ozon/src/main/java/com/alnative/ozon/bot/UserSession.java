package com.alnative.ozon.bot;

import com.alnative.ozon.parser.model.NagruzheniyaReport;
import com.alnative.ozon.parser.model.PromoData;

/**
 * Сессия пользователя: распарсенные данные отчётов, хранятся в памяти.
 * <p>
 * Файлы на диске между сообщениями не держим: временная папка ОС (в которую скачиваются
 * файлы) может быть почищена системой в любой момент, поэтому после разбора файл удаляется
 * сразу, а в сессии остаются готовые данные для расчёта дашборда.
 */
public class UserSession {

    private NagruzheniyaReport accrual;
    private PromoData promo;

    /** SKU товара, для которого ждём ввод себестоимости (после нажатия кнопки в /sebestoimost). */
    private String pendingCostSku;

    public NagruzheniyaReport getAccrual() {
        return accrual;
    }

    public void setAccrual(NagruzheniyaReport accrual) {
        this.accrual = accrual;
    }

    public PromoData getPromo() {
        return promo;
    }

    public void setPromo(PromoData promo) {
        this.promo = promo;
    }

    public String getPendingCostSku() {
        return pendingCostSku;
    }

    public void setPendingCostSku(String pendingCostSku) {
        this.pendingCostSku = pendingCostSku;
    }

    /** Оба отчёта получены — можно считать дашборд. */
    public boolean ready() {
        return accrual != null && promo != null;
    }

    /** Очищает сессию. */
    public void cleanup() {
        accrual = null;
        promo = null;
        pendingCostSku = null;
    }
}
