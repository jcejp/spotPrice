# Spot Energy Price Dashboard

Webová aplikace sledující hodinové spotové ceny elektřiny z trhu EPEX SPOT. Ceny jsou automaticky stahovány, převáděny do CZK a zobrazovány prostřednictvím přehledného dashboardu.

![Java](https://img.shields.io/badge/Java-21-orange) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)

---

## Co aplikace dělá

- Každou hodinu stahuje spotové ceny elektřiny z veřejného **aWATTar API** (EUR/MWh)
- Převádí ceny na **CZK** pomocí aktuálního kurzu z Frankfurter API (ECB)
- Ukládá data do **PostgreSQL** s deduplikací přes SHA-256 hash
- Zobrazuje dashboard s aktuální cenou, denním minimem/maximem a grafem historie za 24 hodin

## Dashboard

- Tři karty: aktuální cena, denní minimum, denní maximum (primárně CZK, EUR v závorce)
- Interaktivní line chart s přepínačem CZK / EUR
- Automatická aktualizace každých 5 minut

## Stack

| Vrstva | Technologie |
|---|---|
| Backend | Java 21, Spring Boot 3.3, Spring Data JPA |
| Databáze | PostgreSQL 16, Flyway |
| Frontend | Vanilla JS, Chart.js |
| Infrastruktura | Docker Compose |

---

## Rychlý start

```bash
# Spuštění databáze + aplikace
./development/docker/start.sh
```

Aplikace bude dostupná na **http://localhost:8080**.
První data se načtou do 60 sekund (dev profil synchronizuje každou minutu).

---

## Dokumentace

- [Zdroje dat](documentation/data-sources.md) – aWATTar API, Frankfurter API, synchronizační logika
- [REST API](documentation/api.md) – endpointy, formáty požadavků a odpovědí
- [Databáze](documentation/database.md) – schéma tabulek, migrace, diagnostické dotazy
- [Spuštění aplikace](documentation/getting-started.md) – požadavky, skripty, produkční nasazení
- [Konfigurace](documentation/configuration.md) – přehled všech konfiguračních vlastností
