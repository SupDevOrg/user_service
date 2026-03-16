plugins {
    java
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
    jacoco
}

group = "ru.sup"
version = "0.0.1-SNAPSHOT"
description = "User service"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.liquibase:liquibase-core")
    implementation("org.springframework.boot:spring-boot-starter-security")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // OpenAPI + Swagger UI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    // === REDIS ===
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    // Если хочешь использовать Lettuce (рекомендуется)
    implementation("io.lettuce:lettuce-core")
    // ОБЯЗАТЕЛЬНО для Lettuce + Pool
    implementation("org.apache.commons:commons-pool2:2.12.0")

    // Для кэширования (включает @Cacheable)
    implementation("org.springframework.boot:spring-boot-starter-cache")

    // === KAFKA ===
    implementation("org.springframework.boot:spring-boot-starter-integration")
    implementation("org.springframework.kafka:spring-kafka")

    // S3-compatible object storage (MinIO)
    implementation("io.minio:minio:8.6.0")

    // Тесты
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")

    // Source: https://mvnrepository.com/artifact/org.testcontainers/testcontainers
    testImplementation("org.testcontainers:testcontainers:2.0.3")
    // Source: https://mvnrepository.com/artifact/org.testcontainers/junit-jupiter
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    // Source: https://mvnrepository.com/artifact/org.testcontainers/testcontainers-postgresql
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.3")
    // Source: https://mvnrepository.com/artifact/org.testcontainers/testcontainers-kafka
    testImplementation("org.testcontainers:testcontainers-kafka:2.0.3")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // PostgreSQL
    runtimeOnly("org.postgresql:postgresql")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(true)
    }
}

tasks.register<JacocoReport>("jacocoUserControllerReport") {
    executionData.setFrom(fileTree(layout.buildDirectory) {
        include("jacoco/test.exec", "jacoco/*.exec")
    })

    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/java/main")) {
            include("ru/sup/userservice/controller/UserController.class")
        }
    )

    sourceDirectories.setFrom(files("src/main/java"))

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/userController"))
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/userController/jacoco.xml"))
        csv.outputLocation.set(layout.buildDirectory.file("reports/jacoco/userController/jacoco.csv"))
    }
}