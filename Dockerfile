# syntax=docker/dockerfile:1

# Build with the same Maven profile as the legacy VM and expand the WAR,
# so the nightly report CronJob can use the classes from the same image.
FROM eclipse-temurin:17-jdk-noble AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    sh mvnw -B -P PostgreSQL -DskipTests package \
 && mkdir /app && cd /app && jar xf /src/target/petclinic.war

# Same Tomcat version as the legacy VM (11.0.26)
FROM tomcat:11.0.26-jre17-temurin-noble

# Non-root user; access log and JULI logs to the console instead of files under logs/
RUN groupadd --system --gid 10001 petclinic \
 && useradd --system --uid 10001 --gid petclinic --no-create-home --home-dir /nonexistent petclinic \
 && mkdir -p /var/app/uploads /var/app/reports conf/Catalina/localhost \
 && chown -R 10001:10001 /var/app conf/Catalina \
 && sed -i 's|directory="logs"|directory="/dev" rotatable="false" buffered="false"|; s|prefix="localhost_access_log" suffix=".txt"|prefix="stdout" suffix=""|' conf/server.xml \
 && sed -i 's|^handlers = .*|handlers = java.util.logging.ConsoleHandler|; s|^\.handlers = .*|.handlers = java.util.logging.ConsoleHandler|' conf/logging.properties

COPY --from=build /app webapps/ROOT
COPY --chmod=755 ops/legacy/nightly-report.sh /usr/local/bin/nightly-report.sh

# Heap sized from the container memory limit; APP_DIR and JAVA are read by nightly-report.sh
ENV CATALINA_OPTS="-XX:MaxRAMPercentage=60" \
    APP_DIR=/usr/local/tomcat/webapps/ROOT \
    JAVA=/opt/java/openjdk/bin/java

USER 10001:10001
