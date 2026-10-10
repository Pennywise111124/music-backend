# syntax=docker/dockerfile:1

# ---------- Giai đoạn 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Chép pom trước: lớp tải dependency được cache khi chỉ sửa mã nguồn
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
# Bỏ test: test dùng Testcontainers cần Docker, không có trong lúc build image
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
    && cp target/*.jar /build/app.jar

# ---------- Giai đoạn 2: chạy ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Chạy bằng user thường, không dùng root
RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /app/uploads \
    && chown -R app:app /app

COPY --from=build --chown=app:app /build/app.jar app.jar

USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
ENTRYPOINT ["java", "-jar", "app.jar"]