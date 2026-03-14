'use strict';

const PRAGUE_LOCALE = 'cs-CZ';
const REFRESH_INTERVAL_MS = 5 * 60 * 1000;

const fmt = {
    czk: new Intl.NumberFormat(PRAGUE_LOCALE, { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
    eur: new Intl.NumberFormat(PRAGUE_LOCALE, { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
    time: new Intl.DateTimeFormat(PRAGUE_LOCALE, { hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Prague' }),
};

let chart = null;
let historyData = [];
let activeCurrency = 'czk';
let activePeriod = 'day';
let periodOffset = 0;

// ── Period helpers ─────────────────────────────────────────────────────────────

function pragueToday() {
    const dateStr = new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Prague' }).format(new Date());
    return new Date(dateStr + 'T00:00:00');
}

function formatDate(d) {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
}

function getPeriodDates() {
    const today = pragueToday();

    if (activePeriod === 'day') {
        const d = new Date(today);
        d.setDate(d.getDate() + periodOffset);
        const str = formatDate(d);
        return { startDate: str, endDate: str };
    }

    if (activePeriod === 'week') {
        const d = new Date(today);
        const dow = d.getDay(); // 0 = Sun
        const diffToMon = (dow === 0 ? -6 : 1 - dow);
        d.setDate(d.getDate() + diffToMon + periodOffset * 7);
        const monday = new Date(d);
        const sunday = new Date(d);
        sunday.setDate(sunday.getDate() + 6);
        return { startDate: formatDate(monday), endDate: formatDate(sunday) };
    }

    // month
    const d = new Date(today);
    d.setMonth(d.getMonth() + periodOffset);
    const first = new Date(d.getFullYear(), d.getMonth(), 1);
    const last = new Date(d.getFullYear(), d.getMonth() + 1, 0);
    return { startDate: formatDate(first), endDate: formatDate(last) };
}

function getPeriodLabel() {
    const { startDate, endDate } = getPeriodDates();

    if (activePeriod === 'day') {
        if (periodOffset === 0) return 'Dnes';
        if (periodOffset === -1) return 'Včera';
        const d = new Date(startDate + 'T12:00:00');
        return d.toLocaleDateString(PRAGUE_LOCALE, { day: 'numeric', month: 'long', year: 'numeric' });
    }

    if (activePeriod === 'week') {
        const start = new Date(startDate + 'T12:00:00');
        const end = new Date(endDate + 'T12:00:00');
        const s = start.toLocaleDateString(PRAGUE_LOCALE, { day: 'numeric', month: 'numeric' });
        const e = end.toLocaleDateString(PRAGUE_LOCALE, { day: 'numeric', month: 'numeric', year: 'numeric' });
        return `${s} – ${e}`;
    }

    // month
    const d = new Date(startDate + 'T12:00:00');
    return d.toLocaleDateString(PRAGUE_LOCALE, { month: 'long', year: 'numeric' });
}

function getChartTimeUnit() {
    return activePeriod === 'day' ? 'hour' : 'day';
}

function getDisplayFormats() {
    if (activePeriod === 'day') return { hour: 'HH:mm' };
    if (activePeriod === 'week') return { day: 'EEE d.M.' };
    return { day: 'd.M.' };
}

// ── Fetching ──────────────────────────────────────────────────────────────────

async function fetchAll() {
    showError(null);

    const { startDate, endDate } = getPeriodDates();
    const historyUrl = `/api/prices/history?startDate=${startDate}&endDate=${endDate}`;

    const [currentResult, statsResult, historyResult] = await Promise.allSettled([
        fetchJson('/api/prices/current'),
        fetchJson('/api/prices/today-stats'),
        fetchJson(historyUrl),
    ]);

    if (currentResult.status === 'fulfilled') {
        renderCurrent(currentResult.value);
    } else {
        showCardUnavailable('card-current', 'current-czk', 'current-eur');
        console.warn('Current price unavailable:', currentResult.reason);
    }

    if (statsResult.status === 'fulfilled') {
        renderStats(statsResult.value);
    } else {
        showCardUnavailable('card-min', 'min-czk', 'min-eur');
        showCardUnavailable('card-max', 'max-czk', 'max-eur');
        console.warn('Today stats unavailable:', statsResult.reason);
    }

    if (historyResult.status === 'fulfilled') {
        historyData = historyResult.value;
        try {
            renderChart(historyData, activeCurrency);
        } catch (err) {
            showError('Chart render error: ' + err.message);
            console.error('renderChart failed:', err);
        }
    } else {
        showError('Price history not available yet. Data will appear after the first sync.');
        console.warn('History unavailable:', historyResult.reason);
    }

    updatePeriodUI();

    const anyOk = [currentResult, statsResult, historyResult].some(r => r.status === 'fulfilled');
    if (anyOk) {
        document.getElementById('last-updated').textContent =
            'Last updated: ' + new Date().toLocaleTimeString(PRAGUE_LOCALE);
    }
}

async function fetchHistory() {
    showError(null);
    const { startDate, endDate } = getPeriodDates();
    const url = `/api/prices/history?startDate=${startDate}&endDate=${endDate}`;
    try {
        historyData = await fetchJson(url);
        renderChart(historyData, activeCurrency);
    } catch (err) {
        historyData = [];
        renderChart([], activeCurrency);
        showError('Price history not available: ' + err.message);
    }
    updatePeriodUI();
}

async function fetchJson(url) {
    const res = await fetch(url);
    if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.error || `HTTP ${res.status}`);
    }
    return res.json();
}

// ── Rendering ─────────────────────────────────────────────────────────────────

function renderCurrent(data) {
    document.getElementById('current-czk').textContent = fmt.czk.format(data.priceCzk) + ' CZK';
    document.getElementById('current-eur').textContent = '(' + fmt.eur.format(data.priceEur) + ' EUR)';
    const ts = new Date(data.timestamp);
    document.getElementById('current-time').textContent =
        'Hour starting at ' + fmt.time.format(ts);
}

function renderStats(data) {
    document.getElementById('min-czk').textContent = fmt.czk.format(data.minCzk) + ' CZK';
    document.getElementById('min-eur').textContent = '(' + fmt.eur.format(data.minEur) + ' EUR)';
    document.getElementById('max-czk').textContent = fmt.czk.format(data.maxCzk) + ' CZK';
    document.getElementById('max-eur').textContent = '(' + fmt.eur.format(data.maxEur) + ' EUR)';
}

function renderChart(data, currency) {
    const labels = data.map(d => new Date(d.timestamp));
    const values = data.map(d => currency === 'czk' ? d.priceCzk : d.priceEur);
    const label = currency === 'czk' ? 'CZK/MWh' : 'EUR/MWh';
    const color = currency === 'czk' ? '#4f7ef8' : '#43d89c';
    const unit = getChartTimeUnit();
    const displayFormats = getDisplayFormats();

    if (chart) {
        chart.data.labels = labels;
        chart.data.datasets[0].data = values;
        chart.data.datasets[0].label = label;
        chart.data.datasets[0].borderColor = color;
        chart.data.datasets[0].backgroundColor = color + '20';
        chart.options.scales.y.title.text = label;
        chart.options.scales.x.time.unit = unit;
        chart.options.scales.x.time.displayFormats = displayFormats;
        chart.update();
        return;
    }

    const ctx = document.getElementById('priceChart').getContext('2d');
    chart = new Chart(ctx, {
        type: 'line',
        data: {
            labels,
            datasets: [{
                label,
                data: values,
                borderColor: color,
                backgroundColor: color + '20',
                borderWidth: 2,
                pointRadius: 3,
                pointHoverRadius: 6,
                tension: 0.3,
                fill: true,
            }],
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                mode: 'index',
                intersect: false,
            },
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        title(items) {
                            return new Date(items[0].parsed.x)
                                .toLocaleString(PRAGUE_LOCALE, { timeZone: 'Europe/Prague' });
                        },
                        label(item) {
                            const row = historyData[item.dataIndex];
                            return [
                                ' CZK: ' + fmt.czk.format(row.priceCzk) + ' CZK/MWh',
                                ' EUR: ' + fmt.eur.format(row.priceEur) + ' EUR/MWh',
                            ];
                        },
                    },
                },
            },
            scales: {
                x: {
                    type: 'time',
                    time: { unit, displayFormats },
                    ticks: { color: '#8b93b8', maxRotation: 0 },
                    grid: { color: '#2e3248' },
                },
                y: {
                    title: { display: true, text: label, color: '#8b93b8' },
                    ticks: { color: '#8b93b8' },
                    grid: { color: '#2e3248' },
                },
            },
        },
    });
}

// ── Period navigation ──────────────────────────────────────────────────────────

function switchPeriod(period) {
    if (period === activePeriod) return;
    activePeriod = period;
    periodOffset = 0;
    fetchHistory();
}

function shiftPeriod(delta) {
    if (periodOffset + delta > 0) return;
    periodOffset += delta;
    fetchHistory();
}

function updatePeriodUI() {
    document.getElementById('period-label').textContent = getPeriodLabel();
    document.getElementById('btn-next').disabled = (periodOffset >= 0);
    ['day', 'week', 'month'].forEach(p => {
        document.getElementById('btn-' + p).classList.toggle('active', p === activePeriod);
    });
}

// ── Currency toggle ───────────────────────────────────────────────────────────

function switchCurrency(currency) {
    if (currency === activeCurrency) return;
    activeCurrency = currency;

    document.getElementById('btn-czk').classList.toggle('active', currency === 'czk');
    document.getElementById('btn-eur').classList.toggle('active', currency === 'eur');

    if (historyData.length) {
        renderChart(historyData, currency);
    }
}

// ── Utilities ─────────────────────────────────────────────────────────────────

function showCardUnavailable(cardId, primaryId, secondaryId) {
    document.getElementById(primaryId).textContent = 'N/A';
    document.getElementById(secondaryId).textContent = 'no data yet';
}

function showError(msg) {
    const el = document.getElementById('error-msg');
    if (msg) {
        el.textContent = msg;
        el.classList.remove('hidden');
    } else {
        el.classList.add('hidden');
    }
}

// ── Bootstrap ─────────────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
    updatePeriodUI();
    fetchAll();
    setInterval(fetchAll, REFRESH_INTERVAL_MS);
});
