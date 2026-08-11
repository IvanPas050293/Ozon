package com.alnative.ozon.bot;

import com.alnative.ozon.calc.DashboardMetrics;
import com.alnative.ozon.catalog.ProductCost;
import com.alnative.ozon.catalog.ProductCostService;
import com.alnative.ozon.config.AppProperties;
import com.alnative.ozon.config.BotProperties;
import com.alnative.ozon.output.DashboardFormatter;
import com.alnative.ozon.parser.ExcelDocumentClassifier;
import com.alnative.ozon.parser.NagruzheniyaParser;
import com.alnative.ozon.parser.PromoParser;
import com.alnative.ozon.parser.model.ExcelRole;
import com.alnative.ozon.parser.model.NagruzheniyaReport;
import com.alnative.ozon.parser.model.PromoData;
import com.alnative.ozon.service.DashboardService;
import com.alnative.ozon.settings.ShopSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

    /** Префикс callback-данных кнопок «задать себестоимость»: {@code seb:<sku>}. */
    private static final String CALLBACK_SEB = "seb:";
    /** Callback кнопки «Отмена» в списке товаров. */
    private static final String CALLBACK_CANCEL = "cancel";
    /** Префикс callback-данных кнопок выбора налога: {@code tax:<pct>}. */
    private static final String CALLBACK_TAX = "tax:";
    /** Callback кнопки «Статистика по товарам». */
    private static final String CALLBACK_STATS = "stats";
    /** Callback кнопки «Себестоимость» (редактирование цен). */
    private static final String CALLBACK_COST = "cost";

    /** Кнопка «Налог» постоянной клавиатуры. */
    private static final String BTN_TAX = "💸 Налог";
    /** Кнопка «Статистика по товарам» постоянной клавиатуры. */
    private static final String BTN_STATS = "📊 Статистика";
    /** Кнопка «Dashboard» постоянной клавиатуры. */
    private static final String BTN_DASH = "📈 Dashboard";
    /** Кнопка «Себестоимость» постоянной клавиатуры. */
    private static final String BTN_COST = "📦 Себестоимость";

    private static final String HELP = """
            📊 Экономика магазина Ozon

            Отправьте два файла из ЛК Ozon:
            1. Отчет по начислениям (Финансы → Экономика магазина → Отчет по начислениям)
            2. Аналитика продвижения (реклама)

            Бот распарсит их и покажет сводку дашборда: выручку, комиссии, логистику, прибыль, ДРР.

            Команды:
            /start — приветствие и настройка налога
            /dashboard — показать дашборд (если файлы отправлены)
            /tovary — статистика по товарам
            /nalog — выбрать налоговую ставку (1–12%)
            /sebestoimost — задать себестоимость товаров кнопками
            /reset — начать заново

            Все команды можно нажимать кнопками постоянного меню внизу чата.
            """;

    /** Приветствие: описание возможностей бота + просьба ввести процент налога. */
    private static final String WELCOME = """
            👋 Привет! Я — бот «Экономика магазина Ozon».

            📊 Что я умею:

            🔹 Dashboard — считаю экономику магазина по файлам из ЛК Ozon:
            выручку, комиссии, логистику, себестоимость, прибыль и ДРР.
            Для этого отправьте два файла:
              1. «Отчет по начислениям» (Финансы → Экономика магазина)
              2. «Аналитика продвижения» (реклама)

            🔹 Статистика по товарам — прибыль по каждому товару
            (за 1 шт и за весь период), маржа, налог и расход на рекламу.

            🔹 Себестоимость — задайте цену товара, чтобы прибыль
            считалась с учётом себестоимости.

            🔹 Налог — укажите вашу налоговую ставку (от 1 до 12%),
            и все расчёты будут с учётом неё.

            ➡️ Чтобы начать, выберите кнопку внизу или отправьте файлы.
            А сейчас давайте настроим налог.
            """;

    private final BotProperties props;
    private final AppProperties appProps;
    private final TelegramClient client;
    private final TelegramFileDownloader downloader;
    private final ExcelDocumentClassifier classifier;
    private final NagruzheniyaParser nagruzheniyaParser;
    private final PromoParser promoParser;
    private final DashboardService dashboardService;
    private final ProductCostService productCostService;
    private final ShopSettingsService shopSettingsService;
    private final SessionStore sessionStore;
    private final DashboardFormatter formatter;

    public OzonBot(BotProperties props,
                   AppProperties appProps,
                   TelegramClient client,
                   TelegramFileDownloader downloader,
                   ExcelDocumentClassifier classifier,
                   NagruzheniyaParser nagruzheniyaParser,
                   PromoParser promoParser,
                   DashboardService dashboardService,
                   ProductCostService productCostService,
                   ShopSettingsService shopSettingsService,
                   SessionStore sessionStore,
                   DashboardFormatter formatter) {
        this.props = props;
        this.appProps = appProps;
        this.client = client;
        this.downloader = downloader;
        this.classifier = classifier;
        this.nagruzheniyaParser = nagruzheniyaParser;
        this.promoParser = promoParser;
        this.dashboardService = dashboardService;
        this.productCostService = productCostService;
        this.shopSettingsService = shopSettingsService;
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
            if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
            } else if (update.hasMessage()) {
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
                return;
            }
            String text = message.getText();
            if (text != null && text.startsWith("/")) {
                handleCommand(chatId, text.trim());
                return;
            }
            if (text != null && handleKeyboardButton(chatId, text)) {
                return;
            }
            UserSession session = sessionStore.get(chatId);
            if (session.getPendingCostSku() != null) {
                // Ждём число — себестоимость для выбранного кнопкой товара.
                handleCostInput(chatId, text == null ? "" : text.trim());
                return;
            }
            send(chatId, "Отправьте два файла xlsx: «Отчет по начислениям» и «Аналитика продвижения».\n" +
                    "Подробнее — /help");
        } catch (Exception e) {
            log.error("Ошибка обработки сообщения от {}", chatId, e);
            send(chatId, "⚠️ Произошла ошибка: " + e.getMessage());
        }
    }

    private void handleCommand(Long chatId, String command) {
        // Команда прерывает ожидание ввода себестоимости.
        sessionStore.get(chatId).setPendingCostSku(null);
        if (command.startsWith("/start")) {
            sendWelcome(chatId);
        } else if (command.startsWith("/help")) {
            send(chatId, HELP);
        } else if (command.startsWith("/dashboard")) {
            showDashboardIfReady(chatId, sessionStore.get(chatId));
        } else if (command.startsWith("/reset")) {
            sessionStore.reset(chatId);
            send(chatId, "Сброшено. Отправьте файлы заново.");
        } else if (command.startsWith("/sebestoimost")) {
            handleSebestoimost(chatId, command);
        } else if (command.startsWith("/nalog")) {
            handleNalog(chatId);
        } else if (command.startsWith("/tovary")) {
            handleProductStats(chatId);
        } else {
            send(chatId, "Неизвестная команда. /help");
        }
    }

    /** Приветствие: описание возможностей + просьба ввести процент налога, с постоянным меню внизу. */
    private void sendWelcome(Long chatId) {
        send(chatId, WELCOME, mainMenuReplyKeyboard());
        // После описания — сразу предлагаем выбрать налоговую ставку.
        handleNalog(chatId);
    }

    /** Кнопка постоянного меню внизу чата → соответствующая команда. Возвращает false, если текст не кнопка. */
    private boolean handleKeyboardButton(Long chatId, String text) {
        switch (text) {
            case BTN_TAX -> handleCommand(chatId, "/nalog");
            case BTN_STATS -> handleCommand(chatId, "/tovary");
            case BTN_DASH -> handleCommand(chatId, "/dashboard");
            case BTN_COST -> handleCommand(chatId, "/sebestoimost");
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Постоянная клавиатура внизу чата: команды нажатием кнопки, без набора текста. */
    private static ReplyKeyboardMarkup mainMenuReplyKeyboard() {
        return ReplyKeyboardMarkup.builder()
                .keyboard(List.of(
                        new KeyboardRow(List.of(
                                KeyboardButton.builder().text(BTN_DASH).build(),
                                KeyboardButton.builder().text(BTN_TAX).build())),
                        new KeyboardRow(List.of(
                                KeyboardButton.builder().text(BTN_STATS).build(),
                                KeyboardButton.builder().text(BTN_COST).build()))))
                .resizeKeyboard(true)
                .isPersistent(true)
                .inputFieldPlaceholder("Выберите команду кнопкой")
                .build();
    }

    /** Кнопки выбора налоговой ставки магазина (1–12%). */
    private void handleNalog(Long chatId) {
        int current = shopSettingsService.getTaxRatePct(chatId);
        List<InlineKeyboardRow> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (int pct = 1; pct <= 12; pct++) {
            row.add(InlineKeyboardButton.builder()
                    .text(pct + "%")
                    .callbackData(CALLBACK_TAX + pct)
                    .build());
            if (pct % 4 == 0) {
                rows.add(new InlineKeyboardRow(row));
                row = new ArrayList<>();
            }
        }
        if (!row.isEmpty()) {
            rows.add(new InlineKeyboardRow(row));
        }
        send(chatId, "💸 Налоговая ставка магазина\nТекущая: " + current + "%\nВыберите ставку:",
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void handleDocument(Long chatId, Document document) {
        String fileName = document.getFileName() == null ? "" : document.getFileName();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            send(chatId, "Принимаю только файлы .xlsx");
            return;
        }
        Path file = null;
        try {
            file = download(document);
            downloader.ensureXlsx(file);
            ExcelRole role = classifier.classify(file);
            UserSession session = sessionStore.get(chatId);
            switch (role) {
                case ACCRUAL -> {
                    NagruzheniyaReport report = nagruzheniyaParser.parse(file);
                    session.setAccrual(report);
                    send(chatId, "✅ Принял: Отчет по начислениям (" + report.accruals().size() + " строк, "
                            + report.periodStart() + " — " + report.periodEnd() + ").");
                }
                case PROMO -> {
                    PromoData promo = promoParser.parse(file);
                    session.setPromo(promo);
                    send(chatId, "✅ Принял: Аналитика продвижения.");
                }
                default -> {
                    session.cleanup();
                    send(chatId, "❌ Не распознал файл. Нужны: «Отчет по начислениям» и «Аналитика продвижения».");
                }
            }
            if (session.ready()) {
                buildDashboard(chatId, session);
            }
        } catch (IOException | TelegramApiException e) {
            Path kept = preserveFailedFile(file, fileName);
            log.warn("Не удалось обработать файл {} от {}: {}", fileName, chatId, e.getMessage(), e);
            if (kept != null) {
                log.warn("Неудачный файл сохранён для диагностики: {}", kept);
            }
            send(chatId, "❌ Не удалось прочитать файл: " + e.getMessage()
                    + "\nПроверьте, что файл выгружен из ЛК Ozon и не пересохранён другой программой, затем отправьте ещё раз.");
        } finally {
            deleteQuietly(file);
        }
    }

    /**
     * Собирает дашборд из распарсенных данных сессии.
     * Сессию не очищаем: отчёты остаются в памяти, чтобы после ввода себестоимости
     * дашборд можно было показать вновь с учётом новых цен (сброс — /reset или новые файлы).
     */
    private void buildDashboard(Long chatId, UserSession session) {
        try {
            DashboardMetrics metrics = dashboardService.build(chatId, session.getAccrual(), session.getPromo());
            send(chatId, formatter.format(metrics), productStatsKeyboard());
        } catch (RuntimeException e) {
            log.warn("Не удалось собрать дашборд для {}: {}", chatId, e.getMessage(), e);
            send(chatId, "⚠️ Ошибка расчёта дашборда: " + e.getMessage());
        }
    }

    /** Кнопки под дашбордом: статистика по товарам + редактирование себестоимости. */
    private static InlineKeyboardMarkup productStatsKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        new InlineKeyboardRow(InlineKeyboardButton.builder()
                                .text("📊 Статистика по товарам")
                                .callbackData(CALLBACK_STATS)
                                .build()),
                        new InlineKeyboardRow(InlineKeyboardButton.builder()
                                .text(BTN_COST)
                                .callbackData(CALLBACK_COST)
                                .build())))
                .build();
    }

    /** Статистика по товарам: прибыль за 1 шт и за период, маржа, налог, реклама. */
    private void handleProductStats(Long chatId) {
        UserSession session = sessionStore.get(chatId);
        if (!session.ready()) {
            send(chatId, "Сначала отправьте «Отчет по начислениям» и «Аналитика продвижения»,\n" +
                    "чтобы посчитать статистику по товарам.");
            return;
        }
        try {
            String text = dashboardService.productStats(chatId, session.getAccrual(), session.getPromo());
            sendLong(chatId, text);
        } catch (RuntimeException e) {
            log.error("Не удалось посчитать статистику по товарам для {}", chatId, e);
            send(chatId, "⚠️ Ошибка расчёта статистики по товарам: " + e.getMessage());
        }
    }

    private void handleSebestoimost(Long chatId, String command) {
        String[] parts = command.split("\\s+");
        if (parts.length >= 3) {
            // /sebestoimost <sku|артикул> <стоимость> — быстрый ввод без кнопок
            String key = parts[1];
            double cost = parseCost(parts[2]);
            if (cost < 0) {
                send(chatId, "Себестоимость не может быть отрицательной.");
                return;
            }
            ProductCost pc = productCostService.setCost(chatId, key, cost);
            if (pc == null) {
                send(chatId, "Товар «" + key + "» не найден. Сначала отправьте «Отчет по начислениям» — бот узнает список товаров.\n" +
                        "Посмотреть список: /sebestoimost");
            } else {
                send(chatId, "✅ Себестоимость «" + pc.getName() + "» (" + pc.getSku() + ") = "
                        + formatMoney(cost) + " ₽");
                afterCostSet(chatId);
            }
            return;
        }

        // Список товаров кнопками: нажатие → бот спрашивает сумму.
        showCostButtons(chatId, null);
    }

    /** Нажатие inline-кнопки: себестоимость (seb:) или налоговая ставка (tax:). */
    private void handleCallbackQuery(CallbackQuery callback) {
        try {
            String data = callback.getData();
            if (data == null) {
                return;
            }
            Long chatId = callback.getMessage() == null ? null : callback.getMessage().getChatId();
            if (chatId == null || !isAllowed(chatId)) {
                return;
            }
            if (data.startsWith(CALLBACK_SEB)) {
                handleSebCallback(chatId, data.substring(CALLBACK_SEB.length()));
            } else if (data.startsWith(CALLBACK_TAX)) {
                handleTaxCallback(chatId, data.substring(CALLBACK_TAX.length()));
            } else if (CALLBACK_COST.equals(data)) {
                handleSebestoimost(chatId, "/sebestoimost");
            } else if (CALLBACK_STATS.equals(data)) {
                handleProductStats(chatId);
            } else {
                return;
            }
            answerCallback(callback.getId());
        } catch (Exception e) {
            log.error("Ошибка обработки callback {}", callback.getData(), e);
        }
    }

    /** Кнопка товара: запоминаем выбранный SKU и просим ввести сумму. */
    private void handleSebCallback(Long chatId, String sku) {
        UserSession session = sessionStore.get(chatId);
        if (CALLBACK_CANCEL.equals(sku)) {
            session.setPendingCostSku(null);
            send(chatId, "Отменено. Чтобы задать себестоимость заново: /sebestoimost");
            return;
        }
        ProductCost pc = productCostService.findByKey(chatId, sku);
        if (pc == null) {
            session.setPendingCostSku(null);
            send(chatId, "Товар не найден. Отправьте «Отчет по начислениям», затем /sebestoimost");
        } else {
            session.setPendingCostSku(pc.getSku());
            send(chatId, "Введите стоимость товара «" + pc.getName() + "» (SKU " + pc.getSku() + ") в рублях.\n" +
                    "Например: 500");
        }
    }

    /** Кнопка налоговой ставки: сохраняем выбор и пересчитываем дашборд, если отчёты в сессии. */
    private void handleTaxCallback(Long chatId, String value) {
        int pct;
        try {
            pct = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return;
        }
        if (pct < 1 || pct > 12) {
            send(chatId, "Ставка должна быть от 1% до 12%.");
            return;
        }
        shopSettingsService.setTaxRatePct(chatId, pct);
        send(chatId, "✅ Налоговая ставка магазина: " + pct + "%");
        showDashboardIfReady(chatId, sessionStore.get(chatId));
    }

    /** Ввод суммы обычным текстом после выбора товара кнопкой. */
    private void handleCostInput(Long chatId, String text) {
        UserSession session = sessionStore.get(chatId);
        String sku = session.getPendingCostSku();
        double cost = parseCost(text);
        if (cost < 0) {
            send(chatId, "Введите стоимость числом, например: 500");
            return;
        }
        session.setPendingCostSku(null);
        ProductCost pc = productCostService.setCost(chatId, sku, cost);
        if (pc == null) {
            send(chatId, "Товар «" + sku + "» не найден. Сначала отправьте «Отчет по начислениям».");
            return;
        }
        send(chatId, "✅ Себестоимость «" + pc.getName() + "» (" + pc.getSku() + ") = "
                + formatMoney(cost) + " ₽");
        afterCostSet(chatId);
    }

    /** Снимает «крутилку» с нажатой inline-кнопки. */
    private void answerCallback(String callbackQueryId) {
        try {
            client.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackQueryId).build());
        } catch (TelegramApiException e) {
            log.debug("Не удалось ответить на callback {}", callbackQueryId, e);
        }
    }

    /** Текст кнопки товара: проставленные помечаются ✓ с ценой, остальные — с SKU. */
    private static String buttonLabel(ProductCost pc) {
        String name = pc.getName() == null || pc.getName().isBlank() ? pc.getSku() : pc.getName();
        if (name.length() > 24) {
            name = name.substring(0, 23) + "…";
        }
        if (pc.getCost() != null) {
            return "✅ " + name + " · " + formatMoney(pc.getCost()) + " ₽";
        }
        return name + " · SKU " + pc.getSku();
    }

    /** Кнопки товаров магазина. Проставленные видны с ценой, остальные — ждут ввода. */
    private void showCostButtons(Long chatId, String header) {
        List<ProductCost> items = productCostService.list(chatId);
        if (items.isEmpty()) {
            send(chatId, "Список товаров пуст. Отправьте «Отчет по начислениям», чтобы бот узнал товары,\n" +
                    "затем откройте список: /sebestoimost");
            return;
        }
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (ProductCost pc : items) {
            rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder()
                    .text(buttonLabel(pc))
                    .callbackData(CALLBACK_SEB + pc.getSku())
                    .build()));
        }
        rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder()
                .text("❌ Отмена")
                .callbackData(CALLBACK_SEB + CALLBACK_CANCEL)
                .build()));
        String text = (header == null ? "📦 Себестоимость товаров" : header)
                + "\nНажмите товар → введите цену:";
        send(chatId, text, InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    /**
     * После успешного ввода цены: если остались товары без цены — снова показываем кнопки,
     * иначе проставляем финиш и собираем дашборд с учётом себестоимости.
     */
    private void afterCostSet(Long chatId) {
        List<ProductCost> items = productCostService.list(chatId);
        if (items.isEmpty()) {
            return;
        }
        long remaining = items.stream().filter(pc -> pc.getCost() == null).count();
        if (remaining == 0) {
            send(chatId, "🎉 Все себестоимости проставлены.");
            showDashboardIfReady(chatId, sessionStore.get(chatId));
        } else {
            showCostButtons(chatId, "Осталось проставить: " + remaining);
        }
    }

    /** Если оба отчёта в сессии — собираем дашборд (с учётом новых цен), иначе просим отправить файлы. */
    private void showDashboardIfReady(Long chatId, UserSession session) {
        if (session.ready()) {
            buildDashboard(chatId, session);
        } else {
            send(chatId, "Чтобы посчитать дашборд с учётом себестоимости, отправьте файлы:\n" +
                    "«Отчет по начислениям» и «Аналитика продвижения».");
        }
    }

    // ------------------------------------------------------------------ utils

    private Path download(Document document) throws TelegramApiException, IOException {
        GetFile request = GetFile.builder().fileId(document.getFileId()).build();
        org.telegram.telegrambots.meta.api.objects.File f = client.execute(request);
        return downloader.download(f);
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

    /** Сохраняет неудачно обработанный файл в data/failed/ для диагностики. */
    private static Path preserveFailedFile(Path file, String originalName) {
        if (file == null) {
            return null;
        }
        try {
            Path dir = Path.of("data", "failed");
            Files.createDirectories(dir);
            String safe = originalName == null || originalName.isBlank()
                    ? "unknown.bin"
                    : originalName.replaceAll("[^A-Za-z0-9._\\- ]", "_");
            Path target = dir.resolve(safe);
            int n = 1;
            while (Files.exists(target)) {
                target = dir.resolve(n + "_" + safe);
                n++;
            }
            Files.copy(file, target);
            return target;
        } catch (IOException ex) {
            log.warn("Не удалось сохранить файл для диагностики: {}", ex.getMessage());
            return null;
        }
    }

    private void send(Long chatId, String text) {
        send(chatId, text, null);
    }

    /** Отправляет длинное сообщение, разбивая по строкам (лимит Telegram — 4096 символов). */
    private void sendLong(Long chatId, String text) {
        if (text.length() <= 4000) {
            send(chatId, text);
            return;
        }
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : text.split("\n")) {
            if (!cur.isEmpty() && cur.length() + line.length() + 1 > 4000) {
                parts.add(cur.toString());
                cur = new StringBuilder();
            }
            cur.append(line).append('\n');
        }
        if (!cur.isEmpty()) {
            parts.add(cur.toString());
        }
        for (String p : parts) {
            send(chatId, p);
        }
    }

    private void send(Long chatId, String text, ReplyKeyboard replyMarkup) {
        try {
            SendMessage message = replyMarkup == null
                    ? SendMessage.builder().chatId(chatId).text(text).build()
                    : SendMessage.builder().chatId(chatId).text(text).replyMarkup(replyMarkup).build();
            client.execute(message);
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
