package cz.jce.spotprice.repository;

import cz.jce.spotprice.entity.SpotPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SpotPriceRepository extends JpaRepository<SpotPrice, Long> {

    Optional<SpotPrice> findByTimestamp(Instant timestamp);

    List<SpotPrice> findAllByTimestampBetweenOrderByTimestampAsc(Instant start, Instant end);

    @Query("SELECT MIN(s.priceEur) FROM SpotPrice s WHERE s.timestamp >= :start AND s.timestamp < :end")
    Optional<BigDecimal> findMinPriceEur(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT MAX(s.priceEur) FROM SpotPrice s WHERE s.timestamp >= :start AND s.timestamp < :end")
    Optional<BigDecimal> findMaxPriceEur(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT MIN(s.priceCzk) FROM SpotPrice s WHERE s.timestamp >= :start AND s.timestamp < :end")
    Optional<BigDecimal> findMinPriceCzk(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT MAX(s.priceCzk) FROM SpotPrice s WHERE s.timestamp >= :start AND s.timestamp < :end")
    Optional<BigDecimal> findMaxPriceCzk(@Param("start") Instant start, @Param("end") Instant end);
}
