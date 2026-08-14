# DataRobort 后端镜像
# 前置：宿主机先完成 mvn package -DskipTests（生成 datarobort-api/target/datarobort-api-*.jar）
# 构建：cd datarobort && docker build -f docker/backend/Dockerfile -t datarobort-backend:0.1.0 .
FROM eclipse-temurin:17-jre

# 沙箱通过宿主 Docker daemon 运行容器（docker CLI 需要调用宿主 socket）
COPY --from=docker:cli /usr/local/bin/docker /usr/local/bin/docker

WORKDIR /app
COPY datarobort-api/target/datarobort-api-0.1.0-SNAPSHOT.jar /app/datarobort.jar
RUN mkdir -p /app/reports

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70"
# 挂载 reports 目录后由 compose 共享；默认落在容器内
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/datarobort.jar"]
