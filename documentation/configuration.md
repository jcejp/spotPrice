# Konfigurace

Konfigurace je rozdělena do dvou souborů v `src/main/resources/`:

| Soubor | Účel |
|---|---|
| `application.yml` | Výchozí konfigurace pro všechna prostředí |
| `application-dev.yml` | Přepsání pro vývojový profil (aktivuje se `-Dspring-boot.run.profiles=dev`) |

---

## Vlastnosti aplikace (`app.*`)

| Vlastnost | Výchozí hodnota | Popis |
|---|---|---|
| `app.awattar.url` | `https://api.awattar.at/v1/marketdata` | URL aWATTar API pro stažení spotových cen |
| `app.frankfurter.url` | `https://api.frankfurter.app/latest?from=EUR&to=CZK` | URL Frankfurter API pro kurz EUR/CZK |
| `app.scheduler.cron` | `0 0 * * * *` | Cron výraz pro synchronizaci (6 polí: sec min hour day month weekday) |

### Cron výrazy

| Výraz | Spuštění |
|---|---|
| `0 0 * * * *` | Každou hodinu (v :00:00) – výchozí produkční nastavení |
| `0 * * * * *` | Každou minutu – dev profil pro rychlé testování |
| `0 0 8-20 * * *` | Každou hodinu od 8:00 do 20:00 |
| `0 0 0 * * *` | Jednou denně o půlnoci |

---

## Databáze (`spring.datasource.*`)

| Vlastnost | Výchozí hodnota |
|---|---|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/spotprice` |
| `spring.datasource.username` | `spotprice` |
| `spring.datasource.password` | `spotprice` |

`spring.jpa.hibernate.ddl-auto` je nastaveno na `validate` – Hibernate schéma nevytváří ani nemění, pouze ověřuje shodu s entitami. Schéma spravuje Flyway.

`spring.jpa.open-in-view` je vypnuto (`false`) – lazy loading probíhá pouze uvnitř `@Transactional` metod.

---

## Flyway (`spring.flyway.*`)

| Vlastnost | Hodnota |
|---|---|
| `spring.flyway.enabled` | `true` |
| `spring.flyway.locations` | `classpath:db/migration` |

Flyway se spustí automaticky při každém startu aplikace a aplikuje všechny dosud neaplikované migrace.

> **Varování:** Nikdy nepovolujte `spring.flyway.clean-on-validation-error=true` v produkci. Tato vlastnost smaže celé schéma.

---

## Přepsání konfigurace

Konfigurace lze přepsat bez úpravy `application.yml`:

### Přes proměnné prostředí (doporučeno pro produkci)

Spring Boot automaticky mapuje proměnné prostředí na vlastnosti (`.` → `_`, velká písmena):

```bash
export APP_AWATTAR_URL=https://api.awattar.at/v1/marketdata
export APP_SCHEDULER_CRON="0 30 * * * *"
export SPRING_DATASOURCE_PASSWORD=tajne_heslo
```

### Přes parametry příkazové řádky

```bash
java -jar spotprice.jar \
  --app.scheduler.cron="0 30 * * * *" \
  --spring.datasource.password=tajne_heslo
```

### Přes externí konfigurační soubor

```bash
java -jar spotprice.jar \
  --spring.config.location=/etc/spotprice/application.yml
```

---

## Logování

Výchozí úroveň logování je `INFO`. Úrovně lze přepsat:

```yaml
logging:
  level:
    cz.jce.spotprice: DEBUG             # detailní logy aplikace
    org.springframework.web.client: DEBUG  # HTTP volání na externí API
    org.flywaydb: DEBUG                 # průběh migrací
```

Logy scheduleru jsou na úrovni `INFO`:
```
INFO  PriceIngestionService - Starting spot price sync
INFO  PriceIngestionService - Payload hash unchanged — skipping sync
INFO  PriceIngestionService - Sync complete — processed 48 price records
```

Chyby při synchronizaci jsou logovány na úrovni `ERROR` a synchronizace je přeskočena – aplikace pokračuje dál.
