package com.alnative.ozon.parser;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Утилиты чтения ячеек xlsx: строка, число, дата — без потери точности SKU/ID.
 */
final class ExcelReadUtil {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private ExcelReadUtil() {
    }

    /** Значение ячейки как строка (пусто → пустая строка). */
    static String stringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        return switch (type) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> numericToString(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    /** Числовое значение ячейки (0 для пустой/текстовой). */
    static double numberValue(Cell cell) {
        if (cell == null) {
            return 0.0;
        }
        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING -> parseDoubleSafe(cell.getStringCellValue());
            case BOOLEAN -> cell.getBooleanCellValue() ? 1.0 : 0.0;
            case FORMULA -> switch (cell.getCachedFormulaResultType()) {
                case NUMERIC -> cell.getNumericCellValue();
                case STRING -> parseDoubleSafe(cell.getStringCellValue());
                default -> 0.0;
            };
            default -> 0.0;
        };
    }

    /** Дата ячейки как {@link LocalDate} (null для пустой/не-даты). */
    static LocalDate dateValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
        } catch (IllegalStateException ignored) {
            // не-дата
        }
        return null;
    }

    /** Строковое представление даты dd.MM.yyyy (или пусто). */
    static String dateString(Cell cell) {
        LocalDate d = dateValue(cell);
        return d == null ? "" : d.format(DATE_FMT);
    }

    /** Число → строка без экспоненты и без «хвостов» (1792723847, а не 1.792723847E9). */
    static String numericToString(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "";
        }
        if (value == Math.floor(value) && !Double.isInfinite(value) && Math.abs(value) < 1e18) {
            return BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP).toPlainString();
        }
        // дробные: до 2 знаков, без лишних нулей
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static double parseDoubleSafe(String s) {
        try {
            return Double.parseDouble(s.trim().replace(" ", "").replace("₽", "").replace(",", "."));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
