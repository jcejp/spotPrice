package cz.jce.spotprice.service;

import cz.jce.spotprice.dto.api.CurrentPriceDto;
import cz.jce.spotprice.dto.api.PriceHistoryDto;
import cz.jce.spotprice.dto.api.TodayStatsDto;
import cz.jce.spotprice.entity.SpotPrice;
import cz.jce.spotprice.repository.SpotPriceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceQueryServiceTest {

    @Mock private SpotPriceRepository spotPriceRepository;
    @InjectMocks private PriceQueryService service;

    private static final Instant CURRENT_HOUR = Instant.now().truncatedTo(ChronoUnit.HOURS);
    private static final SpotPrice SAMPLE_PRICE =
            new SpotPrice(CURRENT_HOUR, new BigDecimal("50.000000"), new BigDecimal("1225.000000"));

    // ── getCurrentPrice ───────────────────────────────────────────────────────

    @Test
    void getCurrentPrice_priceExists_returnsDto() {
        when(spotPriceRepository.findByTimestamp(CURRENT_HOUR)).thenReturn(Optional.of(SAMPLE_PRICE));

        CurrentPriceDto result = service.getCurrentPrice();

        assertThat(result.timestamp()).isEqualTo(CURRENT_HOUR);
        assertThat(result.priceEur()).isEqualByComparingTo("50.000000");
        assertThat(result.priceCzk()).isEqualByComparingTo("1225.000000");
    }

    @Test
    void getCurrentPrice_noPrice_throwsNoSuchElementException() {
        when(spotPriceRepository.findByTimestamp(CURRENT_HOUR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentPrice())
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("No price data available for current hour");
    }

    // ── getTodayStats ─────────────────────────────────────────────────────────

    @Test
    void getTodayStats_dataExists_returnsCorrectMinMax() {
        when(spotPriceRepository.findMinPriceEur(any(), any())).thenReturn(Optional.of(new BigDecimal("30.0")));
        when(spotPriceRepository.findMaxPriceEur(any(), any())).thenReturn(Optional.of(new BigDecimal("75.0")));
        when(spotPriceRepository.findMinPriceCzk(any(), any())).thenReturn(Optional.of(new BigDecimal("735.0")));
        when(spotPriceRepository.findMaxPriceCzk(any(), any())).thenReturn(Optional.of(new BigDecimal("1837.5")));

        TodayStatsDto result = service.getTodayStats();

        assertThat(result.minEur()).isEqualByComparingTo("30.0");
        assertThat(result.maxEur()).isEqualByComparingTo("75.0");
        assertThat(result.minCzk()).isEqualByComparingTo("735.0");
        assertThat(result.maxCzk()).isEqualByComparingTo("1837.5");
    }

    @Test
    void getTodayStats_noData_throwsNoSuchElementException() {
        when(spotPriceRepository.findMinPriceEur(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTodayStats())
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("No price data available for today");
    }

    // ── getHistory ────────────────────────────────────────────────────────────

    @Test
    void getHistory_noParams_returnsResultsFromLast24Hours() {
        when(spotPriceRepository.findAllByTimestampBetweenOrderByTimestampAsc(any(), any()))
                .thenReturn(List.of(SAMPLE_PRICE));

        List<PriceHistoryDto> result = service.getHistory(Optional.empty(), Optional.empty());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().priceEur()).isEqualByComparingTo("50.000000");
    }

    @Test
    void getHistory_noData_returnsEmptyList() {
        when(spotPriceRepository.findAllByTimestampBetweenOrderByTimestampAsc(any(), any()))
                .thenReturn(List.of());

        List<PriceHistoryDto> result = service.getHistory(Optional.empty(), Optional.empty());

        assertThat(result).isEmpty();
    }

    @Test
    void getHistory_withDateRange_returnsResults() {
        when(spotPriceRepository.findAllByTimestampBetweenOrderByTimestampAsc(any(), any()))
                .thenReturn(List.of(SAMPLE_PRICE));

        List<PriceHistoryDto> result = service.getHistory(
                Optional.of(LocalDate.of(2024, 1, 10)),
                Optional.of(LocalDate.of(2024, 1, 12)));

        assertThat(result).hasSize(1);
    }

    @Test
    void getHistory_startDateOnly_usesTodayAsEndDate() {
        when(spotPriceRepository.findAllByTimestampBetweenOrderByTimestampAsc(any(), any()))
                .thenReturn(List.of());

        assertThatNoException().isThrownBy(() ->
                service.getHistory(Optional.of(LocalDate.of(2024, 1, 10)), Optional.empty()));
    }

    @Test
    void getHistory_endDateOnly_usesYesterdayAsStartDate() {
        when(spotPriceRepository.findAllByTimestampBetweenOrderByTimestampAsc(any(), any()))
                .thenReturn(List.of());

        assertThatNoException().isThrownBy(() ->
                service.getHistory(Optional.empty(), Optional.of(LocalDate.of(2024, 3, 15))));
    }

    @Test
    void getHistory_mapsAllFieldsCorrectly() {
        Instant ts = Instant.parse("2024-01-15T08:00:00Z");
        SpotPrice price = new SpotPrice(ts, new BigDecimal("42.123456"), new BigDecimal("1032.024177"));
        when(spotPriceRepository.findAllByTimestampBetweenOrderByTimestampAsc(any(), any()))
                .thenReturn(List.of(price));

        List<PriceHistoryDto> result = service.getHistory(Optional.empty(), Optional.empty());

        assertThat(result).hasSize(1);
        PriceHistoryDto dto = result.getFirst();
        assertThat(dto.timestamp()).isEqualTo(ts);
        assertThat(dto.priceEur()).isEqualByComparingTo("42.123456");
        assertThat(dto.priceCzk()).isEqualByComparingTo("1032.024177");
    }
}
