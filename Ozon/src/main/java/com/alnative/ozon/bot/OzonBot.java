package com.alnative.ozon.bot;

import com.alnative.ozon.calc.DashboardMetrics;
import com.alnative.ozon.catalog.ProductCost;
import com.alnative.ozon.catalog.ProductCostService;
import com.alnative.ozon.config.AppProperties;
import com.alnative.ozon.config.BotProperties;
import com.alnative.ozon.output.DashboardFormatter;
import com.alnative.ozon.parser.ExcelDocumentClassifier;
import com.alnative.ozon.parser.NagruzheniyaParser;
import com.alnative.ozon.parser.model.ExcelRole;
import com.alnative.ozon.parser.model.NagruzheniyaReport;
import com.alnative.ozon.service.DashboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Telegram-бот «Экономика магазина Ozon».
 * Принимает два xlsx-файла («Отчет по начислениям» и «Аналитика продвижения»),
 * считает и отправляет сводку дашборда.
 */
@Service
public class OzonBot implements SpringLongPollingBot, LongPollingUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(OzonBot.class);

    private static final String HELP = """
            📊 Экономика магазина Ozon

            Отправьте два файла из ЛК Ozon:
            1. Отчет по начислениям (Финансы → Экономика магазина → Отчет по начислениям)
            2. Аналитика продвижения (реклама)

            Бот распарсит их и покажет сводку дашборда: выручку, комиссии, логистику, прибыль, ДРР.

            Команды:
            /sebestoimost — список товаров и себестоимости
            /sebestoimost SKU-или-Артикул стоимость — задать себестоимость (название подставится из отчёта)
            /reset — начать заново
            """;

    private final BotProperties props;
    private final AppProperties appProps;
    private final TelegramClient client;
    private final ExcelDocumentClassifier classifier;
    private final NagruzheniyaParser nagruzheniyaParser;
    private final DashboardService dashboardService;
    private final ProductCostService productCostService;
    private final SessionStore sessionStore;
    private final DashboardFormatter formatter;

    public OzonBot(BotProperties props,
                   AppProperties appProps,
                   TelegramClient client,
                   ExcelDocumentClassifier classifier,
                   NagruzheniyaParser nagruzheniyaParser,
                   DashboardService dashboardService,
                   ProductCostService productCostService,
                   SessionStore sessionStore,
                   DashboardFormatter formatter) {
        this.props = props;
        this.appProps = appProps;
        this.client = client;
        this.classifier = classifier;
        this.nagruzheniyaParser = nagruzheniyaParser;
        this.dashboardService = dashboardService;
        this.productCostService = productCostService;
        this.sessionStore = sessionStore;
        this.formatter = formatter;
    }

    @Override
    public String getBotToken() {
        return props.token();
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(List<Update> updates) {
        for (Update update : updates) {
            if (update.hasMessage()) {
                handleMessage(update.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ handlers

    private void handleMessage(Message message) {
        Long chatId = message.getChatId();
        if (chatId == null || !isAllowed(chatId)) {
            return;
        }
        try {
            if (message.hasDocument()) {
                handleDocument(chatId, message.getDocument());
            } else if (message.getText() != null && message.getText().startsWith("/")) {
                handleCommand(chatId, message.getText().trim());
            } else {
                send(chatId, "Отправьте два файла xlsx: «Отчет по начислениям» и «Аналитика продвижения».\n" +
                        "Подробнее — /help");
            }
        } catch (Exception e) {
            log.error("Ошибка обработки сообщения от {}", chatId, e);
            send(chatId, "⚠️ Произошла ошибка: " + e.getMessage());
        }
    }

    private void handleCommand(Long chatId, String command) throws IOException {
        if (command.startsWith("/start") || command.startsWith("/help")) {
            send(chatId, HELP);
        } else if (command.startsWith("/reset")) {
            sessionStore.reset(chatId);
            send(chatId, "Сброшено. Отправьте файлы заново.");
        } else if (command.startsWith("/sebestoimost")) {
            handleSebestoimost(chatId, command);
        } else {
            send(chatId, "Неизвестная команда. /help");
        }
    }

    private void handleDocument(Long chatId, Document document) throws IOException, TelegramApiException {
        String fileName = document.getFileName() == null ? "" : document.getFileName();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            send(chatId, "Принимаю только файлы .xlsx");
            return;
        }
        Path file = download(document.getFileId());
        ExcelRole role = classifier.classify(file);
        UserSession session = sessionStore.get(chatId);
        switch (role) {
            case ACCRUAL -> {
                NagruzheniyaReport report = nagruzheniyaParser.parse(file);
                session.setAccrualFile(file);
                send(chatId, "✅ Принял: Отчет по начислениям (" + report.accruals().size() + " строк, "
                        + report.periodStart() + " — " + report.periodEnd() + ").");
            }
            case PROMO -> {
                session.setPromoFile(file);
                send(chatId, "✅ Принял: Аналитика продвижения.");
            }
            default -> {
                session.cleanup();
                send(chatId, "❌ Не распознал файл. Нужны: «Отчет по начислениям» и «Аналитика продвижения».");
                return;
            }
        }
        if (session.ready()) {
            try {
                DashboardMetrics metrics = dashboardService.build(session.getAccrualFile(), session.getPromoFile());
                send(chatId, formatter.format(metrics));
            } finally {
                session.cleanup();
            }
        }
    }

    private void handleSebestoimost(Long chatId, String command) {
        String[] parts = command.split("\\s+");
        if (parts.length >= 3) {
            // /sebestoimost <sku|артикул> <стоимость>
            String key = parts[1];
            double cost = parseCost(parts[2]);
            if (cost < 0) {
                send(chatId, "Себестоимость не может быть отрицательной.");
                return;
            }
            ProductCost pc = productCostService.setCost(key, cost);
            if (pc == null) {
                send(chatId, "Товар «" + key + "» не найден. Сначала отправьте «Отчет по начислениям» — бот узнает список товаров.\n" +
                        "Посмотреть список: /sebestoimost");
            } else {
                send(chatId, "✅ Себестоимость «" + pc.getName() + "» (" + pc.getSku() + ") = "
                        + formatMoney(cost) + " ₽");
            }
        } else {
            // список
            StringBuilder sb = new StringBuilder("📦 Себестоимость товаров\n");
            List<ProductCost> items = productCostService.list();
            if (items.isEmpty()) {
                sb.append("Список пуст. Отправьте «Отчет по начислениям», чтобы бот узнал товары,\n" +
                        "затем задайте себестоимость: /sebestoimost <SKU> <стоимость>");
            } else {
                sb.append("Задать: /sebestoimost <SKU|Артикул> <стоимость>\n\n");
                int i = 1;
                for (ProductCost pc : items) {
                    sb.append(i++).append(". ").append(pc.getName() == null ? pc.getSku() : pc.getName())
                            .append("\n   SKU ").append(pc.getSku())
                            .append(" · ").append(pc.getCost() == null ? "не задано" : formatMoney(pc.getCost()) + " ₽")
                            .append("\n");
                }
            }
            send(chatId, sb.toString());
        }
    }

    // ------------------------------------------------------------------ utils

    private Path download(String fileId) throws TelegramApiException {
        GetFile request = GetFile.builder().fileId(fileId).build();
        org.telegram.telegrambots.meta.api.objects.File f = client.execute(request);
        return client.downloadFile(f).toPath();
    }

    private void send(Long chatId, String text) {
        try {
            client.execute(SendMessage.builder().chatId(chatId).text(text).build());
        } catch (TelegramApiException e) {
            log.error("Не удалось отправить сообщение в чат {}", chatId, e);
        }
    }

    private boolean isAllowed(Long chatId) {
        String allowed = appProps.allowedChatIds();
        if (allowed == null || allowed.isBlank()) {
            return true;
        }
        for (String id : allowed.split(",")) {
            if (id.trim().equals(chatId.toString())) {
                return true;
            }
        }
        log.warn("Доступ запрещён для chat_id {}", chatId);
        return false;
    }

    private static double parseCost(String s) {
        try {
            return Double.parseDouble(s.trim().replace(',', '.').replace("₽", "").replace(" ", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String formatMoney(double v) {
        return String.format(Locale.ROOT, "%,.0f", v).replace(',', ' ');
    }
}
