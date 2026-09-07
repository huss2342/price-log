package app.pricelog.api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The log is readable by anyone so it can be shown to people; everything that
 * spends money or changes state is not. Getting this boundary wrong either
 * breaks the showcase or hands a stranger the owner's Azure OpenAI bill, so it
 * is pinned here rather than left to inspection.
 */
@SpringBootTest(properties = "pricelog.auth.api-key=test-key-for-the-filter")
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
}
