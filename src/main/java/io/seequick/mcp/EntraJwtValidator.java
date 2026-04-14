package io.seequick.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Validates Entra (Azure AD) JWTs by fetching JWKS and verifying RS256 signatures.
 * JWKS keys are cached for 1 hour to avoid redundant network calls on every request.
 */
class EntraJwtValidator {

    private static final long CACHE_SECONDS = 3600L;

    private final String tenantId;
    private final String clientId;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private volatile Map<String, PublicKey> jwksCache = Map.of();
    private volatile long jwksCachedAtEpoch = 0L;

    EntraJwtValidator(String tenantId, String clientId) {
        this.tenantId = tenantId;
        this.clientId = clientId;
    }

    /** Test-only constructor — injects pre-loaded keys to avoid live JWKS calls. */
    EntraJwtValidator(String tenantId, String clientId, Map<String, PublicKey> preloadedKeys) {
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.jwksCache = preloadedKeys;
        this.jwksCachedAtEpoch = Instant.now().getEpochSecond();
    }

    String tenantId() { return tenantId; }
    String clientId() { return clientId; }

    /**
     * Validates the JWT, throwing an exception with a reason if invalid.
     */
    void validate(String token) throws Exception {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) throw new Exception("invalid token format");

        // Parse header
        byte[] headerBytes = base64UrlDecode(parts[0]);
        JsonNode header = objectMapper.readTree(headerBytes);
        String alg = header.path("alg").asText();
        String kid = header.path("kid").asText();
        if (!"RS256".equals(alg)) throw new Exception("unsupported algorithm: " + alg);
        if (kid.isEmpty()) throw new Exception("missing kid");

        // Parse payload
        byte[] payloadBytes = base64UrlDecode(parts[1]);
        JsonNode claims = objectMapper.readTree(payloadBytes);

        // Validate temporal claims
        long now = Instant.now().getEpochSecond();
        long exp = claims.path("exp").asLong(0);
        long nbf = claims.path("nbf").asLong(0);
        if (exp > 0 && now > exp) throw new Exception("token expired");
        if (nbf > 0 && now < nbf) throw new Exception("token not yet valid");

        // Validate issuer — accept both v2 (login.microsoftonline.com) and v1 (sts.windows.net) tokens
        String iss = claims.path("iss").asText();
        String issV2 = "https://login.microsoftonline.com/" + tenantId + "/v2.0";
        String issV1 = "https://sts.windows.net/" + tenantId + "/";
        if (!issV2.equals(iss) && !issV1.equals(iss)) {
            throw new Exception("invalid issuer: " + iss);
        }

        // Validate audience (Entra sets aud to Application ID URI or client ID directly)
        JsonNode audNode = claims.path("aud");
        if (!audContains(audNode, clientId, "api://" + clientId)) {
            throw new Exception("invalid audience");
        }

        // Verify RS256 signature
        PublicKey pubKey = getPublicKey(kid);
        byte[] sigBytes = base64UrlDecode(parts[2]);
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(pubKey);
        sig.update((parts[0] + "." + parts[1]).getBytes());
        if (!sig.verify(sigBytes)) throw new Exception("signature verification failed");
    }

    private PublicKey getPublicKey(String kid) throws Exception {
        Map<String, PublicKey> cache = jwksCache;
        long now = Instant.now().getEpochSecond();
        if (cache.containsKey(kid) && (now - jwksCachedAtEpoch) < CACHE_SECONDS) {
            return cache.get(kid);
        }
        return refreshAndGet(kid);
    }

    private synchronized PublicKey refreshAndGet(String kid) throws Exception {
        // Re-check under lock — another thread may have refreshed while we waited
        Map<String, PublicKey> cache = jwksCache;
        long now = Instant.now().getEpochSecond();
        if (cache.containsKey(kid) && (now - jwksCachedAtEpoch) < CACHE_SECONDS) {
            return cache.get(kid);
        }

        String url = "https://login.microsoftonline.com/" + tenantId + "/discovery/v2.0/keys";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        JsonNode jwks = objectMapper.readTree(resp.body());
        Map<String, PublicKey> keys = new HashMap<>();
        KeyFactory kf = KeyFactory.getInstance("RSA");

        for (JsonNode key : jwks.path("keys")) {
            if (!"RSA".equals(key.path("kty").asText())) continue;
            String keyKid = key.path("kid").asText();
            String n = key.path("n").asText();
            String e = key.path("e").asText();
            if (keyKid.isEmpty() || n.isEmpty() || e.isEmpty()) continue;
            try {
                BigInteger modulus = new BigInteger(1, base64UrlDecode(n));
                BigInteger exponent = new BigInteger(1, base64UrlDecode(e));
                keys.put(keyKid, kf.generatePublic(new RSAPublicKeySpec(modulus, exponent)));
            } catch (Exception ex) {
                System.err.println("[EntraJwtValidator] skipping malformed key " + keyKid + ": " + ex.getMessage());
            }
        }

        jwksCache = keys;
        jwksCachedAtEpoch = Instant.now().getEpochSecond();

        PublicKey result = keys.get(kid);
        if (result == null) throw new Exception("key not found in JWKS: " + kid);
        return result;
    }

    private static boolean audContains(JsonNode audNode, String... expected) {
        if (audNode.isTextual()) {
            for (String e : expected) {
                if (e.equals(audNode.asText())) return true;
            }
        } else if (audNode.isArray()) {
            for (JsonNode a : audNode) {
                for (String e : expected) {
                    if (e.equals(a.asText())) return true;
                }
            }
        }
        return false;
    }

    private static byte[] base64UrlDecode(String s) {
        // JWT Base64URL strings may omit padding — add it back for the standard decoder
        int pad = s.length() % 4;
        if (pad == 2) s = s + "==";
        else if (pad == 3) s = s + "=";
        return Base64.getUrlDecoder().decode(s);
    }
}
