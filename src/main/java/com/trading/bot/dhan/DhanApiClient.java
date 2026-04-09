package com.trading.bot.dhan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;

/**
 * DhanApiClient — Low-level HTTP client for Dhan Broker API v2.
 *
 * Dhan API base: https://api.dhan.co
 * Docs: https://dhanhq.co/docs/v2/
 *
 * Authentication:
 *   Two headers required on every request:
 *     access-token: <your_token>    (from Dhan Developer Portal — long-lived)
 *     client-id:    <your_client_id>
 *
 * HOW TO GET YOUR DHAN CREDENTIALS:
 * ──────────────────────────────────────────────────────────────
 * 1. Open https://www.dhan.co → Log in → Go to "My Profile"
 * 2. Click "DhanHQ Developer" → "Generate Access Token"
 * 3. Copy Access Token and Client ID
 * 4. Set env vars:
 *      DHAN_ACCESS_TOKEN=eyJ0eXAiOi...
 *      DHAN_CLIENT_ID=1100123456
 *    OR create dhan_config.properties in project root:
 *      dhan.access.token=eyJ0eXAiOi...
 *      dhan.client.id=1100123456
 * ──────────────────────────────────────────────────────────────
 *
 * Dhan access tokens last 30 days (vs Upstox which expires daily).
 * This makes Dhan much more reliable for production use.
 */
public class DhanApiClient {

    // ─── API Base URLs ────────────────────────────────────────────────────────
    public static final String BASE_URL    = "https://api.dhan.co";
    public static final String BASE_URL_V2 = "https://api.dhan.co/v2";

    // ─── Config Keys ──────────────────────────────────────────────────────────
    private static final String CONFIG_FILE    = "dhan_config.properties";
    private static final String ENV_TOKEN      = "DHAN_ACCESS_TOKEN";
    private static final String ENV_CLIENT_ID  = "DHAN_CLIENT_ID";

    // ─── HTTP Client (singleton per instance) ────────────────────────────────
    private final HttpClient   httpClient;
    private final ObjectMapper mapper;

    // ─── Credentials (loaded once) ────────────────────────────────────────────
    private String accessToken;
    private String clientId;

    // ─── Singleton ────────────────────────────────────────────────────────────
    private static DhanApiClient instance;

    public static synchronized DhanApiClient getInstance() {
        if (instance == null) instance = new DhanApiClient();
        return instance;
    }

    private DhanApiClient() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.mapper = new ObjectMapper();
        loadCredentials();
    }

    // ─── Credential Loading ───────────────────────────────────────────────────

    private void loadCredentials() {
        // Priority 1: Environment variables
        String envToken = System.getenv(ENV_TOKEN);
        String envCid   = System.getenv(ENV_CLIENT_ID);

        if (envToken != null && !envToken.isBlank()) {
            this.accessToken = envToken.trim();
            this.clientId    = (envCid != null) ? envCid.trim() : "";
            System.out.println("✅ DhanApiClient: credentials loaded from environment variables");
            return;
        }

        // Priority 2: dhan_config.properties file
        try {
            File f = new File(CONFIG_FILE);
            if (f.exists()) {
                java.util.Properties props = new java.util.Properties();
                props.load(Files.newInputStream(f.toPath()));
                String token = props.getProperty("dhan.access.token", "").trim();
                String cid   = props.getProperty("dhan.client.id", "").trim();
                if (!token.isEmpty()) {
                    this.accessToken = token;
                    this.clientId    = cid;
                    System.out.println("✅ DhanApiClient: credentials loaded from " + CONFIG_FILE);
                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ DhanApiClient: could not read " + CONFIG_FILE + ": " + e.getMessage());
        }

        // No credentials found
        System.err.println("❌ DhanApiClient: No Dhan credentials found!");
        System.err.println("   Set env vars DHAN_ACCESS_TOKEN and DHAN_CLIENT_ID");
        System.err.println("   or create dhan_config.properties with dhan.access.token and dhan.client.id");
        this.accessToken = "";
        this.clientId    = "";
    }

    // ─── Core HTTP Methods ────────────────────────────────────────────────────

    /**
     * GET request to Dhan API.
     *
     * @param path  e.g., "/v2/marketfeed/quote"
     * @return parsed JSON response, or null on failure
     */
    public JsonNode get(String path) {
        return get(path, null);
    }

    public JsonNode get(String path, String queryParams) {
        try {
            String url = BASE_URL + path + (queryParams != null ? "?" + queryParams : "");
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("access-token", accessToken)
                .header("client-id",    clientId)
                .header("Content-Type", "application/json")
                .header("Accept",       "application/json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return handleResponse(resp, "GET", path);

        } catch (Exception e) {
            System.err.println("DhanApiClient GET " + path + " error: " + e.getMessage());
            return null;
        }
    }

    /**
     * POST request to Dhan API with JSON body.
     *
     * @param path  e.g., "/v2/optionchain"
     * @param body  JSON string body
     * @return parsed JSON response, or null on failure
     */
    public JsonNode post(String path, String body) {
        try {
            String url = BASE_URL + path;
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("access-token", accessToken)
                .header("client-id",    clientId)
                .header("Content-Type", "application/json")
                .header("Accept",       "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return handleResponse(resp, "POST", path);

        } catch (Exception e) {
            System.err.println("DhanApiClient POST " + path + " error: " + e.getMessage());
            return null;
        }
    }

    // ─── Response Handling ────────────────────────────────────────────────────

    private JsonNode handleResponse(HttpResponse<String> resp, String method, String path) {
        int status = resp.statusCode();

        if (status == 200 || status == 201) {
            try {
                return mapper.readTree(resp.body());
            } catch (Exception e) {
                System.err.println("DhanApiClient: JSON parse error for " + path + ": " + e.getMessage());
                return null;
            }
        }

        if (status == 401) {
            System.err.println("❌ DhanApiClient: 401 Unauthorized — Dhan token expired or invalid");
            System.err.println("   Refresh your token at https://www.dhan.co → DhanHQ Developer");
            System.err.println("   Then update DHAN_ACCESS_TOKEN env var or dhan_config.properties");
            return null;
        }

        if (status == 429) {
            System.err.println("⚠️ DhanApiClient: 429 Rate Limited — too many requests to Dhan API");
            return null;
        }

        System.err.println("⚠️ DhanApiClient: " + method + " " + path
            + " returned HTTP " + status + ": " + resp.body().substring(0, Math.min(200, resp.body().length())));
        return null;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Check if credentials are configured (before making any calls). */
    public boolean isConfigured() {
        return accessToken != null && !accessToken.isBlank();
    }

    /** Reload credentials from env/file (call after token refresh). */
    public void reloadCredentials() {
        loadCredentials();
    }

    /**
     * Update the access token live — no restart required.
     * Called by the Telegram /tokendhan command.
     *
     * @param newToken  The new Dhan JWT access token (starts with eyJ...)
     */
    public synchronized void updateToken(String newToken) {
        if (newToken == null || newToken.isBlank()) return;
        this.accessToken = newToken.trim();
        System.out.println("✅ DhanApiClient: access token updated live (no restart needed). "
            + "Preview: " + getTokenPreview());
    }

    /** Get current access token (for logging/debug only, never log the full token). */
    public String getTokenPreview() {
        if (accessToken == null || accessToken.length() < 10) return "NOT_SET";
        return accessToken.substring(0, 8) + "...";
    }

    public String getClientId() {
        return clientId;
    }
}
