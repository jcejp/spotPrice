# Zdroje dat

## 1. aWATTar API – spotové ceny elektřiny

**URL:** `https://api.awattar.at/v1/marketdata`
**Metoda:** `GET`
**Autentizace:** není vyžadována
**Dostupnost:** veřejné API, bez API klíče

### Popis

aWATTar je rakouský obchodník s elektřinou, který zpřístupňuje hodinové spotové ceny z trhu EPEX SPOT. Data odpovídají cenám na den dopředu (day-ahead market) pro rakouskou/německou cenovou zónu.

Ceny jsou uváděny v **EUR/MWh**.

### Formát odpovědi

```json
{
  "object": "list",
  "data": [
    {
      "start_timestamp": 1700000000000,
      "end_timestamp":   1700003600000,
      "marketprice":     120.56,
      "unit":            "Eur/MWh"
    }
  ]
}
```

| Pole | Typ | Popis |
|---|---|---|
| `start_timestamp` | `long` | Začátek hodinového intervalu v epoch **milisekundách** (UTC) |
| `end_timestamp` | `long` | Konec intervalu (vždy `start + 3 600 000 ms`) |
| `marketprice` | `double` | Cena v EUR/MWh; může být záporná (přebytek výroby) |
| `unit` | `string` | Vždy `"Eur/MWh"` |

> **Poznámka:** Časová razítka jsou v epoch **milisekundách**, nikoliv sekundách. Záměna způsobí posun časových razítek o ~50 let.

### Rozsah dat

API typicky vrací data pro aktuální den a den dopředu (48 záznamů). Historická data jsou dostupná pouze v omezeném rozsahu.

---

## 2. Frankfurter API – kurz EUR/CZK

**URL:** `https://api.frankfurter.app/latest?from=EUR&to=CZK`
**Metoda:** `GET`
**Autentizace:** není vyžadována
**Dostupnost:** veřejné API, bez API klíče

### Popis

Frankfurter je open-source API pro devizové kurzy vycházející z dat Evropské centrální banky (ECB). Kurzy jsou aktualizovány každý pracovní den přibližně ve 16:00 CET.

### Formát odpovědi

```json
{
  "amount": 1.0,
  "base":   "EUR",
  "date":   "2026-03-14",
  "rates": {
    "CZK": 25.12
  }
}
```

| Pole | Typ | Popis |
|---|---|---|
| `amount` | `number` | Vždy `1.0` (kurz za 1 EUR) |
| `base` | `string` | Zdrojová měna (`"EUR"`) |
| `date` | `string` | Datum posledního kurzu ECB (`YYYY-MM-DD`) |
| `rates.CZK` | `BigDecimal` | Počet CZK za 1 EUR |

> **Poznámka:** O víkendech a svátcích ECB kurzy nepublikuje. API v těchto případech vrátí kurz z posledního pracovního dne.

---

## Synchronizační logika

Synchronizace probíhá jako naplánovaná úloha (`@Scheduled`) řízená cron výrazem.

### Průběh synchronizace

```
1. Stažení raw JSON z aWATTar API
2. Výpočet SHA-256 hashe raw odpovědi
3. Porovnání s posledním záznamem v tabulce sync_log
   ├── Hash je stejný → synchronizace se přeskočí (žádná změna dat)
   └── Hash se liší (nebo tabulka je prázdná – první spuštění)
       ├── Stažení aktuálního kurzu EUR/CZK z Frankfurter API
       ├── Uložení nového hashe do sync_log
       └── Pro každý hodinový záznam:
           ├── Nalezení existujícího záznamu podle timestamp
           ├── Záznam existuje → aktualizace ceny
           └── Záznam neexistuje → vytvoření nového záznamu
```

### Výpočet CZK ceny

```
priceCzk = priceEur × kurz_EUR_CZK
```

Obě hodnoty jsou ukládány s přesností na 6 desetinných míst (`NUMERIC(18, 6)`).

### Odolnost vůči chybám

| Scénář | Chování |
|---|---|
| aWATTar API nedostupné | Chyba je zalogována, synchronizace se přeskočí, scheduler zkusí znovu při dalším spuštění |
| Frankfurter API nedostupné | Celý sync cyklus se přeskočí – záznamy se **neuloží** s neplatným kurzem |
| Duplicitní záznam pro stejnou hodinu | Stávající záznam se aktualizuje (upsert), nevznikne duplicita |
| Prázdná tabulka sync_log | Hash je vždy považován za nový → provede se plná synchronizace |

### HTTP timeouty

| Parametr | Hodnota |
|---|---|
| Connection timeout | 10 sekund |
| Read timeout | 30 sekund |
