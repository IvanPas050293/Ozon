package com.alnative.ozon.bot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Сессия пользователя: загруженные временные файлы отчётов.
 */
public class UserSession {

    private Path accrualFile;
    private Path promoFile;

    public Path getAccrualFile() {
        return accrualFile;
    }

    public void setAccrualFile(Path accrualFile) {
        cleanup(accrualFile);
        this.accrualFile = accrualFile;
    }

    public Path getPromoFile() {
        return promoFile;
    }

    public void setPromoFile(Path promoFile) {
        cleanup(promoFile);
        this.promoFile = promoFile;
    }

    /** Оба файла загружены — можно считать дашборд. */
    public boolean ready() {
        return accrualFile != null && promoFile != null;
    }

    /** Удаляет временные файлы сессии. */
    public void cleanup() {
        deleteQuietly(accrualFile);
        deleteQuietly(promoFile);
        accrualFile = null;
        promoFile = null;
    }

    private static void cleanup(Path oldFile) {
        if (oldFile != null) {
            deleteQuietly(oldFile);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // не критично
        }
    }
}
