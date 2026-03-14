package cz.jce.spotprice.service;

import cz.jce.spotprice.dto.awattar.AwattarDataPoint;
import cz.jce.spotprice.entity.SpotPrice;
import cz.jce.spotprice.entity.SyncLog;
import cz.jce.spotprice.repository.SpotPriceRepository;
import cz.jce.spotprice.repository.SyncLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PricePersistenceService {

    private final SpotPriceRepository spotPriceRepository;
    private final SyncLogRepository syncLogRepository;

    /**
     * Saves a SyncLog record and upserts all price data points atomically.
     * Called from {@link PriceIngestionService} via a separate bean so that
     * Spring's AOP proxy actually intercepts the {@code @Transactional} boundary.
     */
    @Transactional
    public int persist(List<AwattarDataPoint> dataPoints, BigDecimal eurToCzk, String hash) {
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
                        spotPriceRepository.save(existing);
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
}
