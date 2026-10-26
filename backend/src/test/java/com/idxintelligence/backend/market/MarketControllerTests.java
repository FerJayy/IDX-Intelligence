package com.idxintelligence.backend.market;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketController.class)
@Import({MarketService.class, MockMarketDataProvider.class})
class MarketControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void overviewIsLabelledAsDemoData() throws Exception {
        mockMvc.perform(get("/api/market/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.notice").value(MarketService.DEMO_NOTICE))
                .andExpect(jsonPath("$.generatedAt").value(endsWith("+07:00")));
    }

    @Test
    void overviewRanksGainersLosersAndMostActive() throws Exception {
        mockMvc.perform(get("/api/market/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topGainers[0].ticker").value("GOTO"))
                .andExpect(jsonPath("$.topLosers[0].ticker").value("ADRO"))
                .andExpect(jsonPath("$.mostActive[0].ticker").value("BBCA"))
                .andExpect(jsonPath("$.advancers").value(6))
                .andExpect(jsonPath("$.decliners").value(4))
                .andExpect(jsonPath("$.unchanged").value(1));
    }
}
