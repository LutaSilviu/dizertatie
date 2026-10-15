FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./mvnw package -DskipTests -DskipPlaywrightInstall=true

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN java -Dspring.context.exit=onRefresh -jar app.jar || true
ENTRYPOINT ["java", "-jar", "app.jar"]
