# Spuštění aplikace

## Požadavky

| Nástroj | Minimální verze | Poznámka |
|---|---|---|
| Java | 21 | JDK (ne JRE) |
| Docker | 24 | Potřeba pro databázi |
| Docker Compose | 2.x | Součást Docker Desktop |
| Maven | 3.9 | Nebo použijte přiložený `./mvnw` |

---

## Vývojové prostředí

Vývojové prostředí se skládá z databáze PostgreSQL a pgAdmin spuštěných v Dockeru a aplikace spuštěné lokálně s `dev` profilem.

### Pomocí připravených skriptů

Ve složce `development/docker/` jsou k dispozici skripty:

```bash
# První spuštění nebo spuštění po zastavení
./development/docker/start.sh

# Zastavení aplikace i Docker kontejnerů
./development/docker/stop.sh

# Přebuilding a restart (provede mvn clean install)
./development/docker/redeploy.sh
```

> Skripty musí mít právo spuštění (`chmod +x development/docker/*.sh`).

### Ruční spuštění (krok za krokem)

**1. Spuštění databáze**

```bash
cd development/docker
docker compose up -d
```

Počkejte na zdravý stav PostgreSQL:

```bash
docker compose ps
# spotprice-postgres   running (healthy)
# spotprice-pgadmin    running
```

**2. Spuštění aplikace**

```bash
# Z kořenového adresáře projektu
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**3. Ověření**

| Adresa | Popis |
|---|---|
| `http://localhost:8080` | Webový dashboard |
| `http://localhost:8080/actuator/health` | Stav aplikace (`{"status":"UP"}`) |
| `http://localhost:8080/api/prices/current` | Aktuální cena (dostupná po první synchronizaci) |
| `http://localhost:5050` | pgAdmin (admin@local.dev / admin) |

---

## Dev profil

Aktivace profilu `dev` změní chování aplikace:

| Vlastnost | Výchozí | Dev |
|---|---|---|
| Cron synchronizace | každou hodinu (`0 0 * * * *`) | každou minutu (`0 * * * * *`) |
| Log level `cz.jce.spotprice` | INFO | DEBUG |
| Log level Spring HTTP klient | INFO | DEBUG |

S dev profilem proběhne první synchronizace do 60 sekund od startu. Data budou okamžitě viditelná v dashboardu.

---

## Build

```bash
# Kompilace
./mvnw compile

# Kompilace + testy
./mvnw test

# Produkční JAR (přeskočení testů)
./mvnw package -DskipTests

# Čistý build + testy + JAR
./mvnw clean install
```

Výstupní JAR se nachází v `target/spotprice-0.0.1-SNAPSHOT.jar`.

---

## Produkční spuštění

Pro produkční nasazení je potřeba přizpůsobit konfiguraci databáze a externích URL přes proměnné prostředí nebo externí `application.yml`.

```bash
java -jar target/spotprice-0.0.1-SNAPSHOT.jar \
  --spring.datasource.url=jdbc:postgresql://prod-db:5432/spotprice \
  --spring.datasource.username=spotprice \
  --spring.datasource.password=<heslo>
```

Alternativně přes proměnné prostředí:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://prod-db:5432/spotprice
export SPRING_DATASOURCE_USERNAME=spotprice
export SPRING_DATASOURCE_PASSWORD=<heslo>
java -jar target/spotprice-0.0.1-SNAPSHOT.jar
```

---

## Řešení problémů

**Aplikace se nespustí – chyba Flyway**

Ověřte, že databáze běží a je dostupná:
```bash
docker compose -f development/docker/docker-compose.yml ps
```

**Dashboard nezobrazuje data**

Počkejte na první synchronizaci. S dev profilem do 60 sekund, s výchozím profilem do 1 hodiny. Průběh synchronizace je viditelný v logu:
```
INFO  PriceIngestionService - Starting spot price sync
INFO  PriceIngestionService - Sync complete — processed 48 price records
```

**pgAdmin se nepřipojí k databázi**

Zkontrolujte, zda PostgreSQL kontejner prošel health checkem:
```bash
docker inspect --format='{{.State.Health.Status}}' spotprice-postgres
# healthy
```
