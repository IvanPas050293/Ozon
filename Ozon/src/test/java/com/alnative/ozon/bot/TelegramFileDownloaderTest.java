package com.alnative.ozon.bot;

import com.alnative.ozon.config.BotProperties;
import com.alnative.ozon.parser.ExcelDocumentClassifier;
import com.alnative.ozon.parser.NagruzheniyaParser;
import com.alnative.ozon.parser.model.ExcelRole;
import com.alnative.ozon.parser.model.NagruzheniyaReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramFileDownloaderTest {

    private final TelegramFileDownloader downloader = new TelegramFileDownloader(new BotProperties("u", "t"));

    @TempDir
    Path tmp;

    @Test
    void acceptsRealXlsx() throws Exception {
        assertDoesNotThrow(() -> downloader.ensureXlsx(sample("Аналитика продвижения_10.08.2026.xlsx")));
    }

    @Test
    void rejectsHtmlPage() throws Exception {
        Path f = write("<!DOCTYPE html><html><head><title>403 Forbidden</title></head><body>blocked</body></html>");
        IOException e = assertThrows(IOException.class, () -> downloader.ensureXlsx(f));
        assertTrue(e.getMessage().contains("HTML-страница"), e.getMessage());
    }

    @Test
    void rejectsTelegramJsonError() throws Exception {
        Path f = write("{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: file is too big\"}");
        IOException e = assertThrows(IOException.class, () -> downloader.ensureXlsx(f));
        assertTrue(e.getMessage().contains("Telegram вернул ошибку"), e.getMessage());
        assertTrue(e.getMessage().contains("file is too big"), e.getMessage());
    }

    @Test
    void rejectsPlainText() throws Exception {
        Path f = write("просто текст, не архив");
        IOException e = assertThrows(IOException.class, () -> downloader.ensureXlsx(f));
        assertTrue(e.getMessage().contains("не является xlsx"), e.getMessage());
    }

    @Test
    void rejectsEmptyFile() throws Exception {
        Path f = tmp.resolve("empty.xlsx");
        Files.writeString(f, "");
        IOException e = assertThrows(IOException.class, () -> downloader.ensureXlsx(f));
        assertTrue(e.getMessage().contains("не является xlsx"), e.getMessage());
    }

    @Test
    void downloadedFileSurvivesRepeatedParse() throws Exception {
        // Копия файла во временной папке — как файл, скачанный ботом в %TEMP%.
        Path tmpFile = tmp.resolve("download.xlsx");
        Files.copy(sample("Отчет по начислениям_01.08.2026-10.08.2026.xlsx"), tmpFile);

        // 6 раундов: проверка → классификация → парсинг (имитация повторных открытий в build()).
        for (int i = 0; i < 6; i++) {
            downloader.ensureXlsx(tmpFile);
            assertEquals(ExcelRole.ACCRUAL, new ExcelDocumentClassifier().classify(tmpFile));
            NagruzheniyaReport report = new NagruzheniyaParser().parse(tmpFile);
            assertEquals(1417, report.accruals().size());
        }
    }

    @Test
    void rejectsZipThatIsNotXlsx() throws Exception {
        Path f = tmp.resolve("fake.xlsx");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(f))) {
            zos.putNextEntry(new ZipEntry("readme.txt"));
            zos.write("это не Excel".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        IOException e = assertThrows(IOException.class, () -> downloader.ensureXlsx(f));
        assertTrue(e.getMessage().contains("не xlsx"), e.getMessage());
        assertTrue(e.getMessage().contains("readme.txt"), e.getMessage());
    }

    private Path write(String content) throws IOException {
        Path f = tmp.resolve("file.bin");
        Files.writeString(f, content, StandardCharsets.UTF_8);
        return f;
    }

    private static Path sample(String name) {
        URL url = TelegramFileDownloaderTest.class.getResource("/samples/" + name);
        assertNotNull(url, "Не найден тестовый файл " + name);
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
