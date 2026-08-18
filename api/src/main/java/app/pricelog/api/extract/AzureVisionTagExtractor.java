package app.pricelog.api.extract;

import app.pricelog.api.config.PriceLogProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads a price tag photo with an Azure OpenAI vision deployment and returns
 * structured fields. Uses JSON schema strict mode, so the response either
 * conforms to {@code extraction/tag-schema.json} or the call fails.
 */
@Component
public class AzureVisionTagExtractor {

    private static final Logger log = LoggerFactory.getLogger(AzureVisionTagExtractor.class);

    private final PriceLogProperties.AzureOpenAi config;
    private final ObjectMapper mapper;
    private final RestClient restClient;
    private final String systemPrompt;
    private final JsonNode schema;

    public AzureVisionTagExtractor(PriceLogProperties properties, ObjectMapper mapper) {
        this.config = properties.getAzureOpenAi();
        this.mapper = mapper;
        this.systemPrompt = readResource("extraction/tag-prompt.txt");
        this.schema = readJsonResource("extraction/tag-schema.json");

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(config.getTimeoutSeconds()));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public boolean isConfigured() {
        return !config.getEndpoint().isBlank() && !config.getApiKey().isBlank();
    }

    /**
     * @param imageBytes  the photo as uploaded
     * @param contentType image MIME type, used to build the data URL
     * @param chainHint   store chain the user already picked, or null to let the
     *                    model infer it from the tag design
     */
    public ExtractedTag extract(byte[] imageBytes, String contentType, String chainHint) {
        if (!isConfigured()) {
            throw new TagExtractionException(
                    "Azure OpenAI is not configured. Set pricelog.azure-openai.endpoint and .api-key.");
        }

        String dataUrl = "data:" + (contentType == null ? "image/jpeg" : contentType)
                + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

        String userText = chainHint == null || chainHint.isBlank()
                ? "Read this price tag."
                : "Read this price tag. The shopper says this store is " + chainHint
                        + "; trust that over your own guess for storeChain.";

        ObjectNode body = buildRequest(systemPrompt, userText, dataUrl);

        String url = config.getEndpoint().replaceAll("/+$", "")
                + "/openai/deployments/" + config.getDeployment()
                + "/chat/completions?api-version=" + config.getApiVersion();

        JsonNode response;
        try {
            response = restClient.post()
                    .uri(url)
                    .header("api-key", config.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new TagExtractionException("Azure OpenAI call failed: " + e.getMessage(), e);
        }

        if (response == null) {
            throw new TagExtractionException("Azure OpenAI returned an empty response.");
        }

        JsonNode choice = response.path("choices").path(0);
        String finishReason = choice.path("finish_reason").asText("");
        if ("length".equals(finishReason)) {
            throw new TagExtractionException(
                    "Extraction truncated before finishing. Raise pricelog.azure-openai.max-completion-tokens.");
        }
        if ("content_filter".equals(finishReason)) {
            throw new TagExtractionException("Azure content filter blocked this image.");
        }

        String content = choice.path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new TagExtractionException("Azure OpenAI returned no content. finish_reason=" + finishReason);
        }

        logUsage(response);

        try {
            return mapper.readValue(content, ExtractedTag.class);
        } catch (JacksonException e) {
            throw new TagExtractionException("Could not parse extraction JSON: " + e.getMessage(), e);
        }
    }

    private ObjectNode buildRequest(String system, String userText, String dataUrl) {
        ObjectNode body = mapper.createObjectNode();

        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);

        ObjectNode user = messages.addObject().put("role", "user");
        ArrayNode parts = user.putArray("content");
        parts.addObject().put("type", "text").put("text", userText);
        ObjectNode image = parts.addObject().put("type", "image_url");
        // "high" detail costs more tokens but small print on a tag is the whole job.
        image.putObject("image_url").put("url", dataUrl).put("detail", "high");

        ObjectNode format = body.putObject("response_format").put("type", "json_schema");
        ObjectNode jsonSchema = format.putObject("json_schema");
        jsonSchema.put("name", "price_tag");
        jsonSchema.put("strict", true);
        jsonSchema.set("schema", schema);

        body.put("max_completion_tokens", config.getMaxCompletionTokens());
        if (!config.getReasoningEffort().isBlank()) {
            body.put("reasoning_effort", config.getReasoningEffort());
        }
        return body;
    }

    private void logUsage(JsonNode response) {
        JsonNode usage = response.path("usage");
        if (!usage.isMissingNode()) {
            log.info("tag extraction tokens: prompt={} completion={} total={}",
                    usage.path("prompt_tokens").asInt(),
                    usage.path("completion_tokens").asInt(),
                    usage.path("total_tokens").asInt());
        }
    }

    private String readResource(String path) {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing classpath resource " + path, e);
        }
    }

    private JsonNode readJsonResource(String path) {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return mapper.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing classpath resource " + path, e);
        } catch (JacksonException e) {
            throw new IllegalStateException("Invalid JSON in classpath resource " + path, e);
        }
    }
}
