package com.alnative.ozon.parser;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Открытие xlsx как {@link XSSFWorkbook} с приведением {@link InvalidFormatException} к {@link IOException}.
 */
final class ExcelWorkbooks {

    private ExcelWorkbooks() {
    }

    static Workbook open(Path path) throws IOException {
        try {
            return new XSSFWorkbook(path.toFile());
        } catch (InvalidFormatException e) {
            throw new IOException("Файл не является корректным xlsx: " + path.getFileName(), e);
        }
    }
}
