const API_BASE = '/api';
let sessionId = localStorage.getItem('db_session_id');

async function init() {
    const urlParams = new URLSearchParams(window.location.search);
    const token = urlParams.get('token');

    if (token) {
        document.getElementById('auth-status').innerText = 'Verifying token...';
        try {
            const resp = await fetch(`/api/auth/verify?token=${token}`);
            const data = await resp.json();
            if (data.success) {
                sessionId = data.sessionId;
                localStorage.setItem('db_session_id', sessionId);
                // Clean URL
                window.history.replaceState({}, document.title, "/");
            } else {
                alert('Auth failed: ' + data.error);
            }
        } catch (e) {
            alert('Auth error: ' + e);
        }
    }

    if (!sessionId) {
        document.getElementById('auth-status').innerText = 'Please run /db web in-game to get a login link.';
        return;
    }

    document.getElementById('login-overlay').classList.add('hidden');
    loadDatabases();
}

async function loadDatabases() {
    const resp = await apiFetch('/databases');
    const dbs = await resp.json();
    const list = document.getElementById('db-list');
    const select = document.getElementById('db-select');
    
    list.innerHTML = '<h3>Databases</h3>';
    dbs.forEach(db => {
        const p = document.createElement('p');
        p.innerText = '📁 ' + db;
        p.style.cursor = 'pointer';
        p.onclick = () => loadTables(db);
        list.appendChild(p);

        const opt = document.createElement('option');
        opt.value = db;
        opt.innerText = db;
        select.appendChild(opt);
    });
}

async function loadTables(db) {
    const resp = await apiFetch(`/databases/${db}/tables`);
    const tables = await resp.json();
    alert(`Tables in ${db}: \n` + tables.join(', '));
}

async function apiFetch(path, options = {}) {
    options.headers = options.headers || {};
    options.headers['Authorization'] = `Bearer ${sessionId}`;
    const resp = await fetch(API_BASE + path, options);
    if (resp.status === 401) {
        localStorage.removeItem('db_session_id');
        window.location.reload();
    }
    return resp;
}

document.getElementById('run-btn').onclick = async () => {
    const db = document.getElementById('db-select').value;
    const sql = document.getElementById('sql-input').value;
    if (!db || !sql) return alert('Select DB and enter SQL');

    const btn = document.getElementById('run-btn');
    btn.disabled = true;
    btn.innerText = 'Running...';

    try {
        const resp = await apiFetch(`/databases/${db}/query`, {
            method: 'POST',
            body: sql
        });
        const data = await resp.json();
        renderResults(data);
    } catch (e) {
        alert('Error: ' + e);
    } finally {
        btn.disabled = false;
        btn.innerText = 'Execute Query';
    }
};

function renderResults(data) {
    const container = document.getElementById('results-table-container');
    const card = document.getElementById('results-card');
    card.classList.remove('hidden');

    if (data.length === 0) {
        container.innerHTML = '<p>No results / Success (0 rows).</p>';
        return;
    }

    if (data[0].updateCount !== undefined) {
        container.innerHTML = `<p>Update Success. Rows affected: ${data[0].updateCount}</p>`;
        return;
    }

    let html = '<table><thead><tr>';
    const keys = Object.keys(data[0]);
    keys.forEach(k => html += `<th>${k}</th>`);
    html += '</tr></thead><tbody>';

    data.forEach(row => {
        html += '<tr>';
        keys.forEach(k => html += `<td>${row[k]}</td>`);
        html += '</tr>';
    });

    html += '</tbody></table>';
    container.innerHTML = html;
}

init();
