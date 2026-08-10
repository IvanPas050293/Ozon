package com.alnative.ozon.parser.model;

/**
 * Роль присланного пользователем xlsx-файла.
 */
public enum ExcelRole {
    /** «Отчет по начислениям» (финансы → экономика магазина). */
    ACCRUAL("Отчет по начислениям"),
    /** «Аналитика продвижения» (реклама). */
    PROMO("Аналитика продвижения"),
    /** Файл не распознан. */
    UNKNOWN("Не распознан");

    private final String title;

    ExcelRole(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
