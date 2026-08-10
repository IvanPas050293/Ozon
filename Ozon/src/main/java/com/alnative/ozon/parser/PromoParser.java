package com.alnative.ozon.parser;

import com.alnative.ozon.parser.model.PromoData;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Парсер файла «Аналитика продвижения»: лист Statistics (статистика кампаний) и Union (связка карточек).
 * Основной результат для дашборда — суммарный расход на продвижение (ДРР = расход / выручка).
 */
@Component
public class PromoParser {

    private static final Logger log = LoggerFactory.getLogger(PromoParser.class);

    private static final List<FieldMatcher> STAT_FIELDS = List.of(
            field("sku", h -> h.equals("sku")),
            field("name", h -> h.equals("названиетовара")),
            field("tool", h -> h.equals("инструмент")),
            field("placement", h -> h.equals("месторазмещения")),
            field("campaignId", h -> h.equals("idкампании")),
            field("expense", h -> h.startsWith("расход")),
            field("drr", h -> h.equals("дрр") || h.startsWith("дррвпродвижении")),
            field("sales", h -> h.equals("продаживпродвижении") || h.equals("продаживпродвижениируб") || h.equals("продажи")),
            field("soldUnits", h -> h.equals("проданотоваровшт") || h.equals("заказышт")),
            field("ctr", h -> h.startsWith("ctr")),
            field("impressions", h -> h.equals("показы")),
            field("clicks", h -> h.equals("клики")),
            field("cartAdds", h -> h.startsWith("добавлениявкорзину") || h.equals("вкорзину")),
            field("cartConversion", h -> h.startsWith("конверсиявкорзину")),
            field("costPerOrder", h -> h.startsWith("затратыназаказ")),
            field("costPerClick", h -> h.startsWith("стоимостьклика") || h.startsWith("средняястоимостьклика"))
    );

    private static final List<FieldMatcher> UNION_FIELDS = List.of(
            field("sku", h -> h.equals("skuвпродвижении")),
            field("name", h -> h.equals("названиетоваравпродвижении")),
            field("tool", h -> h.equals("инструмент")),
            field("placement", h -> h.equals("месторазмещения")),
            field("campaignId", h -> h.equals("idкампании")),
            field("unionSku", h -> h.equals("skuизобъединеннойкарточки")),
            field("unionName", h -> h.equals("названиетовараизобъединеннойкарточки")),
            field("sales", h -> h.equals("продаживпродвижении") || h.equals("продаживпродвижениируб")),
            field("soldUnits", h -> h.equals("проданотоваровшт"))
    );

    public PromoData parse(Path path) throws IOException {
        try (Workbook workbook = ExcelWorkbooks.open(path)) {
            Sheet stats = findSheet(workbook, "statistics", "аналитик", "продвиж");
            String periodStart = "";
            String periodEnd = "";
            double totalExpense = 0.0;
            List<PromoData.PromoStatRow> rows = new ArrayList<>();

            if (stats != null) {
                Row first = stats.getRow(0);
                String firstCell = first == null ? "" : ExcelReadUtil.stringValue(first.getCell(0));
                if (firstCell.startsWith("Период")) {
                    periodStart = parsePeriod(firstCell, 0);
                    periodEnd = parsePeriod(firstCell, 1);
                }

                HeaderMap h = findHeaderRow(stats, STAT_FIELDS, "sku", "expense");
                if (h == null) {
                    throw new IOException("Не найден заголовок листа Statistics («SKU»/«Расход»).");
                }
                for (int r = h.rowIndex() + 1; r <= stats.getLastRowNum(); r++) {
                    Row row = stats.getRow(r);
                    if (isBlankRow(row) || h.str(row, "sku").isBlank() && h.str(row, "expense").isBlank()) {
                        continue;
                    }
                    double expense = h.num(row, "expense");
                    totalExpense += expense;
                    rows.add(new PromoData.PromoStatRow(
                            h.str(row, "sku"), h.str(row, "name"), h.str(row, "tool"), h.str(row, "placement"),
                            h.str(row, "campaignId"), expense, h.num(row, "drr"), h.num(row, "sales"),
                            h.num(row, "soldUnits"), h.num(row, "ctr"), h.num(row, "impressions"),
                            h.num(row, "clicks"), h.num(row, "cartAdds"), h.num(row, "cartConversion"),
                            h.num(row, "costPerOrder"), h.num(row, "costPerClick")));
                }
            }

            Sheet union = findSheet(workbook, "union");
            List<PromoData.PromoUnionRow> unionRows = parseUnion(union);

            log.info("Продвижение: {} строк статистики, расход {}.", rows.size(), totalExpense);
            return new PromoData(periodStart, periodEnd, totalExpense, rows, unionRows);
        }
    }

    private List<PromoData.PromoUnionRow> parseUnion(Sheet sheet) {
        List<PromoData.PromoUnionRow> out = new ArrayList<>();
        if (sheet == null) {
            return out;
        }
        HeaderMap h = findHeaderRow(sheet, UNION_FIELDS, "sku", "unionSku");
        if (h == null) {
            return out;
        }
        for (int r = h.rowIndex() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (isBlankRow(row) || h.str(row, "sku").isBlank()) {
                continue;
            }
            out.add(new PromoData.PromoUnionRow(
                    h.str(row, "sku"), h.str(row, "name"), h.str(row, "tool"), h.str(row, "placement"),
                    h.str(row, "campaignId"), h.str(row, "unionSku"), h.str(row, "unionName"),
                    h.num(row, "sales"), h.num(row, "soldUnits")));
        }
        return out;
    }

    private Sheet findSheet(Workbook workbook, String... keywords) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String name = workbook.getSheetName(i).toLowerCase(Locale.ROOT);
            for (String kw : keywords) {
                if (name.contains(kw)) {
                    return workbook.getSheetAt(i);
                }
            }
        }
        return null;
    }

    private boolean isBlankRow(Row row) {
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

    /** Из строки «Период: DD.MM.YYYY-DD.MM.YYYY» извлекает start (idx 0) или end (idx 1). */
    private static String parsePeriod(String s, int idx) {
        int dash = s.indexOf('-', s.indexOf(':'));
        if (dash < 0) {
            return "";
        }
        String start = s.substring(s.indexOf(':') + 1, dash).trim();
        String end = s.substring(dash + 1).trim();
        return idx == 0 ? start : end;
    }

    // ---------------------------------------------------------------------

    private static FieldMatcher field(String name, Predicate<String> predicate) {
        return new FieldMatcher(name, predicate);
    }

    private record FieldMatcher(String name, Predicate<String> predicate) {
    }

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

        private String str(Row row, String field) {
            Integer c = colByField.get(field);
            return c == null ? "" : ExcelReadUtil.stringValue(row.getCell(c));
        }

        private double num(Row row, String field) {
            Integer c = colByField.get(field);
            return c == null ? 0.0 : ExcelReadUtil.numberValue(row.getCell(c));
        }
    }

    private HeaderMap findHeaderRow(Sheet sheet, List<FieldMatcher> fields, String... required) {
        for (int r = 0; r <= Math.min(sheet.getLastRowNum(), 30); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            Map<String, Integer> colByField = new LinkedHashMap<>();
            for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
                String header = NagruzheniyaParser.normalize(ExcelReadUtil.stringValue(row.getCell(c)));
                if (header.isBlank()) {
                    continue;
                }
                for (FieldMatcher f : fields) {
                    if (f.predicate().test(header) && !colByField.containsKey(f.name())) {
                        colByField.put(f.name(), c);
                    }
                }
            }
            boolean all = true;
            for (String req : required) {
                if (!colByField.containsKey(req)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return new HeaderMap(r, colByField);
            }
        }
        return null;
    }
}
