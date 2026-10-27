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
# Serial GC and a capped heap keep the JVM inside 512 MB; C1-only JIT starts faster on a tiny CPU.
# Secrets come from Render environment variables. Set before the training run so the archive matches.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=64m -XX:TieredStopAtLevel=1"
# Unpack the jar and record a class-data-sharing archive: a training start that stops right after
# the context is built (no database: Flyway off, the pool only connects on first use).
# On 0.1 CPU this cuts the cold start after Render's idle sleep considerably.
RUN java -Djarmode=tools -jar app.jar extract --destination extracted \
    && rm app.jar \
    && java -XX:ArchiveClassesAtExit=extracted/app.jsa -Dspring.context.exit=onRefresh \
        -Dspring.flyway.enabled=false -DDB_URL=jdbc:postgresql://localhost/none -DDB_USERNAME=x -DDB_PASSWORD=x \
        -jar extracted/app.jar \
    && chown -R app extracted
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:SharedArchiveFile=extracted/app.jsa", "-jar", "extracted/app.jar"]
