package cz.jce.spotprice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "spot_price")
@Getter
@Setter
@NoArgsConstructor
public class SpotPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Instant timestamp;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal priceEur;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal priceCzk;

    public SpotPrice(Instant timestamp, BigDecimal priceEur, BigDecimal priceCzk) {
        this.timestamp = timestamp;
        this.priceEur = priceEur;
        this.priceCzk = priceCzk;
    }
}
