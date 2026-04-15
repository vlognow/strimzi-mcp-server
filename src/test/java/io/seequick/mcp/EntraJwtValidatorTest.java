package io.seequick.mcp;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.*;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for EntraJwtValidator.
 *
 * Uses a locally-generated RSA key pair injected via the test constructor — no live
 * JWKS calls are made.
 */
class EntraJwtValidatorTest {

    private static final String TENANT_ID  = "test-tenant-id";
    private static final String CLIENT_ID  = "test-client-id";
    private static final String KID        = "test-kid";

    private static PrivateKey privateKey;
    private static PublicKey  publicKey;

    // Recreated before each test so that tests are fully isolated — if one test
    // uses an unknown kid it would trigger a live JWKS call that overwrites the
    // cache and corrupts state for subsequent tests.
    private EntraJwtValidator validator;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        privateKey = pair.getPrivate();
        publicKey  = pair.getPublic();
    }

    @org.junit.jupiter.api.BeforeEach
    void setupValidator() {
        validator = new EntraJwtValidator(TENANT_ID, CLIENT_ID, Map.of(KID, publicKey));
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void validToken_passes() throws Exception {
        String token = buildToken(KID, "RS256", issV2(), CLIENT_ID, nowPlus(3600), 0);
        assertThatCode(() -> validator.validate(token)).doesNotThrowAnyException();
    }

    @Test
    void validToken_v1Issuer_passes() throws Exception {
        String token = buildToken(KID, "RS256", issV1(), CLIENT_ID, nowPlus(3600), 0);
        assertThatCode(() -> validator.validate(token)).doesNotThrowAnyException();
    }

    @Test
    void validToken_apiUriAudience_passes() throws Exception {
        String token = buildToken(KID, "RS256", issV2(), "api://" + CLIENT_ID, nowPlus(3600), 0);
        assertThatCode(() -> validator.validate(token)).doesNotThrowAnyException();
    }

    @Test
    void validToken_arrayAudience_passes() throws Exception {
        String payload = buildPayload(issV2(), CLIENT_ID, nowPlus(3600), 0).replace(
                "\"aud\":\"" + CLIENT_ID + "\"",
                "\"aud\":[\"" + CLIENT_ID + "\",\"other\"]");
        String token = signToken(KID, "RS256", payload);
        assertThatCode(() -> validator.validate(token)).doesNotThrowAnyException();
    }

    @Test
    void validToken_noNbf_passes() throws Exception {
        // nbf of 0 means absent — should still pass
        String token = buildToken(KID, "RS256", issV2(), CLIENT_ID, nowPlus(3600), 0);
        assertThatCode(() -> validator.validate(token)).doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // Structural / format rejections
    // -------------------------------------------------------------------------

    @Test
    void missingDots_rejected() {
        assertThatThrownBy(() -> validator.validate("notavalidtoken"))
                .hasMessageContaining("invalid token format");
    }

    @Test
    void twoPartToken_rejected() {
        assertThatThrownBy(() -> validator.validate("header.payload"))
                .hasMessageContaining("invalid token format");
    }

    @Test
    void unsupportedAlgorithm_rejected() throws Exception {
        String token = buildToken(KID, "HS256", issV2(), CLIENT_ID, nowPlus(3600), 0);
        assertThatThrownBy(() -> validator.validate(token))
                .hasMessageContaining("unsupported algorithm");
    }

    @Test
    void missingKid_rejected() throws Exception {
        String token = buildToken("", "RS256", issV2(), CLIENT_ID, nowPlus(3600), 0);
        assertThatThrownBy(() -> validator.validate(token))
                .hasMessageContaining("missing kid");
    }

    // -------------------------------------------------------------------------
    // Temporal claim rejections
    // -------------------------------------------------------------------------

    @Test
    void expiredToken_rejected() throws Exception {
        String token = buildToken(KID, "RS256", issV2(), CLIENT_ID, nowMinus(60), 0);
        assertThatThrownBy(() -> validator.validate(token))
                .hasMessageContaining("token expired");
    }

    @Test
    void notYetValid_rejected() throws Exception {
        // nbf 5 minutes in the future
        String token = buildToken(KID, "RS256", issV2(), CLIENT_ID, nowPlus(3600), nowPlus(300));
        assertThatThrownBy(() -> validator.validate(token))
                .hasMessageContaining("token not yet valid");
    }

    // -------------------------------------------------------------------------
    // Claims rejections
    // -------------------------------------------------------------------------

    @Test
    void wrongIssuer_rejected() throws Exception {
        String token = buildToken(KID, "RS256", "https://evil.example.com/", CLIENT_ID, nowPlus(3600), 0);
        assertThatThrownBy(() -> validator.validate(token))
                .hasMessageContaining("invalid issuer");
    }

    @Test
    void wrongTenantInIssuer_rejected() throws Exception {
        String token = buildToken(KID, "RS256",
                "https://login.microsoftonline.com/other-tenant/v2.0", CLIENT_ID, nowPlus(3600), 0);
        assertThatThrownBy(() -> validator.validate(token))
                .hasMessageContaining("invalid issuer");
    }

    @Test
    void wrongAudience_rejected() throws Exception {
        String token = buildToken(KID, "RS256", issV2(), "wrong-client-id", nowPlus(3600), 0);
        assertThatThrownBy(() -> validator.validate(token))
                .hasMessageContaining("invalid audience");
    }

    // -------------------------------------------------------------------------
    // Signature rejections
    // -------------------------------------------------------------------------

    @Test
    void tamperedPayload_rejected() throws Exception {
        String token = buildToken(KID, "RS256", issV2(), CLIENT_ID, nowPlus(3600), 0);
        String[] parts = token.split("\\.");
        // Flip the last character of the payload — tampering may cause a JSON parse
        // error (invalid base64 → garbled JSON) or a signature verification failure,
        // both of which are acceptable rejections.
        String tamperedPayload = parts[1].substring(0, parts[1].length() - 1) +
                (parts[1].charAt(parts[1].length() - 1) == 'A' ? 'B' : 'A');
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];
        assertThatThrownBy(() -> validator.validate(tampered))
                .isInstanceOf(Exception.class);
    }

    @Test
    void wrongSignature_rejected() throws Exception {
        // Build a valid token, then replace the signature with garbage bytes
        String token = buildToken(KID, "RS256", issV2(), CLIENT_ID, nowPlus(3600), 0);
        String[] parts = token.split("\\.");
        String badSig = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("invalidsignature".getBytes());
        String tampered = parts[0] + "." + parts[1] + "." + badSig;
        assertThatThrownBy(() -> validator.validate(tampered))
                .isInstanceOf(Exception.class);
    }

    @Test
    void unknownKid_rejected() throws Exception {
        // validator only knows KID="test-kid"; use a different kid
        String token = buildToken("unknown-kid", "RS256", issV2(), CLIENT_ID, nowPlus(3600), 0);
        assertThatThrownBy(() -> validator.validate(token))
                .hasMessageContaining("key not found");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String issV2() {
        return "https://login.microsoftonline.com/" + TENANT_ID + "/v2.0";
    }

    private static String issV1() {
        return "https://sts.windows.net/" + TENANT_ID + "/";
    }

    private static long nowPlus(int seconds) {
        return Instant.now().getEpochSecond() + seconds;
    }

    private static long nowMinus(int seconds) {
        return Instant.now().getEpochSecond() - seconds;
    }

    /**
     * Builds a signed JWT with the given parameters.
     * nbf=0 means the claim is omitted.
     */
    private static String buildToken(String kid, String alg, String iss, String aud,
                                     long exp, long nbf) throws Exception {
        return signToken(kid, alg, buildPayload(iss, aud, exp, nbf));
    }

    private static String buildPayload(String iss, String aud, long exp, long nbf) {
        long now = Instant.now().getEpochSecond();
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"iss\":\"").append(iss).append("\",");
        sb.append("\"aud\":\"").append(aud).append("\",");
        sb.append("\"iat\":").append(now).append(",");
        sb.append("\"exp\":").append(exp);
        if (nbf != 0) sb.append(",\"nbf\":").append(nbf);
        sb.append("}");
        return sb.toString();
    }

    private static String signToken(String kid, String alg, String payloadJson) throws Exception {
        String headerJson = "{\"alg\":\"" + alg + "\",\"typ\":\"JWT\",\"kid\":\"" + kid + "\"}";
        String header  = base64url(headerJson.getBytes());
        String payload = base64url(payloadJson.getBytes());
        String signingInput = header + "." + payload;

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey);
        sig.update(signingInput.getBytes());
        byte[] sigBytes = sig.sign();

        return signingInput + "." + base64url(sigBytes);
    }

    private static String base64url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
