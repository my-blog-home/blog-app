# 화면 빌드 → 서버 빌드(화면 포함 jar) → 실행 이미지
FROM node:22-alpine AS web
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk AS api
WORKDIR /app
COPY backend/ backend/
COPY --from=web /app/frontend/dist frontend/dist
WORKDIR /app/backend
# 통합 테스트는 DB·Redis가 필요하므로 이미지 빌드에서는 건너뛴다 (build.sh에서 돌린다)
RUN ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=api /app/backend/build/libs/myblog.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
ENV PORT=8330
RUN mkdir -p /var/lib/ylog/images
VOLUME /var/lib/ylog/images
EXPOSE 8330
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
