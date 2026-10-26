package com.idxintelligence.backend.market;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockController.class)
@Import({MarketService.class, MockMarketDataProvider.class})
class StockControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void stockLookupIsCaseInsensitiveAndLabelledDemo() throws Exception {
        mockMvc.perform(get("/api/stocks/bbca"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.quote.ticker").value("BBCA"));
    }

    @Test
    void unknownTickerReturns404() throws Exception {
        mockMvc.perform(get("/api/stocks/XXXX")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/stocks/XXXX/prices")).andExpect(status().isNotFound());
    }

    @Test
    void unknownRangeReturns400() throws Exception {
        mockMvc.perform(get("/api/stocks/BBCA/prices?range=5Y")).andExpect(status().isBadRequest());
    }

    @Test
    void priceHistoryEndsAtTheDashboardQuote() throws Exception {
        mockMvc.perform(get("/api/stocks/BBCA/prices?range=1M"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.range").value("1M"))
                .andExpect(jsonPath("$.bars.length()").value(allOf(greaterThanOrEqualTo(20), lessThanOrEqualTo(23))))
                .andExpect(jsonPath("$.bars[-1].close").value(9850))
                .andExpect(jsonPath("$.bars[-2].close").value(9775))
                .andExpect(jsonPath("$.bars[-1].volume").value(85_400_000));
    }
}
