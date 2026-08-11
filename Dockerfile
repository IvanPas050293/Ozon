# ==== Стадия 1: сборка проекта ====
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Копируем только pom-файлы — кэш слоёв Docker не сбрасывается при правках кода
COPY pom.xml .
COPY Ozon/pom.xml Ozon/
RUN mvn -B -pl Ozon -am dependency:go-offline

# Исходники и полная сборка (без тестов — тесты гоняются локально/в CI)
COPY Ozon/src Ozon/src
RUN mvn -B -pl Ozon -am package -DskipTests

# ==== Стадия 2: runtime ====
FROM eclipse-temurin:25-jre
WORKDIR /app

# Папки бота: H2-база и временные файлы отчётов
RUN mkdir -p /app/data /app/tmp

# Готовый jar (spring-boot repackage)
COPY --from=build /build/Ozon/target/*.jar /app/ozon-bot.jar

# Значения по умолчанию; обязательные настройки (BOT_TOKEN и др.) задаются через .env в docker compose
ENV BOT_USERNAME=ozon_analytics_bot
ENV BOT_TOKEN=""
ENV TAX_SYSTEM=1
ENV TAX_RATE=0.06
ENV ALLOWED_CHAT_IDS=""
ENV JAVA_OPTS="-Xms64m -Xmx512m"

# Spring Boot по умолчанию слушает 8080 (для локальных проверок; для Telegram не нужен входящий порт)
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/ozon-bot.jar"]
