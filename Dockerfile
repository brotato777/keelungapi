# 1. 使用官方 Maven + JDK 17 作為 build stage
FROM maven:3.9.2-eclipse-temurin-17 AS build

# 設定工作目錄
WORKDIR /app

# 複製 pom.xml 並下載依賴
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 複製專案原始碼
COPY src ./src

# 建置 jar
RUN mvn clean package -DskipTests

# 2. 使用更小的 JDK 運行映像
FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# 從 build stage 複製 jar
COPY --from=build /app/target/keelungapi-0.0.1-SNAPSHOT.jar app.jar

# 設定環境變數 PORT（Railway 會自動提供）
ENV PORT=8080

# Spring Boot 配置，使用 Railway 提供的 PORT
ENTRYPOINT ["java","-jar","/app/app.jar","--server.port=${PORT}"]
