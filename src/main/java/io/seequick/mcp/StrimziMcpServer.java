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
import java.time.Instant;
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
     *
     * When ENTRA_TENANT_ID and ENTRA_CLIENT_ID are set, the server enforces Entra
     * JWT authentication on all MCP endpoints. Otherwise it runs in open/no-auth mode
     * (suitable for local development only).
     */
    public void startHttp(int port) {
        String entraTenantId = System.getenv("ENTRA_TENANT_ID");
        String entraClientId = System.getenv("ENTRA_CLIENT_ID");
        EntraJwtValidator entraValidator = (entraTenantId != null && !entraTenantId.isBlank()
                && entraClientId != null && !entraClientId.isBlank())
                ? new EntraJwtValidator(entraTenantId, entraClientId)
                : null;

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

            // This filter handles OAuth discovery endpoints and, when Entra is configured,
            // validates Bearer tokens on MCP paths. Must be a Filter (not a Servlet) so it
            // runs before the /*-mapped MCP servlet.
            org.apache.tomcat.util.descriptor.web.FilterDef filterDef = new org.apache.tomcat.util.descriptor.web.FilterDef();
            filterDef.setFilterName("auth");
            filterDef.setFilter((req, res, chain) -> {
                jakarta.servlet.http.HttpServletRequest httpReq = (jakarta.servlet.http.HttpServletRequest) req;
                jakarta.servlet.http.HttpServletResponse httpRes = (jakarta.servlet.http.HttpServletResponse) res;
                String uri = httpReq.getRequestURI();
                System.err.println("[MCP] " + httpReq.getMethod() + " " + uri);

                String host = httpReq.getHeader("Host");
                // Use https for real hostnames; http for local dev
                String scheme = (host != null && !host.startsWith("localhost") && !host.startsWith("127.0.0.1"))
                        ? "https" : "http";

                if (uri.equals("/.well-known/oauth-authorization-server")
                        || uri.equals("/.well-known/openid-configuration")) {
                    httpRes.setStatus(200);
                    httpRes.setContentType("application/json");
                    if (entraValidator != null) {
                        String tid = entraValidator.tenantId();
                        String cid = entraValidator.clientId();
                        String base = "https://login.microsoftonline.com/" + tid + "/oauth2/v2.0";
                        httpRes.getWriter().write(
                                "{\"issuer\":\"https://login.microsoftonline.com/" + tid + "/v2.0\""
                                + ",\"authorization_endpoint\":\"" + base + "/authorize\""
                                + ",\"token_endpoint\":\"" + base + "/token\""
                                + ",\"registration_endpoint\":\"" + scheme + "://" + host + "/register\""
                                + ",\"jwks_uri\":\"https://login.microsoftonline.com/" + tid + "/discovery/v2.0/keys\""
                                + ",\"scopes_supported\":[\"api://" + cid + "/mcp.access\"]"
                                + ",\"response_types_supported\":[\"code\"]"
                                + ",\"grant_types_supported\":[\"authorization_code\"]"
                                + ",\"code_challenge_methods_supported\":[\"S256\"]}");
                    } else {
                        // Open mode: point clients to our own fake token endpoint
                        String issuer = scheme + "://" + host;
                        httpRes.getWriter().write(
                                "{\"issuer\":\"" + issuer + "\""
                                + ",\"authorization_endpoint\":\"" + issuer + "/authorize\""
                                + ",\"token_endpoint\":\"" + issuer + "/token\""
                                + ",\"registration_endpoint\":\"" + issuer + "/register\""
                                + ",\"grant_types_supported\":[\"client_credentials\"]"
                                + ",\"token_endpoint_auth_methods_supported\":[\"none\",\"client_secret_post\"]"
                                + ",\"response_types_supported\":[]}");
                    }
                    return;
                }

                if (uri.equals("/register") && "POST".equals(httpReq.getMethod())) {
                    httpRes.setContentType("application/json");
                    if (entraValidator != null) {
                        // Static shim for RFC 7591 dynamic client registration.
                        // Claude Code requires this endpoint; we always return the pre-registered Entra client ID.
                        String body = new String(httpReq.getInputStream().readAllBytes());
                        System.err.println("[MCP] register request: " + body);
                        String redirectUris = "[]";
                        try {
                            com.fasterxml.jackson.databind.JsonNode node = new ObjectMapper().readTree(body);
                            com.fasterxml.jackson.databind.JsonNode uris = node.path("redirect_uris");
                            if (uris.isArray()) redirectUris = uris.toString();
                        } catch (Exception ex) { /* use empty array */ }
                        httpRes.setStatus(201);
                        httpRes.getWriter().write(
                                "{\"client_id\":\"" + entraValidator.clientId() + "\""
                                + ",\"client_id_issued_at\":" + Instant.now().getEpochSecond()
                                + ",\"redirect_uris\":" + redirectUris
                                + ",\"token_endpoint_auth_method\":\"none\"}");
                    } else {
                        httpRes.setStatus(201);
                        httpRes.getWriter().write("{\"client_id\":\"mcp-public\",\"client_secret\":\"open\""
                                + ",\"grant_types\":[\"client_credentials\"]"
                                + ",\"token_endpoint_auth_method\":\"none\"}");
                    }
                    return;
                }

                // Fake token endpoint — only active in open/no-auth mode
                if (uri.equals("/token") && "POST".equals(httpReq.getMethod()) && entraValidator == null) {
                    httpRes.setStatus(200);
                    httpRes.setContentType("application/json");
                    httpRes.getWriter().write("{\"access_token\":\"open\",\"token_type\":\"Bearer\",\"expires_in\":86400}");
                    return;
                }

                // For any other non-MCP path, return JSON 404.
                boolean isMcpPath = (uri.equals("/sse") && "GET".equals(httpReq.getMethod()))
                        || uri.startsWith("/messages");
                if (!isMcpPath) {
                    httpRes.setStatus(404);
                    httpRes.setContentType("application/json");
                    httpRes.getWriter().write("{\"error\":\"not_found\"}");
                    return;
                }

                // MCP path — require a valid Entra Bearer token when auth is configured
                if (entraValidator != null) {
                    String authHeader = httpReq.getHeader("Authorization");
                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        System.err.println("[MCP] 401 no token on " + uri);
                        httpRes.setHeader("WWW-Authenticate", "Bearer realm=\"strimzi-mcp-server\""
                                + ",resource_metadata=\"" + scheme + "://" + host + "/.well-known/oauth-protected-resource\"");
                        httpRes.setStatus(401);
                        httpRes.setContentType("application/json");
                        httpRes.getWriter().write("{\"error\":\"unauthorized\"}");
                        return;
                    }
                    String token = authHeader.substring("Bearer ".length());
                    try {
                        entraValidator.validate(token);
                        System.err.println("[MCP] auth ok on " + uri);
                    } catch (Exception e) {
                        System.err.println("[MCP] 401 invalid token on " + uri + ": " + e.getMessage());
                        httpRes.setHeader("WWW-Authenticate",
                                "Bearer realm=\"strimzi-mcp-server\", error=\"invalid_token\""
                                + ",resource_metadata=\"" + scheme + "://" + host + "/.well-known/oauth-protected-resource\"");
                        httpRes.setStatus(401);
                        httpRes.setContentType("application/json");
                        httpRes.getWriter().write("{\"error\":\"unauthorized\"}");
                        return;
                    }
                }

                chain.doFilter(req, res);
            });
            ctx.addFilterDef(filterDef);
            org.apache.tomcat.util.descriptor.web.FilterMap filterMap = new org.apache.tomcat.util.descriptor.web.FilterMap();
            filterMap.setFilterName("auth");
            filterMap.addURLPattern("/*");
            ctx.addFilterMap(filterMap);

            Tomcat.addServlet(ctx, "mcp", transportProvider).addMapping("/*");

            tomcat.start();
            System.err.println("Strimzi MCP Server listening on port " + port
                    + (entraValidator != null ? " (Entra auth enabled)" : " (open/no-auth mode)"));
            tomcat.getServer().await();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start HTTP server on port " + port, e);
        }
    }
}
