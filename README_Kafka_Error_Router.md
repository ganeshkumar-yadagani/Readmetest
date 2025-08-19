package com.yourorg.largefile.publisher;

import com.azure.core.credential.TokenCredential;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class LargeStorageBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(LargeStorageBuilder.class);

    // ---------- Entity types ----------
    private static final String ET_PUBLISHER = "PUBLISHER";
    private static final String ET_EVENT     = "EVENT";

    // ---------- Rules payload field names ----------
    private static final String FIELD_PUBLISHER_CONFIGS = "publisherConfigs";
    private static final String FIELD_LFE_CONFIGS       = "largeFileEventConfigs";
    private static final String FIELD_ENTITY_TYPE       = "entityType";
    private static final String FIELD_PROPERTY_NAME     = "propertyName";
    private static final String FIELD_PROPERTY_VALUE    = "propertyValue";
    private static final String FIELD_EVENT_TYPE        = "eventType";
    private static final String FIELD_MAX_SIZE_KB       = "maxEventSizeInKb";
    private static final String FIELD_LARGEFILE_TYPE    = "largeFileType";

    // ---------- Known property keys coming from publisherConfigs ----------
    private static final String STRIPNULLS_PROP        = "deep.largefile.stripNulls";
    private static final String RETRIES_PROP           = "deep.event.largefile.retries";
    private static final String TENANTID_PROP          = "deep.largefile.azure.tenantId";
    private static final String AUTHORITY_PROP         = "deep.largefile.azure.authority";
    private static final String STORAGEACCOUNT_PROP    = "deep.largefile.azure.storageAccount";
    private static final String RESOURCE_PROP          = "deep.largefile.azure.resource";

    // ---------- Defaults / tuning ----------
    private static final long DEFAULT_BLOCK_SIZE       = 4L * 1024 * 1024; // 4 MB
    private static final long DEFAULT_TIMEOUT_IN_SEC   = 300L;
    private static final int  DEFAULT_MAX_CONCURRENCY  = 8;

    // ---------- Builder inputs ----------
    private final String environment;
    private String applicationId;     // clientId / appId for Azure AD
    private boolean enableCertAuth;   // true = cert auth, false = client secret
    private String clientSecret;      // used when enableCertAuth = false
    private String publicCertPem;     // used when enableCertAuth = true

    // optionally allow injecting a prebuilt BlobServiceClient (tests)
    private BlobServiceClient prebuiltBlobServiceClient;

    public LargeStorageBuilder(String environment) {
        this.environment = environment;
    }

    public LargeStorageBuilder withApplicationId(String applicationId) {
        this.applicationId = applicationId;
        return this;
    }

    /** Use Azure AD client secret flow. */
    public LargeStorageBuilder withAzureSecret(String clientSecret) {
        this.enableCertAuth = false;
        this.clientSecret   = clientSecret;
        return this;
    }

    /** Use Azure AD certificate (PEM) flow. */
    public LargeStorageBuilder withAzureCertificate(String publicCertPem) {
        this.enableCertAuth = true;
        this.publicCertPem  = publicCertPem;
        return this;
    }

    /** For tests or special cases. If set, builder will not create a new BlobServiceClient. */
    public LargeStorageBuilder withBlobServiceClient(BlobServiceClient client) {
        this.prebuiltBlobServiceClient = client;
        return this;
    }

    /**
     * Build EventStorage by calling rules, extracting publisher configs for a specific entity type,
     * parsing large-file event configs, building Azure credential & BlobServiceClient.
     */
    public EventStorage build() throws StorageInitializationException {
        // 1) Pull rules JSON (your existing call)
        JsonNode root = callLargeFileRules();
        if (root == null) {
            throw new StorageInitializationException("Rules response was empty", 6000);
        }

        // 2) Optional global tuning (keep backward compatible)
        long blockSize       = getLong(root, "blockSize", DEFAULT_BLOCK_SIZE);
        long timeoutSec      = getLong(root, "timeout", DEFAULT_TIMEOUT_IN_SEC);
        int  maxConcurrency  = getInt (root, "maxConcurrency", DEFAULT_MAX_CONCURRENCY);

        // 3) Build a minimal index of publisherConfigs -> read only specific properties
        //    PUBLISHER-scoped properties
        boolean stripNulls     = readBooleanProp(root, ET_PUBLISHER, STRIPNULLS_PROP, true);
        String  tenantId       = readStringProp (root, ET_PUBLISHER, TENANTID_PROP,       null);
        String  authorityUrl   = readStringProp (root, ET_PUBLISHER, AUTHORITY_PROP,      null);
        String  resourceUrl    = readStringProp (root, ET_PUBLISHER, RESOURCE_PROP,       null);
        String  storageAccount = readStringProp (root, ET_PUBLISHER, STORAGEACCOUNT_PROP, null);

        //    EVENT-scoped property (example: token retries)
        int tokenRetries       = readIntProp   (root, ET_EVENT,     RETRIES_PROP,          3);

        // 4) Validate required azure fields
        requireNonBlank(storageAccount, "Missing publisher property: " + STORAGEACCOUNT_PROP, 7001);
        requireNonBlank(tenantId,       "Missing publisher property: " + TENANTID_PROP,       7002);
        requireNonBlank(authorityUrl,   "Missing publisher property: " + AUTHORITY_PROP,      7003);
        requireNonBlank(resourceUrl,    "Missing publisher property: " + RESOURCE_PROP,       7004);

        // 5) Parse largeFileEventConfigs -> map
        Map<String, LargeStorageEventConfig> eventConfigMap = parseEventConfigs(root.path(FIELD_LFE_CONFIGS));
        if (eventConfigMap.isEmpty()) {
            throw new StorageInitializationException("There are no events configured for this publisher.", 7000);
        }

        // 6) Build credential (secret or cert) using your existing provider
        TokenCredential credential;
        try {
            credential = new AzureTokenProvider(
                    tenantId,
                    applicationId,
                    enableCertAuth,
                    clientSecret,
                    publicCertPem,
                    authorityUrl,
                    resourceUrl
            ).getTokenProvider(tokenRetries); // adjust if your API differs
        } catch (MalformedURLException e) {
            LOGGER.error("Malformed URL in Azure configuration", e);
            throw new StorageInitializationException("Invalid Azure URL(s) in configuration", 7005);
        } catch (RuntimeException re) {
            LOGGER.error("Azure credential creation failed", re);
            throw new StorageInitializationException("Failed to build Azure credentials", re, 7006);
        }

        // 7) BlobServiceClient: reuse if injected, else build a fresh one
        BlobServiceClient blobServiceClient = this.prebuiltBlobServiceClient;
        if (blobServiceClient == null) {
            String endpoint = "https://" + storageAccount + ".blob.core.windows.net/";
            blobServiceClient = new BlobServiceClientBuilder()
                    .endpoint(endpoint)
                    .credential(credential)
                    .buildClient();
        }

        // 8) Return EventStorage (single client reused; containers resolved inside EventStorage)
        return new EventStorage(
                environment,
                blobServiceClient,
                eventConfigMap,
                stripNulls,
                blockSize,
                maxConcurrency,
                timeoutSec
        );
    }

    // ======= JSON helpers (DTO‑free) =======

    /** Read a single property from publisherConfigs filtered by entityType. */
    private static String readStringProp(JsonNode root, String entityType, String propName, String defVal) {
        JsonNode list = root.path(FIELD_PUBLISHER_CONFIGS);
        if (!list.isArray()) return defVal;

        for (JsonNode n : list) {
            if (!entityType.equalsIgnoreCase(n.path(FIELD_ENTITY_TYPE).asText(null))) continue;
            if (!propName.equals(n.path(FIELD_PROPERTY_NAME).asText(null))) continue;

            String v = n.path(FIELD_PROPERTY_VALUE).asText(null);
            return v != null ? v.trim() : defVal;
        }
        return defVal;
    }

    private static boolean readBooleanProp(JsonNode root, String entityType, String propName, boolean defVal) {
        String v = readStringProp(root, entityType, propName, null);
        if (v == null) return defVal;
        String s = v.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(s) || "1".equals(s) || "yes".equals(s)) return true;
        if ("false".equals(s) || "0".equals(s) || "no".equals(s)) return false;
        return defVal;
    }

    private static int readIntProp(JsonNode root, String entityType, String propName, int defVal) {
        String v = readStringProp(root, entityType, propName, null);
        if (v == null) return defVal;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException ignore) { return defVal; }
    }

    private static Map<String, LargeStorageEventConfig> parseEventConfigs(JsonNode arr) {
        Map<String, LargeStorageEventConfig> map = new HashMap<>();
        if (arr == null || !arr.isArray()) return map;

        for (JsonNode cfg : arr) {
            String eventType = text(cfg, FIELD_EVENT_TYPE, null);
            if (eventType == null || eventType.isBlank()) continue;

            int maxSizeKb = getInt(cfg, FIELD_MAX_SIZE_KB, 100);
            String lfTypeRaw = text(cfg, FIELD_LARGEFILE_TYPE, "NONE");

            LargeFileType lfType;
            try {
                lfType = LargeFileType.valueOf(lfTypeRaw);
            } catch (IllegalArgumentException e) {
                lfType = LargeFileType.NONE;
            }

            map.put(eventType, new LargeStorageEventConfig(eventType, maxSizeKb, lfType));
        }
        return map;
    }

    private static String text(JsonNode node, String field, String def) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? def : v.asText();
    }

    private static int getInt(JsonNode node, String field, int def) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? def : v.asInt(def);
    }

    private static long getLong(JsonNode node, String field, long def) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? def : v.asLong(def);
    }

    private static void requireNonBlank(String s, String message, int code) throws StorageInitializationException {
        if (s == null || s.trim().isEmpty()) {
            throw new StorageInitializationException(message, code);
        }
    }

    // ======= You already have this; keep your existing implementation =======
    private JsonNode callLargeFileRules() {
        // TODO: call your rules service / config provider and return the JsonNode
        throw new UnsupportedOperationException("callLargeFileRules() must be implemented");
    }
}
