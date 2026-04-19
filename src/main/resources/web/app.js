// ================================================================
//  DBMANAGER — app.js
// ================================================================

const API = '/api';
let sessionId = localStorage.getItem('db_session_id');

const state = {
  databases:   [],
  activeDb:    null,
  expandedDbs: new Set(),
  tableCache:  {},      // db -> string[]
  columnCache: {},      // db.table -> string[]
  fsCallback:  null,
  fsMode:      'file',  // 'file' | 'dir'
};

// ================================================================
//  SQL KEYWORD LIST
// ================================================================

const SQL_KEYWORDS = [
  'SELECT','FROM','WHERE','INSERT','INTO','VALUES','UPDATE','SET','DELETE',
  'CREATE','TABLE','DATABASE','SCHEMA','INDEX','VIEW','DROP','ALTER','ADD',
  'COLUMN','JOIN','LEFT','RIGHT','INNER','OUTER','FULL','CROSS','ON','AS',
  'LIMIT','OFFSET','ORDER','BY','GROUP','HAVING','DISTINCT','NOT','NULL',
  'IS','IN','BETWEEN','LIKE','ILIKE','AND','OR','EXISTS','PRIMARY','KEY',
  'FOREIGN','REFERENCES','UNIQUE','DEFAULT','AUTO_INCREMENT','AUTOINCREMENT',
  'IF','THEN','ELSE','CASE','WHEN','END','BEGIN','COMMIT','ROLLBACK',
  'TRANSACTION','CONSTRAINT','CHECK','TRIGGER','PROCEDURE','FUNCTION',
  'RETURNS','UNION','ALL','INTERSECT','EXCEPT','WITH','RECURSIVE',
  'EXPLAIN','ANALYZE','SHOW','DESCRIBE','USE','GRANT','REVOKE',
  'INTEGER','INT','BIGINT','SMALLINT','TINYINT','MEDIUMINT','SERIAL',
  'TEXT','VARCHAR','CHAR','CHARACTER','VARYING','BOOLEAN','BOOL',
  'FLOAT','DOUBLE','DECIMAL','NUMERIC','REAL','MONEY',
  'DATE','TIME','TIMESTAMP','DATETIME','INTERVAL',
  'BLOB','BINARY','VARBINARY','BYTEA','JSON','JSONB','UUID','ARRAY',
  'COUNT','SUM','AVG','MAX','MIN','COALESCE','NULLIF','CAST','CONVERT',
  'NOW','CURRENT_TIMESTAMP','CURRENT_DATE','CURRENT_TIME',
  'CONCAT','LENGTH','SUBSTR','SUBSTRING','TRIM','LTRIM','RTRIM',
  'UPPER','LOWER','REPLACE','REVERSE','REPEAT',
  'ROUND','FLOOR','CEIL','CEILING','ABS','MOD','POWER','SQRT','RANDOM',
  'STRFTIME','DATE_FORMAT','YEAR','MONTH','DAY','HOUR','MINUTE','SECOND',
  'IFNULL','NVL','IIF','GREATEST','LEAST','ROW_NUMBER','RANK','DENSE_RANK',
  'OVER','PARTITION','PRECEDING','FOLLOWING','UNBOUNDED','ROWS','RANGE',
  'LAG','LEAD','FIRST_VALUE','LAST_VALUE','NTH_VALUE','NTILE',
  'RETURNING','CONFLICT','IGNORE','REPLACE','UPSERT','ON CONFLICT',
  'PRAGMA','VACUUM','ATTACH','DETACH',
].sort();

// ================================================================
//  UTILITIES
// ================================================================

function esc(v) {
  if (v == null) return '<span class="null-val">NULL</span>';
  return String(v).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

function escAttr(v) {
  return v == null ? '' : String(v).replace(/&/g,'&amp;').replace(/"/g,'&quot;').replace(/</g,'&lt;');
}

function fmtSize(b) {
  if (b < 1024) return b + ' B';
  if (b < 1048576) return (b/1024).toFixed(1) + ' KB';
  return (b/1048576).toFixed(1) + ' MB';
}

// ================================================================
//  TOASTS
// ================================================================

const ICONS = { info: 'ℹ️', success: '✅', error: '❌', warning: '⚠️' };

function toast(msg, type = 'info', dur = 4500) {
  const el = document.createElement('div');
  el.className = `toast toast-${type}`;
  el.innerHTML = `<span class="toast-icon">${ICONS[type]}</span><span>${esc(msg)}</span>`;
  document.getElementById('toasts').appendChild(el);
  requestAnimationFrame(() => requestAnimationFrame(() => el.classList.add('show')));
  setTimeout(() => { el.classList.remove('show'); setTimeout(() => el.remove(), 250); }, dur);
}

// ================================================================
//  API
// ================================================================

async function apiFetch(path, opts = {}) {
  opts.headers = { ...opts.headers, Authorization: `Bearer ${sessionId}` };
  const r = await fetch(API + path, opts);
  if (r.status === 401) { localStorage.removeItem('db_session_id'); sessionId = null; location.reload(); }
  return r;
}

// ================================================================
//  AUTH
// ================================================================

async function init() {
  const token = new URLSearchParams(location.search).get('token');
  if (token) {
    setAuthStatus('Verifying token…');
    try {
      const r = await fetch(`/api/auth/verify?token=${encodeURIComponent(token)}`);
      const d = await r.json();
      if (d.success) {
        sessionId = d.sessionId;
        localStorage.setItem('db_session_id', sessionId);
        history.replaceState({}, '', '/');
      } else {
        setAuthStatus(`Auth failed — ${d.error}`);
        return;
      }
    } catch (e) {
      setAuthStatus(`Connection error — ${e.message}`);
      return;
    }
  }
  if (!sessionId) { setAuthStatus('Run /db web in-game to get a login link'); return; }

  document.getElementById('auth-screen').hidden = true;
  document.getElementById('app').hidden = false;

  initTabs();
  initQuery();
  initConnect();
  initFsBrowser();
  await loadDatabases();
}

function setAuthStatus(msg) { document.getElementById('auth-status').textContent = msg; }

// ================================================================
//  TABS
// ================================================================

function initTabs() {
  document.querySelectorAll('.tab').forEach(b => b.addEventListener('click', () => switchTab(b.dataset.tab)));
  document.getElementById('open-connect-btn').addEventListener('click', () => switchTab('connect'));
}

function switchTab(name) {
  document.querySelectorAll('.tab').forEach(b => b.classList.toggle('active', b.dataset.tab === name));
  document.querySelectorAll('.tab-pane').forEach(p => p.hidden = true);
  const pane = document.getElementById(`tab-${name}`);
  if (pane) pane.hidden = false;
  if (name === 'connect') { renderActiveConnsList(); refreshConnDropdowns(); }
}

// ================================================================
//  SIDEBAR
// ================================================================

async function loadDatabases() {
  try {
    const r = await apiFetch('/databases');
    state.databases = await r.json();
    renderSidebar();
  } catch (e) { toast('Failed to load databases: ' + e.message, 'error'); }
}

function renderSidebar() {
  const list = document.getElementById('db-list');
  if (!state.databases.length) {
    list.innerHTML = '<div class="db-empty">No connections — use the Connect tab</div>';
    return;
  }
  list.innerHTML = '';
  for (const db of state.databases) {
    const isActive   = state.activeDb === db;
    const isExpanded = state.expandedDbs.has(db);
    const tables     = state.tableCache[db];

    const item = document.createElement('div');
    item.className = 'db-item' + (isActive ? ' active' : '');

    let tablesHtml = '';
    if (isExpanded) {
      if (!tables) {
        tablesHtml = '<div class="db-tables"><div class="db-table-loading">Loading…</div></div>';
      } else if (!tables.length) {
        tablesHtml = '<div class="db-tables"><div class="db-table-loading">No tables</div></div>';
      } else {
        tablesHtml = '<div class="db-tables">' + tables.map(t =>
          `<div class="db-table-item" data-db="${escAttr(db)}" data-table="${escAttr(t)}">
             <span class="db-table-icon">⊞</span>${esc(t)}
           </div>`
        ).join('') + '</div>';
      }
    }

    item.innerHTML = `
      <div class="db-row">
        <span class="db-arrow ${isExpanded ? 'open' : ''}">▶</span>
        <span class="db-name">${esc(db)}</span>
        <span class="db-indicator ${isActive ? 'live' : ''}"></span>
      </div>${tablesHtml}`;

    item.querySelector('.db-row').addEventListener('click', () => toggleDbExpand(db));
    item.querySelectorAll('.db-table-item').forEach(el => el.addEventListener('click', () => {
      setActiveDb(db);
      document.getElementById('sql-input').value = `SELECT * FROM \`${el.dataset.table}\` LIMIT 100;`;
      switchTab('query');
      AUTOCOMPLETE.hide();
    }));

    list.appendChild(item);
  }
}

async function toggleDbExpand(db) {
  setActiveDb(db);
  if (state.expandedDbs.has(db)) {
    state.expandedDbs.delete(db);
    renderSidebar();
  } else {
    state.expandedDbs.add(db);
    renderSidebar();
    if (!state.tableCache[db]) await loadTables(db);
    renderSidebar();
  }
}

async function loadTables(db) {
  try {
    const r = await apiFetch(`/databases/${encodeURIComponent(db)}/tables`);
    if (!r.ok) throw new Error((await r.json()).error || r.status);
    state.tableCache[db] = await r.json();
  } catch (e) {
    state.tableCache[db] = [];
    toast(`Could not load tables for "${db}": ${e.message}`, 'error');
  }
}

function setActiveDb(db) {
  state.activeDb = db;
  const badge = document.getElementById('active-db-badge');
  badge.textContent = db;
  badge.classList.add('active');
  renderSidebar();
}

// ================================================================
//  QUERY TAB
// ================================================================

function initQuery() {
  const input = document.getElementById('sql-input');

  input.addEventListener('keydown', e => {
    if (e.ctrlKey && e.key === 'Enter') { e.preventDefault(); AUTOCOMPLETE.hide(); runQuery(); return; }

    // Autocomplete navigation
    if (!document.getElementById('ac-popup').hidden) {
      if (e.key === 'ArrowDown')  { e.preventDefault(); AUTOCOMPLETE.move(1);  return; }
      if (e.key === 'ArrowUp')    { e.preventDefault(); AUTOCOMPLETE.move(-1); return; }
      if (e.key === 'Tab' || e.key === 'Enter') {
        if (AUTOCOMPLETE.accept()) { e.preventDefault(); return; }
      }
      if (e.key === 'Escape')     { e.preventDefault(); AUTOCOMPLETE.hide();   return; }
    }

    // Tab inserts 2 spaces when autocomplete is closed
    if (e.key === 'Tab' && document.getElementById('ac-popup').hidden) {
      e.preventDefault();
      const s = input.selectionStart, end = input.selectionEnd;
      input.value = input.value.slice(0, s) + '  ' + input.value.slice(end);
      input.selectionStart = input.selectionEnd = s + 2;
    }
  });

  input.addEventListener('input', () => AUTOCOMPLETE.trigger(input));
  input.addEventListener('blur',  () => setTimeout(() => AUTOCOMPLETE.hide(), 150));
  input.addEventListener('click', () => AUTOCOMPLETE.trigger(input));

  document.getElementById('run-btn').addEventListener('click', runQuery);
  document.getElementById('clear-btn').addEventListener('click', () => {
    input.value = '';
    document.getElementById('results-area').hidden = true;
    document.getElementById('query-timing').textContent = '';
    AUTOCOMPLETE.hide();
  });
  document.getElementById('sidebar-refresh').addEventListener('click', async () => {
    state.tableCache = {};
    state.columnCache = {};
    await loadDatabases();
    toast('Connections refreshed', 'success');
  });
}

async function runQuery() {
  const sql = document.getElementById('sql-input').value.trim();
  if (!sql)            { toast('Enter a SQL statement first', 'warning'); return; }
  if (!state.activeDb) { toast('Select a database from the sidebar first', 'warning'); return; }

  const btn = document.getElementById('run-btn');
  btn.disabled = true;
  btn.innerHTML = '<span>◌</span> Running…';
  document.getElementById('query-timing').textContent = '';

  const t0 = performance.now();
  try {
    const r = await apiFetch(`/databases/${encodeURIComponent(state.activeDb)}/query`, {
      method: 'POST', body: sql,
    });
    const ms   = Math.round(performance.now() - t0);
    const data = await r.json();
    document.getElementById('query-timing').textContent = ms + 'ms';
    if (!r.ok) { toast(data.error || 'Query failed', 'error', 7000); }
    else        { renderResults(data, ms); }
  } catch (e) {
    toast('Network error: ' + e.message, 'error');
  } finally {
    btn.disabled = false;
    btn.innerHTML = '<span>▶</span> Run Query';
  }
}

function renderResults(data, ms) {
  const area      = document.getElementById('results-area');
  const container = document.getElementById('results-container');
  const meta      = document.getElementById('results-meta');
  const csvBtn    = document.getElementById('export-csv-btn');
  area.hidden = false;

  if (!data || !data.length) {
    meta.textContent = '0 rows';
    container.innerHTML = '<div class="results-msg">No rows returned</div>';
    csvBtn.hidden = true;
    return;
  }
  if (data[0].updateCount !== undefined) {
    const n = data[0].updateCount;
    meta.textContent = `${n} row${n !== 1 ? 's' : ''} affected · ${ms}ms`;
    container.innerHTML = `<div class="results-msg ok">✓ ${n} row${n !== 1 ? 's' : ''} affected</div>`;
    csvBtn.hidden = true;
    delete state.tableCache[state.activeDb]; // invalidate table list
    return;
  }

  const keys = Object.keys(data[0]);
  meta.textContent = `${data.length} row${data.length !== 1 ? 's' : ''} · ${keys.length} col${keys.length !== 1 ? 's' : ''} · ${ms}ms`;
  csvBtn.hidden = false;
  csvBtn.onclick = () => exportCsv(data, keys);

  let html = '<table class="results-table"><thead><tr>';
  keys.forEach(k => { html += `<th>${esc(k)}</th>`; });
  html += '</tr></thead><tbody>';
  data.forEach(row => {
    html += '<tr>';
    keys.forEach(k => { html += `<td title="${escAttr(row[k])}">${esc(row[k])}</td>`; });
    html += '</tr>';
  });
  html += '</tbody></table>';
  container.innerHTML = html;
}

function exportCsv(data, keys) {
  const wrap = v => {
    if (v == null) return '';
    const s = String(v);
    return (s.includes(',') || s.includes('"') || s.includes('\n')) ? `"${s.replace(/"/g,'""')}"` : s;
  };
  const csv  = [keys.join(','), ...data.map(r => keys.map(k => wrap(r[k])).join(','))].join('\n');
  const a    = Object.assign(document.createElement('a'), {
    href: URL.createObjectURL(new Blob([csv], { type: 'text/csv' })),
    download: `${state.activeDb}_${Date.now()}.csv`,
  });
  a.click(); URL.revokeObjectURL(a.href);
}

// ================================================================
//  AUTOCOMPLETE
// ================================================================

const AUTOCOMPLETE = {
  items: [], selIdx: -1, word: '',

  /** Get the word being typed at cursor */
  wordAt(el) {
    const before = el.value.slice(0, el.selectionStart);
    const m = before.match(/([a-zA-Z_][a-zA-Z0-9_]*)$/);
    return m ? m[1] : '';
  },

  /** Build suggestion list */
  suggest(word) {
    if (!word) return [];
    const up  = word.toUpperCase();
    const low = word.toLowerCase();
    const kws = SQL_KEYWORDS
      .filter(k => k.startsWith(up) && k !== up)
      .slice(0, 6)
      .map(k => ({ label: k, type: 'kw' }));
    const tbls = (state.tableCache[state.activeDb] || [])
      .filter(t => t.toLowerCase().startsWith(low) && t.toLowerCase() !== low)
      .slice(0, 4)
      .map(t => ({ label: t, type: 'tbl' }));
    return [...tbls, ...kws].slice(0, 8);
  },

  /** Calculate pixel position of caret inside textarea */
  caretPos(el) {
    const m = document.createElement('div');
    const cs = getComputedStyle(el);
    ['fontFamily','fontSize','fontWeight','fontStyle','lineHeight',
     'letterSpacing','paddingTop','paddingLeft','paddingRight',
     'paddingBottom','borderTopWidth','borderLeftWidth','boxSizing',
     'tabSize'].forEach(p => { m.style[p] = cs[p]; });
    m.style.cssText += ';position:absolute;top:-9999px;left:-9999px;visibility:hidden;width:' + cs.width + ';white-space:pre-wrap;word-break:break-word;overflow:hidden';
    const pre = document.createTextNode(el.value.slice(0, el.selectionStart));
    const span = document.createElement('span');
    span.textContent = '\u200b';
    m.appendChild(pre); m.appendChild(span);
    document.body.appendChild(m);
    const sr = span.getBoundingClientRect();
    const er = el.getBoundingClientRect();
    document.body.removeChild(m);
    return { x: sr.left - er.left, y: sr.bottom - er.top - el.scrollTop + 4 };
  },

  trigger(el) {
    const word = this.wordAt(el);
    if (!word) { this.hide(); return; }
    const items = this.suggest(word);
    if (!items.length) { this.hide(); return; }
    this.items  = items;
    this.selIdx = 0;
    this.word   = word;
    this.render();

    // Position popup
    const popup = document.getElementById('ac-popup');
    popup.hidden = false;
    const { x, y } = this.caretPos(el);
    const er = el.getBoundingClientRect();
    const pw = 260;
    const left = Math.min(er.left + x, window.innerWidth - pw - 12);
    let   top  = er.top  + y;
    // Flip above if too close to bottom
    if (top + 200 > window.innerHeight) top = er.top + y - popup.offsetHeight - 8;
    popup.style.cssText = `left:${left}px;top:${top}px;`;
  },

  render() {
    const popup = document.getElementById('ac-popup');
    const typeLabel = { kw: 'keyword', tbl: 'table', col: 'column' };
    popup.innerHTML = this.items.map((item, i) =>
      `<div class="ac-item ${i === this.selIdx ? 'ac-selected' : ''}" data-i="${i}">
         <span class="ac-label">${esc(item.label)}</span>
         <span class="ac-type ac-${item.type}">${typeLabel[item.type]}</span>
       </div>`
    ).join('');
    popup.querySelectorAll('.ac-item').forEach(el =>
      el.addEventListener('mousedown', e => { e.preventDefault(); this.accept(+el.dataset.i); })
    );
  },

  move(dir) {
    if (!this.items.length) return;
    this.selIdx = (this.selIdx + dir + this.items.length) % this.items.length;
    this.render();
  },

  accept(idx = this.selIdx) {
    if (idx < 0 || idx >= this.items.length) return false;
    const item = this.items[idx];
    const el   = document.getElementById('sql-input');
    const cur  = el.selectionStart;
    const before = el.value.slice(0, cur);
    const after  = el.value.slice(cur);
    const start  = before.length - this.word.length;
    el.value = before.slice(0, start) + item.label + after;
    const pos = start + item.label.length;
    el.selectionStart = el.selectionEnd = pos;
    this.hide();
    el.focus();
    return true;
  },

  hide() {
    document.getElementById('ac-popup').hidden = true;
    this.items  = [];
    this.selIdx = -1;
  },
};

// ================================================================
//  CONNECT TAB
// ================================================================

function initConnect() {
  document.getElementById('rm-type').addEventListener('change', function () {
    document.getElementById('rm-port').placeholder = this.value === 'postgresql' ? '5432' : '3306';
  });
  document.getElementById('sq-browse-btn').addEventListener('click', () =>
    openFsBrowser('file', path => { document.getElementById('sq-path').value = path; })
  );
  document.getElementById('cr-sq-browse-btn').addEventListener('click', () =>
    openFsBrowser('dir', dir => { document.getElementById('cr-sq-dir').value = dir; })
  );

  document.getElementById('sq-connect-btn').addEventListener('click', addSqliteConn);
  document.getElementById('rm-connect-btn').addEventListener('click', addRemoteConn);
  document.getElementById('cr-sq-btn').addEventListener('click',    createSqliteDb);
  document.getElementById('cr-rm-btn').addEventListener('click',    createRemoteDb);
}

async function addSqliteConn() {
  const name = document.getElementById('sq-name').value.trim();
  const path = document.getElementById('sq-path').value.trim();
  if (!name) { toast('Connection name is required', 'warning'); return; }
  if (!path) { toast('File path is required', 'warning'); return; }
  await doConnect('sq-connect-btn', { name, type: 'sqlite', path });
}

async function addRemoteConn() {
  const name     = document.getElementById('rm-name').value.trim();
  const type     = document.getElementById('rm-type').value;
  const host     = document.getElementById('rm-host').value.trim() || 'localhost';
  const portRaw  = document.getElementById('rm-port').value;
  const port     = portRaw ? parseInt(portRaw) : (type === 'postgresql' ? 5432 : 3306);
  const database = document.getElementById('rm-database').value.trim();
  const username = document.getElementById('rm-user').value.trim();
  const password = document.getElementById('rm-pass').value;
  if (!name) { toast('Connection name is required', 'warning'); return; }
  await doConnect('rm-connect-btn', { name, type, host, port, database, username, password });
}

async function createSqliteDb() {
  const name   = document.getElementById('cr-sq-name').value.trim();
  const dir    = document.getElementById('cr-sq-dir').value.trim();
  const file   = document.getElementById('cr-sq-file').value.trim() || name;
  if (!name) { toast('Connection name is required', 'warning'); return; }
  if (!file) { toast('Filename is required', 'warning'); return; }
  const filename = file.endsWith('.db') ? file : file + '.db';
  const path     = dir ? (dir.replace(/\/$/, '') + '/' + filename) : filename;
  await doConnect('cr-sq-btn', { name, type: 'sqlite', path });
  if (!document.getElementById('cr-sq-name').value) {
    document.getElementById('cr-sq-file').value = '';
    document.getElementById('cr-sq-dir').value  = '';
  }
}

async function createRemoteDb() {
  const via    = document.getElementById('cr-rm-via').value;
  const dbname = document.getElementById('cr-rm-dbname').value.trim();
  if (!via)    { toast('Select a connection to create the database on', 'warning'); return; }
  if (!dbname) { toast('Database name is required', 'warning'); return; }

  const btn = document.getElementById('cr-rm-btn');
  btn.disabled = true; btn.textContent = 'Creating…';
  try {
    const r = await apiFetch('/databases/create', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ via, dbname }),
    });
    const d = await r.json();
    if (r.ok) {
      toast(`Database "${dbname}" created on ${via}`, 'success');
      document.getElementById('cr-rm-dbname').value = '';
    } else { toast(d.error || 'Creation failed', 'error', 7000); }
  } catch (e) { toast('Error: ' + e.message, 'error'); }
  finally { btn.disabled = false; btn.textContent = 'Create Database'; }
}

async function doConnect(btnId, body) {
  const btn      = document.getElementById(btnId);
  const origText = btn.textContent;
  btn.disabled = true; btn.textContent = 'Connecting…';
  try {
    const r = await apiFetch('/connections', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
    });
    const d = await r.json();
    if (r.ok) {
      toast(`Connected: ${body.name}`, 'success');
      const prefix = btnId.startsWith('cr-sq') ? 'cr-sq' : btnId.startsWith('sq') ? 'sq' : 'rm';
      const nameField = document.getElementById(`${prefix}-name`);
      if (nameField) nameField.value = '';
      state.tableCache = {};
      await loadDatabases();
      refreshConnDropdowns();
      renderActiveConnsList();
    } else { toast(d.error || 'Connection failed', 'error', 7000); }
  } catch (e) { toast('Error: ' + e.message, 'error'); }
  finally { btn.disabled = false; btn.textContent = origText; }
}

async function removeConn(name) {
  try {
    const r = await apiFetch(`/connections/${encodeURIComponent(name)}`, { method: 'DELETE' });
    if (r.ok) {
      if (state.activeDb === name) {
        state.activeDb = null;
        const badge = document.getElementById('active-db-badge');
        badge.textContent = 'No database selected';
        badge.classList.remove('active');
      }
      delete state.tableCache[name];
      state.expandedDbs.delete(name);
      await loadDatabases();
      refreshConnDropdowns();
      renderActiveConnsList();
      toast(`Disconnected: ${name}`, 'info');
    } else {
      const d = await r.json();
      toast(d.error || 'Failed to disconnect', 'error');
    }
  } catch (e) { toast('Error: ' + e.message, 'error'); }
}

function renderActiveConnsList() {
  const list = document.getElementById('active-conns-list');
  if (!state.databases.length) {
    list.innerHTML = '<div class="db-empty">No active connections</div>';
    return;
  }
  list.innerHTML = state.databases.map(db => `
    <div class="conn-row">
      <span class="conn-indicator"></span>
      <span class="conn-name">${esc(db)}</span>
      <button class="btn-disconnect" data-db="${escAttr(db)}" title="Disconnect">✕</button>
    </div>`).join('');
  list.querySelectorAll('.btn-disconnect').forEach(b =>
    b.addEventListener('click', () => removeConn(b.dataset.db))
  );
}

function refreshConnDropdowns() {
  const sel = document.getElementById('cr-rm-via');
  const cur = sel.value;
  sel.innerHTML = '<option value="">— select a connection —</option>' +
    state.databases.map(db => `<option value="${escAttr(db)}" ${db === cur ? 'selected' : ''}>${esc(db)}</option>`).join('');
}

// ================================================================
//  FS BROWSER MODAL
// ================================================================

let fsCurrentPath    = '';
let fsSelectedPath   = null;

function initFsBrowser() {
  document.getElementById('fs-close-btn').addEventListener('click', closeFs);
  document.querySelector('.modal-backdrop').addEventListener('click', closeFs);
  document.getElementById('fs-select-file-btn').addEventListener('click', () => {
    if (fsSelectedPath && state.fsCallback) { state.fsCallback(fsSelectedPath); closeFs(); }
  });
  document.getElementById('fs-select-dir-btn').addEventListener('click', () => {
    if (state.fsCallback) { state.fsCallback(fsCurrentPath || '.'); closeFs(); }
  });
}

function openFsBrowser(mode, onSelect) {
  state.fsMode    = mode;
  state.fsCallback = onSelect;
  fsSelectedPath  = null;
  document.getElementById('fs-selected-label').textContent = mode === 'dir' ? 'Navigate to the target folder' : 'Nothing selected';
  document.getElementById('fs-select-file-btn').disabled = true;
  document.getElementById('fs-select-dir-btn').hidden    = mode !== 'dir';
  document.getElementById('fs-select-file-btn').hidden   = mode === 'dir';
  document.getElementById('fs-modal').hidden = false;
  browseFsDir('');
}

function closeFs() {
  document.getElementById('fs-modal').hidden = true;
  fsSelectedPath = null;
}

async function browseFsDir(path) {
  fsCurrentPath = path;
  const list    = document.getElementById('fs-list');
  list.innerHTML = '<div class="fs-msg">Loading…</div>';

  // Update "use this folder" label
  if (state.fsMode === 'dir') {
    document.getElementById('fs-selected-label').textContent = path || '/ (server root)';
  }

  try {
    const r = await apiFetch(`/fs/browse?path=${encodeURIComponent(path)}`);
    const d = await r.json();
    if (!r.ok) { list.innerHTML = `<div class="fs-msg fs-err">${esc(d.error)}</div>`; return; }

    // Breadcrumb
    const crumb  = document.getElementById('fs-crumb');
    const parts  = d.current ? d.current.split('/').filter(Boolean) : [];
    const segs   = [{ label: 'root', path: '' }, ...parts.map((p, i) => ({ label: p, path: parts.slice(0, i+1).join('/') }))];
    crumb.innerHTML = segs.map((s, i) =>
      `<span class="bc-seg" data-path="${escAttr(s.path)}">${esc(s.label)}</span>${i < segs.length-1 ? '<span class="bc-slash">/</span>' : ''}`
    ).join('');
    crumb.querySelectorAll('.bc-seg').forEach(el =>
      el.addEventListener('click', () => browseFsDir(el.dataset.path))
    );

    list.innerHTML = '';

    // Up link
    if (d.parent !== null && d.parent !== undefined) {
      const up = document.createElement('div');
      up.className = 'fs-row fs-dir';
      up.innerHTML = '<span class="fs-icon">⬆️</span><span class="fs-name">.. (parent folder)</span>';
      up.addEventListener('click', () => browseFsDir(d.parent));
      list.appendChild(up);
    }

    // Directories
    d.dirs.forEach(dir => {
      const el = document.createElement('div');
      el.className = 'fs-row fs-dir';
      el.innerHTML = `<span class="fs-icon">📁</span><span class="fs-name">${esc(dir.name)}</span>`;
      el.addEventListener('click', () => browseFsDir(dir.rel));
      list.appendChild(el);
    });

    // Files (only in file mode)
    if (state.fsMode === 'file') {
      d.files.forEach(file => {
        const el = document.createElement('div');
        el.className = 'fs-row fs-file';
        el.innerHTML = `<span class="fs-icon">🗄️</span><span class="fs-name">${esc(file.name)}</span><span class="fs-meta">${fmtSize(file.size)}</span>`;
        el.addEventListener('click', () => {
          list.querySelectorAll('.fs-row.selected').forEach(e => e.classList.remove('selected'));
          el.classList.add('selected');
          fsSelectedPath = file.abs;
          document.getElementById('fs-selected-label').textContent = file.abs;
          document.getElementById('fs-select-file-btn').disabled = false;
        });
        list.appendChild(el);
      });
    }

    if (!d.dirs.length && (state.fsMode !== 'file' || !d.files.length)) {
      const msg = document.createElement('div');
      msg.className = 'fs-msg';
      msg.textContent = state.fsMode === 'file' ? 'No SQLite files in this directory' : 'No subdirectories';
      list.appendChild(msg);
    }
  } catch (e) {
    list.innerHTML = `<div class="fs-msg fs-err">Error: ${esc(e.message)}</div>`;
  }
}

// ================================================================
//  BOOT
// ================================================================
init();
