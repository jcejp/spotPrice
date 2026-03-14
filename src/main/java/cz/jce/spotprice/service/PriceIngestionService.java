package cz.jce.spotprice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.jce.spotprice.dto.api.SyncResultDto;
import cz.jce.spotprice.dto.awattar.AwattarDataPoint;
import cz.jce.spotprice.dto.awattar.AwattarResponse;
import cz.jce.spotprice.entity.SpotPrice;
import cz.jce.spotprice.entity.SyncLog;
import cz.jce.spotprice.repository.SpotPriceRepository;
import cz.jce.spotprice.repository.SyncLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceIngestionService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final SpotPriceRepository spotPriceRepository;
    private final SyncLogRepository syncLogRepository;
    private final ExchangeRateService exchangeRateService;

    @Value("${app.awattar.url}")
    private String awattarUrl;

    /**
     * Scheduled task – runs according to cron, respects hash deduplication.
     */
    @Scheduled(cron = "${app.scheduler.cron}")
    public void syncPrices() {
        log.info("Starting scheduled spot price sync");
        try {
            SyncResultDto result = performSync(false);
            log.info("Scheduled sync finished: status={}, records={}", result.status(), result.recordsProcessed());
        } catch (Exception e) {
            log.error("Unexpected error during scheduled sync", e);
        }
    }

    /**
     * Manual trigger – always bypasses hash check and forces a full fetch and persist.
     *
     * @return result of the sync operation
     * @throws RestClientException                        if aWATTar API is unavailable
     * @throws ExchangeRateService.ExchangeRateFetchException if Frankfurter API is unavailable
     */
    public SyncResultDto triggerManualSync() {
        log.info("Manual sync triggered");
        return performSync(true);
    }

    // ── Core logic ────────────────────────────────────────────────────────────

    private SyncResultDto performSync(boolean force) {
        String rawJson = fetchRawJson();
        String hash = computeSha256(rawJson);

        if (!force) {
            Optional<SyncLog> latestLog = syncLogRepository.findTopByOrderBySyncTimeDesc();
            if (latestLog.isPresent() && latestLog.get().getPayloadHash().equals(hash)) {
                log.info("Payload hash unchanged — skipping sync");
                return new SyncResultDto(SyncResultDto.Status.SKIPPED, 0, Instant.now(),
                        "Data unchanged since last sync");
            }
        }

        BigDecimal eurToCzk = exchangeRateService.fetchEurToCzk();
        AwattarResponse awattarResponse;
        try {
            awattarResponse = objectMapper.readValue(rawJson, AwattarResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse aWATTar response", e);
        }

        int count = persistPrices(awattarResponse.data(), eurToCzk, hash);
        log.info("Sync complete — processed {} price records (force={})", count, force);
        return new SyncResultDto(SyncResultDto.Status.SYNCED, count, Instant.now(),
                "Successfully synced " + count + " records");
    }

    @Transactional
    protected int persistPrices(List<AwattarDataPoint> dataPoints, BigDecimal eurToCzk, String hash) {
        syncLogRepository.save(new SyncLog(Instant.now(), hash));

        for (AwattarDataPoint point : dataPoints) {
            Instant hourTimestamp = Instant.ofEpochMilli(point.startTimestamp())
                    .truncatedTo(ChronoUnit.HOURS);

            BigDecimal priceEur = BigDecimal.valueOf(point.marketprice()).setScale(6, RoundingMode.HALF_UP);
            BigDecimal priceCzk = priceEur.multiply(eurToCzk).setScale(6, RoundingMode.HALF_UP);

            spotPriceRepository.findByTimestamp(hourTimestamp).ifPresentOrElse(
                    existing -> {
                        existing.setPriceEur(priceEur);
                        existing.setPriceCzk(priceCzk);
                        log.debug("Updated price for {}", hourTimestamp);
                    },
                    () -> {
                        spotPriceRepository.save(new SpotPrice(hourTimestamp, priceEur, priceCzk));
                        log.debug("Saved new price for {}", hourTimestamp);
                    }
            );
        }

        return dataPoints.size();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String fetchRawJson() {
        // Fetch today + tomorrow: day-ahead prices are published ~13:00 CET daily
        ZoneId zone = ZoneId.of("Europe/Prague");
        LocalDate today = LocalDate.now(zone);
        long start = today.atStartOfDay(zone).toInstant().toEpochMilli();
        long end   = today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli();

        log.debug("Fetching aWATTar data: start={}, end={}", start, end);
        String response = restClient.get()
                .uri(awattarUrl + "?start=" + start + "&end=" + end)
                .retrieve()
                .body(String.class);
        if (response == null) {
            throw new RestClientException("Empty response from aWATTar API");
        }
        return response;
    }

    private String computeSha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
