package com.alnative.ozon.calc;

import java.util.Locale;
import java.util.function.BiPredicate;

/**
 * Категории расходов дашборда (соответствуют «распределяемым» колонкам листа «База»: P…CM).
 * Правила повторяют master-формулы шаблона; комиссии зависят от знака суммы.
 */
enum ExpenseCategory {

    P(Kind.COMISSION, (t, v) -> t.equals("Вознаграждение за продажу") && v < 0),
    Q(Kind.COMISSION, (t, v) -> (t.equals("Вознаграждение за продажу") || t.equals("Возврат вознаграждения")) && v > 0),
    R(Kind.OTHER, (t, v) -> t.equals("Эквайринг")),
    S(Kind.LOGISTICS, (t, v) -> t.equals("Логистика") || t.equals("Логистика - отмена начисления") || t.contains("Обработка отправления")),
    T(Kind.LOGISTICS, (t, v) -> t.equals("Последняя миля") || t.equals("Последняя миля - отмена начисления")
            || t.equals("Доставка до места выдачи") || t.equals("Доставка до места выдачи - отмена начисления")),
    U(Kind.LOGISTICS, (t, v) -> t.equals("Выдача товара") || t.equals("Выдача товара - отмена начисления (Сторно возвратов на ПВЗ)")),
    V(Kind.LOGISTICS, (t, v) -> t.equals("Обработка возвратов, отмен и невыкупов партнёрами") || t.equals("Обработка возвратов Ozon")),
    W(Kind.LOGISTICS, (t, v) -> t.equals("Обратная логистика")),
    X(Kind.LOGISTICS, (t, v) -> t.equals("Доставка курьером Pick-up")),
    Y(Kind.LOGISTICS, (t, v) -> t.equals("Организация выезда курьера")),
    AA(Kind.OTHER, (t, v) -> t.equals("Продвижение в поиске") || t.equals("Продвижение с оплатой за заказ")),
    AB(Kind.OTHER, (t, v) -> t.equals("Иные электронные услуги")),
    AC(Kind.OTHER, (t, v) -> t.equals("Оплата за клик") || t.equals("Трафареты") || t.equals("Вывод в топ")),
    AD(Kind.OTHER, (t, v) -> t.equals("Реклама в сети Интернет на Сайте") || t.equals("Технические услуги и иные услуги")),
    AE(Kind.OTHER, (t, v) -> t.equals("Бонусы продавца")),
    AF(Kind.OTHER, (t, v) -> t.equals("Ozon Рассрочка")),
    AG(Kind.OTHER, (t, v) -> t.equals("Подключение продвижения бренда")),
    AH(Kind.OTHER, (t, v) -> t.equals("Продвижение бренда")),
    AI(Kind.OTHER, (t, v) -> t.contains("Утилизация")),
    AJ(Kind.OTHER, (t, v) -> t.equals("Звёздные товары")),
    AK(Kind.OTHER, (t, v) -> t.equals("Услуга продвижения Бонусы продавца - рассылка") || t.equals("Бонусы продавца - рассылка")),
    AL(Kind.OTHER, (t, v) -> t.equals("Приобретение отзывов на платформе") || t.equals("Баллы за отзывы")),
    AM(Kind.OTHER, (t, v) -> t.equals("Размещение товаров на складах Ozon") || t.equals("Начисление за хранение/утилизацию возвратов")),
    AN(Kind.OTHER, (t, v) -> t.equals("Premium-подписка") || t.equals("Подписка Premium")),
    AO(Kind.OTHER, (t, v) -> t.equals("Подписка Premium Plus")),
    AP(Kind.OTHER, (t, v) -> t.equals("Компенсации за поврежденный на складе товар")),
    AQ(Kind.OTHER, (t, v) -> t.equals("Гибкий график выплат")),
    AR(Kind.OTHER, (t, v) -> t.equals("Досрочная выплата")),
    AS(Kind.OTHER, (t, v) -> t.equals("Обработка брака")),
    AT(Kind.OTHER, (t, v) -> t.equals("Корректировка стоимости услуг")),
    AU(Kind.OTHER, (t, v) -> t.equals("Начисления по претензиям")),
    AV(Kind.OTHER, (t, v) -> t.equals("Доставка товаров на склад Ozon (кросс-докинг)") || t.equals("Кросс-докинг")),
    AW(Kind.OTHER, (t, v) -> t.equals("Перечисление за доставку от покупателя")),
    AX(Kind.OTHER, (t, v) -> t.equals("Удержание за недовложение товара")),
    AY(Kind.OTHER, (t, v) -> t.equals("Обработка неопознанных излишков с приемки")),
    AZ(Kind.OTHER, (t, v) -> t.equals("Обработка опознанных излишков") || t.equals("Обработка опознанных излишков в составе грузоместа")),
    BA(Kind.OTHER, (t, v) -> t.equals("Компенсация за уничтоженный товар")),
    BB(Kind.OTHER, (t, v) -> t.equals("Прочие компенсации")),
    BC(Kind.OTHER, (t, v) -> t.equals("Списание по утилизации в доставке")),
    BD(Kind.OTHER, (t, v) -> t.equals("Бронирование места для размещения на складе")),
    BE(Kind.OTHER, (t, v) -> t.equals("Обработка товара в составе грузоместа")),
    BF(Kind.OTHER, (t, v) -> t.contains("Обработка операционных ошибок продавца")),
    BG(Kind.OTHER, (t, v) -> t.equals("Подготовка товаров к возврату")),
    BH(Kind.OTHER, (t, v) -> t.equals("Услуга продвижения Premium")),
    BI(Kind.OTHER, (t, v) -> t.equals("Бронирование места и персонала для поставки с неполным составом")
            || t.equals("Бронирование места и персонала для поставки с неполным составом в составе грузоместа")),
    BJ(Kind.OTHER, (t, v) -> t.equals("Обработка срока годности товаров")),
    BK(Kind.OTHER, (t, v) -> t.equals("Доп. вознаграждение за доставку RFBS")),
    BL(Kind.OTHER, (t, v) -> t.equals("Закрепление отзыва")),
    BM(Kind.OTHER, (t, v) -> t.equals("Генерация видеообложки")),
    BN(Kind.OTHER, (t, v) -> t.equals("Модерация запрещённого контента")),
    BO(Kind.OTHER, (t, v) -> t.equals("Перенос карточек товаров")),
    BP(Kind.OTHER, (t, v) -> t.equals("Перевыставление услуг доставки Агрегатор РФБС")),
    BQ(Kind.OTHER, (t, v) -> t.equals("Агентское вознаграждение за заключение и сопровождение договора транспортно-экспедиционных услуг по организации перевозки")),
    BR(Kind.OTHER, (t, v) -> t.equals("Услуги Партнёров Ozon на схеме realFBS")),
    BS(Kind.OTHER, (t, v) -> t.equals("Агентское вознаграждение Ozon Агрегатор realFBS")),
    BT(Kind.OTHER, (t, v) -> t.equals("Компенсации за утерянный на складе товар") || t.equals("Потеря по вине Ozon в логистике")
            || t.equals("Брак по вине Ozon на складе") || t.equals("Потеря по вине Ozon на складе")),
    BU(Kind.OTHER, (t, v) -> t.equals("Начисление по спору")),
    BV(Kind.OTHER, (t, v) -> t.equals("Частичная компенсация покупателю")),
    BW(Kind.OTHER, (t, v) -> t.equals("Ozon Data")),
    BX(Kind.OTHER, (t, v) -> t.equals("Декомпенсации и возвращение товаров на склад")),
    BY(Kind.OTHER, (t, v) -> t.equals("Брендовая полка")),
    BZ(Kind.OTHER, (t, v) -> t.equals("Доставка возвратов до склада продавца силами Ozon")),
    CA(Kind.OTHER, (t, v) -> t.equals("Сервисный сбор за интеграцию с логистической платформой")),
    CB(Kind.OTHER, (t, v) -> t.equals("Корректировка суммы акта о премии")),
    CC(Kind.OTHER, (t, v) -> t.equals("Взаимозачет требований между Договорами")),
    CD(Kind.OTHER, (t, v) -> t.equals("Краткосрочное размещение возврата FBS")),
    CE(Kind.OTHER, (t, v) -> t.equals("Вывоз товара")),
    CF(Kind.OTHER, (t, v) -> t.equals("Сортировка товара по зонам размещения")),
    CG(Kind.OTHER, (t, v) -> t.equals("Дополнительная обработка ОВХ")),
    CH(Kind.OTHER, (t, v) -> t.equals("Абонентское обслуживание по продвижению товаров")),
    CI(Kind.OTHER, (t, v) -> t.equals("Бонус за достижение цели продаж")),
    CJ(Kind.OTHER, (t, v) -> t.contains("Подготовка товара к вывозу")),
    CK(Kind.OTHER, (t, v) -> t.equals("Временное размещение товара в СЦ/ПВЗ")),
    CL(Kind.OTHER, (t, v) -> t.toLowerCase(Locale.ROOT).contains("упаковк")),
    CM(Kind.OTHER, (t, v) -> t.equals("Инвентаризация взаиморасчетов"));

    enum Kind {
        COMISSION, LOGISTICS, OTHER
    }

    private final Kind kind;
    private final BiPredicate<String, Double> predicate;

    ExpenseCategory(Kind kind, BiPredicate<String, Double> predicate) {
        this.kind = kind;
        this.predicate = predicate;
    }

    Kind kind() {
        return kind;
    }

    /** Возвращает категорию для типа начисления и суммы, или null, если тип не расход. */
    static ExpenseCategory classify(String type, double total) {
        for (ExpenseCategory c : values()) {
            if (c.predicate.test(type, total)) {
                return c;
            }
        }
        return null;
    }
}
