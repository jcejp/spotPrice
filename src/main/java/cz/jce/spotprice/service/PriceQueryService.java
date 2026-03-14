package cz.jce.spotprice.service;

import cz.jce.spotprice.dto.api.CurrentPriceDto;
import cz.jce.spotprice.dto.api.PriceHistoryDto;
import cz.jce.spotprice.dto.api.TodayStatsDto;
import cz.jce.spotprice.entity.SpotPrice;
import cz.jce.spotprice.repository.SpotPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PriceQueryService {

    private static final ZoneId PRAGUE_ZONE = ZoneId.of("Europe/Prague");

    private final SpotPriceRepository spotPriceRepository;

    public CurrentPriceDto getCurrentPrice() {
        Instant currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS);
        SpotPrice price = spotPriceRepository.findByTimestamp(currentHour)
                .orElseThrow(() -> new NoSuchElementException(
                        "No price data available for current hour: " + currentHour));
        return toCurrentDto(price);
    }

    public TodayStatsDto getTodayStats() {
        Instant[] todayRange = todayRange();
        Instant start = todayRange[0];
        Instant end = todayRange[1];

        BigDecimal minEur = spotPriceRepository.findMinPriceEur(start, end)
                .orElseThrow(() -> new NoSuchElementException("No price data available for today"));
        BigDecimal maxEur = spotPriceRepository.findMaxPriceEur(start, end)
                .orElseThrow(() -> new NoSuchElementException("No price data available for today"));
        BigDecimal minCzk = spotPriceRepository.findMinPriceCzk(start, end)
                .orElseThrow(() -> new NoSuchElementException("No price data available for today"));
        BigDecimal maxCzk = spotPriceRepository.findMaxPriceCzk(start, end)
                .orElseThrow(() -> new NoSuchElementException("No price data available for today"));

        return new TodayStatsDto(minEur, minCzk, maxEur, maxCzk);
    }

    public List<PriceHistoryDto> getHistory(Optional<LocalDate> startDate, Optional<LocalDate> endDate) {
        Instant start;
        Instant end;

        if (startDate.isPresent() || endDate.isPresent()) {
            LocalDate from = startDate.orElse(LocalDate.now(PRAGUE_ZONE).minusDays(1));
            LocalDate to = endDate.orElse(LocalDate.now(PRAGUE_ZONE));
            start = from.atStartOfDay(PRAGUE_ZONE).toInstant();
            end = to.plusDays(1).atStartOfDay(PRAGUE_ZONE).toInstant();
        } else {
            end = Instant.now();
            start = end.minus(24, ChronoUnit.HOURS);
        }

        return spotPriceRepository.findAllByTimestampBetweenOrderByTimestampAsc(start, end)
                .stream()
                .map(this::toHistoryDto)
                .toList();
    }

    private Instant[] todayRange() {
        LocalDate today = LocalDate.now(PRAGUE_ZONE);
        Instant start = today.atStartOfDay(PRAGUE_ZONE).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(PRAGUE_ZONE).toInstant();
        return new Instant[]{start, end};
    }

    private CurrentPriceDto toCurrentDto(SpotPrice p) {
        return new CurrentPriceDto(p.getTimestamp(), p.getPriceEur(), p.getPriceCzk());
    }

    private PriceHistoryDto toHistoryDto(SpotPrice p) {
        return new PriceHistoryDto(p.getTimestamp(), p.getPriceEur(), p.getPriceCzk());
    }
}
