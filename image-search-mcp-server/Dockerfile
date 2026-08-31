FROM maven:3.9-amazoncorretto-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

FROM amazoncorretto:21
WORKDIR /app
COPY --from=builder /app/target/image-search-mcp-server-0.0.1-SNAPSHOT.jar app.jar
ENV SPRING_PROFILES_ACTIVE=sse
ENV JAVA_OPTS="-Dfile.encoding=UTF-8"
EXPOSE 8127
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
