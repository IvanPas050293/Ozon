package com.alnative.ozon.parser;

import com.alnative.ozon.parser.model.ExcelRole;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Определяет роль xlsx-файла по именам листов:
 * начисления — лист «Начисления», продвижение — лист «Statistics»/«Аналитика».
 */
@Component
public class ExcelDocumentClassifier {

    private static final Logger log = LoggerFactory.getLogger(ExcelDocumentClassifier.class);

    public ExcelRole classify(Path path) throws IOException {
        try (Workbook workbook = ExcelWorkbooks.open(path)) {
            String lower = null;
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                String name = workbook.getSheetName(i);
                if (lower == null) {
                    lower = "";
                }
                lower += "|" + name;
            }
            if (lower == null) {
                return ExcelRole.UNKNOWN;
            }
            String l = lower.toLowerCase(Locale.ROOT);
            if (l.contains("начисл")) {
                return ExcelRole.ACCRUAL;
            }
            if (l.contains("statistics") || l.contains("аналитик") || l.contains("продвиж")) {
                return ExcelRole.PROMO;
            }
            return ExcelRole.UNKNOWN;
        }
    }
}
