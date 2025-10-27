# ==============================
# Сборка проекта с Gradle
# ==============================
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Кешируем зависимости
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts gradlew ./
RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon || true

# Копируем исходники и собираем
COPY src/ src/
RUN ./gradlew clean build -x test --no-daemon --parallel

# ==============================
# Финальный образ
# ==============================
FROM eclipse-temurin:21-jre
WORKDIR /app

# Устанавливаем curl для healthcheck (опционально)
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Копируем JAR файлы
RUN mkdir -p ./libs
COPY --from=builder /app/build/libs/*.jar ./libs/

# Создаем стартовый скрипт
RUN echo '#!/bin/sh' > start.sh \
    && echo 'echo "Содержимое папки libs:"' >> start.sh \
    && echo 'ls -l libs' >> start.sh \
    && echo 'JAR=$(ls libs | grep -v "sources\\|javadoc" | head -n 1)' >> start.sh \
    && echo 'echo "Запускаем $JAR"' >> start.sh \
    && echo 'exec java -jar "libs/$JAR"' >> start.sh

RUN chmod +x start.sh

# ENTRYPOINT через скрипт
ENTRYPOINT ["./start.sh"]

EXPOSE 8080
