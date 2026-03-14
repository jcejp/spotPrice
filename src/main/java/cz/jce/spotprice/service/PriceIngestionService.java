package cz.jce.spotprice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Scheduled(cron = "${app.scheduler.cron}")
    public void syncPrices() {
        log.info("Starting spot price sync");
        try {
            String rawJson = fetchRawJson();
            String hash = computeSha256(rawJson);

            Optional<SyncLog> latestLog = syncLogRepository.findTopByOrderBySyncTimeDesc();
            if (latestLog.isPresent() && latestLog.get().getPayloadHash().equals(hash)) {
                log.info("Payload hash unchanged — skipping sync");
                return;
            }

            log.info("New data detected, processing prices");
            BigDecimal eurToCzk = exchangeRateService.fetchEurToCzk();

            AwattarResponse awattarResponse = objectMapper.readValue(rawJson, AwattarResponse.class);
            persistPrices(awattarResponse.data(), eurToCzk, hash);
            log.info("Sync complete — processed {} price records", awattarResponse.data().size());

        } catch (ExchangeRateService.ExchangeRateFetchException e) {
            log.error("Exchange rate fetch failed, skipping sync cycle: {}", e.getMessage());
        } catch (RestClientException e) {
            log.error("Failed to fetch data from aWATTar API: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during price sync", e);
        }
    }

    @Transactional
    protected void persistPrices(List<AwattarDataPoint> dataPoints, BigDecimal eurToCzk, String hash) {
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
    }

    private String fetchRawJson() {
        log.debug("Fetching raw JSON from aWATTar: {}", awattarUrl);
        String response = restClient.get()
                .uri(awattarUrl)
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
