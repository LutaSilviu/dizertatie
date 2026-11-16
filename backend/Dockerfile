FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Optional extra trusted CA certificates (certs/*.crt at the repository root), for networks
# where HTTPS is inspected by an antivirus or a corporate proxy. With none present this is a no-op.
COPY --from=certs . /extra-certs/
RUN touch /extra-ca.pem \
 && for c in /extra-certs/*.crt; do \
      [ -f "$c" ] || continue; \
      keytool -importcert -noprompt -cacerts -storepass changeit \
              -alias "extra-$(basename "$c" .crt)" -file "$c" || exit 1; \
      cat "$c" >> /extra-ca.pem; \
    done

# Resolve dependencies in their own layer so source edits don't re-download them.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline -DskipPlaywrightInstall=true
COPY src src
RUN mvn -B -q package -DskipTests -DskipPlaywrightInstall=true \
 && cp target/*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# Playwright ships a glibc-linked node binary and needs real browser libraries,
# so the runtime image is Debian-based rather than Alpine (musl cannot exec it).
FROM eclipse-temurin:21-jre-noble
WORKDIR /app

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Optional extra trusted CA certificates (certs/*.crt at the repository root), for networks
# where HTTPS is inspected by an antivirus or a corporate proxy. With none present this is a no-op.
COPY --from=certs . /extra-certs/
RUN touch /extra-ca.pem \
 && for c in /extra-certs/*.crt; do \
      [ -f "$c" ] || continue; \
      keytool -importcert -noprompt -cacerts -storepass changeit \
              -alias "extra-$(basename "$c" .crt)" -file "$c" || exit 1; \
      cat "$c" >> /extra-ca.pem; \
    done
# The Playwright driver (node) and Chromium keep their own trust stores.
ENV NODE_EXTRA_CA_CERTS=/extra-ca.pem
RUN if [ -s /extra-ca.pem ]; then \
      apt-get update && apt-get install -y --no-install-recommends libnss3-tools \
      && rm -rf /var/lib/apt/lists/* \
      && mkdir -p /root/.pki/nssdb && certutil -d sql:/root/.pki/nssdb -N --empty-password \
      && for c in /extra-certs/*.crt; do \
           certutil -d sql:/root/.pki/nssdb -A -t "C,," -n "extra-$(basename "$c" .crt)" -i "$c" || exit 1; \
         done; \
    fi

# Third-party jars change rarely; copy them first so the browser install below
# stays cached across application code changes.
COPY --from=build /app/extracted/dependencies/ ./
# Playwright.create() validates every browser in its registry and downloads any
# that are missing, so installing all of them here keeps container startup
# offline and fast. The app itself only launches chromium.
RUN java -cp 'BOOT-INF/lib/*' com.microsoft.playwright.CLI install --with-deps

COPY --from=build /app/extracted/spring-boot-loader/ ./
COPY --from=build /app/extracted/snapshot-dependencies/ ./
COPY --from=build /app/extracted/application/ ./

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
