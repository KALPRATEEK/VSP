# Build Stage
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Copy all Maven files
COPY pom.xml .
COPY core/pom.xml core/
COPY middleware/pom.xml middleware/
COPY engine/pom.xml engine/
COPY control/pom.xml control/

# Copy source code
COPY core/src core/src
COPY middleware/src middleware/src
COPY engine/src engine/src
COPY control/src control/src

# Build the application
RUN apk add --no-cache maven && \
    mvn clean package -DskipTests -B

# Runtime Stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Install Docker CLI and Docker Compose for container management
RUN apk add --no-cache docker-cli docker-cli-compose

# Copy the built JAR from control module
COPY --from=build /app/control/target/*.jar app.jar

# Create directory for generated docker-compose files
RUN mkdir -p /app/generated

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
