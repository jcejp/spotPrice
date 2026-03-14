package cz.jce.spotprice.controller;

import cz.jce.spotprice.dto.api.CurrentPriceDto;
import cz.jce.spotprice.dto.api.PriceHistoryDto;
import cz.jce.spotprice.dto.api.TodayStatsDto;
import cz.jce.spotprice.service.PriceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/prices")
@RequiredArgsConstructor
public class PriceController {

    private final PriceQueryService priceQueryService;

    @GetMapping("/current")
    public ResponseEntity<CurrentPriceDto> getCurrent() {
        return ResponseEntity.ok(priceQueryService.getCurrentPrice());
    }

    @GetMapping("/today-stats")
    public ResponseEntity<TodayStatsDto> getTodayStats() {
        return ResponseEntity.ok(priceQueryService.getTodayStats());
    }

    @GetMapping("/history")
    public ResponseEntity<List<PriceHistoryDto>> getHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(priceQueryService.getHistory(
                Optional.ofNullable(startDate),
                Optional.ofNullable(endDate)
        ));
    }
}
