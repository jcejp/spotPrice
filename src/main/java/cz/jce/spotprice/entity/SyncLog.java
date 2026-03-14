package cz.jce.spotprice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "sync_log")
@Getter
@Setter
@NoArgsConstructor
public class SyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant syncTime;

    @Column(nullable = false, length = 64)
    private String payloadHash;

    public SyncLog(Instant syncTime, String payloadHash) {
        this.syncTime = syncTime;
        this.payloadHash = payloadHash;
    }
}
