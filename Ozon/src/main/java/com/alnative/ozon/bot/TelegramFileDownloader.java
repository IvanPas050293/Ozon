package com.alnative.ozon.bot;

import com.alnative.ozon.config.BotProperties;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.File;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Скачивание файла из Telegram с проверкой HTTP-статуса.
 * <p>
 * Штатный {@code TelegramClient#downloadFile} в telegrambots 10.2.0 пишет во временный
 * файл тело ответа даже при HTTP != 200: ошибка/HTML-страница сохраняются как «файл»,
 * и POI потом сообщает «Файл не является корректным xlsx». Поэтому качаем сами:
 * URL собирается с экранированием пути, проверяется статус, у файла проверяется ZIP-магия.
 */
@Component
public class TelegramFileDownloader {

    private static final Logger log = LoggerFactory.getLogger(TelegramFileDownloader.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(120);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Pattern TELEGRAM_DESCRIPTION = Pattern.compile("\"description\"\\s*:\\s*\"([^\"]+)\"");

    private final BotProperties props;

    public TelegramFileDownloader(BotProperties props) {
        this.props = props;
    }

    /** Скачивает файл из Telegram во временный файл с расширением .xlsx. */
    public Path download(File file) throws IOException {
        String url = fileUrl(file);
        log.info("Скачиваю файл {} ({})", file.getFilePath(), url);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .GET()
                .build();
        HttpResponse<InputStream> response = send(request);
        if (response.statusCode() != 200) {
            throw new IOException("Telegram не отдал файл (HTTP " + response.statusCode() + "): " + errorText(response));
        }
        Path tmp = Files.createTempFile("ozon-", ".xlsx");
        try (InputStream in = response.body()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("Скачан файл {} ({} байт, сервер: {}, файл Telegram: {})",
                tmp, Files.size(tmp), file.getFilePath(), file.getFileId());
        return tmp;
    }

    /**
     * Проверяет, что файл — настоящий xlsx.
     * <p>
     * 1) ZIP-сигнатура «PK»; 2) внутри — обязательные части OOXML
     * («[Content_Types].xml» и «xl/workbook.xml»); 3) файл открывается POI.
     *
     * @throws IOException с понятным объяснением, если это не xlsx (ошибка Telegram,
     *                     HTML вместо файла, ZIP не xlsx, повреждённый файл)
     */
    public void ensureXlsx(Path path) throws IOException {
        byte[] head;
        try (InputStream in = Files.newInputStream(path)) {
            head = in.readNBytes(1024);
        }
        if (head.length >= 2 && head[0] == 'P' && head[1] == 'K') {
            validateXlsxZip(path);
            return;
        }
        throw new IOException(diagnose(head));
    }

    private static void validateXlsxZip(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            boolean hasContentTypes = zip.getEntry("[Content_Types].xml") != null;
            boolean hasWorkbook = zip.getEntry("xl/workbook.xml") != null;
            if (!hasContentTypes || !hasWorkbook) {
                throw new IOException("Файл — ZIP-архив, но внутри не xlsx: нет обязательных частей "
                        + "(xl/workbook.xml, [Content_Types].xml). Содержимое архива: " + entries(zip));
            }
        } catch (ZipException e) {
            throw new IOException("Файл повреждён или скачан не полностью: " + e.getMessage(), e);
        }
        // Финальная проверка — откроется ли файл как Excel-книга; показываем настоящую причину POI.
        try (XSSFWorkbook ignored = new XSSFWorkbook(path.toFile())) {
            log.info("Файл {} открылся как Excel: листы {}", path.getFileName(), sheetNames(ignored));
        } catch (Exception e) {
            throw new IOException("Файл повреждён (" + rootCause(e).getMessage() + ")", e);
        }
    }

    private static String sheetNames(Workbook wb) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('«').append(wb.getSheetName(i)).append('»');
        }
        return sb.toString();
    }

    /** Самый глубокий {@link Throwable} в цепочке (для понятной причины). */
    private static Throwable rootCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static String entries(ZipFile zip) {
        List<String> names = new ArrayList<>();
        Enumeration<? extends ZipEntry> it = zip.entries();
        while (it.hasMoreElements() && names.size() < 30) {
            names.add(it.nextElement().getName());
        }
        return names.isEmpty() ? "архив пуст" : String.join(", ", names);
    }

    private HttpResponse<InputStream> send(HttpRequest request) throws IOException {
        try {
            return HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Прервано скачивание файла из Telegram", e);
        }
    }

    private String fileUrl(File file) throws IOException {
        String filePath = file.getFilePath();
        if (filePath == null || filePath.isBlank()) {
            throw new IOException("Telegram не вернул путь к файлу (пустой file_path)");
        }
        String encodedPath = Arrays.stream(filePath.split("/"))
                .map(TelegramFileDownloader::encodeSegment)
                .collect(Collectors.joining("/"));
        return "https://api.telegram.org/file/bot" + props.token() + "/" + encodedPath;
    }

    private static String encodeSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Человекочитаемая причина, почему скачанный файл не xlsx. */
    private static String diagnose(byte[] head) {
        String text = new String(head, StandardCharsets.UTF_8).trim();
        if (text.startsWith("{") || text.startsWith("[")) {
            Matcher m = TELEGRAM_DESCRIPTION.matcher(text);
            String desc = m.find() ? m.group(1) : clip(text);
            return "Telegram вернул ошибку вместо файла: " + desc;
        }
        if (text.toLowerCase(Locale.ROOT).contains("<html") || text.toLowerCase(Locale.ROOT).startsWith("<!doctype")) {
            return "Скачалась HTML-страница вместо xlsx — похоже на блокировку или перехват трафика к Telegram";
        }
        return "файл повреждён или не является xlsx (не ZIP-архив)";
    }

    private static String errorText(HttpResponse<InputStream> response) {
        try (InputStream in = response.body()) {
            String body = new String(in.readNBytes(2048), StandardCharsets.UTF_8).trim();
            Matcher m = TELEGRAM_DESCRIPTION.matcher(body);
            return m.find() ? m.group(1) : clip(body);
        } catch (IOException e) {
            return "тело ответа не прочитано";
        }
    }

    private static String clip(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }
}
