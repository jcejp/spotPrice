package cz.jce.spotprice.controller;

import cz.jce.spotprice.dto.api.SyncResultDto;
import cz.jce.spotprice.service.ExchangeRateService;
import cz.jce.spotprice.service.PriceIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private final PriceIngestionService priceIngestionService;

    /**
     * POST /api/sync
     *
     * Forces an immediate fetch and persist of spot price data from the aWATTar API,
     * bypassing the hash deduplication check. Useful for manual refreshes or
     * after a failed scheduled run.
     */
    @PostMapping
    public ResponseEntity<SyncResultDto> triggerSync() {
        try {
            SyncResultDto result = priceIngestionService.triggerManualSync();
            return ResponseEntity.ok(result);
        } catch (ExchangeRateService.ExchangeRateFetchException e) {
            log.error("Manual sync failed — exchange rate unavailable: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    new SyncResultDto(SyncResultDto.Status.FAILED, 0, Instant.now(),
                            "Exchange rate service unavailable: " + e.getMessage())
            );
        } catch (RestClientException e) {
            log.error("Manual sync failed — aWATTar API unavailable: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    new SyncResultDto(SyncResultDto.Status.FAILED, 0, Instant.now(),
                            "aWATTar API unavailable: " + e.getMessage())
            );
        }
    }
}
