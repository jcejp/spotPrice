package cz.jce.spotprice.service;

import cz.jce.spotprice.dto.awattar.AwattarDataPoint;
import cz.jce.spotprice.entity.SpotPrice;
import cz.jce.spotprice.entity.SyncLog;
import cz.jce.spotprice.repository.SpotPriceRepository;
import cz.jce.spotprice.repository.SyncLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PricePersistenceServiceTest {

    @Mock private SpotPriceRepository spotPriceRepository;
    @Mock private SyncLogRepository syncLogRepository;
    @InjectMocks private PricePersistenceService service;

    private static final Instant HOUR = Instant.parse("2024-01-15T10:00:00Z");
    private static final BigDecimal EUR_TO_CZK = new BigDecimal("24.500000");
    private static final AwattarDataPoint DATA_POINT = new AwattarDataPoint(
            HOUR.toEpochMilli(),
            HOUR.plus(1, ChronoUnit.HOURS).toEpochMilli(),
            50.0,
            "Eur/MWh"
    );

    @Test
    void persist_newTimestamp_savesNewSpotPrice() {
        when(spotPriceRepository.findByTimestamp(HOUR)).thenReturn(Optional.empty());

        int count = service.persist(List.of(DATA_POINT), EUR_TO_CZK, "hash123");

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<SpotPrice> captor = ArgumentCaptor.forClass(SpotPrice.class);
        verify(spotPriceRepository).save(captor.capture());
        assertThat(captor.getValue().getTimestamp()).isEqualTo(HOUR);
        assertThat(captor.getValue().getPriceEur()).isEqualByComparingTo("50.000000");
        assertThat(captor.getValue().getPriceCzk()).isEqualByComparingTo("1225.000000");
    }

    @Test
    void persist_existingTimestamp_updatesValuesAndSaves() {
        SpotPrice existing = new SpotPrice(HOUR, new BigDecimal("40.000000"), new BigDecimal("980.000000"));
        when(spotPriceRepository.findByTimestamp(HOUR)).thenReturn(Optional.of(existing));

        service.persist(List.of(DATA_POINT), EUR_TO_CZK, "hash123");

        verify(spotPriceRepository).save(existing);
        assertThat(existing.getPriceEur()).isEqualByComparingTo("50.000000");
        assertThat(existing.getPriceCzk()).isEqualByComparingTo("1225.000000");
    }

    @Test
    void persist_savesSyncLogWithCorrectHash() {
        when(spotPriceRepository.findByTimestamp(any())).thenReturn(Optional.empty());

        service.persist(List.of(DATA_POINT), EUR_TO_CZK, "abc123");

        ArgumentCaptor<SyncLog> captor = ArgumentCaptor.forClass(SyncLog.class);
        verify(syncLogRepository).save(captor.capture());
        assertThat(captor.getValue().getPayloadHash()).isEqualTo("abc123");
        assertThat(captor.getValue().getSyncTime()).isNotNull();
    }

    @Test
    void persist_syncLogSavedBeforePrices() {
        when(spotPriceRepository.findByTimestamp(any())).thenReturn(Optional.empty());
        var order = inOrder(syncLogRepository, spotPriceRepository);

        service.persist(List.of(DATA_POINT), EUR_TO_CZK, "hash");

        order.verify(syncLogRepository).save(any());
        order.verify(spotPriceRepository).save(any());
    }

    @Test
    void persist_multipleDataPoints_returnsCorrectCount() {
        Instant hour2 = HOUR.plus(1, ChronoUnit.HOURS);
        AwattarDataPoint point2 = new AwattarDataPoint(
                hour2.toEpochMilli(), hour2.plus(1, ChronoUnit.HOURS).toEpochMilli(), 60.0, "Eur/MWh");
        when(spotPriceRepository.findByTimestamp(any())).thenReturn(Optional.empty());

        int count = service.persist(List.of(DATA_POINT, point2), EUR_TO_CZK, "hash");

        assertThat(count).isEqualTo(2);
        verify(spotPriceRepository, times(2)).save(any(SpotPrice.class));
    }

    @Test
    void persist_emptyDataPoints_savesOnlySyncLog() {
        int count = service.persist(List.of(), EUR_TO_CZK, "hash");

        assertThat(count).isZero();
        verify(syncLogRepository).save(any());
        verifyNoInteractions(spotPriceRepository);
    }

    @Test
    void persist_startTimestampTruncatedToHour() {
        // Timestamp 30 minutes into the hour must be truncated down
        long midHourMs = HOUR.plusSeconds(1800).toEpochMilli();
        AwattarDataPoint point = new AwattarDataPoint(
                midHourMs, midHourMs + 3_600_000L, 50.0, "Eur/MWh");
        when(spotPriceRepository.findByTimestamp(HOUR)).thenReturn(Optional.empty());

        service.persist(List.of(point), EUR_TO_CZK, "hash");

        ArgumentCaptor<SpotPrice> captor = ArgumentCaptor.forClass(SpotPrice.class);
        verify(spotPriceRepository).save(captor.capture());
        assertThat(captor.getValue().getTimestamp()).isEqualTo(HOUR);
    }

    @Test
    void persist_priceScaledToSixDecimalPlaces() {
        // marketprice with many decimals must be stored with scale 6
        AwattarDataPoint point = new AwattarDataPoint(
                HOUR.toEpochMilli(), HOUR.plus(1, ChronoUnit.HOURS).toEpochMilli(),
                33.33333333, "Eur/MWh");
        when(spotPriceRepository.findByTimestamp(HOUR)).thenReturn(Optional.empty());

        service.persist(List.of(point), EUR_TO_CZK, "hash");

        ArgumentCaptor<SpotPrice> captor = ArgumentCaptor.forClass(SpotPrice.class);
        verify(spotPriceRepository).save(captor.capture());
        assertThat(captor.getValue().getPriceEur().scale()).isEqualTo(6);
        assertThat(captor.getValue().getPriceCzk().scale()).isEqualTo(6);
    }
}
