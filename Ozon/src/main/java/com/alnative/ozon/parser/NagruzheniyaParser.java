package com.alnative.ozon.parser;

import com.alnative.ozon.parser.model.Accrual;
import com.alnative.ozon.parser.model.NagruzheniyaReport;
import org.apache.poi.ss.usermodel.Cell;
import org.springframework.stereotype.Component;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Парсер отчёта «Отчет по начислениям».
 * <p>
 * Структура файла: строка 1 — «Период: DD.MM.YYYY-DD.MM.YYYY», строка 2 — заголовки,
 * данные — со строки 3. Заголовки ищутся по содержимому, поэтому файл устойчив к порядку колонок.
 */
@Component
public class NagruzheniyaParser {

    private static final Logger log = LoggerFactory.getLogger(NagruzheniyaParser.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /**
     * Разбирает файл начислений.
     *
     * @param path путь к xlsx
     * @return распарсенный отчёт
     * @throws IOException при ошибке чтения/структуры файла
     */
    public NagruzheniyaReport parse(Path path) throws IOException {
        try (Workbook workbook = ExcelWorkbooks.open(path)) {
            Sheet sheet = selectSheet(workbook);
            HeaderMap headers = findHeaderRow(sheet);
            if (headers == null) {
                throw new IOException("Не найден заголовок отчёта начислений (нет колонки «ID начисления»/«Тип начисления»).");
            }

            List<Accrual> accruals = new ArrayList<>();
            Map<String, String> skuToName = new LinkedHashMap<>();
            LocalDate min = null;
            LocalDate max = null;

            int headerRowIdx = headers.rowIndex();
            for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (isEmptyRow(row)) {
                    continue;
                }
                Accrual a = parseRow(row, headers);
                if (a == null) {
                    continue;
                }
                accruals.add(a);
                if (a.date() != null) {
                    min = min == null ? a.date() : min.isAfter(a.date()) ? a.date() : min;
                    max = max == null ? a.date() : max.isBefore(a.date()) ? a.date() : max;
                }
                if (!a.sku().isBlank() && !a.name().isBlank()) {
                    skuToName.putIfAbsent(a.sku(), a.name());
                }
            }
            if (accruals.isEmpty()) {
                throw new IOException("В файле начислений нет строк данных.");
            }

            String start = min == null ? "" : min.format(FMT);
            String end = max == null ? "" : max.format(FMT);
            log.info("Начисления: {} строк, период {} — {}", accruals.size(), start, end);
            return new NagruzheniyaReport(accruals, skuToName, start, end);
        }
    }

    /** Лист с начислениями: предпочитаем лист с «Начисл» в имени, иначе первый. */
    private Sheet selectSheet(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String name = workbook.getSheetName(i);
            if (name.toLowerCase(Locale.ROOT).contains("начисл")) {
                return workbook.getSheetAt(i);
            }
        }
        return workbook.getSheetAt(0);
    }

    private boolean isEmptyRow(Row row) {
        if (row == null) {
            return true;
        }
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && !cell.toString().isBlank()) {
                return false;
            }
        }
        return true;
    }

    private Accrual parseRow(Row row, HeaderMap h) {
        // Пропускаем служебные/не-строки: без ID начисления и без типа — не строка начисления
        String id = h.str(row, "id");
        String type = h.str(row, "type");
        if (id.isBlank() && type.isBlank()) {
            return null;
        }
        return new Accrual(
                id,
                h.date(row, "date"),
                h.str(row, "group"),
                type,
                h.str(row, "artikul"),
                h.str(row, "sku"),
                h.str(row, "name"),
                h.num(row, "qty"),
                h.num(row, "sellerPrice"),
                h.date(row, "acceptDate"),
                h.str(row, "platform"),
                h.str(row, "scheme"),
                h.num(row, "rewardPct"),
                h.num(row, "total")
        );
    }

    // ---------------------------------------------------------------------
    // Заголовки
    // ---------------------------------------------------------------------

    private static final List<FieldMatcher> FIELDS = List.of(
            field("id", h -> h.equals("idначисления")),
            field("date", h -> h.equals("датаначисления")),
            field("group", h -> h.equals("группауслуг")),
            field("type", h -> h.equals("типначисления")),
            field("artikul", h -> h.equals("артикул")),
            field("sku", h -> h.equals("sku")),
            field("name", h -> h.contains("названиетовара")),
            field("qty", h -> h.equals("количество")),
            field("sellerPrice", h -> h.startsWith("ценапродавца")),
            field("acceptDate", h -> h.startsWith("датапринятия")),
            field("platform", h -> h.startsWith("платформа")),
            field("scheme", h -> h.equals("схемаработы")),
            field("rewardPct", h -> h.startsWith("вознаграждениеozon")),
            field("total", h -> h.startsWith("суммаитого"))
    );

    private static FieldMatcher field(String name, Predicate<String> predicate) {
        return new FieldMatcher(name, predicate);
    }

    private record FieldMatcher(String name, Predicate<String> predicate) {
    }

    /** Маппинг колонок по имени поля + индекс строки заголовка. */
    private static final class HeaderMap {
        private final int rowIndex;
        private final Map<String, Integer> colByField;

        private HeaderMap(int rowIndex, Map<String, Integer> colByField) {
            this.rowIndex = rowIndex;
            this.colByField = colByField;
        }

        private int rowIndex() {
            return rowIndex;
        }

        private int index(String field) {
            Integer c = colByField.get(field);
            return c == null ? -1 : c;
        }

        private String str(Row row, String field) {
            int c = index(field);
            return c < 0 ? "" : ExcelReadUtil.stringValue(row.getCell(c));
        }

        private double num(Row row, String field) {
            int c = index(field);
            return c < 0 ? 0.0 : ExcelReadUtil.numberValue(row.getCell(c));
        }

        private LocalDate date(Row row, String field) {
            int c = index(field);
            return c < 0 ? null : ExcelReadUtil.dateValue(row.getCell(c));
        }
    }

    private HeaderMap findHeaderRow(Sheet sheet) {
        for (int r = 0; r <= Math.min(sheet.getLastRowNum(), 30); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            Map<String, Integer> colByField = new LinkedHashMap<>();
            for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
                String header = normalize(ExcelReadUtil.stringValue(row.getCell(c)));
                if (header.isBlank()) {
                    continue;
                }
                for (FieldMatcher f : FIELDS) {
                    if (f.predicate().test(header) && !colByField.containsKey(f.name())) {
                        colByField.put(f.name(), c);
                    }
                }
            }
            if (colByField.containsKey("id") && colByField.containsKey("type")
                    || colByField.containsKey("date") && colByField.containsKey("total")) {
                return new HeaderMap(r, colByField);
            }
        }
        return null;
    }

    /** Нормализация заголовка: нижний регистр, без пробелов/пунктуации/символов ₽ и %. */
    static String normalize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char ch : s.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
