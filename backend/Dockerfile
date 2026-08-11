# syntax=docker/dockerfile:1.7

# Gradle과 JDK가 포함된 빌드 전용 단계다. 최종 이미지에는 빌드 도구가 포함되지 않는다.
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

# 의존성 관련 파일을 먼저 복사해 소스 변경 시에도 Docker 빌드 캐시를 재사용한다.
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies --configuration runtimeClasspath

COPY src src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar && \
    cp build/libs/*.jar build/app.jar

# 실행에는 JRE만 사용해 이미지 크기와 공격 표면을 줄인다.
FROM eclipse-temurin:21-jre-jammy AS runtime

RUN apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates curl && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd --system techview && \
    useradd --system --gid techview --home-dir /app techview

WORKDIR /app
COPY --from=builder --chown=techview:techview /workspace/build/app.jar app.jar

# 애플리케이션은 root 권한 없이 실행한다.
USER techview

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
