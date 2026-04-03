FROM eclipse-temurin:21-jre

COPY target/strimzi-mcp-server-*.jar /app/strimzi-mcp-server.jar

ENV MCP_HTTP_PORT=8080

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/strimzi-mcp-server.jar"]
