package app.pricelog.api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The shipped default: nothing is readable without the key.
 *
 * <p>Public reads exist for a deployment that wants to publish its log, but the
 * showcase is served by the front end's own sample data, so leaving them on
 * would expose a household's shopping and its tag photos for no benefit. That
 * this is the default, and not merely how production happens to be configured,
 * is the thing worth a test.
 */
@SpringBootTest(properties = "pricelog.auth.api-key=test-key-for-the-filter")
@AutoConfigureMockMvc
class ApiKeyFilterClosedTest {

    @Autowired
    MockMvc mvc;

    @Test
    void readsAreShutWithoutTheKey() throws Exception {
        mvc.perform(get("/api/stores")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/search").param("q", "eggs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/deals/published")).andExpect(status().isUnauthorized());
    }

    @Test
    void writesAreShutTooOfCourse() throws Exception {
        mvc.perform(post("/api/captures")).andExpect(status().isUnauthorized());
    }

    @Test
    void theOwnerIsUnaffected() throws Exception {
        mvc.perform(get("/api/stores").header("X-API-Key", "test-key-for-the-filter"))
                .andExpect(status().isOk());
    }

    @Test
    void healthStaysOpenSoProbesWork() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
