package app.pricelog.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.pricelog.api.domain.Category;
import app.pricelog.api.domain.PriceObservation;
import app.pricelog.api.domain.Product;
import app.pricelog.api.domain.Store;
import app.pricelog.api.repo.PriceObservationRepository;
import app.pricelog.api.repo.ProductRepository;
import app.pricelog.api.repo.StoreRepository;
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
 * Stores are per-user and user-managed now: create, rename, delete, and the
 * rule that one user's stores are invisible to everyone else.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StoreCrudTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    StoreRepository stores;

    @Autowired
    ProductRepository products;

    @Autowired
    PriceObservationRepository observations;

    @Autowired
    UserRepository users;

    @Test
    void createListsRenameAndDelete() throws Exception {
        String auth = bearer();

        long id = createStore(auth, "COSTCO", "Costco Tustin");

        mvc.perform(get("/api/stores").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + id + ")].label").value("Costco Tustin"))
                .andExpect(jsonPath("$[?(@.id==" + id + ")].observationCount").value(0));

        mvc.perform(put("/api/stores/" + id)
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Costco Irvine\",\"city\":\"Irvine\",\"state\":\"CA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Costco Irvine"))
                .andExpect(jsonPath("$.city").value("Irvine"));

        mvc.perform(delete("/api/stores/" + id).header("Authorization", auth))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/stores").header("Authorization", auth))
                .andExpect(jsonPath("$[?(@.id==" + id + ")]").isEmpty());
    }

    @Test
    void duplicateChainAndLabelIsAConflict() throws Exception {
        String auth = bearer();
        createStore(auth, "ALDI", "Aldi Main");

        mvc.perform(post("/api/stores")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chain\":\"ALDI\",\"label\":\"Aldi Main\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void blankLabelAndUnknownChainAreUnprocessable() throws Exception {
        String auth = bearer();

        mvc.perform(post("/api/stores")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chain\":\"COSTCO\",\"label\":\"  \"}"))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(post("/api/stores")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chain\":\"TARGET\",\"label\":\"Target\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void renamingOntoAnExistingNameIsAConflict() throws Exception {
        String auth = bearer();
        long first = createStore(auth, "WALMART", "Walmart A");
        long second = createStore(auth, "WALMART", "Walmart B");

        mvc.perform(put("/api/stores/" + second)
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Walmart A\"}"))
                .andExpect(status().isConflict());

        // The failed rename changed nothing.
        assertThat(stores.findById(second).orElseThrow().getLabel()).isEqualTo("Walmart B");
        assertThat(stores.findById(first).orElseThrow().getLabel()).isEqualTo("Walmart A");
    }

    @Test
    void deletingAStoreWithObservationsIsAConflict() throws Exception {
        String auth = bearer();
        long userId = currentUserId(auth);
        long storeId = createStore(auth, "COSTCO", "Costco Busy");

        Store store = stores.findById(storeId).orElseThrow();
        Product product = new Product();
        product.setDisplayName("Busy Item");
        product.setNormalizedKey("store-crud-" + UUID.randomUUID());
        product.setComparisonKey("PANTRY|busy|plain");
        product.setCategory(Category.PANTRY);
        product = products.save(product);
        PriceObservation observation = new PriceObservation();
        observation.setUserId(userId);
        observation.setProduct(product);
        observation.setStore(store);
        observation.setObservedOn(LocalDate.now());
        observation.setPriceCents(999);
        observations.save(observation);

        mvc.perform(delete("/api/stores/" + storeId).header("Authorization", auth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "Store has 1 logged prices. Move or delete them first."));
    }

    @Test
    void oneUserCannotSeeOrTouchAnothersStores() throws Exception {
        String alice = bearer();
        String bob = bearer();
        long aliceStore = createStore(alice, "COSTCO", "Alice Costco");

        // Bob's list does not include it.
        mvc.perform(get("/api/stores").header("Authorization", bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + aliceStore + ")]").isEmpty());

        // Neither does touching it: 404, not 403, so ids cannot be probed.
        mvc.perform(put("/api/stores/" + aliceStore)
                        .header("Authorization", bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Bob Costco\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/stores/" + aliceStore).header("Authorization", bob))
                .andExpect(status().isNotFound());

        assertThat(stores.findById(aliceStore).orElseThrow().getLabel()).isEqualTo("Alice Costco");
    }

    @Test
    void theSameChainAndLabelCanExistForTwoUsers() throws Exception {
        String alice = bearer();
        String bob = bearer();

        long aliceStore = createStore(alice, "ALDI", "Aldi");
        long bobStore = createStore(bob, "ALDI", "Aldi");

        assertThat(aliceStore).isNotEqualTo(bobStore);
    }

    private String bearer() throws Exception {
        String email = "store-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String response = result.getResponse().getContentAsString();
        int start = response.indexOf("\"token\":\"") + 9;
        return "Bearer " + response.substring(start, response.indexOf('"', start));
    }

    private long currentUserId(String auth) throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/me").header("Authorization", auth))
                .andExpect(status().isOk())
                .andReturn();
        String response = result.getResponse().getContentAsString();
        int start = response.indexOf("\"id\":") + 5;
        int end = response.indexOf(',', start);
        return Long.parseLong(response.substring(start, end));
    }

    private long createStore(String auth, String chain, String label) throws Exception {
        MvcResult result = mvc.perform(post("/api/stores")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chain\":\"" + chain + "\",\"label\":\"" + label + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chain").value(chain))
                .andExpect(jsonPath("$.label").value(label))
                .andReturn();
        String response = result.getResponse().getContentAsString();
        int start = response.indexOf("\"id\":") + 5;
        int end = response.indexOf(',', start);
        return Long.parseLong(response.substring(start, end));
    }
}
