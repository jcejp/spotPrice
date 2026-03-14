# Databáze

Aplikace používá **PostgreSQL 16**. Schéma je spravováno výhradně přes **Flyway** migrace. Hibernate má nastaveno `ddl-auto: validate` – schéma nikdy nemění, pouze ověřuje shodu s entitami při startu.

---

## Tabulky

### `spot_price`

Uchovává hodinové spotové ceny elektřiny.

```sql
CREATE TABLE spot_price (
    id        BIGSERIAL      PRIMARY KEY,
    timestamp TIMESTAMPTZ    NOT NULL UNIQUE,
    price_eur NUMERIC(18, 6) NOT NULL,
    price_czk NUMERIC(18, 6) NOT NULL
);

CREATE INDEX idx_spot_price_timestamp ON spot_price (timestamp);
```

| Sloupec | Typ | Popis |
|---|---|---|
| `id` | `BIGSERIAL` | Automaticky generovaný primární klíč |
| `timestamp` | `TIMESTAMPTZ` | Začátek hodinového intervalu v UTC. Unikátní klíč – jedna hodina = jeden záznam. |
| `price_eur` | `NUMERIC(18,6)` | Cena v EUR/MWh, přesnost 6 desetinných míst |
| `price_czk` | `NUMERIC(18,6)` | Cena v CZK/MWh vypočtená jako `price_eur × kurz_EUR_CZK` |

**Indexy:**
- `PRIMARY KEY` na `id`
- `UNIQUE` constraint na `timestamp` – databázová ochrana proti duplicitním záznamům
- `idx_spot_price_timestamp` – urychluje časové dotazy pro `/history` a `/today-stats`

---

### `sync_log`

Uchovává historii synchronizací. Každý úspěšný sync (kdy se data změnila) vytvoří nový řádek.

```sql
CREATE TABLE sync_log (
    id           BIGSERIAL    PRIMARY KEY,
    sync_time    TIMESTAMPTZ  NOT NULL,
    payload_hash VARCHAR(64)  NOT NULL
);
```

| Sloupec | Typ | Popis |
|---|---|---|
| `id` | `BIGSERIAL` | Primární klíč |
| `sync_time` | `TIMESTAMPTZ` | Čas provedení synchronizace (UTC) |
| `payload_hash` | `VARCHAR(64)` | SHA-256 hash raw JSON odpovědi z aWATTar API (64 hex znaků) |

Při každém spuštění scheduleru se načte poslední záznam (`ORDER BY sync_time DESC LIMIT 1`). Pokud se hash shoduje s aktuálními daty z API, synchronizace se přeskočí.

---

## Flyway migrace

Migrační soubory jsou uloženy v `src/main/resources/db/migration/`.

| Soubor | Popis |
|---|---|
| `V1__create_spot_price.sql` | Vytvoří tabulku `spot_price` s indexem |
| `V2__create_sync_log.sql` | Vytvoří tabulku `sync_log` |

Flyway je spuštěno automaticky při startu aplikace. Migrace jsou aplikovány vzestupně a každá je aplikována právě jednou.

> **Důležité:** Nikdy neupravujte existující migrační soubory. Pro změnu schématu vždy vytvořte nový soubor s vyšším verzovacím číslem (např. `V3__...`).

---

## Připojení k databázi

### Vývojové prostředí (Docker)

```
Host:     localhost
Port:     5432
Database: spotprice
User:     spotprice
Password: spotprice
```

JDBC URL: `jdbc:postgresql://localhost:5432/spotprice`

### pgAdmin

Webový klient pro správu databáze je dostupný na `http://localhost:5050` po spuštění Docker Compose. Server "SpotPrice Dev" je předkonfigurován – stačí zadat heslo `spotprice`.

### Užitečné SQL dotazy pro diagnostiku

```sql
-- Počet uložených hodinových cen
SELECT COUNT(*) FROM spot_price;

-- Posledních 5 synchronizací
SELECT * FROM sync_log ORDER BY sync_time DESC LIMIT 5;

-- Ceny za dnešní den (Prague time)
SELECT timestamp AT TIME ZONE 'Europe/Prague' AS local_time, price_eur, price_czk
FROM spot_price
WHERE timestamp >= DATE_TRUNC('day', NOW() AT TIME ZONE 'Europe/Prague') AT TIME ZONE 'Europe/Prague'
ORDER BY timestamp;

-- Denní statistiky
SELECT
    MIN(price_eur) AS min_eur,
    MAX(price_eur) AS max_eur,
    MIN(price_czk) AS min_czk,
    MAX(price_czk) AS max_czk
FROM spot_price
WHERE timestamp >= DATE_TRUNC('day', NOW() AT TIME ZONE 'Europe/Prague') AT TIME ZONE 'Europe/Prague';
```
