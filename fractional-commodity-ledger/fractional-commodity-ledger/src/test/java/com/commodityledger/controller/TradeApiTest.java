package com.commodityledger.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP-level tests: security, status codes, validation and JSON shape. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TradeApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requestsWithoutCredentialsAreRejected() throws Exception {
        mockMvc.perform(get("/api/commodities"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tradersCanReadCommodities() throws Exception {
        mockMvc.perform(get("/api/commodities").with(trader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.symbol == 'GOLD')]").exists());
    }

    @Test
    void onlyAdminsCanChangePrices() throws Exception {
        String body = "{\"pricePerUnit\": 175.50}";

        mockMvc.perform(put("/api/commodities/SILVER/price").with(trader())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/commodities/SILVER/price").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricePerUnit").value(175.5));
    }

    @Test
    void fullBuyFlowOverHttp() throws Exception {
        long userId = createUser();

        mockMvc.perform(post("/api/users/{id}/wallet/deposit", userId).with(trader())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 10000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(10000.0));

        mockMvc.perform(post("/api/trades/buy").with(trader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + userId + ", \"commodity\": \"GOLD\", \"amount\": 500}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trade.side").value("BUY"))
                .andExpect(jsonPath("$.trade.quantity").value(0.04))
                .andExpect(jsonPath("$.trade.grossAmount").value(500.0))
                .andExpect(jsonPath("$.trade.ledgerEntries.length()").value(2))
                .andExpect(jsonPath("$.walletBalance").value(9500.0))
                .andExpect(jsonPath("$.replayed").value(false));

        mockMvc.perform(get("/api/users/{id}/portfolio", userId).with(trader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdings[0].commodity").value("GOLD"))
                .andExpect(jsonPath("$.cashBalance").value(9500.0));

        mockMvc.perform(get("/api/ledger/verify").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consistent").value(true));
    }

    @Test
    void insufficientFundsReturns422WithErrorCode() throws Exception {
        long userId = createUser();

        mockMvc.perform(post("/api/trades/buy").with(trader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + userId + ", \"commodity\": \"GOLD\", \"amount\": 500}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void quantityAndAmountTogetherFailValidation() throws Exception {
        mockMvc.perform(post("/api/trades/buy").with(trader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 1, \"commodity\": \"GOLD\", \"quantity\": 1, \"amount\": 500}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.exactlyOneOfQuantityOrAmount").exists());
    }

    @Test
    void unknownUserReturns404() throws Exception {
        mockMvc.perform(get("/api/users/999999").with(trader()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private long createUser() throws Exception {
        String response = mockMvc.perform(post("/api/users").with(trader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Api User\", \"email\": \"api-" + UUID.randomUUID() + "@example.com\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(response, "$.id");
        return id.longValue();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor trader() {
        return httpBasic("trader", "trader123");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return httpBasic("admin", "admin123");
    }
}
