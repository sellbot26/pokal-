/* ===== Pokal Dashboard ===== */

let me = null;
let settings = {};
let rates = {};
let guildsCache = [];

const EXPLORERS = {
    'BTC': 'https://blockstream.info/tx/',
    'ETH': 'https://etherscan.io/tx/',
    'LTC': 'https://blockchair.com/litecoin/transaction/',
    'USDT-TRC20': 'https://tronscan.org/#/transaction/',
    'SOL': 'https://solscan.io/tx/'
};

const DATE = new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' });

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => document.querySelectorAll(sel);

function money(value) {
    const currency = settings.currency === 'USD' ? 'USD' : 'EUR';
    return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(value ?? 0);
}

// ===== Icon system (inline SVG, no emoji) =====

const ICON_PATHS = {
    'grid': '<rect x="3" y="3" width="7" height="7"></rect><rect x="14" y="3" width="7" height="7"></rect><rect x="14" y="14" width="7" height="7"></rect><rect x="3" y="14" width="7" height="7"></rect>',
    'dollar': '<line x1="12" y1="1" x2="12" y2="23"></line><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"></path>',
    'box': '<path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path><polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline><line x1="12" y1="22.08" x2="12" y2="12"></line>',
    'archive': '<polyline points="21 8 21 21 3 21 3 8"></polyline><rect x="1" y="3" width="22" height="5"></rect><line x1="10" y1="12" x2="14" y2="12"></line>',
    'file-text': '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line>',
    'tag': '<path d="M20.59 13.41L13.42 20.58a2 2 0 0 1-2.83 0L2.59 12.58a1.996 1.996 0 0 1-.59-1.42V4a2 2 0 0 1 2-2h7.17c.53 0 1.04.21 1.41.59l8 8a2 2 0 0 1 0 2.82z"></path><line x1="7" y1="7" x2="7.01" y2="7"></line>',
    'credit-card': '<rect x="1" y="4" width="22" height="16" rx="2" ry="2"></rect><line x1="1" y1="10" x2="23" y2="10"></line>',
    'users': '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path><circle cx="9" cy="7" r="4"></circle><path d="M23 21v-2a4 4 0 0 0-3-3.87"></path><path d="M16 3.13a4 4 0 0 1 0 7.75"></path>',
    'message-square': '<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>',
    'server': '<rect x="2" y="2" width="20" height="8" rx="2" ry="2"></rect><rect x="2" y="14" width="20" height="8" rx="2" ry="2"></rect><line x1="6" y1="6" x2="6.01" y2="6"></line><line x1="6" y1="18" x2="6.01" y2="18"></line>',
    'settings': '<circle cx="12" cy="12" r="3"></circle><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"></path>',
    'shopping-bag': '<path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"></path><line x1="3" y1="6" x2="21" y2="6"></line><path d="M16 10a4 4 0 0 1-8 0"></path>',
    'shopping-cart': '<circle cx="9" cy="21" r="1"></circle><circle cx="20" cy="21" r="1"></circle><path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"></path>',
    'sun': '<circle cx="12" cy="12" r="5"></circle><line x1="12" y1="1" x2="12" y2="3"></line><line x1="12" y1="21" x2="12" y2="23"></line><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"></line><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"></line><line x1="1" y1="12" x2="3" y2="12"></line><line x1="21" y1="12" x2="23" y2="12"></line><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"></line><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"></line>',
    'moon': '<path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path>',
    'log-out': '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path><polyline points="16 17 21 12 16 7"></polyline><line x1="21" y1="12" x2="9" y2="12"></line>',
    'edit-2': '<path d="M17 3a2.828 2.828 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3z"></path>',
    'trash-2': '<polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path><line x1="10" y1="11" x2="10" y2="17"></line><line x1="14" y1="11" x2="14" y2="17"></line>',
    'upload': '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line>',
    'plus': '<line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line>',
    'x': '<line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line>',
    'check-circle': '<path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline>',
    'alert-triangle': '<path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line>',
    'refresh-cw': '<polyline points="23 4 23 10 17 10"></polyline><polyline points="1 20 1 14 7 14"></polyline><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"></path>',
    'send': '<line x1="22" y1="2" x2="11" y2="13"></line><polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>',
    'save': '<path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"></path><polyline points="17 21 17 13 7 13 7 21"></polyline><polyline points="7 3 7 8 15 8"></polyline>',
    'download': '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line>',
    'key': '<path d="M21 2l-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4"></path>',
    'trending-up': '<polyline points="23 6 13.5 15.5 8.5 10.5 1 18"></polyline><polyline points="17 6 23 6 23 12"></polyline>',
    'chevron-down': '<polyline points="6 9 12 15 18 9"></polyline>',
    'chevron-right': '<polyline points="9 18 15 12 9 6"></polyline>',
    'arrow-right': '<line x1="5" y1="12" x2="19" y2="12"></line><polyline points="12 5 19 12 12 19"></polyline>',
    'mail': '<path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"></path><polyline points="22,6 12,13 2,6"></polyline>',
    'percent': '<line x1="19" y1="5" x2="5" y2="19"></line><circle cx="6.5" cy="6.5" r="2.5"></circle><circle cx="17.5" cy="17.5" r="2.5"></circle>',
    'sparkles': '<path d="M12 3l1.6 4.3L18 9l-4.4 1.7L12 15l-1.6-4.3L6 9l4.4-1.7z"></path><path d="M18 15l.8 2.2L21 18l-2.2.8L18 21l-.8-2.2L15 18l2.2-.8z"></path>',
    'crown': '<path d="M2 8l4.5 4L12 4l5.5 8L22 8l-2 12H4L2 8z"></path>',
    'target': '<circle cx="12" cy="12" r="10"></circle><circle cx="12" cy="12" r="6"></circle><circle cx="12" cy="12" r="2"></circle>',
    'external-link': '<path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"></path><polyline points="15 3 21 3 21 9"></polyline><line x1="10" y1="14" x2="21" y2="3"></line>',
    'award': '<circle cx="12" cy="8" r="7"></circle><polyline points="8.21 13.89 7 23 12 20 17 23 15.79 13.88"></polyline>',
    'lock': '<rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 10 0v4"></path>',
    'smile': '<circle cx="12" cy="12" r="10"></circle><path d="M8 14s1.5 2 4 2 4-2 4-2"></path><line x1="9" y1="9" x2="9.01" y2="9"></line><line x1="15" y1="9" x2="15.01" y2="9"></line>'
};

function icon(name, cls = 'icon') {
    return `<svg class="${cls}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${ICON_PATHS[name] || ''}</svg>`;
}

function populateIcons() {
    $$('[data-icon]').forEach(el => { el.innerHTML = icon(el.dataset.icon); });
}

// ===== API helpers (with CSRF token from cookie) =====

function csrfToken() {
    const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : '';
}

async function api(path, options = {}) {
    const opts = { headers: {}, ...options };
    if (opts.method && opts.method !== 'GET') {
        opts.headers['X-XSRF-TOKEN'] = csrfToken();
    }
    if (opts.body && !(opts.body instanceof FormData)) {
        opts.headers['Content-Type'] = 'application/json';
        opts.body = JSON.stringify(opts.body);
    }
    const res = await fetch(path, opts);
    if (res.status === 401) { location.href = '/'; throw new Error('Not logged in'); }
    if (!res.ok) {
        let msg = 'Error ' + res.status;
        try { msg = (await res.json()).error || msg; } catch (e) { /* no JSON body */ }
        throw new Error(msg);
    }
    const text = await res.text();
    return text ? JSON.parse(text) : null;
}

function toast(message, isError = false) {
    const el = $('#toast');
    el.textContent = message;
    el.className = 'toast' + (isError ? ' error' : '');
    el.hidden = false;
    clearTimeout(el._timer);
    el._timer = setTimeout(() => { el.hidden = true; }, 3500);
}

function esc(value) {
    const div = document.createElement('div');
    div.textContent = value == null ? '' : String(value);
    return div.innerHTML;
}

function badge(status) {
    const labels = { PENDING: 'Pending', PAID: 'Paid', DELIVERED: 'Delivered', CANCELLED: 'Cancelled', EXPIRED: 'Expired', FAILED: 'Failed' };
    return `<span class="badge badge-${esc(status)}">${labels[status] || esc(status)}</span>`;
}

// ===== Branding =====

function applyBranding() {
    const name = settings.shopName || 'Pokal';
    document.title = 'Dashboard — ' + name;
    $('#brandName').textContent = name;
    $('#previewBotName') && ($('#previewBotName').textContent = name);
    $('#dmPreviewBot') && ($('#dmPreviewBot').textContent = name);
    $('#dmPreviewFooter') && ($('#dmPreviewFooter').textContent = name);
    if (settings.brandColor && /^#[0-9a-fA-F]{6}$/.test(settings.brandColor)) {
        document.documentElement.style.setProperty('--accent', settings.brandColor);
    }
    if (settings.logoUrl) {
        $$('.brand-icon-img, .discord-avatar img').forEach(img => img.src = settings.logoUrl);
    }
    const banner = $('#maintenanceBanner');
    if (banner) banner.hidden = settings.maintenance !== 'true';
    $('#convertCurrency') && ($('#convertCurrency').textContent = settings.currency === 'USD' ? 'USD' : 'EUR');
}

// ===== Init =====

async function init() {
    try {
        me = await api('/api/me');
    } catch (e) {
        return; // redirect already in progress
    }
    try { settings = await api(me.admin ? '/api/admin/settings' : '/api/settings'); } catch (e) { settings = {}; }
    applyBranding();
    populateIcons();

    $('#userName').textContent = me.username || me.id;
    $('#userRole').textContent = me.admin ? 'Admin' : 'Member';
    if (me.avatar) { const img = $('#userAvatar'); img.src = me.avatar; img.hidden = false; }

    // Top-right user bar
    $('#topbarName').textContent = me.username || me.id;
    if (me.avatar) { const ta = $('#topbarAvatar'); ta.src = me.avatar; ta.hidden = false; }

    if (!me.admin) $$('.admin-only').forEach(el => el.remove());

    $$('.nav-btn').forEach(btn => btn.addEventListener('click', () => showSection(btn.dataset.section)));
    // Server-Switcher über den Brand-Block auf-/zuklappen
    $('#brandBox').addEventListener('click', () => {
        const sw = $('#guildSwitcher');
        if (sw) sw.hidden = !sw.hidden || !guildsCache.length;
    });
    showSection('overview');

    initModals();
    initProductForm();
    initDiscountForm();
    initRestockForm();
    initEmbedEditor();
    initGuildSwitcher();
    initLicenses();
    initChart();
    initPayments();
    initDelivery();
    initBilling();
    $('#orderFilter').addEventListener('change', loadOrders);
    $('#logoutBtn').addEventListener('click', logout);
    if (me.admin) initGoal();
    relocateUserSettings(); // eigene Payment-Methods + Delivery in Settings-Tabs holen
    initSettings(); // Tab-Logik für alle
    loadPlanChip();
}

let myPlanTier = 'FREE';
let myIsOwner = false;
const TIER_RANK = { FREE: 0, PRO: 1, BUSINESS: 2 };
function planAtLeast(tier) { return myIsOwner || (TIER_RANK[myPlanTier] ?? 0) >= (TIER_RANK[tier] ?? 0); }

/** Sperrt Pro-Features in der UI (Server erzwingt es zusätzlich). */
function applyPlanGates() {
    const pro = planAtLeast('PRO');
    const color = $('#eColor');
    if (color) {
        color.disabled = !pro;
        const lbl = color.closest('label');
        if (lbl && !lbl.querySelector('.plan-lock') && !pro) {
            lbl.insertAdjacentHTML('beforeend', ' <span class="plan-lock">PRO</span>');
        }
    }
    const roleOpt = document.querySelector('#pDeliveryType option[value="ROLE"]');
    if (roleOpt) { roleOpt.disabled = !pro; roleOpt.textContent = pro ? 'Discord role' : 'Discord role (Pro)'; }

    // Settings-Tabs: Payment Methods für alle, Delivery + Coupons erst ab Pro
    ['delivery', 'coupons'].forEach(tab => {
        const btn = document.querySelector(`#settingsTabs [data-tab="${tab}"]`);
        if (btn && !pro && !btn.querySelector('.plan-lock')) {
            btn.insertAdjacentHTML('beforeend', ' <span class="plan-lock">PRO</span>');
        }
    });
}

async function loadPlanChip() {
    try {
        const plan = await api('/api/my/plan');
        const t = plan.tier;
        myPlanTier = t.id;
        myIsOwner = !!plan.isSiteAdmin;
        applyPlanGates();
        const badge = $('#planChipBadge');
        badge.textContent = t.name.toUpperCase();
        badge.className = 'plan-chip-badge' + (t.id === 'PRO' ? ' pro' : t.id === 'BUSINESS' ? ' business' : '');
        const limit = t.productLimit > 100000 ? '∞' : t.productLimit;
        $('#planChipUsage').textContent = plan.isSiteAdmin
            ? 'Site owner · no limits'
            : `${plan.productsUsed} / ${limit} products`;

        // Top-right plan badge
        const tp = $('#topbarPlan');
        if (tp) {
            if (plan.isSiteAdmin) { tp.textContent = 'OWNER'; tp.className = 'topbar-plan owner'; }
            else {
                tp.textContent = t.name.toUpperCase();
                tp.className = 'topbar-plan' + (t.id === 'PRO' ? ' pro' : t.id === 'BUSINESS' ? ' business' : '');
            }
        }
    } catch (e) { /* optional */ }
}

async function logout() {
    await fetch('/logout', { method: 'POST', headers: { 'X-XSRF-TOKEN': csrfToken() } });
    location.href = '/';
}

function showSection(name) {
    $$('.section').forEach(s => s.hidden = true);
    const section = $('#section-' + name);
    if (!section) return;
    section.hidden = false;
    $$('.nav-btn').forEach(b => b.classList.toggle('active', b.dataset.section === name));
    const loaders = {
        overview: loadOverview, products: loadProducts, orders: loadOrders,
        customers: loadCustomers, myorders: loadMyOrders,
        payments: loadPayments, stock: loadStock, embeds: loadEmbedSection,
        settings: loadSettingsForm, server: loadServerSection, billing: loadBillingSection,
        delivery: loadDelivery
    };
    loaders[name]?.();
}

window.showSection = showSection;

// ===== Theme =====

function initTheme() {
    const saved = localStorage.getItem('theme') || 'dark';
    document.documentElement.dataset.theme = saved;
    updateThemeIcon();
    $('#themeToggle').addEventListener('click', () => {
        const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
        document.documentElement.dataset.theme = next;
        localStorage.setItem('theme', next);
        updateThemeIcon();
        if (revenueChart) { renderChart(lastSeries); }
    });
}

function updateThemeIcon() {
    $('#themeToggle').innerHTML = icon(document.documentElement.dataset.theme === 'dark' ? 'sun' : 'moon');
}

// ===== Overview =====

function statCard(label, value, iconName, chipClass, accent) {
    return `<div class="stat-card">
        <div class="stat-card-top">
            <span class="stat-card-label">${label}</span>
            <span class="stat-card-chip ${chipClass}">${icon(iconName)}</span>
        </div>
        <div class="stat-card-value${accent ? ' accent' : ''}">${value}</div>
    </div>`;
}

async function loadOverview() {
    try {
        const stats = await api(apiScope() + '/stats');
        const conv = stats.ordersTotal > 0
            ? Math.round(((stats.ordersTotal - stats.ordersPending) / stats.ordersTotal) * 100) + '%'
            : '0%';
        $('#statCards').innerHTML =
            statCard('Total Revenue', money(stats.revenueTotal), 'trending-up', 'green', true)
            + statCard('Total Orders', stats.ordersTotal, 'shopping-cart', 'blue', false)
            + statCard('Conversion Rate', conv, 'percent', 'purple', false)
            + statCard('Active Products', stats.activeCustomers != null ? (stats.topProducts.length || 0) : 0, 'box', 'gold', false);
        // Active products aus /api/my/products zählen (verlässlicher)
        try {
            const prods = await api(me.admin ? '/api/products' : '/api/my/products');
            $('#statCards').children[3].querySelector('.stat-card-value').textContent = prods.filter(p => p.active).length;
        } catch (e) { /* fallback bleibt */ }

        renderOrdersByStatus(stats);
        updateGoal(stats.revenueMonth);
        loadChart();
        loadRecentOrders();
    } catch (e) { toast(e.message, true); }
}

function renderOrdersByStatus(stats) {
    const el = $('#ordersByStatus');
    if (!el) return;
    if (!stats.ordersTotal) { el.innerHTML = '<p class="muted" style="margin-top:14px">No orders yet.</p>'; return; }
    const rows = [
        ['Delivered', stats.ordersTotal - stats.ordersPending, 'var(--green)'],
        ['Pending', stats.ordersPending, 'var(--yellow)']
    ];
    const max = Math.max(1, ...rows.map(r => r[1]));
    el.innerHTML = '<div style="margin-top:14px">' + rows.map(([label, count, color]) => `
        <div class="obs-row">
            <span class="obs-label">${label}</span>
            <span class="obs-bar"><span class="obs-bar-fill" style="width:${(count / max) * 100}%;background:${color}"></span></span>
            <span class="obs-count">${count}</span>
        </div>`).join('') + '</div>';
}

async function loadRecentOrders() {
    try {
        const orders = await api(ordersPath());
        $('#recentTable tbody').innerHTML = orders.slice(0, 8).map(o => `
            <tr>
                <td>${DATE.format(new Date(o.createdAt))}</td>
                <td>${esc(o.username)}</td>
                <td>${esc(o.productName)} x${o.quantity}</td>
                <td>${money(o.totalPrice)}</td>
                <td>${badge(o.status)}</td>
            </tr>`).join('') || '<tr><td colspan="5" class="muted">No orders yet</td></tr>';
    } catch (e) { /* panel is optional */ }
}

// ===== Revenue chart =====

let revenueChart = null;
let chartRange = 'month';
let lastSeries = [];

function initChart() {
    $$('#chartRange .range-tab').forEach(tab => tab.addEventListener('click', () => {
        $$('#chartRange .range-tab').forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        chartRange = tab.dataset.range;
        loadChart();
    }));
}

async function loadChart() {
    try {
        lastSeries = await api(apiScope() + '/stats/series?range=' + chartRange);
        renderChart(lastSeries);
    } catch (e) { /* chart is optional */ }
}

function renderChart(series) {
    if (typeof Chart === 'undefined') return;
    const css = getComputedStyle(document.documentElement);
    const accent = css.getPropertyValue('--accent').trim() || '#6366f1';
    const textDim = css.getPropertyValue('--text-dim').trim() || '#8b8d9c';
    const canvas = $('#revenueChart');
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    const gradient = ctx.createLinearGradient(0, 0, 0, 300);
    gradient.addColorStop(0, accent + '40');
    gradient.addColorStop(1, accent + '00');

    // Regenbogen-Verlauf für die Umsatzlinie
    const width = canvas.width || 800;
    const rainbow = ctx.createLinearGradient(0, 0, width, 0);
    rainbow.addColorStop(0.00, '#ff5f6d');
    rainbow.addColorStop(0.20, '#ffb347');
    rainbow.addColorStop(0.40, '#47e08a');
    rainbow.addColorStop(0.60, '#38bdf8');
    rainbow.addColorStop(0.80, '#8b5cf6');
    rainbow.addColorStop(1.00, '#ff5fa2');

    if (revenueChart) revenueChart.destroy();
    revenueChart = new Chart(ctx, {
        type: 'line',
        data: {
            labels: series.map(p => p.label),
            datasets: [{
                data: series.map(p => Number(p.value)),
                borderColor: rainbow,
                backgroundColor: gradient,
                fill: true,
                tension: 0.4,
                borderWidth: 3,
                pointRadius: 0,
                pointHoverRadius: 5,
                pointBackgroundColor: accent
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                tooltip: {
                    displayColors: false,
                    callbacks: { label: (item) => money(item.parsed.y) }
                }
            },
            scales: {
                x: { grid: { display: false }, ticks: { color: textDim, maxTicksLimit: 12, font: { size: 11 } } },
                y: {
                    grid: { color: 'rgba(128,128,160,0.12)' },
                    ticks: { color: textDim, font: { size: 11 }, callback: (v) => money(v) },
                    beginAtZero: true
                }
            },
            interaction: { mode: 'index', intersect: false }
        }
    });
}

// ===== Monthly goal =====

function initGoal() {
    $('#editGoalBtn').addEventListener('click', async () => {
        const current = Number(settings.monthlyGoal || 1000);
        const input = prompt('Monthly revenue goal:', current);
        if (input == null) return;
        const goal = Math.max(0, Number(input) || 0);
        try {
            settings = await api('/api/admin/settings', { method: 'PUT', body: { monthlyGoal: String(goal) } });
            toast('Monthly goal saved');
            loadOverview();
        } catch (e) { toast(e.message, true); }
    });
}

function updateGoal(revenueMonth) {
    if (!$('#goalFill')) return; // Monatsziel-Panel ist admin-only
    const goal = Number(settings.monthlyGoal || 1000);
    const revenue = Number(revenueMonth || 0);
    const percent = goal > 0 ? Math.min(100, (revenue / goal) * 100) : 0;
    $('#goalFill').style.width = percent.toFixed(1) + '%';
    $('#goalText').textContent = `${money(revenue)} of ${money(goal)} (${percent.toFixed(0)}%)`;
}

// ===== Crypto & Wallets =====

function initCrypto() {
    $('#convertAmount').addEventListener('input', renderConverter);
    // Auszahlungs-Wallets werden ausschließlich unter „Payments" (pro Verkäufer) verwaltet.
}

async function loadCrypto() {
    try {
        rates = await api('/api/rates');
        const entries = Object.entries(rates);
        $('#cryptoCards').innerHTML = entries.map(([coin, data]) => {
            const change = data.eur_24h_change;
            const changeHtml = change != null
                ? `<div class="change" style="color:var(--${change >= 0 ? 'green' : 'red'})">${change >= 0 ? '▲' : '▼'} ${Math.abs(change).toFixed(2)}% (24h)</div>`
                : '';
            return `<div class="crypto-card">
                <div class="coin">${esc(coin)}</div>
                <div class="price">${money(settings.currency === 'USD' ? data.usd : data.eur)}</div>
                ${changeHtml}
            </div>`;
        }).join('') || '<p class="muted">Rates unavailable</p>';
        renderConverter();
    } catch (e) { toast(e.message, true); }
}

function renderConverter() {
    const amount = Number($('#convertAmount').value || 0);
    const useUsd = settings.currency === 'USD';
    const rows = [];
    for (const [coin, data] of Object.entries(rates)) {
        const price = useUsd ? data.usd : data.eur;
        if (!price) continue;
        const converted = amount / price;
        const decimals = converted >= 1 ? 4 : 8;
        rows.push(`<div class="converter-row"><span>${esc(coin)}</span><b>${converted.toFixed(decimals)}</b></div>`);
    }
    // Stablecoin USDC ≈ USDT rate (pegged to the dollar)
    const usdt = rates['USDT'];
    if (usdt) {
        const price = useUsd ? usdt.usd : usdt.eur;
        if (price) rows.push(`<div class="converter-row"><span>USDC</span><b>${(amount / price).toFixed(2)}</b></div>`);
    }
    $('#convertResult').innerHTML = rows.join('')
        || '<p class="muted">Rates unavailable — try again later.</p>';
}

// ===== Products =====

let productsCache = [];

function guildName(guildId) {
    if (!guildId) return '—';
    const g = guildsCache.find(g => g.id === guildId);
    return g ? g.name : 'Unknown server';
}

async function loadProducts() {
    try {
        const guildId = activeGuildId();
        // Site-Admin nutzt die öffentliche Liste (sieht alles), Tenants ihre eigene Verwaltungsansicht
        const path = me.admin ? '/api/products' : '/api/my/products';
        productsCache = await api(path + (guildId ? '?guildId=' + guildId : ''));
        $('#productsTable tbody').innerHTML = productsCache.map(p => `
            <tr>
                <td>${p.imageUrl ? `<img class="thumb" src="${esc(p.imageUrl)}" alt="">` : icon('box')}</td>
                <td><b>${esc(p.name)}</b></td>
                <td>${esc(guildName(p.guildId))}</td>
                <td>${esc(p.category || '—')}</td>
                <td>${money(p.price)}</td>
                <td>${p.stock === -1 ? '∞' : p.stock}</td>
                <td>${esc(p.deliveryType)}</td>
                <td><span class="badge badge-${p.active ? 'active' : 'inactive'}">${p.active ? 'Active' : 'Inactive'}</span></td>
                <td class="row">
                    <button class="btn btn-sm btn-icon" title="Post as buy embed" onclick="productToEmbed(${p.id})">${icon('send')}</button>
                    <button class="btn btn-sm btn-icon" onclick="editProduct(${p.id})">${icon('edit-2')}</button>
                    <button class="btn btn-sm btn-icon btn-danger" onclick="deactivateProduct(${p.id})">${icon('trash-2')}</button>
                </td>
            </tr>`).join('') || '<tr><td colspan="9" class="muted">No products</td></tr>';
    } catch (e) { toast(e.message, true); }
}

const DELIVERY_DATA_LABELS = {
    TEXT: 'Delivery text (sent via DM)',
    KEY: 'Custom message (optional — the key is always included)',
    ROLE: 'Role ID to assign',
    FILE: 'Download link (sent via DM)'
};

function applyDeliveryTypeUi() {
    const type = $('#pDeliveryType').value;
    $('#pDeliveryDataLabel').textContent = DELIVERY_DATA_LABELS[type] || 'Delivery data';
    $('#keysField').hidden = type !== 'KEY';
}

function initProductForm() {
    $('#newProductBtn')?.addEventListener('click', () => {
        // Ohne verwaltbaren Server kann kein Produkt angelegt werden — klare Anleitung statt totem Dropdown
        if (!guildsCache.length) {
            toast('Add the bot to a Discord server where you are an admin first — see the "Bot & Servers" tab.', true);
            showSection('server');
            return;
        }
        openProductModal(null);
    });
    $('#pDeliveryType').addEventListener('change', applyDeliveryTypeUi);

    $('#uploadImageBtn').addEventListener('click', () => $('#pImageFile').click());
    $('#pImageFile').addEventListener('change', async () => {
        const file = $('#pImageFile').files[0];
        if (!file) return;
        const form = new FormData();
        form.append('file', file);
        try {
            const result = await api('/api/upload', { method: 'POST', body: form });
            $('#pImageUrl').value = result.url;
            toast('Image uploaded');
        } catch (e) { toast(e.message, true); }
    });

    $('#productForm').addEventListener('submit', async (event) => {
        event.preventDefault();
        const id = $('#pId').value;
        if (!$('#pGuildId').value) { toast('Please select a server.', true); return; }
        const body = {
            guildId: $('#pGuildId').value,
            name: $('#pName').value,
            description: $('#pDescription').value,
            price: parseFloat($('#pPrice').value),
            category: $('#pCategory').value,
            stock: parseInt($('#pStock').value || '-1', 10),
            deliveryType: $('#pDeliveryType').value,
            deliveryData: $('#pDeliveryData').value,
            instructions: $('#pInstructions').value,
            imageUrl: $('#pImageUrl').value,
            active: $('#pActive').checked
        };
        try {
            const saved = id
                ? await api(apiScope() + '/products/' + id, { method: 'PUT', body })
                : await api(apiScope() + '/products', { method: 'POST', body });
            const keys = $('#pKeys').value.trim();
            if (keys && body.deliveryType === 'KEY') {
                await api(`${apiScope()}/products/${saved.id}/keys`, { method: 'POST', body: { keys } });
            }
            closeModals();
            toast('Product saved');
            loadProducts();
        } catch (e) { toast(e.message, true); }
    });
}

function openProductModal(product) {
    $('#productModalTitle').textContent = product ? 'Edit Product' : 'New Product';
    $('#pGuildId').innerHTML = '<option value="">Select a server…</option>'
        + guildsCache.map(g => `<option value="${esc(g.id)}">${esc(g.name)}</option>`).join('');
    $('#pGuildId').value = product?.guildId || activeGuildId() || '';
    $('#pId').value = product?.id || '';
    $('#pName').value = product?.name || '';
    $('#pDescription').value = product?.description || '';
    $('#pPrice').value = product?.price ?? '';
    $('#pCategory').value = product?.category || '';
    $('#pStock').value = product?.stock ?? -1;
    $('#pDeliveryType').value = product?.deliveryType || 'TEXT';
    $('#pDeliveryData').value = product?.deliveryData || '';
    $('#pInstructions').value = product?.instructions || '';
    $('#pImageUrl').value = product?.imageUrl || '';
    $('#pActive').checked = product ? product.active : true;
    $('#pKeys').value = '';
    applyDeliveryTypeUi();
    $('#productModal').hidden = false;
}

window.editProduct = (id) => {
    const product = productsCache.find(p => p.id === id);
    if (product) openProductModal(product);
};

window.deactivateProduct = async (id) => {
    if (!confirm('Remove this product from the shop?')) return;
    try {
        await api(apiScope() + '/products/' + id, { method: 'DELETE' });
        toast('Product deactivated');
        loadProducts();
    } catch (e) { toast(e.message, true); }
};

// ===== Stock =====

let stockCache = [];

async function loadStock() {
    try {
        const guildId = activeGuildId();
        stockCache = await api(apiScope() + '/stock' + (guildId ? '?guildId=' + guildId : ''));
        const active = stockCache.filter(s => s.active);
        const out = active.filter(s => s.stock === 0).length;
        const low = active.filter(s => s.stock > 0 && s.stock <= 5).length;
        const infinite = active.filter(s => s.stock === -1).length;

        $('#stockCards').innerHTML = [
            ['Active Products', active.length, ''],
            ['Out of Stock', out, out > 0 ? 'var(--red)' : ''],
            ['Low Stock (≤5)', low, low > 0 ? 'var(--yellow)' : ''],
            ['Unlimited', infinite, '']
        ].map(([label, value, color]) =>
            `<div class="card"><div class="card-label">${label}</div><div class="card-value" ${color ? `style="color:${color}"` : ''}>${value}</div></div>`
        ).join('');

        $('#stockTable tbody').innerHTML = stockCache.map(s => {
            let state;
            if (!s.active) state = '<span class="badge badge-inactive">Inactive</span>';
            else if (s.stock === -1) state = '<span class="badge badge-ok">Unlimited</span>';
            else if (s.stock === 0) state = '<span class="badge badge-out">Out of stock</span>';
            else if (s.stock <= 5) state = '<span class="badge badge-low">Low</span>';
            else state = '<span class="badge badge-ok">OK</span>';
            return `<tr>
                <td><b>${esc(s.name)}</b></td>
                <td>${esc(guildName(s.guildId))}</td>
                <td>${esc(s.category || '—')}</td>
                <td>${esc(s.deliveryType)}</td>
                <td>${s.stock === -1 ? '∞' : s.stock}</td>
                <td>${s.unusedKeys != null ? s.unusedKeys : '—'}</td>
                <td>${state}</td>
                <td class="row">
                    <button class="btn btn-sm btn-icon" title="Restock keys" data-restock="${s.id}">${icon('key')}</button>
                    <button class="btn btn-sm btn-icon" data-adjust="${s.id}" data-delta="-1">−</button>
                    <button class="btn btn-sm btn-icon" data-adjust="${s.id}" data-delta="1">+</button>
                    <button class="btn btn-sm btn-icon" data-setstock="${s.id}">${icon('edit-2')}</button>
                </td>
            </tr>`;
        }).join('') || '<tr><td colspan="8" class="muted">No products</td></tr>';
    } catch (e) { toast(e.message, true); }
}

window.loadStock = loadStock;

window.adjustStock = async (id, current, delta) => {
    const next = current === -1 ? (delta > 0 ? 1 : 0) : Math.max(0, current + delta);
    try {
        await api(`${apiScope()}/products/${id}/stock`, { method: 'PUT', body: { stock: next } });
        loadStock();
    } catch (e) { toast(e.message, true); }
};

window.setStockPrompt = async (id, current) => {
    const input = prompt('New stock level (-1 = unlimited):', current);
    if (input == null) return;
    try {
        await api(`${apiScope()}/products/${id}/stock`, { method: 'PUT', body: { stock: parseInt(input, 10) } });
        toast('Stock updated');
        loadStock();
    } catch (e) { toast(e.message, true); }
};

function initRestockForm() {
    $('#restockForm').addEventListener('submit', async (event) => {
        event.preventDefault();
        const id = $('#rProductId').value;
        const keys = $('#rKeys').value.trim();
        if (!keys) return;
        try {
            const result = await api(`${apiScope()}/products/${id}/keys`, { method: 'POST', body: { keys } });
            closeModals();
            toast(`${result.added} key(s) added — ${result.available} available`);
            loadStock();
        } catch (e) { toast(e.message, true); }
    });

    // Delegated click handling for the stock table — avoids passing arbitrary
    // product names (which may contain quotes) through inline onclick strings.
    $('#stockTable').addEventListener('click', (event) => {
        const restockBtn = event.target.closest('[data-restock]');
        if (restockBtn) {
            const id = Number(restockBtn.dataset.restock);
            const product = stockCache.find(s => s.id === id);
            if (product) openRestockModal(product.id, product.name);
            return;
        }
        const adjustBtn = event.target.closest('[data-adjust]');
        if (adjustBtn) {
            const id = Number(adjustBtn.dataset.adjust);
            const delta = Number(adjustBtn.dataset.delta);
            const product = stockCache.find(s => s.id === id);
            if (product) adjustStock(id, product.stock, delta);
            return;
        }
        const setBtn = event.target.closest('[data-setstock]');
        if (setBtn) {
            const id = Number(setBtn.dataset.setstock);
            const product = stockCache.find(s => s.id === id);
            if (product) setStockPrompt(id, product.stock);
        }
    });
}

function openRestockModal(id, name) {
    $('#rProductId').value = id;
    $('#restockProductLabel').textContent = name;
    $('#rKeys').value = '';
    $('#restockModal').hidden = false;
}

// ===== Orders (admin) =====

/** Site-Admin sieht alle Bestellungen, Tenants nur die für ihre eigenen Produkte. */
function ordersPath() {
    return me.admin ? '/api/admin/orders' : '/api/my/shop-orders';
}

async function loadOrders() {
    try {
        const status = $('#orderFilter').value;
        const orders = await api(ordersPath() + (status ? '?status=' + status : ''));
        $('#ordersTable tbody').innerHTML = orders.map(o => {
            const tx = o.txHash && EXPLORERS[o.payCurrency]
                ? `<a href="${EXPLORERS[o.payCurrency]}${esc(o.txHash)}" target="_blank" rel="noopener">${icon('external-link', 'icon icon-sm')}</a>`
                : (o.txHash ? `<span title="${esc(o.txHash)}">${icon('file-text', 'icon icon-sm')}</span>` : '—');
            const payment = o.payCurrency
                ? `${o.payCurrency === 'KARTE' ? 'Card' : esc(o.payCurrency)}<br><small class="muted">${o.payCurrency === 'KARTE' ? 'PayGate' : (o.payAmount ?? '')}</small>` : '—';
            const actions = [];
            if (o.status === 'PENDING') {
                actions.push(`<button class="btn btn-sm btn-icon" title="Simulate payment (mock only)" onclick="simulatePayment(${o.id})">${icon('check-circle')}</button>`);
                actions.push(`<button class="btn btn-sm btn-icon btn-danger" title="Cancel" onclick="setOrderStatus(${o.id}, 'CANCELLED')">${icon('x')}</button>`);
            }
            if (o.status === 'PAID') {
                actions.push(`<button class="btn btn-sm btn-icon" title="Mark as delivered" onclick="setOrderStatus(${o.id}, 'DELIVERED')">${icon('box')}</button>`);
            }
            return `<tr>
                <td>#${o.id}</td>
                <td>${DATE.format(new Date(o.createdAt))}</td>
                <td>${esc(o.username)}<br><small class="muted">${esc(o.userId)}</small></td>
                <td>${esc(o.productName)} x${o.quantity}</td>
                <td>${money(o.totalPrice)}${o.discountPercent ? `<br><small class="muted">-${o.discountPercent}% (${esc(o.discountCode)})</small>` : ''}</td>
                <td>${payment}</td>
                <td>${tx}</td>
                <td>${badge(o.status)}</td>
                <td class="row">${actions.join('')}</td>
            </tr>`;
        }).join('') || '<tr><td colspan="9" class="muted">No orders</td></tr>';
    } catch (e) { toast(e.message, true); }
}

window.setOrderStatus = async (id, status) => {
    try {
        await api(`${ordersPath()}/${id}/status`, { method: 'PUT', body: { status } });
        toast('Status updated');
        loadOrders();
    } catch (e) { toast(e.message, true); }
};

window.simulatePayment = async (id) => {
    if (!confirm('Simulate payment for order #' + id + '? (mock mode only)')) return;
    try {
        await api(`${ordersPath()}/${id}/simulate-payment`, { method: 'POST' });
        toast('Payment confirmed, delivery triggered');
        loadOrders();
    } catch (e) { toast(e.message, true); }
};

// ===== Payments (manual) =====

let paymentsCache = [];

function initPayments() {
    // Eigene Zahlungsmethoden — für JEDEN Nutzer inkl. Admin (Zahlungen für die eigenen Produkte)
    $('#savePaymentConfigBtn')?.addEventListener('click', async () => {
        try {
            const result = await api('/api/my/payment-config', {
                method: 'PUT',
                body: {
                    paygateWallet: $('#myPgWallet').value,
                    paygateEmail: $('#myPgEmail').value,
                    cryptoWallets: $('#myCryptoWallets').value,
                    logWebhookUrl: $('#myLogWebhook').value,
                    nowpaymentsApiKey: $('#myNpKey').value || null,
                    nowpaymentsIpnSecret: $('#myNpSecret').value || null
                }
            });
            $('#myNpKey').value = '';
            $('#myNpSecret').value = '';
            applyMyPaymentConfig(result);
            toast('Payment methods saved');
        } catch (e) { toast(e.message, true); }
    });

    $('#newPaymentBtn').addEventListener('click', () => openPaymentModal(null));
    $('#paymentSearch').addEventListener('input', renderPayments);
    $('#paymentStatusFilter').addEventListener('change', renderPayments);
    $('#paymentMethodFilter').addEventListener('change', renderPayments);

    $('#manualPaymentForm').addEventListener('submit', async (event) => {
        event.preventDefault();
        const id = $('#mpId').value;
        const dateValue = $('#mpDate').value;
        const body = {
            customer: $('#mpCustomer').value,
            amount: parseFloat($('#mpAmount').value),
            method: $('#mpMethod').value,
            status: $('#mpStatus').value,
            date: dateValue ? new Date(dateValue).toISOString() : null,
            note: $('#mpNote').value
        };
        try {
            if (id) await api(apiScope() + '/payments/' + id, { method: 'PUT', body });
            else await api(apiScope() + '/payments', { method: 'POST', body });
            closeModals();
            toast('Payment saved');
            loadPayments();
        } catch (e) { toast(e.message, true); }
    });
}

function applyMyPaymentConfig(config) {
    $('#myPgWallet').value = config.paygateWallet || '';
    $('#myPgEmail').value = config.paygateEmail || '';
    $('#myCryptoWallets').value = config.cryptoWallets || '';
    $('#myLogWebhook').value = config.logWebhookUrl || '';
    setPaymentStatus('pmPaygateDot', 'pmPaygateBadge', !!config.paygateConnected);
    setPaymentStatus('pmCryptoDot', 'pmCryptoBadge', !!config.cryptoConnected);
    setPaymentStatus('pmWebhookDot', 'pmWebhookBadge', !!config.logWebhookUrl);
}

async function loadPayments() {
    try { applyMyPaymentConfig(await api('/api/my/payment-config')); } catch (e) { /* optional */ }
    try {
        paymentsCache = await api(apiScope() + '/payments');
        renderPayments();
    } catch (e) { toast(e.message, true); }
}

function renderPayments() {
    const query = ($('#paymentSearch').value || '').toLowerCase();
    const status = $('#paymentStatusFilter').value;
    const method = $('#paymentMethodFilter').value;
    const filtered = paymentsCache.filter(p =>
        (!query || (p.customer || '').toLowerCase().includes(query) || (p.note || '').toLowerCase().includes(query))
        && (!status || p.status === status)
        && (!method || p.method === method));

    const methodLabels = { KREDITKARTE: 'Credit Card', PAYPAL: 'PayPal', UEBERWEISUNG: 'Bank Transfer', KRYPTO: 'Crypto', SONSTIGES: 'Other' };
    $('#paymentsTable tbody').innerHTML = filtered.map(p => `
        <tr>
            <td>${DATE.format(new Date(p.paymentDate))}</td>
            <td><b>${esc(p.customer)}</b></td>
            <td>${money(p.amount)}</td>
            <td>${methodLabels[p.method] || esc(p.method)}</td>
            <td>${esc(p.note || '—')}</td>
            <td>${badge(p.status)}</td>
            <td class="row">
                <button class="btn btn-sm btn-icon" onclick="editPayment(${p.id})">${icon('edit-2')}</button>
                <button class="btn btn-sm btn-icon btn-danger" onclick="deletePayment(${p.id})">${icon('trash-2')}</button>
            </td>
        </tr>`).join('') || '<tr><td colspan="7" class="muted">No payments</td></tr>';
}

function openPaymentModal(payment) {
    $('#paymentModalTitle').textContent = payment ? 'Edit Payment' : 'New Payment';
    $('#mpId').value = payment?.id || '';
    $('#mpCustomer').value = payment?.customer || '';
    $('#mpAmount').value = payment?.amount ?? '';
    $('#mpMethod').value = payment?.method || 'KREDITKARTE';
    $('#mpStatus').value = payment?.status || 'PAID';
    $('#mpNote').value = payment?.note || '';
    if (payment?.paymentDate) {
        const d = new Date(payment.paymentDate);
        d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
        $('#mpDate').value = d.toISOString().slice(0, 16);
    } else {
        $('#mpDate').value = '';
    }
    $('#manualPaymentModal').hidden = false;
}

window.editPayment = (id) => {
    const payment = paymentsCache.find(p => p.id === id);
    if (payment) openPaymentModal(payment);
};

window.deletePayment = async (id) => {
    if (!confirm('Delete this payment?')) return;
    try {
        await api(apiScope() + '/payments/' + id, { method: 'DELETE' });
        toast('Payment deleted');
        loadPayments();
    } catch (e) { toast(e.message, true); }
};

// ===== Customers =====

async function loadCustomers() {
    try {
        const customers = await api(me.admin ? '/api/admin/customers' : '/api/my/shop-customers');
        $('#customersTable tbody').innerHTML = customers.map(c => `
            <tr>
                <td>${c.avatar ? `<img class="thumb" style="border-radius:50%" src="${esc(c.avatar)}" alt="">` : icon('users')}</td>
                <td><b>${esc(c.username)}</b></td>
                <td><small>${esc(c.id)}</small></td>
                <td>${c.orderCount}</td>
                <td>${money(c.totalSpent)}</td>
                <td>${c.lastLogin ? DATE.format(new Date(c.lastLogin)) : '—'}</td>
                <td><span class="badge badge-${c.banned ? 'inactive' : 'active'}">${c.banned ? 'Banned' : 'Active'}</span></td>
                <td>${me.admin ? `<button class="btn btn-sm ${c.banned ? '' : 'btn-danger'}" onclick="toggleBan('${esc(c.id)}', ${!c.banned})">
                    ${c.banned ? 'Unban' : 'Ban'}</button>` : ''}</td>
            </tr>`).join('') || '<tr><td colspan="8" class="muted">No customers</td></tr>';
    } catch (e) { toast(e.message, true); }
}

window.toggleBan = async (id, banned) => {
    try {
        await api(`/api/admin/customers/${id}/ban`, { method: 'PUT', body: { banned } });
        toast(banned ? 'Customer banned' : 'Customer unbanned');
        loadCustomers();
    } catch (e) { toast(e.message, true); }
};

// ===== Coupons =====

async function loadDiscounts() {
    try {
        const discounts = await api(apiScope() + '/discounts');
        $('#discountsTable tbody').innerHTML = discounts.map(d => `
            <tr>
                <td><b>${esc(d.code)}</b></td>
                <td>${esc(guildName(d.guildId))}</td>
                <td>${d.percent}%</td>
                <td>${d.uses}${d.maxUses ? ' / ' + d.maxUses : ''}</td>
                <td>${d.expiresAt ? DATE.format(new Date(d.expiresAt)) : '∞'}</td>
                <td><span class="badge badge-${d.active ? 'active' : 'inactive'}">${d.active ? 'Active' : 'Inactive'}</span></td>
                <td class="row">
                    <button class="btn btn-sm btn-icon" onclick="toggleDiscount(${d.id})">${icon(d.active ? 'x' : 'check-circle')}</button>
                    <button class="btn btn-sm btn-icon btn-danger" onclick="deleteDiscount(${d.id})">${icon('trash-2')}</button>
                </td>
            </tr>`).join('') || '<tr><td colspan="7" class="muted">No coupons</td></tr>';
    } catch (e) { toast(e.message, true); }
}

function initDiscountForm() {
    $('#newDiscountBtn')?.addEventListener('click', () => {
        $('#dGuildId').innerHTML = '<option value="">Select a server…</option>'
            + guildsCache.map(g => `<option value="${esc(g.id)}">${esc(g.name)}</option>`).join('');
        $('#dGuildId').value = activeGuildId() || '';
        $('#discountModal').hidden = false;
    });
    $('#discountForm').addEventListener('submit', async (event) => {
        event.preventDefault();
        if (!$('#dGuildId').value) { toast('Please select a server.', true); return; }
        try {
            await api(apiScope() + '/discounts', {
                method: 'POST',
                body: {
                    guildId: $('#dGuildId').value,
                    code: $('#dCode').value,
                    percent: parseInt($('#dPercent').value, 10),
                    maxUses: parseInt($('#dMaxUses').value || '0', 10),
                    validDays: $('#dValidDays').value ? parseInt($('#dValidDays').value, 10) : null
                }
            });
            closeModals();
            toast('Coupon created');
            loadDiscounts();
        } catch (e) { toast(e.message, true); }
    });
}

window.toggleDiscount = async (id) => {
    try {
        await api(`${apiScope()}/discounts/${id}/toggle`, { method: 'PUT' });
        loadDiscounts();
    } catch (e) { toast(e.message, true); }
};

window.deleteDiscount = async (id) => {
    if (!confirm('Delete this coupon?')) return;
    try {
        await api(apiScope() + '/discounts/' + id, { method: 'DELETE' });
        loadDiscounts();
    } catch (e) { toast(e.message, true); }
};

// ===== Bot & Servers =====

async function loadServerSection() {
    try {
        const data = await api(me.admin ? '/api/admin/guilds' : '/api/my/guilds');
        guildsCache = data.guilds || [];
        renderGuildSwitcher();

        $('#botStatusPanel').innerHTML = `
            <div class="bot-status-row">
                <span class="status-dot ${data.botOnline ? 'online' : 'offline'}"></span>
                <div>
                    <b>${data.botOnline ? esc(data.botName) + ' is online' : 'Bot is offline'}</b>
                    <div class="muted" style="font-size:12.5px">${data.botOnline ? 'Connected to ' + guildsCache.length + ' server(s)' : 'Check DISCORD_BOT_TOKEN in .env'}</div>
                </div>
            </div>`;

        const inviteBtn = $('#inviteBotBtn');
        if (data.inviteUrl) { inviteBtn.href = data.inviteUrl; inviteBtn.hidden = false; }
        else inviteBtn.hidden = true;

        $('#guildGrid').innerHTML = guildsCache.map(g => `
            <div class="guild-card">
                ${g.icon ? `<img src="${esc(g.icon)}" alt="" class="guild-icon">` : `<div class="guild-icon guild-icon-fallback">${icon('server')}</div>`}
                <div class="guild-name">${esc(g.name)}</div>
                <div class="muted" style="font-size:12px">${esc(g.members)} members</div>
            </div>`).join('') || '<p class="muted">The bot is not in any server yet.</p>';
        populateIcons();
    } catch (e) { toast(e.message, true); }
}

function initGuildSwitcher() {
    $('#guildSwitcher').addEventListener('change', () => {
        localStorage.setItem('activeGuildId', $('#guildSwitcher').value);
        if (!$('#section-embeds').hidden) populateEmbedChannels();
        if (!$('#section-products').hidden) loadProducts();
        if (!$('#section-stock').hidden) loadStock();
    });
    // Guild-Liste früh laden, damit das Produkt-Modal sofort eine Server-Auswahl anbieten kann
    loadGuildsCache();
}

async function loadGuildsCache() {
    try {
        const data = await api(me.admin ? '/api/admin/guilds' : '/api/my/guilds');
        guildsCache = data.guilds || [];
        renderGuildSwitcher();
    } catch (e) { /* Bot evtl. offline */ }
}

function activeGuildId() {
    return localStorage.getItem('activeGuildId') || '';
}

/** Site-Admin verwaltet alles (/api/admin/*), jeder andere Nutzer nur seinen eigenen Bereich (/api/my/*). */
function apiScope() {
    return me.admin ? '/api/admin' : '/api/my';
}

function renderGuildSwitcher() {
    const select = $('#guildSwitcher');
    if (!guildsCache.length) { select.hidden = true; return; }
    select.hidden = false;
    const saved = localStorage.getItem('activeGuildId') || '';
    select.innerHTML = '<option value="">All servers</option>'
        + guildsCache.map(g => `<option value="${esc(g.id)}">${esc(g.name)}</option>`).join('');
    select.value = guildsCache.some(g => g.id === saved) ? saved : '';
}

// ===== Billing & Plan (Vendra-style) =====

const PLAN_ICONS = { FREE: 'box', PRO: 'sparkles', BUSINESS: 'crown' };

let billingCycle = 'yearly';   // 'monthly' | 'yearly' — vom Toggle gesteuert
let billingState = null;       // { plans, currentId } für Re-Render beim Umschalten

function initBilling() {
    $$('#billingToggle .billing-toggle-btn').forEach(btn => btn.addEventListener('click', () => {
        $$('#billingToggle .billing-toggle-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        billingCycle = btn.dataset.cycle === 'monthly' ? 'monthly' : 'yearly';
        renderPlanGrid();
    }));
}

async function loadBillingSection() {
    try {
        const [plan, plans] = await Promise.all([api('/api/my/plan'), api('/api/plans')]);
        const currentId = plan.isSiteAdmin ? null : plan.tier.id;
        const expiry = plan.expiresAt ? ` · expires ${DATE.format(new Date(plan.expiresAt))}` : '';
        $('#currentPlanSub').innerHTML = plan.isSiteAdmin
            ? 'As the site owner you have <b>no limits</b> — these tiers apply to other users of your dashboard.'
            : `You're currently on the <span class="badge badge-active">${esc(plan.tier.name)}</span> plan${expiry}.`;
        billingState = { plans, currentId };
        renderPlanGrid();
    } catch (e) { toast(e.message, true); }
    loadLicenses();
    loadUserPlans();
}

async function loadUserPlans() {
    if (!me.admin) return;
    try {
        const users = await api('/api/admin/users');
        const opt = (cur, v, label) => `<option value="${v}"${cur === v ? ' selected' : ''}>${label}</option>`;
        $('#userPlansTable tbody').innerHTML = users.map(u => {
            const cls = u.tier === 'PRO' ? 'pro' : u.tier === 'BUSINESS' ? 'business' : '';
            const expires = u.expiresAt ? DATE.format(new Date(u.expiresAt)) : (u.tier === 'FREE' ? '—' : '∞');
            const setter = u.isSiteAdmin
                ? '<span class="muted">Owner</span>'
                : `<select class="input" style="width:auto" data-setplan="${esc(u.id)}">${opt(u.tier,'FREE','Free (remove)')}${opt(u.tier,'PRO','Pro')}${opt(u.tier,'BUSINESS','Business')}</select>`;
            return `<tr>
                <td>${u.avatar ? `<img class="thumb" style="border-radius:50%" src="${esc(u.avatar)}" alt="">` : icon('users')}</td>
                <td><b>${esc(u.username || u.id)}</b>${u.isSiteAdmin ? ' <span class="plan-lock">OWNER</span>' : ''}<br><small class="muted">${esc(u.id)}</small></td>
                <td><span class="plan-badge ${cls}">${esc(u.tier)}</span></td>
                <td>${expires}</td>
                <td>${setter}</td>
            </tr>`;
        }).join('') || '<tr><td colspan="5" class="muted">No users yet</td></tr>';

        $$('#userPlansTable [data-setplan]').forEach(sel => sel.addEventListener('change', async () => {
            const uid = sel.dataset.setplan;
            try {
                await api(`/api/admin/users/${uid}/plan`, { method: 'PUT', body: { tier: sel.value, days: 0 } });
                toast(sel.value === 'FREE' ? 'Plan removed' : `Plan set to ${sel.value}`);
                loadUserPlans();
            } catch (e) { toast(e.message, true); }
        }));
    } catch (e) { toast(e.message, true); }
}

function renderPlanGrid() {
    if (!billingState) return;
    $('#billingPlanGrid').innerHTML = billingState.plans.map(p => renderPlanCard(p, billingState.currentId)).join('');
    $$('#billingPlanGrid [data-buy-plan]').forEach(btn =>
        btn.addEventListener('click', () => startPlanPurchase(btn.dataset.buyPlan)));
}

function planPrice(p) {
    return billingCycle === 'yearly' ? (p.yearlyPrice ?? p.price) : (p.monthlyPrice ?? p.price);
}

function renderPlanCard(p, currentId) {
    const isCurrent = p.id === currentId;
    const isFree = (p.monthlyPrice ?? p.price) <= 0;
    const iconChip = p.id === 'FREE' ? '' :
        `<div class="plan-card-chip ${p.id === 'BUSINESS' ? 'business' : ''}">${icon(PLAN_ICONS[p.id] || 'sparkles')}</div>`;
    const cycleLabel = billingCycle === 'yearly' ? '/yr' : '/mo';
    const priceLine = isFree
        ? `<div class="plan-price">€ 0 <span>/mo</span></div><div class="plan-tagline">${esc(p.tagline)}</div>`
        : `<div class="plan-price">${money(planPrice(p))} <span>${cycleLabel}</span></div><div class="plan-tagline">${esc(p.tagline)}</div>`;
    let cta;
    if (isCurrent) cta = `<button class="btn plan-current-btn" disabled>Your current plan</button>`;
    else if (isFree) cta = `<div class="plan-footer">Default plan</div>`;
    else cta = `<button class="btn btn-primary plan-cta" data-buy-plan="${p.id}">Upgrade to ${esc(p.name)}</button>`;

    return `<div class="plan-card${p.popular ? ' popular' : ''}${isCurrent ? ' is-current' : ''}">
        ${isCurrent ? '<span class="plan-current-badge">CURRENT PLAN</span>' : ''}
        <div class="plan-card-head">
            ${iconChip}
            <div>
                <div class="plan-card-name">${esc(p.name)}</div>
                <span class="plan-badge ${p.id.toLowerCase()}">${p.id}</span>
            </div>
        </div>
        ${priceLine}
        <ul class="plan-list">${p.features.map(f => `<li>${icon('check-circle')}${esc(f)}</li>`).join('')}</ul>
        ${cta}
    </div>`;
}

async function startPlanPurchase(tierId) {
    // Karte (PayGate) bevorzugen, sonst Krypto — mit dem aktuell gewählten Abrechnungszeitraum
    const currency = settings.paygateWallet ? 'KARTE' : 'BTC';
    try {
        const result = await api('/api/my/plan/purchase', { method: 'POST', body: { tier: tierId, cycle: billingCycle, currency } });
        await showPayment(result.orderId);
    } catch (e) { toast(e.message, true); }
}

function initLicenses() {
    // Mengen-Dropdown 1–12 füllen
    const countSel = $('#licenseCountSelect');
    if (countSel && !countSel.options.length) {
        countSel.innerHTML = Array.from({ length: 12 }, (_, i) => `<option value="${i + 1}">${i + 1}</option>`).join('');
    }
    $('#generateLicenseBtn')?.addEventListener('click', async () => {
        try {
            const license = await api('/api/admin/plan/licenses', {
                method: 'POST',
                body: {
                    tier: $('#licenseTierSelect').value,
                    cycle: $('#licenseCycleSelect').value,
                    count: parseInt($('#licenseCountSelect').value, 10)
                }
            });
            await navigator.clipboard.writeText(license.code).catch(() => {});
            toast(`License generated (${license.durationLabel}, copied): ${license.code}`);
            loadLicenses();
        } catch (e) { toast(e.message, true); }
    });

    $('#refreshUserPlansBtn')?.addEventListener('click', loadUserPlans);

    $('#redeemLicenseBtn').addEventListener('click', async () => {
        const code = $('#redeemCodeInput').value.trim();
        if (!code) return;
        try {
            const result = await api('/api/my/plan/redeem', { method: 'POST', body: { code } });
            $('#redeemCodeInput').value = '';
            toast(`License redeemed — plan upgraded to ${result.tier.name}`);
            loadBillingSection();
        } catch (e) { toast(e.message, true); }
    });
}

async function loadLicenses() {
    if (!me.admin) return;
    try {
        const licenses = await api('/api/admin/plan/licenses');
        $('#licensesTable tbody').innerHTML = licenses.map(l => `
            <tr>
                <td><code>${esc(l.code)}</code></td>
                <td>${esc(l.tier)}</td>
                <td>${esc(l.durationLabel || (l.days ? l.days + ' days' : '∞'))}</td>
                <td>${l.redeemed ? '<span class="badge badge-active">Redeemed</span>' : '<span class="badge badge-PENDING">Available</span>'}</td>
                <td>${DATE.format(new Date(l.createdAt))}</td>
                <td>${!l.redeemed ? `<button class="btn btn-sm btn-icon btn-danger" data-revoke="${l.id}">${icon('trash-2')}</button>` : ''}</td>
            </tr>`).join('') || '<tr><td colspan="6" class="muted">No licenses generated yet</td></tr>';

        $$('#licensesTable [data-revoke]').forEach(btn => btn.addEventListener('click', async () => {
            if (!confirm('Revoke this unredeemed license?')) return;
            try {
                await api('/api/admin/plan/licenses/' + btn.dataset.revoke, { method: 'DELETE' });
                toast('License revoked');
                loadLicenses();
            } catch (e) { toast(e.message, true); }
        }));
    } catch (e) { toast(e.message, true); }
}

// ===== Embed Builder =====

let embedsCache = [];
let channelsCache = [];
let currentEmbedId = null;

const EMBED_INPUT_IDS = ['eName', 'eContent', 'eTitle', 'eUrl', 'eDescription', 'eColor', 'eTimestamp',
    'eThumbnail', 'eImage', 'eAuthor', 'eAuthorIcon', 'eFooter', 'eFooterIcon'];

function initEmbedEditor() {
    EMBED_INPUT_IDS.forEach(id => $('#' + id)?.addEventListener('input', renderEmbedPreview));
    $('#eTimestamp').addEventListener('change', renderEmbedPreview);
    $('#addFieldBtn').addEventListener('click', () => { addFieldEditor(); renderEmbedPreview(); });
    $('#addButtonBtn').addEventListener('click', () => { addButtonEditor(); renderEmbedPreview(); });
    $('#embedNewBtn').addEventListener('click', resetEmbedEditor);
    $('#embedSaveBtn').addEventListener('click', () => saveEmbed(false));
    $('#embedSendBtn').addEventListener('click', () => saveEmbed(true));
    $('#embedDeleteBtn').addEventListener('click', deleteEmbed);
    $('#embedLoadSelect').addEventListener('change', () => {
        const id = Number($('#embedLoadSelect').value);
        const saved = embedsCache.find(e => e.id === id);
        if (saved) loadEmbedIntoEditor(saved);
    });
    renderEmbedPreview();
}

async function loadEmbedSection() {
    // Products for the buy-button selector — only your own products, not everyone's
    try { productsCache = await api(me.admin ? '/api/products' : '/api/my/products'); } catch (e) { /* optional */ }
    renderEmbedPreview();
    try {
        embedsCache = await api(apiScope() + '/embeds');
        const select = $('#embedLoadSelect');
        select.innerHTML = '<option value="">Load embed…</option>'
            + embedsCache.map(e => `<option value="${e.id}">${esc(e.name)}</option>`).join('');
        if (currentEmbedId) select.value = currentEmbedId;
    } catch (e) { toast(e.message, true); }
    try {
        channelsCache = await api(apiScope() + '/channels');
        populateEmbedChannels();
    } catch (e) { /* bot may be offline */ }
}

function populateEmbedChannels() {
    const activeGuild = localStorage.getItem('activeGuildId') || '';
    const filtered = activeGuild ? channelsCache.filter(c => c.guildId === activeGuild) : channelsCache;
    $('#embedChannelSelect').innerHTML = '<option value="">Select channel…</option>'
        + filtered.map(c => `<option value="${esc(c.id)}">#${esc(c.name)} (${esc(c.guild)})</option>`).join('');
}

function collectEmbedData() {
    const fields = [...$$('#fieldsEditor .field-editor')].map(el => ({
        name: el.querySelector('.f-name').value,
        value: el.querySelector('.f-value').value,
        inline: el.querySelector('.f-inline').checked
    })).filter(f => f.name.trim() && f.value.trim());

    const buttons = [...$$('#buttonsEditor .button-editor')].map(el => {
        const type = el.querySelector('.b-type').value;
        const base = {
            label: el.querySelector('.b-label').value,
            emoji: el.querySelector('.b-emoji').value
        };
        if (type === 'product') {
            return { ...base, productId: Number(el.querySelector('.b-product').value) || 0, style: el.querySelector('.b-style').value };
        }
        return { ...base, url: el.querySelector('.b-url').value };
    }).filter(b => b.label.trim() && (b.productId || (b.url && b.url.trim())));

    return {
        content: $('#eContent').value,
        title: $('#eTitle').value,
        url: $('#eUrl').value,
        description: $('#eDescription').value,
        color: $('#eColor').value,
        timestamp: $('#eTimestamp').checked,
        thumbnail: $('#eThumbnail').value,
        image: $('#eImage').value,
        author: $('#eAuthor').value,
        authorIcon: $('#eAuthorIcon').value,
        footer: $('#eFooter').value,
        footerIcon: $('#eFooterIcon').value,
        fields, buttons
    };
}

function addFieldEditor(field = {}) {
    const wrap = document.createElement('div');
    wrap.className = 'field-editor';
    wrap.innerHTML = `
        <button type="button" class="btn btn-sm btn-danger btn-icon remove-btn">${icon('x')}</button>
        <div class="row">
            <label>Field name <input class="input f-name" maxlength="256"></label>
            <label style="flex:0 0 auto;align-self:end"><span class="checkbox" style="margin-top:22px"><input type="checkbox" class="f-inline"> Inline</span></label>
        </div>
        <label>Value <textarea class="input f-value" rows="2" maxlength="1024"></textarea></label>`;
    wrap.querySelector('.f-name').value = field.name || '';
    wrap.querySelector('.f-value').value = field.value || '';
    wrap.querySelector('.f-inline').checked = !!field.inline;
    wrap.querySelector('.remove-btn').addEventListener('click', () => { wrap.remove(); renderEmbedPreview(); });
    wrap.querySelectorAll('input, textarea').forEach(el => el.addEventListener('input', renderEmbedPreview));
    $('#fieldsEditor').appendChild(wrap);
}

function addButtonEditor(button = {}) {
    const wrap = document.createElement('div');
    wrap.className = 'button-editor';
    wrap.innerHTML = `
        <button type="button" class="btn btn-sm btn-danger btn-icon remove-btn">${icon('x')}</button>
        <div class="row">
            <label>Type
                <select class="input b-type">
                    <option value="product">Buy product</option>
                    <option value="link">Open link</option>
                </select>
            </label>
            <label>Label <input class="input b-label" maxlength="80" placeholder="Buy now"></label>
        </div>
        <div class="row">
            <label class="grow">Emoji
                <div class="row" style="gap:8px">
                    <input class="input b-emoji" maxlength="32" placeholder="Optional">
                    <button type="button" class="btn btn-ghost btn-icon b-emoji-pick" title="Pick emoji">${icon('smile')}</button>
                </div>
            </label>
            <label class="b-style-wrap">Button color
                <select class="input b-style">
                    <option value="success">Green</option>
                    <option value="primary">Blue</option>
                    <option value="secondary">Grey</option>
                    <option value="danger">Red</option>
                </select>
            </label>
        </div>
        <label class="b-product-wrap">Product
            <select class="input b-product"></select>
        </label>
        <label class="b-url-wrap">URL <input class="input b-url" placeholder="https://…"></label>`;

    const productSelect = wrap.querySelector('.b-product');
    productSelect.innerHTML = productsCache.filter(p => p.active)
        .map(p => `<option value="${p.id}">${esc(p.name)} — ${money(p.price)}</option>`).join('')
        || '<option value="">— create a product first —</option>';

    const typeSelect = wrap.querySelector('.b-type');
    typeSelect.value = button.url ? 'link' : 'product';
    wrap.querySelector('.b-label').value = button.label || '';
    wrap.querySelector('.b-emoji').value = button.emoji || '';
    wrap.querySelector('.b-url').value = button.url || '';
    wrap.querySelector('.b-style').value = button.style || 'success';
    if (button.productId) productSelect.value = String(button.productId);

    const applyType = () => {
        const isProduct = typeSelect.value === 'product';
        wrap.querySelector('.b-product-wrap').hidden = !isProduct;
        wrap.querySelector('.b-style-wrap').hidden = !isProduct;
        wrap.querySelector('.b-url-wrap').hidden = isProduct;
    };
    applyType();

    typeSelect.addEventListener('change', () => { applyType(); renderEmbedPreview(); });
    wrap.querySelector('.remove-btn').addEventListener('click', () => { wrap.remove(); renderEmbedPreview(); });
    wrap.querySelector('.b-emoji-pick').addEventListener('click', (event) => {
        openEmojiPicker(wrap.querySelector('.b-emoji'), event.currentTarget);
    });
    wrap.querySelectorAll('input, select').forEach(el => {
        el.addEventListener('input', renderEmbedPreview);
        el.addEventListener('change', renderEmbedPreview);
    });
    $('#buttonsEditor').appendChild(wrap);
}

// ===== Emoji picker (server custom emojis + common Unicode picks) =====

const COMMON_EMOJIS = [
    ['🛒', 'cart'], ['💎', 'gem'], ['🔥', 'fire'], ['⭐', 'star'], ['✨', 'sparkles'],
    ['💰', 'money'], ['🎁', 'gift'], ['🚀', 'rocket'], ['🔑', 'key'], ['📦', 'package'],
    ['✅', 'check'], ['❌', 'cross'], ['🔔', 'bell'], ['👑', 'crown'], ['💠', 'diamond'],
    ['⚡', 'lightning'], ['🛡️', 'shield'], ['🏆', 'trophy'], ['🏅', 'medal'], ['💳', 'card']
];

let customEmojisCache = null;
let emojiPickerTarget = null;

function emojiDisplay(value) {
    if (!value) return '';
    const match = String(value).match(/^<a?:\w+:(\d+)>$/);
    if (match) return `<img class="emoji-inline" src="https://cdn.discordapp.com/emojis/${match[1]}.png" alt="">`;
    return esc(value);
}

function ensureEmojiPicker() {
    if ($('#emojiPickerPopover')) return;
    const el = document.createElement('div');
    el.id = 'emojiPickerPopover';
    el.className = 'emoji-picker-popover';
    el.hidden = true;
    el.innerHTML = `
        <input class="input" id="emojiPickerSearch" placeholder="Search emoji…">
        <div class="emoji-picker-body" id="emojiPickerBody"></div>`;
    document.body.appendChild(el);
    $('#emojiPickerSearch').addEventListener('input', renderEmojiPicker);
    document.addEventListener('click', (event) => {
        if (!el.hidden && !el.contains(event.target) && !event.target.closest('.b-emoji-pick')) {
            el.hidden = true;
        }
    });
}

async function openEmojiPicker(targetInput, anchorEl) {
    ensureEmojiPicker();
    emojiPickerTarget = targetInput;
    if (customEmojisCache === null) {
        try { customEmojisCache = await api(apiScope() + '/emojis'); } catch (e) { customEmojisCache = []; }
    }
    $('#emojiPickerSearch').value = '';
    renderEmojiPicker();

    const popover = $('#emojiPickerPopover');
    const rect = anchorEl.getBoundingClientRect();
    popover.style.top = (window.scrollY + rect.bottom + 6) + 'px';
    popover.style.left = Math.max(12, window.scrollX + rect.right - 300) + 'px';
    popover.hidden = false;
}

function renderEmojiPicker() {
    const query = ($('#emojiPickerSearch').value || '').toLowerCase();
    const customMatches = (customEmojisCache || []).filter(e => e.name.toLowerCase().includes(query));
    const commonMatches = COMMON_EMOJIS.filter(([, label]) => label.includes(query));

    let html = '';
    if (customMatches.length) {
        html += '<div class="emoji-picker-label">Server Emojis</div><div class="emoji-picker-grid">'
            + customMatches.map(e => `<button type="button" class="emoji-cell" data-value="${esc(e.formatted)}" title="${esc(e.name)}"><img src="${esc(e.imageUrl)}" alt=""></button>`).join('')
            + '</div>';
    }
    if (commonMatches.length) {
        html += '<div class="emoji-picker-label">Common</div><div class="emoji-picker-grid">'
            + commonMatches.map(([ch, label]) => `<button type="button" class="emoji-cell" data-value="${ch}" title="${label}">${ch}</button>`).join('')
            + '</div>';
    }
    $('#emojiPickerBody').innerHTML = html || '<p class="muted" style="padding:10px;font-size:12.5px">No emoji found.</p>';
    $$('#emojiPickerBody .emoji-cell').forEach(btn => btn.addEventListener('click', () => {
        if (emojiPickerTarget) {
            emojiPickerTarget.value = btn.dataset.value;
            emojiPickerTarget.dispatchEvent(new Event('input', { bubbles: true }));
        }
        $('#emojiPickerPopover').hidden = true;
    }));
}

function renderEmbedPreview() {
    const data = collectEmbedData();
    $('#pContent').textContent = data.content;
    $('#pContent').style.display = data.content ? '' : 'none';

    const preview = $('#embedPreview');
    preview.style.borderLeftColor = data.color || 'var(--accent)';
    const hasEmbed = data.title || data.description || data.fields.length || data.image || data.author || data.footer;
    preview.style.display = hasEmbed ? '' : 'none';

    let main = '';
    if (data.author) {
        main += `<div class="d-author">${data.authorIcon ? `<img src="${esc(data.authorIcon)}" alt="">` : ''}${esc(data.author)}</div>`;
    }
    if (data.title) main += `<div class="d-title">${esc(data.title)}</div>`;
    if (data.description) main += `<div class="d-desc">${esc(data.description)}</div>`;
    if (data.fields.length) {
        main += '<div class="d-fields">' + data.fields.map(f =>
            `<div class="d-field ${f.inline ? '' : 'full'}"><div class="d-field-name">${esc(f.name)}</div><div class="d-field-value">${esc(f.value)}</div></div>`
        ).join('') + '</div>';
    }

    let html = `<div class="d-main">${main}</div>`;
    if (data.thumbnail) html += `<img class="d-thumb" src="${esc(data.thumbnail)}" alt="" onerror="this.style.display='none'">`;
    if (data.image) html += `<img class="d-image" src="${esc(data.image)}" alt="" onerror="this.style.display='none'">`;
    if (data.footer || data.timestamp) {
        const time = data.timestamp ? new Date().toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' }) : '';
        html += `<div class="d-footer">${data.footerIcon ? `<img src="${esc(data.footerIcon)}" alt="">` : ''}${esc(data.footer)}${data.footer && time ? ' • ' : ''}${time}</div>`;
    }
    preview.innerHTML = html;

    $('#pButtons').innerHTML = data.buttons.map(b => {
        const cls = b.productId ? 'style-' + (b.style || 'success') : 'link';
        return `<span class="d-btn ${cls}">${b.emoji ? emojiDisplay(b.emoji) + ' ' : ''}${esc(b.label)}</span>`;
    }).join('');
}

/** Load a product from the table as a ready-made buy embed. */
window.productToEmbed = (id) => {
    const p = productsCache.find(x => x.id === id);
    if (!p) return;
    showSection('embeds');
    resetEmbedEditor();
    $('#eName').value = 'product-' + p.name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
    $('#eTitle').value = p.name;
    $('#eDescription').value = p.description || '';
    $('#eImage').value = p.imageUrl || '';
    $('#eFooter').value = settings.shopName || '';
    $('#eTimestamp').checked = true;
    addFieldEditor({ name: 'Price', value: money(p.price), inline: true });
    addFieldEditor({ name: 'Stock', value: p.stock === -1 ? 'Unlimited' : p.stock + ' available', inline: true });
    if (p.category) addFieldEditor({ name: 'Category', value: p.category, inline: true });
    addButtonEditor({ productId: p.id, label: 'Buy Now', emoji: '🛒', style: 'success' });
    renderEmbedPreview();
    toast('Buy embed ready — pick a channel and hit Send');
};

function loadEmbedIntoEditor(saved) {
    currentEmbedId = saved.id;
    const data = saved.data || {};
    $('#eName').value = saved.name || '';
    $('#eContent').value = data.content || '';
    $('#eTitle').value = data.title || '';
    $('#eUrl').value = data.url || '';
    $('#eDescription').value = data.description || '';
    $('#eColor').value = /^#[0-9a-fA-F]{6}$/.test(data.color || '') ? data.color : '#6366f1';
    $('#eTimestamp').checked = !!data.timestamp;
    $('#eThumbnail').value = data.thumbnail || '';
    $('#eImage').value = data.image || '';
    $('#eAuthor').value = data.author || '';
    $('#eAuthorIcon').value = data.authorIcon || '';
    $('#eFooter').value = data.footer || '';
    $('#eFooterIcon').value = data.footerIcon || '';
    $('#fieldsEditor').innerHTML = '';
    (data.fields || []).forEach(addFieldEditor);
    $('#buttonsEditor').innerHTML = '';
    (data.buttons || []).forEach(addButtonEditor);
    $('#embedDeleteBtn').hidden = false;
    renderEmbedPreview();
}

function resetEmbedEditor() {
    currentEmbedId = null;
    $('#embedLoadSelect').value = '';
    $('#eName').value = '';
    EMBED_INPUT_IDS.filter(id => id !== 'eName' && id !== 'eColor').forEach(id => {
        const el = $('#' + id);
        if (el.type === 'checkbox') el.checked = false; else el.value = '';
    });
    $('#eColor').value = settings.brandColor && /^#[0-9a-fA-F]{6}$/.test(settings.brandColor) ? settings.brandColor : '#6366f1';
    $('#fieldsEditor').innerHTML = '';
    $('#buttonsEditor').innerHTML = '';
    $('#embedDeleteBtn').hidden = true;
    renderEmbedPreview();
}

async function saveEmbed(sendAfter) {
    const name = $('#eName').value.trim();
    if (!name) { toast('Please enter an embed name.', true); return; }
    const data = collectEmbedData();
    if (!data.title && !data.description && !data.fields.length) {
        toast('Embed needs at least a title, description or one field.', true);
        return;
    }
    try {
        const result = currentEmbedId
            ? await api(apiScope() + '/embeds/' + currentEmbedId, { method: 'PUT', body: { name, data } })
            : await api(apiScope() + '/embeds', { method: 'POST', body: { name, data } });
        currentEmbedId = result.id;
        $('#embedDeleteBtn').hidden = false;
        toast('Embed saved');

        if (sendAfter) {
            const channelId = $('#embedChannelSelect').value;
            if (!channelId) { toast('Please select a channel.', true); return; }
            const sent = await api(`${apiScope()}/embeds/${currentEmbedId}/send`, { method: 'POST', body: { channelId } });
            toast(`Embed sent to #${sent.channel}`);
        }
        loadEmbedSection();
    } catch (e) { toast(e.message, true); }
}

async function deleteEmbed() {
    if (!currentEmbedId || !confirm('Delete this saved embed?')) return;
    try {
        await api(apiScope() + '/embeds/' + currentEmbedId, { method: 'DELETE' });
        toast('Embed deleted');
        resetEmbedEditor();
        loadEmbedSection();
    } catch (e) { toast(e.message, true); }
}

// ===== Settings =====

/** Holt die eigenen (pro-Nutzer) Panels in die Settings-Tabs — so sieht JEDER Nutzer nützliche Settings. */
function relocateUserSettings() {
    const pm = $('#myProvidersPanel');
    const pmTab = $('#settingsTabPaymentMethods');
    if (pm && pmTab && pm.parentElement !== pmTab) pmTab.appendChild(pm);
    const dg = $('.delivery-grid');
    const dTab = $('#settingsTabDelivery');
    if (dg && dTab && dg.parentElement !== dTab) dTab.appendChild(dg);
}

function selectSettingsTab(name) {
    // Delivery + Coupons sind Pro-Features — Free-Nutzer werden zum Upgrade geleitet
    if ((name === 'delivery' || name === 'coupons') && !planAtLeast('PRO')) {
        toast('Delivery & Coupons are a Pro feature — upgrade to unlock.', true);
        showSection('billing');
        return;
    }
    $$('#settingsTabs .settings-nav-item').forEach(t => t.classList.toggle('active', t.dataset.tab === name));
    // Nur vorhandene Panels umschalten — für Nutzer sind die admin-only-Panels aus dem DOM entfernt
    const panels = { general: 'settingsTabGeneral', branding: 'settingsTabBranding',
        payments: 'settingsTabPayments', logs: 'settingsTabLogs',
        paymentmethods: 'settingsTabPaymentMethods', delivery: 'settingsTabDelivery', coupons: 'settingsTabCoupons' };
    Object.entries(panels).forEach(([tab, id]) => { const el = $('#' + id); if (el) el.hidden = tab !== name; });
    if (name === 'coupons') loadDiscounts();
    if (name === 'paymentmethods') loadPayments();
    if (name === 'delivery') loadDelivery();
}

function initSettings() {
    $$('#settingsTabs .settings-nav-item').forEach(tab =>
        tab.addEventListener('click', () => selectSettingsTab(tab.dataset.tab)));

    $('#settingsForm')?.addEventListener('submit', async (event) => {
        event.preventDefault();
        try {
            settings = await api('/api/admin/settings', {
                method: 'PUT',
                body: {
                    shopName: $('#sShopName').value,
                    description: $('#sDescription').value,
                    currency: $('#sCurrency').value,
                    discordInvite: $('#sDiscordInvite').value,
                    supportServer: $('#sSupportServer').value,
                    monthlyGoal: $('#sMonthlyGoal').value
                }
            });
            applyBranding();
            toast('Settings saved');
        } catch (e) { toast(e.message, true); }
    });

    $('#brandingForm')?.addEventListener('submit', async (event) => {
        event.preventDefault();
        try {
            settings = await api('/api/admin/settings', {
                method: 'PUT',
                body: {
                    brandColor: $('#sBrandColor').value,
                    logoUrl: $('#sLogoUrl').value,
                    bannerUrl: $('#sBannerUrl').value,
                    footerText: $('#sFooterText').value,
                    socialLinks: $('#sSocialLinks').value
                }
            });
            applyBranding();
            toast('Branding saved');
        } catch (e) { toast(e.message, true); }
    });

    $('#logsForm')?.addEventListener('submit', async (event) => {
        event.preventDefault();
        try {
            settings = await api('/api/admin/settings', {
                method: 'PUT',
                body: {
                    logChannelId: $('#sLogChannelId').value.trim(),
                    reviewChannelId: $('#sReviewChannelId').value.trim(),
                    siteLogWebhookUrl: $('#sLogWebhookUrl').value.trim(),
                    logOrders: $('#sLogOrders').checked ? 'true' : 'false',
                    logSales: $('#sLogSales').checked ? 'true' : 'false',
                    logErrors: $('#sLogErrors').checked ? 'true' : 'false'
                }
            });
            toast('Logging settings saved');
        } catch (e) { toast(e.message, true); }
    });

    $('#sMaintenance')?.addEventListener('change', async () => {
        try {
            settings = await api('/api/admin/settings', {
                method: 'PUT',
                body: { maintenance: $('#sMaintenance').checked ? 'true' : 'false' }
            });
            applyBranding();
            toast($('#sMaintenance').checked ? 'Maintenance mode enabled' : 'Maintenance mode disabled');
        } catch (e) { toast(e.message, true); }
    });

    $('#pgSaveBtn')?.addEventListener('click', async () => {
        try {
            settings = await api('/api/admin/settings', {
                method: 'PUT',
                body: {
                    paygateWallet: $('#pgWallet').value,
                    paygateEmail: $('#pgEmail').value
                }
            });
            toast('PayGate saved');
            updateSitePaymentStatus();
        } catch (e) { toast(e.message, true); }
    });

    $('#stripeSaveBtn')?.addEventListener('click', async () => {
        // Nur nicht-leere Secrets senden — leere Felder lassen den bestehenden Key unangetastet
        const body = {};
        if ($('#sStripeSecret').value.trim()) body.stripeSecretKey = $('#sStripeSecret').value.trim();
        if ($('#sStripeWebhookSecret').value.trim()) body.stripeWebhookSecret = $('#sStripeWebhookSecret').value.trim();
        if (!Object.keys(body).length) { toast('Enter a Stripe key first.', true); return; }
        try {
            settings = await api('/api/admin/settings', { method: 'PUT', body });
            $('#sStripeSecret').value = '';
            $('#sStripeWebhookSecret').value = '';
            settings = await api('/api/admin/settings'); // Status (stripeConfigured) neu laden
            toast('Stripe saved');
            updateSitePaymentStatus();
        } catch (e) { toast(e.message, true); }
    });
}

async function loadSettingsForm() {
    // Ersten sichtbaren Tab aktivieren (Admin: General, normaler Nutzer: Coupons)
    const firstTab = $('#settingsTabs .settings-nav-item');
    if (firstTab) selectSettingsTab(firstTab.dataset.tab);
    // Site-Einstellungen sind admin-only — normale Nutzer laden sie nicht (bekämen 403)
    if (!me.admin) return;
    try { settings = await api('/api/admin/settings'); } catch (e) { toast(e.message, true); return; }
    $('#sShopName').value = settings.shopName || '';
    $('#sDescription').value = settings.description || '';
    $('#sBrandColor').value = /^#[0-9a-fA-F]{6}$/.test(settings.brandColor || '') ? settings.brandColor : '#6366f1';
    $('#sCurrency').value = settings.currency === 'USD' ? 'USD' : 'EUR';
    $('#sLogoUrl').value = settings.logoUrl || '';
    $('#sBannerUrl').value = settings.bannerUrl || '';
    $('#sFooterText').value = settings.footerText || '';
    $('#sDiscordInvite').value = settings.discordInvite || '';
    $('#sSupportServer').value = settings.supportServer || '';
    $('#sSocialLinks').value = settings.socialLinks || '';
    $('#sMonthlyGoal').value = settings.monthlyGoal || 1000;
    $('#sMaintenance').checked = settings.maintenance === 'true';
    $('#sLogChannelId').value = settings.logChannelId || '';
    $('#sReviewChannelId').value = settings.reviewChannelId || '';
    $('#sLogWebhookUrl').value = settings.siteLogWebhookUrl || '';
    $('#sLogOrders').checked = settings.logOrders !== 'false';
    $('#sLogSales').checked = settings.logSales !== 'false';
    $('#sLogErrors').checked = settings.logErrors !== 'false';
    $('#pgWallet').value = settings.paygateWallet || '';
    $('#pgEmail').value = settings.paygateEmail || '';
    updateSitePaymentStatus();
}

function setPaymentStatus(dotId, badgeId, connected) {
    const dot = $('#' + dotId);
    const badge = $('#' + badgeId);
    if (!dot || !badge) return;
    dot.className = 'status-dot ' + (connected ? 'online' : 'offline');
    badge.textContent = connected ? 'Active' : 'Setup required';
    badge.className = 'provider-badge' + (connected ? ' connected' : '');
}

function updateSitePaymentStatus() {
    setPaymentStatus('sitePaygateDot', 'sitePaygateBadge', !!settings.paygateWallet);
    setPaymentStatus('siteStripeDot', 'siteStripeBadge', settings.stripeConfigured === 'true');
    setPaymentStatus('siteCryptoDot', 'siteCryptoBadge',
        settings.cryptoConfigured === 'true' && settings.cryptoProviderActive === 'true');
}

// ===== Delivery Message =====

const DM_DEFAULT_TITLE = '🎉 Your order is ready!';

function initDelivery() {
    $('#dmTitle')?.addEventListener('input', renderDmPreview);
    $('#dmMessage')?.addEventListener('input', renderDmPreview);
    $('#dmSaveBtn')?.addEventListener('click', async () => {
        try {
            await api('/api/my/delivery-config', {
                method: 'PUT',
                body: { title: $('#dmTitle').value, message: $('#dmMessage').value }
            });
            toast('Delivery message saved');
        } catch (e) { toast(e.message, true); }
    });
    $('#dmResetBtn')?.addEventListener('click', async () => {
        $('#dmTitle').value = '';
        $('#dmMessage').value = '';
        renderDmPreview();
        try {
            await api('/api/my/delivery-config', { method: 'PUT', body: { title: '', message: '' } });
            toast('Reset to default');
        } catch (e) { toast(e.message, true); }
    });
}

async function loadDelivery() {
    try {
        const cfg = await api('/api/my/delivery-config');
        $('#dmTitle').value = cfg.title || '';
        $('#dmMessage').value = cfg.message || '';
    } catch (e) { /* optional */ }
    renderDmPreview();
}

function renderDmPreview() {
    if (!$('#dmPreviewTitle')) return;
    $('#dmPreviewTitle').textContent = $('#dmTitle').value.trim() || DM_DEFAULT_TITLE;
    const msg = $('#dmMessage').value.trim();
    const el = $('#dmPreviewMessage');
    el.textContent = msg;
    el.hidden = !msg;
}

// ===== My Orders (customer) =====

async function loadMyOrders() {
    try {
        const orders = await api('/api/my/orders');
        $('#myOrdersList').innerHTML = orders.map(o => {
            const tx = o.txHash && EXPLORERS[o.payCurrency]
                ? `<a href="${EXPLORERS[o.payCurrency]}${esc(o.txHash)}" target="_blank" rel="noopener">View transaction</a>` : '';
            const payBtn = o.status === 'PENDING'
                ? `<button class="btn btn-primary btn-sm" onclick="showPayment(${o.id})">Pay Now</button>` : '';
            const keys = (o.deliveredKeys || []).map(k =>
                `<div class="pay-address" title="Click to copy"
                      onclick="navigator.clipboard.writeText(this.textContent.trim()).then(() => toast('Key copied'))">${esc(k)}</div>`
            ).join('');
            return `<div class="order-card">
                <div class="order-head"><b>#${o.id} — ${esc(o.productName)} x${o.quantity}</b>${badge(o.status)}</div>
                <div class="order-meta">
                    ${DATE.format(new Date(o.createdAt))} • ${money(o.totalPrice)}
                    ${o.payCurrency ? ' • ' + (o.payCurrency === 'KARTE' ? 'Card' : esc(o.payCurrency)) : ''}
                    ${o.discountPercent ? ` • -${o.discountPercent}%` : ''}
                </div>
                ${keys ? `<div style="margin-bottom:10px"><small class="muted">Your key${o.deliveredKeys.length > 1 ? 's' : ''} (click to copy):</small>${keys}</div>` : ''}
                <div class="row">${payBtn}${tx}</div>
            </div>`;
        }).join('') || '<p class="muted">You have no orders yet. Use /shop on the Discord server!</p>';
    } catch (e) { toast(e.message, true); }
}

window.showPayment = async (orderId) => {
    try {
        const payment = await api(`/api/my/orders/${orderId}/payment`);
        if (payment.provider === 'paygate' || payment.provider === 'stripe') {
            const label = payment.provider === 'stripe' ? 'Pay with Stripe' : 'Go to Card Payment';
            $('#paymentDetails').innerHTML = `
                <p class="muted">Pay securely by card, Apple Pay or Google Pay.<br>
                Delivery is automatic once the payment is confirmed.</p>
                <div class="pay-amount">${money(payment.amount)}</div>
                <a class="btn btn-primary" href="${esc(payment.address)}" target="_blank" rel="noopener">${label}</a>`;
        } else {
            $('#paymentDetails').innerHTML = `
                <p class="muted">Send <b>exactly</b> this amount to the address below.<br>
                Delivery is automatic once the blockchain confirms it.</p>
                <div class="pay-amount">${esc(String(payment.amount))} ${esc(payment.currency)}</div>
                ${payment.qr ? `<img class="pay-qr" src="${payment.qr}" alt="QR code">` : ''}
                <div class="pay-address" title="Click to copy"
                     onclick="navigator.clipboard.writeText(this.textContent.trim()).then(() => toast('Address copied'))">
                    ${esc(payment.address)}
                </div>`;
        }
        $('#paymentModal').hidden = false;
    } catch (e) { toast(e.message, true); }
};

// ===== Modals =====

function initModals() {
    $$('.modal-backdrop').forEach(backdrop => {
        backdrop.addEventListener('click', (event) => {
            if (event.target === backdrop) closeModals();
        });
    });
    $$('[data-close]').forEach(btn => btn.addEventListener('click', closeModals));
}

function closeModals() {
    $$('.modal-backdrop').forEach(m => m.hidden = true);
}

window.toast = toast;
init();

