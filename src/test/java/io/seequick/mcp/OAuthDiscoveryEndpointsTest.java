package io.seequick.mcp;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Boots the HTTP transport on an ephemeral port and exercises the OAuth discovery
 * endpoints that MCP clients (e.g. Claude Code) rely on.
 *
 * Regression guard for the missing RFC 9728 protected-resource-metadata endpoint:
 * the 401 on /mcp advertises {@code resource_metadata}, but if that URL 404s the
 * client never learns the scope and Entra rejects the token request with AADSTS900144.
 */
class OAuthDiscoveryEndpointsTest {

    private static final String TENANT_ID = "test-tenant-id";
    private static final String CLIENT_ID = "test-client-id";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    @Test
    void entraMode_protectedResourceMetadata_advertisesScopeAndServer() throws Exception {
        int port = startServer(new EntraJwtValidator(TENANT_ID, CLIENT_ID));
        String base = "http://localhost:" + port;

        // 401 on the MCP endpoint must point clients at the protected-resource metadata.
        HttpResponse<String> mcp = get(base + "/mcp");
        assertThat(mcp.statusCode()).isEqualTo(401);
        assertThat(mcp.headers().firstValue("WWW-Authenticate").orElse(""))
                .contains("resource_metadata=\"" + base + "/.well-known/oauth-protected-resource\"");

        // The advertised endpoint must resolve (this is the bug: it used to 404) and carry the scope.
        HttpResponse<String> prm = get(base + "/.well-known/oauth-protected-resource");
        assertThat(prm.statusCode()).isEqualTo(200);
        assertThat(prm.body())
                .contains("\"resource\":\"" + base + "\"")
                .contains("\"authorization_servers\":[\"" + base + "\"]")
                .contains("\"scopes_supported\":[\"api://" + CLIENT_ID + "/mcp.access\"]");

        // Path-suffixed variant some clients request must also resolve.
        assertThat(get(base + "/.well-known/oauth-protected-resource/mcp").statusCode()).isEqualTo(200);

        // Authorization-server metadata stays intact.
        HttpResponse<String> asm = get(base + "/.well-known/oauth-authorization-server");
        assertThat(asm.statusCode()).isEqualTo(200);
        assertThat(asm.body())
                .contains("login.microsoftonline.com/" + TENANT_ID)
                .contains("\"scopes_supported\":[\"api://" + CLIENT_ID + "/mcp.access\"]");
    }

    @Test
    void openMode_protectedResourceMetadata_servedWithoutScope() throws Exception {
        int port = startServer(null);
        String base = "http://localhost:" + port;

        HttpResponse<String> prm = get(base + "/.well-known/oauth-protected-resource");
        assertThat(prm.statusCode()).isEqualTo(200);
        assertThat(prm.body())
                .contains("\"resource\":\"" + base + "\"")
                .doesNotContain("scopes_supported");
    }

    /** Starts the server on a free port in a daemon thread and waits until it answers. */
    private int startServer(EntraJwtValidator validator) throws Exception {
        int port = freePort();
        StrimziMcpServer server = new StrimziMcpServer(mock(KubernetesClient.class));
        Thread t = new Thread(() -> server.startHttp(port, validator), "mcp-test-http");
        t.setDaemon(true);
        t.start();

        String base = "http://localhost:" + port + "/.well-known/oauth-authorization-server";
        for (int i = 0; i < 100; i++) {
            try {
                if (get(base).statusCode() == 200) return port;
            } catch (Exception ignored) {
                // not up yet
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("server did not start on port " + port);
    }

    private HttpResponse<String> get(String url) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(2)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
