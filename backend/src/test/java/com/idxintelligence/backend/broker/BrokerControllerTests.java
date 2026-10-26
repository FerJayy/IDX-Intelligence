package com.idxintelligence.backend.broker;

import com.idxintelligence.backend.market.MarketService;
import com.idxintelligence.backend.market.MockMarketDataProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.oneOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BrokerController.class, properties = "app.data-source=demo")
@Import({BrokerAnalysisService.class, MockBrokerDataProvider.class, MarketService.class, MockMarketDataProvider.class})
class BrokerControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void brokerSummaryIsLabelledDemoAndCoversTheRange() throws Exception {
        mockMvc.perform(get("/api/stocks/BBCA/brokers?range=1M"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.notice").value(MarketService.DEMO_NOTICE))
                .andExpect(jsonPath("$.range").value("1M"))
                .andExpect(jsonPath("$.topBuyers.length()").value(lessThanOrEqualTo(5)))
                .andExpect(jsonPath("$.topSellers.length()").value(lessThanOrEqualTo(5)))
                .andExpect(jsonPath("$.assessment.status").value(oneOf("ACCUMULATION", "NEUTRAL", "DISTRIBUTION")));
    }

    @Test
    void unknownTickerReturns404() throws Exception {
        mockMvc.perform(get("/api/stocks/XXXX/brokers")).andExpect(status().isNotFound());
    }
}
