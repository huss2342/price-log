package app.pricelog.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.pricelog.api.domain.Chain;
import app.pricelog.api.repo.PriceObservationRepository;
import app.pricelog.api.repo.StoreRepository;
import app.pricelog.api.repo.TagRuleRepository;
import app.pricelog.api.repo.UserProductWatchRepository;
import app.pricelog.api.security.UserRepository;
import app.pricelog.api.storage.PhotoStore;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The account lifecycle: register, sign in, change the password, and delete
 * everything. Runs against the dev-default config (blank API key), where the
 * filters stay out of the way and requests without a token act as the owner.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    @Autowired
    StoreRepository stores;

    @Autowired
    PriceObservationRepository observations;

    @Autowired
    TagRuleRepository rules;

    @Autowired
    UserProductWatchRepository watches;

    @Autowired
    PhotoStore photos;

    @Autowired
    app.pricelog.api.repo.ProductRepository productRepo;

    @Test
    void registerReturnsATokenThatAuthenticatesMe() throws Exception {
        String email = uniqueEmail();
        String token = register(email, "password123");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.displayName").value("Auth"));
    }

    @Test
    void duplicateEmailIsAConflict() throws Exception {
        String email = uniqueEmail();
        register(email, "password123");

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, "password123")))
                .andExpect(status().isConflict());
    }

    @Test
    void weakPasswordAndBadEmailAreUnprocessable() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(uniqueEmail(), "short")))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("not-an-email", "password123")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void emailsAreNormalizedToLowercase() throws Exception {
        String email = uniqueEmail();
        String token = register(email.toUpperCase(), "password123");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        // And the normalized form cannot be registered a second time.
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, "password123")))
                .andExpect(status().isConflict());
    }

    @Test
    void badLoginIsUnauthorized() throws Exception {
        String email = uniqueEmail();
        register(email, "password123");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "wrong-password")))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("nobody-" + UUID.randomUUID() + "@example.com", "password123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChangeNeedsTheCurrentOne() throws Exception {
        String email = uniqueEmail();
        String token = register(email, "password123");

        // Wrong current password: forbidden, and the old one still works.
        mvc.perform(put("/api/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"nope\",\"newPassword\":\"newpassword123\"}"))
                .andExpect(status().isForbidden());

        // A weak new password is rejected even with the right current one.
        mvc.perform(put("/api/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password123\",\"newPassword\":\"short\"}"))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(put("/api/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password123\",\"newPassword\":\"newpassword123\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "newpassword123")))
                .andExpect(status().isOk());
    }

    @Test
    void deletingTheAccountWipesItsRowsAndPhotos() throws Exception {
        String email = uniqueEmail();
        String token = register(email, "password123");
        Long userId = users.findByEmail(email).orElseThrow().getId();
        String auth = "Bearer " + token;

        // A store, a tag rule, and a watched product with a photo.
        MvcResult storeResult = mvc.perform(post("/api/stores")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chain\":\"COSTCO\",\"label\":\"My Costco\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long storeId = Long.parseLong(storeResult.getResponse().getContentAsString()
                .replaceAll(".*\"id\":(\\d+).*", "$1"));

        mvc.perform(post("/api/tag-rules")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chain\":\"COSTCO\",\"matchType\":\"PRICE_ENDING\","
                                + "\"pattern\":\".97\",\"signal\":\"CLEARANCE\",\"meaning\":\"clearance\"}"))
                .andExpect(status().isCreated());

        var product = new app.pricelog.api.domain.Product();
        product.setDisplayName("Delete Me Item");
        product.setNormalizedKey("auth-test-" + UUID.randomUUID());
        product.setComparisonKey("PANTRY|delete-me|plain");
        product.setCategory(app.pricelog.api.domain.Category.PANTRY);
        var persisted = productRepo.save(product);
        String photoKey = photos.store("bytes".getBytes(), "image/jpeg", "tag");
        var observation = new app.pricelog.api.domain.PriceObservation();
        observation.setUserId(userId);
        observation.setProduct(persisted);
        observation.setStore(stores.findById(storeId).orElseThrow());
        observation.setObservedOn(java.time.LocalDate.now());
        observation.setPriceCents(500);
        observation.setPhotoUrl(photoKey);
        observations.save(observation);

        mvc.perform(put("/api/products/" + persisted.getId() + "/watch")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"watched\":true,\"targetPriceCents\":400}"))
                .andExpect(status().isOk());

        assertThat(stores.findByIdAndUserId(storeId, userId)).isPresent();

        mvc.perform(delete("/api/auth/account").header("Authorization", auth))
                .andExpect(status().isNoContent());

        assertThat(users.findById(userId)).isEmpty();
        assertThat(stores.findByIdAndUserId(storeId, userId)).isEmpty();
        assertThat(observations.findByUserIdOrderByObservedOnDescIdDesc(userId)).isEmpty();
        assertThat(rules.findByUserId(userId)).isEmpty();
        assertThat(watches.findByUserId(userId)).isEmpty();
        assertThat(photos.load(photoKey)).isEmpty();

        // The token died with the account.
        mvc.perform(get("/api/auth/me").header("Authorization", auth))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aNewAccountStartsWithTheOwnersTagRules() throws Exception {
        String email = uniqueEmail();
        String token = register(email, "password123");

        mvc.perform(get("/api/tag-rules").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(
                        rules.findByUserId(users.findFirstByOrderByIdAsc().orElseThrow().getId()).size()));
    }

    private String uniqueEmail() {
        return "auth-" + UUID.randomUUID() + "@example.com";
    }

    private String registerBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"displayName\":\"Auth\"}";
    }

    private String loginBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private String register(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        String response = result.getResponse().getContentAsString();
        int start = response.indexOf("\"token\":\"") + 9;
        return response.substring(start, response.indexOf('"', start));
    }
}
