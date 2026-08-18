package app.pricelog.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pricelog")
public class PriceLogProperties {

    private final AzureOpenAi azureOpenAi = new AzureOpenAi();
    private final Photos photos = new Photos();
    private final Auth auth = new Auth();

    public AzureOpenAi getAzureOpenAi() {
        return azureOpenAi;
    }

    public Photos getPhotos() {
        return photos;
    }

    public Auth getAuth() {
        return auth;
    }

    public static class AzureOpenAi {
        /** Resource endpoint, e.g. https://my-openai-account.openai.azure.com */
        private String endpoint = "";
        private String apiKey = "";
        /** Deployment name, not the model name. */
        private String deployment = "tag-extractor";
        private String apiVersion = "2025-01-01-preview";
        /** gpt-5 family only. Low keeps latency and cost down for OCR-shaped work. */
        private String reasoningEffort = "low";
        private int maxCompletionTokens = 4000;
        private int timeoutSeconds = 90;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getDeployment() {
            return deployment;
        }

        public void setDeployment(String deployment) {
            this.deployment = deployment;
        }

        public String getApiVersion() {
            return apiVersion;
        }

        public void setApiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
        }

        public String getReasoningEffort() {
            return reasoningEffort;
        }

        public void setReasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
        }

        public int getMaxCompletionTokens() {
            return maxCompletionTokens;
        }

        public void setMaxCompletionTokens(int maxCompletionTokens) {
            this.maxCompletionTokens = maxCompletionTokens;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public static class Photos {
        /** "local" writes to disk for development, "blob" writes to Azure Storage. */
        private String mode = "local";
        private String localDir = "./data/photos";
        private String connectionString = "";
        private String container = "tag-photos";

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getLocalDir() {
            return localDir;
        }

        public void setLocalDir(String localDir) {
            this.localDir = localDir;
        }

        public String getConnectionString() {
            return connectionString;
        }

        public void setConnectionString(String connectionString) {
            this.connectionString = connectionString;
        }

        public String getContainer() {
            return container;
        }

        public void setContainer(String container) {
            this.container = container;
        }
    }

    public static class Auth {
        /** Shared secret sent as the X-API-Key header. Empty disables the check. */
        private String apiKey = "";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }
}
