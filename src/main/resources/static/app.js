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

// ── Fetching ──────────────────────────────────────────────────────────────────

async function fetchAll() {
    showError(null);
    try {
        const [current, stats, history] = await Promise.all([
            fetchJson('/api/prices/current'),
            fetchJson('/api/prices/today-stats'),
            fetchJson('/api/prices/history'),
        ]);
        renderCurrent(current);
        renderStats(stats);
        historyData = history;
        renderChart(history, activeCurrency);
        document.getElementById('last-updated').textContent =
            'Last updated: ' + new Date().toLocaleTimeString(PRAGUE_LOCALE);
    } catch (err) {
        showError('Failed to load data. The server may not have prices yet.');
        console.error(err);
    }
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

    if (chart) {
        chart.data.labels = labels;
        chart.data.datasets[0].data = values;
        chart.data.datasets[0].label = label;
        chart.data.datasets[0].borderColor = color;
        chart.data.datasets[0].backgroundColor = color + '20';
        chart.options.scales.y.title.text = label;
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
                            const row = data[item.dataIndex];
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
                    time: { unit: 'hour', displayFormats: { hour: 'HH:mm' } },
                    adapters: { date: { locale: PRAGUE_LOCALE } },
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
    fetchAll();
    setInterval(fetchAll, REFRESH_INTERVAL_MS);
});
