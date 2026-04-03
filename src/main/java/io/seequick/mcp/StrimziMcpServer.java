package io.seequick.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.seequick.mcp.tool.StrimziTool;
import io.seequick.mcp.tool.factory.ClusterToolFactory;
import io.seequick.mcp.tool.factory.KafkaToolFactory;
import io.seequick.mcp.tool.factory.ObservabilityToolFactory;
import io.seequick.mcp.tool.factory.SecurityToolFactory;
import io.seequick.mcp.tool.factory.ToolFactory;
import io.seequick.mcp.tool.factory.TopicToolFactory;
import io.seequick.mcp.tool.factory.UserToolFactory;
import io.seequick.mcp.tool.factory.UtilityToolFactory;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.function.Supplier;

/**
 * Strimzi MCP Server - provides MCP tools for interacting with Strimzi Kafka on Kubernetes.
 */
public class StrimziMcpServer {

    private static final String SERVER_NAME = "strimzi-mcp-server";
    private static final String SERVER_VERSION = "0.3.0";

    private static final List<ToolFactory> FACTORIES = List.of(
            new KafkaToolFactory(),
            new TopicToolFactory(),
            new UserToolFactory(),
            new ClusterToolFactory(),
            new ObservabilityToolFactory(),
            new SecurityToolFactory(),
            new UtilityToolFactory()
    );

    private final KubernetesClientResolver clientResolver;
    private final List<StrimziTool> tools;

    public StrimziMcpServer(KubernetesClientResolver clientResolver) {
        this.clientResolver = clientResolver;
        this.tools = createTools();
    }

    public static void main(String[] args) {
        KubernetesClientResolver resolver = new KubernetesClientResolver();

        // Suppress stdout during API version detection: the Kubernetes exec credential plugin
        // (e.g. aws sso login) may write interactive prompts to stdout, which corrupts the
        // MCP stdio transport before it has started.
        String topicUserVersion = withSuppressedStdout(() -> StrimziApiVersionDetector.detect(resolver.resolveDefault()));
        StrimziApiVersion.setTopicUserVersion(topicUserVersion);

        StrimziMcpServer server = new StrimziMcpServer(resolver);

        String httpPort = System.getenv("MCP_HTTP_PORT");
        if (httpPort != null && !httpPort.isBlank()) {
            server.startHttp(Integer.parseInt(httpPort));
        } else {
            server.start();
        }
    }

    /**
     * Runs {@code action} with stdout redirected to /dev/null, then restores the original stdout.
     * Prevents any output written during the action (e.g. from AWS credential plugins) from
     * corrupting the MCP stdio transport.
     */
    static <T> T withSuppressedStdout(Supplier<T> action) {
        PrintStream origOut = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        try {
            return action.get();
        } finally {
            System.setOut(origOut);
        }
    }

    /**
     * Creates all available Strimzi tools using factories.
     */
    private List<StrimziTool> createTools() {
        return FACTORIES.stream()
                .flatMap(factory -> factory.createTools(clientResolver).stream())
                .toList();
    }

    /**
     * Starts the MCP server with stdio transport.
     */
    public void start() {
        StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(new JacksonMcpJsonMapper(new ObjectMapper()));

        McpSyncServer syncServer = McpServer.sync(transportProvider)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .build();

        // Register all tools
        tools.forEach(tool -> syncServer.addTool(tool.getSpecification()));

        // Block main thread - the transport provider handles stdin/stdout
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            syncServer.close();
        }
    }

    /**
     * Starts the MCP server with SSE transport on the given port.
     * Activated when MCP_HTTP_PORT environment variable is set.
     */
    public void startHttp(int port) {
        String baseUrl = "http://localhost:" + port;
        HttpServletSseServerTransportProvider transportProvider =
                HttpServletSseServerTransportProvider.builder()
                        .baseUrl(baseUrl)
                        .sseEndpoint("/sse")
                        .messageEndpoint("/messages")
                        .build();

        McpSyncServer syncServer = McpServer.sync(transportProvider)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .build();

        tools.forEach(tool -> syncServer.addTool(tool.getSpecification()));

        try {
            Tomcat tomcat = new Tomcat();
            tomcat.setPort(port);
            tomcat.getConnector();

            Context ctx = tomcat.addContext("", null);
            Tomcat.addServlet(ctx, "mcp", transportProvider).addMapping("/*");

            tomcat.start();
            System.err.println("Strimzi MCP Server listening on port " + port);
            tomcat.getServer().await();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start HTTP server on port " + port, e);
        }
    }
}
