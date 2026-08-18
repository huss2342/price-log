# Built remotely by Azure Container Registry, so no local Docker is needed.
FROM mcr.microsoft.com/openjdk/jdk:21-ubuntu AS build
WORKDIR /src

# Dependencies resolve in their own layer, so code edits do not re-download Maven.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests

FROM mcr.microsoft.com/openjdk/jdk:21-ubuntu
WORKDIR /app

# Runs unprivileged: nothing in this container needs root. The base image
# already ships an `app` user, so only create one when it is missing.
RUN id -u app >/dev/null 2>&1 || useradd --system --create-home --uid 10001 app
USER app

COPY --from=build --chown=app:app /src/target/*.jar /app/app.jar

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
