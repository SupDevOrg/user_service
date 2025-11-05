# ==============================
# Stage 1: Build JAR
# ==============================
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Копируем скрипты и зависимости Gradle
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle gradle
RUN chmod +x gradlew
RUN ./gradlew --no-daemon dependencies || true

# Копируем исходники и собираем
COPY src src
RUN ./gradlew clean build -x test --no-daemon --parallel


# ==============================
# Stage 2: Final image (distroless)
# ==============================
FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app

# Копируем jar
COPY --from=builder /app/build/libs/*.jar app.jar

# Spring Boot запускается напрямую через java -jar
ENTRYPOINT ["java", "-jar", "app.jar"]
EXPOSE 8080 8081
