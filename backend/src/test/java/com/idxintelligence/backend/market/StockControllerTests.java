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

@WebMvcTest(controllers = StockController.class, properties = "app.data-source=demo")
@Import({MarketService.class, MockMarketDataProvider.class, CandleService.class, IndexController.class,
        StockControllerTests.TestBeans.class})
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
    void stockListSearchesSortsAndPages() throws Exception {
        mockMvc.perform(get("/api/stocks?search=bank&sort=change&order=desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.stocks[0].ticker").value("BMRI"))   // +1,21%, then BBCA +0,77%, BBRI -1,46%
                .andExpect(jsonPath("$.stocks[2].ticker").value("BBRI"));
        mockMvc.perform(get("/api/stocks?sector=Energy&sort=ticker&order=asc&size=1&page=2"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.stocks.length()").value(1))
                .andExpect(jsonPath("$.stocks[0].ticker").value("PGAS"));
        mockMvc.perform(get("/api/stocks?sort=nonsense")).andExpect(status().isBadRequest());
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class TestBeans {
        @org.springframework.context.annotation.Bean
        java.time.Clock clock() {
            return java.time.Clock.systemUTC();
        }

        @org.springframework.context.annotation.Bean
        com.idxintelligence.backend.ingest.YahooFinanceClient yahoo() {
            return org.mockito.Mockito.mock(com.idxintelligence.backend.ingest.YahooFinanceClient.class);
        }
    }

    @Test
    void candlesForDailyAndLongerIntervals() throws Exception {
        mockMvc.perform(get("/api/stocks/BBCA/candles?interval=1d&range=1M"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interval").value("1d"))
                .andExpect(jsonPath("$.range").value("1M"))
                .andExpect(jsonPath("$.intraday").value(false))
                .andExpect(jsonPath("$.candles[-1].close").value(9850));
        mockMvc.perform(get("/api/stocks/BBCA/candles?interval=1mo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("MAX"));
        mockMvc.perform(get("/api/stocks/BBCA/candles?interval=2h")).andExpect(status().isBadRequest());
    }

    @Test
    void demoModeHasNoIntradayCandles() throws Exception {
        mockMvc.perform(get("/api/stocks/BBCA/candles?interval=1h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intraday").value(true))
                .andExpect(jsonPath("$.candles.length()").value(0));
    }

    @Test
    void stockListFiltersByDirection() throws Exception {
        mockMvc.perform(get("/api/stocks?change=flat"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.stocks[0].ticker").value("UNVR"));
        mockMvc.perform(get("/api/stocks?change=sideways")).andExpect(status().isBadRequest());
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
