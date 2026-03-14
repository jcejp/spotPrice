# REST API Reference

Základní cesty: `/api/prices`, `/api/sync`

Všechny odpovědi jsou v JSON formátu. Časová razítka jsou ve formátu ISO 8601 (UTC), např. `"2026-03-14T13:00:00Z"`. Číselné hodnoty jsou desetinná čísla s přesností 6 míst.

---

## GET /api/prices/current

Vrátí cenu elektřiny pro aktuální hodinu.

**"Aktuální hodina"** je definována jako začátek hodiny podle UTC – například v `13:47` UTC vrátí cenu platnou od `13:00` UTC.

### Odpověď `200 OK`

```json
{
  "timestamp": "2026-03-14T13:00:00Z",
  "priceEur":  95.420000,
  "priceCzk":  2397.762400
}
```

| Pole | Typ | Popis |
|---|---|---|
| `timestamp` | `string` (ISO 8601) | Začátek hodinového intervalu (UTC) |
| `priceEur` | `number` | Cena v EUR/MWh |
| `priceCzk` | `number` | Cena v CZK/MWh |

### Odpověď `404 Not Found`

Vrátí se v případě, že pro aktuální hodinu ještě neexistují data (aplikace nebyla synchronizována).

```json
{
  "error": "No price data available for current hour: 2026-03-14T13:00:00Z"
}
```

### Příklad

```bash
curl http://localhost:8080/api/prices/current
```

---

## GET /api/prices/today-stats

Vrátí denní minimum a maximum cen pro dnešní den. Hranice dne jsou počítány v časové zóně **Europe/Prague**.

### Odpověď `200 OK`

```json
{
  "minEur":  42.150000,
  "minCzk":  1059.452000,
  "maxEur":  187.320000,
  "maxCzk":  4708.046400
}
```

| Pole | Typ | Popis |
|---|---|---|
| `minEur` | `number` | Nejnižší cena dne v EUR/MWh |
| `minCzk` | `number` | Nejnižší cena dne v CZK/MWh |
| `maxEur` | `number` | Nejvyšší cena dne v EUR/MWh |
| `maxCzk` | `number` | Nejvyšší cena dne v CZK/MWh |

### Odpověď `404 Not Found`

```json
{
  "error": "No price data available for today"
}
```

### Příklad

```bash
curl http://localhost:8080/api/prices/today-stats
```

---

## GET /api/prices/history

Vrátí seznam hodinových cen seřazených vzestupně podle času.

### Query parametry

| Parametr | Typ | Povinný | Popis |
|---|---|---|---|
| `startDate` | `string` (YYYY-MM-DD) | Ne | Začátek rozsahu (od půlnoci Prague time) |
| `endDate` | `string` (YYYY-MM-DD) | Ne | Konec rozsahu (do půlnoci Prague time následujícího dne) |

**Výchozí chování (bez parametrů):** vrátí záznamy za posledních 24 hodin od aktuálního okamžiku.

**Částečné zadání parametrů:**
- Pouze `startDate` → do dnešního konce dne
- Pouze `endDate` → od předchozího dne

### Odpověď `200 OK`

```json
[
  {
    "timestamp": "2026-03-13T23:00:00Z",
    "priceEur":  78.300000,
    "priceCzk":  1967.760000
  },
  {
    "timestamp": "2026-03-14T00:00:00Z",
    "priceEur":  65.100000,
    "priceCzk":  1636.314000
  }
]
```

Prázdné pole `[]` je vráceno v případě, že pro zadaný rozsah neexistují žádná data (není to chyba).

### Příklady

```bash
# Posledních 24 hodin
curl http://localhost:8080/api/prices/history

# Konkrétní den
curl "http://localhost:8080/api/prices/history?startDate=2026-03-14&endDate=2026-03-14"

# Od určitého data do dnes
curl "http://localhost:8080/api/prices/history?startDate=2026-03-10"
```

---

## POST /api/sync

Vynutí okamžité stažení a uložení spotových cen z aWATTar API. Na rozdíl od plánované synchronizace **přeskočí kontrolu hash deduplication** – data jsou vždy stažena a zpracována bez ohledu na to, zda se od posledního běhu změnila.

Typické použití:
- ruční refresh po výpadku scheduleru
- první naplnění databáze bez čekání na plánovaný běh
- ověření, že API je dostupné a data jsou aktuální

### Odpověď `200 OK` – synchronizace proběhla

```json
{
  "status":           "SYNCED",
  "recordsProcessed": 48,
  "syncedAt":         "2026-03-14T13:05:22Z",
  "message":          "Successfully synced 48 records"
}
```

### Odpověď `503 Service Unavailable` – externí API nedostupné

```json
{
  "status":           "FAILED",
  "recordsProcessed": 0,
  "syncedAt":         "2026-03-14T13:05:22Z",
  "message":          "aWATTar API unavailable: ..."
}
```

| Pole | Typ | Popis |
|---|---|---|
| `status` | `string` | `SYNCED` – data uložena, `FAILED` – chyba externího API |
| `recordsProcessed` | `number` | Počet zpracovaných hodinových záznamů |
| `syncedAt` | `string` (ISO 8601) | Čas provedení synchronizace (UTC) |
| `message` | `string` | Lidsky čitelný popis výsledku nebo chyby |

### Příklad

```bash
curl -X POST http://localhost:8080/api/sync
```

---

## Chybové odpovědi

Všechny chybové odpovědi mají stejnou strukturu:

```json
{
  "error": "Popis chyby"
}
```

| HTTP Status | Situace |
|---|---|
| `404 Not Found` | Požadovaná data neexistují (není nasynchronizováno) |
| `503 Service Unavailable` | Externí API (aWATTar nebo Frankfurter) není dostupné při manuálním syncu |
| `500 Internal Server Error` | Neočekávaná chyba na straně serveru |

---

## Actuator endpointy

Spring Boot Actuator je dostupný na:

| Endpoint | Popis |
|---|---|
| `GET /actuator/health` | Stav aplikace a databázového připojení |
| `GET /actuator/info` | Informace o verzi aplikace |
