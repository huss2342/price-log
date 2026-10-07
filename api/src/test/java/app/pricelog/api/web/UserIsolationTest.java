package app.pricelog.api.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.pricelog.api.capture.StoreResolver;
import app.pricelog.api.domain.Category;
import app.pricelog.api.domain.Chain;
import app.pricelog.api.domain.PriceObservation;
import app.pricelog.api.domain.Product;
import app.pricelog.api.domain.Store;
import app.pricelog.api.repo.PriceObservationRepository;
import app.pricelog.api.repo.ProductRepository;
import app.pricelog.api.security.UserRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * One user's log is invisible to another: observations, the watchlist, and
 * tag rules. The catalog rows are shared, but every read is scoped through
 * the reader's own observations.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserIsolationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    @Autowired
    ProductRepository products;

    @Autowired
    PriceObservationRepository observations;

    @Autowired
    StoreResolver storeResolver;

    @Test
    void observationsAreInvisibleAcrossUsers() throws Exception {
        String alice = bearer();
        String bob = bearer();
        long aliceId = userIdOf(alice);

        long observationId = logObservation(aliceId, "iso-milk");

        // Alice sees it; Bob gets an empty log and a 404 on the id.
        mvc.perform(get("/api/observations").header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + observationId + ")]").isNotEmpty());
        mvc.perform(get("/api/observations").header("Authorization", bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/observations/" + observationId).header("Authorization", bob))
                .andExpect(status().isNotFound());

        // The paged shape is per-user too.
        mvc.perform(get("/api/observations").header("Authorization", bob)
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Review queue, search, and history only know the reader's rows.
        mvc.perform(get("/api/observations/pending").header("Authorization", bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/search").header("Authorization", bob).param("q", "Isolation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/search").header("Authorization", alice).param("q", "Isolation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isNotEmpty());
    }

    @Test
    void watchlistAndTagRulesAreInvisibleAcrossUsers() throws Exception {
        String alice = bearer();
        String bob = bearer();
        long aliceId = userIdOf(alice);

        long productId = logObservation(aliceId, "iso-eggs");

        mvc.perform(put("/api/products/" + productId + "/watch")
                        .header("Authorization", alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"watched\":true,\"targetPriceCents\":300}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.watched").value(true))
                .andExpect(jsonPath("$.targetPriceCents").value(300));

        // A negative target is rejected.
        mvc.perform(put("/api/products/" + productId + "/watch")
                        .header("Authorization", alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetPriceCents\":-5}"))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(get("/api/watchlist").header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isNotEmpty());
        mvc.perform(get("/api/watchlist").header("Authorization", bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // Alice's own rules are hers; Bob only ever sees his own seed copy.
        mvc.perform(get("/api/tag-rules").header("Authorization", alice))
                .andExpect(jsonPath("$[?(@.pattern=='.97')]").isNotEmpty());
        mvc.perform(get("/api/tag-rules").header("Authorization", bob))
                .andExpect(jsonPath("$[?(@.pattern=='.97')]").isNotEmpty());

        // Bob cannot delete Alice's rule: it is not his, so it does not exist.
        long aliceRule = ruleIdOf(alice, ".97");
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete("/api/tag-rules/" + aliceRule).header("Authorization", bob))
                .andExpect(status().isNotFound());
    }

    @Test
    void captureStoreIdMustBelongToTheCaller() throws Exception {
        String alice = bearer();
        String bob = bearer();
        long aliceId = userIdOf(alice);

        Store aliceStore = storeResolver.forChain(aliceId, Chain.COSTCO);

        // Bob naming Alice's store id gets a 404, not someone else's store.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/captures")
                        .file("photo", "not-a-photo".getBytes())
                        .param("storeId", String.valueOf(aliceStore.getId()))
                        .header("Authorization", bob))
                .andExpect(status().isNotFound());
    }

    private long logObservation(long userId, String suffix) {
        Product product = new Product();
        product.setDisplayName("Isolation " + suffix);
        product.setNormalizedKey("iso-" + suffix + "-" + UUID.randomUUID());
        product.setComparisonKey("DAIRY|iso-" + suffix + "|plain");
        product.setCategory(Category.DAIRY);
        product = products.save(product);

        Store store = storeResolver.forChain(userId, Chain.COSTCO);
        PriceObservation observation = new PriceObservation();
        observation.setUserId(userId);
        observation.setProduct(product);
        observation.setStore(store);
        observation.setObservedOn(LocalDate.now());
        observation.setPriceCents(499);
        return observations.save(observation).getId();
    }

    private long ruleIdOf(String auth, String pattern) throws Exception {
        MvcResult result = mvc.perform(get("/api/tag-rules").header("Authorization", auth))
                .andExpect(status().isOk())
                .andReturn();
        String response = result.getResponse().getContentAsString();
        int patternAt = response.indexOf("\"pattern\":\"" + pattern + "\"");
        int idAt = response.lastIndexOf("\"id\":", patternAt);
        int start = idAt + 5;
        int end = response.indexOf(',', start);
        return Long.parseLong(response.substring(start, end));
    }

    private long userIdOf(String auth) throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/me").header("Authorization", auth))
                .andExpect(status().isOk())
                .andReturn();
        String response = result.getResponse().getContentAsString();
        int start = response.indexOf("\"id\":") + 5;
        int end = response.indexOf(',', start);
        return Long.parseLong(response.substring(start, end));
    }

    private String bearer() throws Exception {
        String email = "iso-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String response = result.getResponse().getContentAsString();
        int start = response.indexOf("\"token\":\"") + 9;
        return "Bearer " + response.substring(start, response.indexOf('"', start));
    }
}
