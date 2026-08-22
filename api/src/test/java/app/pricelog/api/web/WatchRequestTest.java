package app.pricelog.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import app.pricelog.api.web.AlertController.WatchRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

/**
 * Jackson 3 turned FAIL_ON_NULL_FOR_PRIMITIVES on by default, so a request body
 * that omits a primitive boolean is rejected outright rather than defaulted.
 * Request records therefore use wrappers, and this pins that.
 */
@SpringBootTest
class WatchRequestTest {

    @Autowired
    private ObjectMapper mapper;

    @Test
    void omittedFieldsAreAbsentRatherThanAnError() {
        WatchRequest request = mapper.readValue("{\"watched\":true}", WatchRequest.class);

        assertThat(request.watched()).isTrue();
        assertThat(request.clearTarget()).isNull();
        assertThat(request.targetPriceCents()).isNull();
    }

    @Test
    void anExplicitNullIsAlsoAccepted() {
        WatchRequest request =
                mapper.readValue("{\"watched\":true,\"clearTarget\":null}", WatchRequest.class);

        assertThat(request.clearTarget()).isNull();
    }

    @Test
    void aTargetPriceStillReadsThrough() {
        WatchRequest request =
                mapper.readValue("{\"watched\":true,\"targetPriceCents\":1700}", WatchRequest.class);

        assertThat(request.targetPriceCents()).isEqualTo(1700);
    }
}
