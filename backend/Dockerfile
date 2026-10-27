# Backend image for Render (free plan: 512 MB RAM, 0.1 CPU). Build: docker build -t idx-backend backend
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
# Tests need an embedded PostgreSQL; they run locally (.\mvnw.cmd test), not in the image build.
RUN ./mvnw -B -q package -DskipTests && cp target/backend-*.jar app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --no-create-home app
COPY --from=build /app/app.jar app.jar
USER app
# Serial GC and a capped heap keep the JVM inside 512 MB; secrets come from Render environment variables.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=64m"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
