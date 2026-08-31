# 构建阶段：预装 Maven 和 JDK21
FROM maven:3.9-amazoncorretto-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# 运行阶段：仅保留 JRE 运行环境，镜像更小
FROM amazoncorretto:21
WORKDIR /app
COPY --from=builder /app/target/lianba-ai-agent-0.0.1-SNAPSHOT.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Dfile.encoding=UTF-8"
EXPOSE 8123
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
