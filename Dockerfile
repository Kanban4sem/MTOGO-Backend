FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY target/app.jar app.jar
USER app
EXPOSE 8080
# Lets the JVM respect the container's memory limit when several services share one server
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# start-period gives Spring and Flyway time to start before failed checks count
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=6 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
