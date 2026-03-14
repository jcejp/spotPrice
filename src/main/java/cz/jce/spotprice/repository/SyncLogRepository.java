package cz.jce.spotprice.repository;

import cz.jce.spotprice.entity.SyncLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

    Optional<SyncLog> findTopByOrderBySyncTimeDesc();
}
