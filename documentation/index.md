# Spot Energy Price Dashboard – Dokumentace

Aplikace sleduje hodinové spotové ceny elektřiny na rakouském trhu (aWATTar), převádí je na CZK a zobrazuje je prostřednictvím webového dashboardu.

## Obsah dokumentace

| Dokument | Popis |
|---|---|
| [Zdroje dat](data-sources.md) | Popis externích API (aWATTar, Frankfurter), formát dat, synchronizační logika |
| [REST API](api.md) | Referenční dokumentace vystavených endpointů s příklady požadavků a odpovědí |
| [Databáze](database.md) | Schéma tabulek, Flyway migrace, indexy |
| [Spuštění aplikace](getting-started.md) | Požadavky, vývojové prostředí, konfigurace, spouštěcí skripty |
| [Konfigurace](configuration.md) | Přehled všech konfiguračních vlastností a profilů |

## Rychlý přehled

```
aWATTar API  ──►  PriceIngestionService  ──►  PostgreSQL
                        │                          │
               Frankfurter API              PriceQueryService
                                                   │
                                         REST API (/api/prices/*)
                                                   │
                                          Frontend Dashboard
```

- **Backend:** Java 21, Spring Boot 3.3, Spring Data JPA, Flyway
- **Databáze:** PostgreSQL 16
- **Frontend:** Vanilla JS, Chart.js
- **Synchronizace:** každou hodinu (výchozí cron `0 0 * * * *`)
