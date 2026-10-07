package app.pricelog.api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The boundary with public reads switched on. Getting it wrong either breaks a
 * published log or hands a stranger the owner's Azure OpenAI bill, so it is
 * pinned here rather than left to inspection.
 *
 * <p>{@link ApiKeyFilterClosedTest} covers the shipped default, where reads are
 * shut too.
 */
@SpringBootTest(properties = {
        "pricelog.auth.api-key=test-key-for-the-filter",
        "pricelog.auth.public-read=true",
})
@AutoConfigureMockMvc
class ApiKeyFilterTest {

    private static final String KEY = "test-key-for-the-filter";

    @Autowired
    MockMvc mvc;

    @Test
    void readsAreOpenToAnyone() throws Exception {
        mvc.perform(get("/api/stores")).andExpect(status().isOk());
        mvc.perform(get("/api/search").param("q", "eggs")).andExpect(status().isOk());
        mvc.perform(get("/api/watchlist")).andExpect(status().isOk());
    }

    @Test
    void capturingNeedsTheKey() throws Exception {
        mvc.perform(post("/api/captures")).andExpect(status().isUnauthorized());
    }

    @Test
    void everyMutationNeedsTheKey() throws Exception {
        mvc.perform(post("/api/stores")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/observations/1")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/observations/1")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/products/1/watch")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/tag-rules/1")).andExpect(status().isUnauthorized());
    }

    /**
     * A GET, but it scrapes Costco's site on the caller's behalf. Free to the
     * visitor, not free to the owner.
     */
    @Test
    void forcingADealsRefreshNeedsTheKey() throws Exception {
        mvc.perform(get("/api/deals").param("force", "true")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/deals").param("force", "false")).andExpect(status().isOk());
    }

    @Test
    void theKeyStillGrantsEverything() throws Exception {
        mvc.perform(get("/api/stores").header("X-API-Key", KEY)).andExpect(status().isOk());
        mvc.perform(get("/api/deals").param("force", "false").header("X-API-Key", KEY))
                .andExpect(status().isOk());
    }

    @Test
    void aWrongKeyIsNoBetterThanNone() throws Exception {
        mvc.perform(post("/api/captures").header("X-API-Key", "nope"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * A capture with no photo attached is the caller's mistake, not the
     * server's. The catch-all handler once relabelled Spring's own 400 as a
     * 500, which hides a client error inside the server error log.
     */
    @Test
    void aMalformedCaptureIsABadRequestNotAServerFault() throws Exception {
        // A well-formed multipart upload that simply omits the "photo" part.
        mvc.perform(multipart("/api/captures").header("X-API-Key", KEY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void healthStaysOpenSoProbesWork() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    /**
     * A forged token must not fall through to the API-key check or the
     * anonymous public reads: it is rejected where it stands.
     */
    @Test
    void aBadBearerTokenIsRejectedOutright() throws Exception {
        mvc.perform(get("/api/stores").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    /** The legacy key still works, and acts as the owner. */
    @Test
    void theKeyActsAsTheOwner() throws Exception {
        mvc.perform(get("/api/auth/me").header("X-API-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner@local"));
    }

    /**
     * A Bearer token stands on its own: with the key configured, a signed-in
     * user writes without also presenting the legacy key.
     */
    @Test
    void aBearerTokenNeedsNoApiKey() throws Exception {
        String token = register("filter-" + UUID.randomUUID() + "@example.com");

        mvc.perform(post("/api/stores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chain\":\"COSTCO\",\"label\":\"Token Costco\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void anonymousMeIsUnauthorized() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    /** Register and login are the front door: they cannot ask for the key. */
    @Test
    void registerAndLoginNeedNoKey() throws Exception {
        String email = "filter-" + UUID.randomUUID() + "@example.com";
        String token = register(email);

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        // The new token authenticates where the key used to be the only way in.
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void aWrongPasswordIsUnauthorized() throws Exception {        String email = "filter-" + UUID.randomUUID() + "@example.com";
        register(email);

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String register(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"password123\","
                + "\"displayName\":\"Filter\"}";
        var result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andReturn();
        String response = result.getResponse().getContentAsString();
        int start = response.indexOf("\"token\":\"") + 9;
        return response.substring(start, response.indexOf('"', start));
    }
}
