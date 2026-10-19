FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN ./mvnw package -DskipTests -DskipPlaywrightInstall=true

# Playwright ships a glibc-linked node binary and needs real browser libraries,
# so the runtime image is Debian-based rather than Alpine (musl cannot exec it).
FROM eclipse-temurin:21-jre-noble
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Install the Chromium build Playwright expects, plus its system libraries.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
# Playwright.create() validates every browser in its registry and downloads any
# that are missing, so installing all of them here keeps container startup
# offline and fast. The app itself only launches chromium.
RUN java -cp app.jar -Dloader.main=com.microsoft.playwright.CLI \
      org.springframework.boot.loader.launch.PropertiesLauncher install --with-deps

ENTRYPOINT ["java", "-jar", "app.jar"]
