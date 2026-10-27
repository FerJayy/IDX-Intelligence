package com.idxintelligence.backend.ingest;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Manual import triggers; see {@link AdminAuth}. */
@RestController
public class AdminImportController {

    private final PriceImportService priceImport;
    private final BrokerImportService brokerImport;
    private final StockListingService stockListing;
    private final ScreenerService screener;
    private final AdminAuth adminAuth;

    public AdminImportController(PriceImportService priceImport, BrokerImportService brokerImport,
                                 StockListingService stockListing, ScreenerService screener, AdminAuth adminAuth) {
        this.priceImport = priceImport;
        this.brokerImport = brokerImport;
        this.stockListing = stockListing;
        this.screener = screener;
        this.adminAuth = adminAuth;
    }

    @PostMapping("/api/admin/import/screener")
    public ScreenerService.Result importScreener(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        adminAuth.require(token);
        return screener.importLatest();
    }

    @PostMapping("/api/admin/import/stocks")
    public StockListingService.Result importStockListing(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        adminAuth.require(token);
        return stockListing.sync();
    }

    @PostMapping("/api/admin/import/prices")
    public PriceImportService.ImportResult importPrices(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        adminAuth.require(token);
        return priceImport.importAll();
    }

    @PostMapping("/api/admin/import/brokers")
    public BrokerImportService.Result importBrokers(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        adminAuth.require(token);
        return brokerImport.importBatch();
    }
}
