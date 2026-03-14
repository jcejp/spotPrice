package cz.jce.spotprice.repository;

import cz.jce.spotprice.entity.SpotPrice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
class SpotPriceRepositoryTest {

    @Autowired
    private SpotPriceRepository repository;

    private static final Instant T1 = Instant.parse("2024-01-15T08:00:00Z");
    private static final Instant T2 = Instant.parse("2024-01-15T09:00:00Z");
    private static final Instant T3 = Instant.parse("2024-01-15T10:00:00Z");

    // ── findByTimestamp ───────────────────────────────────────────────────────

    @Test
    void findByTimestamp_existingRecord_returnsPrice() {
        repository.save(new SpotPrice(T1, new BigDecimal("50.000000"), new BigDecimal("1225.000000")));

        Optional<SpotPrice> result = repository.findByTimestamp(T1);

        assertThat(result).isPresent();
        assertThat(result.get().getPriceEur()).isEqualByComparingTo("50.000000");
        assertThat(result.get().getPriceCzk()).isEqualByComparingTo("1225.000000");
    }

    @Test
    void findByTimestamp_nonExistent_returnsEmpty() {
        Optional<SpotPrice> result = repository.findByTimestamp(T1);

        assertThat(result).isEmpty();
    }

    // ── findAllByTimestampBetween ─────────────────────────────────────────────

    @Test
    void findAllByTimestampBetween_returnsAllInRangeOrderedAscending() {
        repository.saveAll(List.of(
                new SpotPrice(T3, new BigDecimal("70.0"), new BigDecimal("1715.0")),
                new SpotPrice(T1, new BigDecimal("50.0"), new BigDecimal("1225.0")),
                new SpotPrice(T2, new BigDecimal("60.0"), new BigDecimal("1470.0"))
        ));

        List<SpotPrice> result = repository.findAllByTimestampBetweenOrderByTimestampAsc(
                T1, T3.plus(1, ChronoUnit.HOURS));

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getTimestamp()).isEqualTo(T1);
        assertThat(result.get(1).getTimestamp()).isEqualTo(T2);
        assertThat(result.get(2).getTimestamp()).isEqualTo(T3);
    }

    @Test
    void findAllByTimestampBetween_noDataInRange_returnsEmpty() {
        repository.save(new SpotPrice(T3, new BigDecimal("70.0"), new BigDecimal("1715.0")));

        List<SpotPrice> result = repository.findAllByTimestampBetweenOrderByTimestampAsc(T1, T2);

        assertThat(result).isEmpty();
    }

    // ── min/max aggregation queries ───────────────────────────────────────────

    @Test
    void findMinPriceEur_multipleRecords_returnsMinimum() {
        saveThreePrices();

        Optional<BigDecimal> min = repository.findMinPriceEur(T1, T3.plus(1, ChronoUnit.HOURS));

        assertThat(min).isPresent().hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("30.0"));
    }

    @Test
    void findMaxPriceEur_multipleRecords_returnsMaximum() {
        saveThreePrices();

        Optional<BigDecimal> max = repository.findMaxPriceEur(T1, T3.plus(1, ChronoUnit.HOURS));

        assertThat(max).isPresent().hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("70.0"));
    }

    @Test
    void findMinPriceCzk_multipleRecords_returnsMinimum() {
        saveThreePrices();

        Optional<BigDecimal> min = repository.findMinPriceCzk(T1, T3.plus(1, ChronoUnit.HOURS));

        assertThat(min).isPresent().hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("735.0"));
    }

    @Test
    void findMaxPriceCzk_multipleRecords_returnsMaximum() {
        saveThreePrices();

        Optional<BigDecimal> max = repository.findMaxPriceCzk(T1, T3.plus(1, ChronoUnit.HOURS));

        assertThat(max).isPresent().hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("1715.0"));
    }

    @Test
    void findMinMaxEur_noDataInRange_returnsEmpty() {
        assertThat(repository.findMinPriceEur(T1, T2)).isEmpty();
        assertThat(repository.findMaxPriceEur(T1, T2)).isEmpty();
    }

    @Test
    void aggregations_excludeRecordsOutsideRange() {
        // T1 = 30 EUR, T2 = 50 EUR (in range), T3 = 70 EUR (out of range)
        saveThreePrices();

        Optional<BigDecimal> max = repository.findMaxPriceEur(T1, T2.plus(1, ChronoUnit.SECONDS));

        // T3 (70) is excluded, so max should be 50
        assertThat(max).isPresent().hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("50.0"));
    }

    // ── UNIQUE constraint ─────────────────────────────────────────────────────

    @Test
    void save_duplicateTimestamp_throwsDataIntegrityViolationException() {
        repository.save(new SpotPrice(T1, new BigDecimal("50.0"), new BigDecimal("1225.0")));

        assertThatThrownBy(() ->
                repository.saveAndFlush(new SpotPrice(T1, new BigDecimal("60.0"), new BigDecimal("1470.0")))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private void saveThreePrices() {
        repository.saveAll(List.of(
                new SpotPrice(T1, new BigDecimal("30.0"), new BigDecimal("735.0")),
                new SpotPrice(T2, new BigDecimal("50.0"), new BigDecimal("1225.0")),
                new SpotPrice(T3, new BigDecimal("70.0"), new BigDecimal("1715.0"))
        ));
    }
}
