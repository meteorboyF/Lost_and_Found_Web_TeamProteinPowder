/**
 * admin.js — moderation workspace.
 *
 * The key is held in sessionStorage, not localStorage: it should not outlive
 * the browser session on a shared machine. Every request carries it, and the
 * server checks it on every endpoint — hiding the UI is not the control.
 */
(function () {
  'use strict';

  var LF = window.LF;
  var e = LF.escapeHtml;
  var KEY = 'lf.adminkey';

  var state = { tab: 'overview', items: [], comments: [], overview: null };

  function key() {
    try {
      return sessionStorage.getItem(KEY) || '';
    } catch (err) {
      return '';
    }
  }

  function request(path, options) {
    options = options || {};
    return fetch(path, {
      method: options.method || 'GET',
      headers: { Accept: 'application/json', 'X-Admin-Key': key() }
    }).then(function (res) {
      return res.json().then(function (payload) {
        if (!res.ok) throw Object.assign(new Error(payload.message || 'Request failed'), { status: res.status });
        return payload;
      });
    });
  }

  /* ------------------------------------------------------------------
     Sign in
     ------------------------------------------------------------------ */

  function showGate(message) {
    document.querySelector('[data-admin-gate]').hidden = false;
    document.querySelector('[data-admin-app]').hidden = true;
    var slot = document.querySelector('[data-admin-error]');
    if (slot) slot.textContent = message || '';
  }

  function showApp() {
    document.querySelector('[data-admin-gate]').hidden = true;
    document.querySelector('[data-admin-app]').hidden = false;
    loadAll();
  }

  function initGate() {
    var form = document.querySelector('[data-admin-form]');
    form.addEventListener('submit', function (ev) {
      ev.preventDefault();
      var value = form.elements.adminKey.value.trim();
      if (!value) {
        document.querySelector('[data-admin-error]').textContent = 'Enter the moderation key';
        return;
      }
      try {
        sessionStorage.setItem(KEY, value);
      } catch (err) {}

      request('/api/admin/session', { method: 'POST' })
        .then(showApp)
        .catch(function () {
          try {
            sessionStorage.removeItem(KEY);
          } catch (err) {}
          showGate('That key was not accepted.');
        });
    });

    var out = document.querySelector('[data-admin-signout]');
    if (out) {
      out.addEventListener('click', function () {
        try {
          sessionStorage.removeItem(KEY);
        } catch (err) {}
        showGate('');
      });
    }
  }

  /* ------------------------------------------------------------------
     Data
     ------------------------------------------------------------------ */

  function loadAll() {
    var host = document.querySelector('[data-admin-panel]');
    host.innerHTML = '<div class="h-40 animate-pulse rounded-2xl bg-sunken"></div>';

    Promise.all([
      request('/api/admin/overview'),
      request('/api/admin/items'),
      request('/api/admin/comments')
    ])
      .then(function (r) {
        state.overview = r[0];
        state.items = r[1];
        state.comments = r[2];
        render();
      })
      .catch(function (err) {
        if (err.status === 401) {
          showGate('That key is no longer valid.');
          return;
        }
        host.innerHTML = LF.errorState(err.message);
        var retry = host.querySelector('[data-retry]');
        if (retry) retry.addEventListener('click', loadAll);
      });
  }

  /* ------------------------------------------------------------------
     Render
     ------------------------------------------------------------------ */

  function statTile(label, value, tone) {
    return (
      '<div class="card p-4">' +
      '<p class="text-2xl font-bold tabular-nums ' + (tone || 'text-heading') + '">' + e(String(value)) + '</p>' +
      '<p class="mt-1 text-xs text-muted">' + e(label) + '</p></div>'
    );
  }

  function renderOverview() {
    var o = state.overview;
    var s = o.stats;

    var maxCat = Math.max.apply(null, Object.values(o.byCategory).concat([1]));
    var bars = Object.keys(o.byCategory).map(function (label) {
      var n = o.byCategory[label];
      return (
        '<div class="flex items-center gap-3">' +
          '<span class="w-36 shrink-0 truncate text-sm text-body">' + e(label) + '</span>' +
          '<span class="h-2.5 flex-1 overflow-hidden rounded-full bg-sunken">' +
            '<span class="block h-full rounded-full bg-brand" style="width:' +
              ((n / maxCat) * 100).toFixed(1) + '%"></span>' +
          '</span>' +
          '<span class="w-8 shrink-0 text-right font-mono text-sm text-muted">' + n + '</span>' +
        '</div>'
      );
    }).join('');

    return (
      '<div class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">' +
        statTile('Total posts', s.total) +
        statTile('Open', s.open) +
        statTile('In progress', s.pending, 'text-amber') +
        statTile('Resolved', s.resolved, 'text-found') +
        statTile('Lost', s.lost, 'text-lost') +
        statTile('Found', s.found, 'text-found') +
      '</div>' +
      '<div class="mt-4 grid gap-4 lg:grid-cols-2">' +
        '<div class="card p-5">' +
          '<p class="text-sm font-semibold text-heading">By category</p>' +
          '<div class="mt-4 grid gap-2.5">' + bars + '</div>' +
        '</div>' +
        '<div class="grid content-start gap-4">' +
          '<div class="card p-5">' +
            '<p class="text-sm font-semibold text-heading">This week</p>' +
            '<p class="mt-1 text-3xl font-bold tabular-nums text-heading">' + o.postedThisWeek + '</p>' +
            '<p class="text-xs text-muted">posts in the last 7 days</p>' +
          '</div>' +
          '<div class="card p-5">' +
            '<p class="text-sm font-semibold text-heading">Needs attention</p>' +
            '<p class="mt-1 text-3xl font-bold tabular-nums ' +
              (o.staleOverNinetyDays ? 'text-lost' : 'text-heading') + '">' +
              o.staleOverNinetyDays + '</p>' +
            '<p class="text-xs text-muted">unresolved for more than 90 days</p>' +
          '</div>' +
        '</div>' +
      '</div>'
    );
  }

  function renderItems() {
    if (!state.items.length) {
      return LF.emptyState({ title: 'No posts', message: 'The board is empty.' });
    }

    var rows = state.items.map(function (i) {
      return (
        '<tr class="border-t border-line">' +
          '<td class="p-3"><a class="font-mono text-xs text-brand hover:underline" ' +
            'href="/item.html?ref=' + encodeURIComponent(i.reference) + '">' + e(i.reference) + '</a></td>' +
          '<td class="p-3"><p class="text-sm font-medium text-heading">' + e(i.title) + '</p>' +
            '<p class="text-xs text-muted">' + e(i.category) + ' · ' + e(i.location) + '</p></td>' +
          '<td class="p-3">' + LF.kindPill(i.kind) + '</td>' +
          '<td class="p-3">' + LF.statusPill(i.status) + '</td>' +
          '<td class="p-3"><p class="text-sm text-body">' + e(i.reporterName) + '</p>' +
            '<p class="text-xs text-muted">' + e(i.reporterEmail) + '</p></td>' +
          '<td class="p-3 text-xs text-muted">' + e(LF.timeAgo(i.createdAt)) + '</td>' +
          '<td class="p-3 text-right"><button type="button" class="btn btn-ghost btn-sm text-lost" ' +
            'data-remove="' + e(i.reference) + '">Remove</button></td>' +
        '</tr>'
      );
    }).join('');

    return (
      '<div class="card overflow-hidden">' +
        '<div class="overflow-x-auto">' +
          '<table class="w-full min-w-[56rem] text-left">' +
            '<thead class="bg-sunken text-xs uppercase tracking-wide text-muted">' +
              '<tr><th class="p-3 font-semibold">Reference</th><th class="p-3 font-semibold">Item</th>' +
              '<th class="p-3 font-semibold">Kind</th><th class="p-3 font-semibold">Status</th>' +
              '<th class="p-3 font-semibold">Posted by</th><th class="p-3 font-semibold">When</th>' +
              '<th class="p-3"></th></tr></thead>' +
            '<tbody>' + rows + '</tbody>' +
          '</table>' +
        '</div>' +
      '</div>'
    );
  }

  function renderComments() {
    if (!state.comments.length) {
      return LF.emptyState({ icon: 'i-message', title: 'No comments', message: 'Nothing to moderate.' });
    }

    return '<div class="grid gap-3">' + state.comments.map(function (c) {
      return (
        '<div class="card p-4 ' + (c.hidden ? 'opacity-60' : '') + '">' +
          '<div class="flex flex-wrap items-start justify-between gap-3">' +
            '<div class="min-w-0">' +
              '<p class="text-sm font-semibold text-heading">' + e(c.authorName) +
                (c.authorEmail ? ' <span class="font-normal text-muted">' + e(c.authorEmail) + '</span>' : '') +
              '</p>' +
              '<p class="text-xs text-muted">on <a class="text-brand hover:underline" ' +
                'href="/item.html?ref=' + encodeURIComponent(c.itemReference) + '">' + e(c.itemTitle) + '</a>' +
                ' · ' + e(LF.timeAgo(c.createdAt)) + '</p>' +
            '</div>' +
            '<div class="flex items-center gap-2">' +
              (c.hidden ? '<span class="pill bg-lost-soft text-lost-text">Hidden</span>' : '') +
              '<button type="button" class="btn btn-secondary btn-sm" data-toggle="' + c.id + '">' +
                (c.hidden ? 'Restore' : 'Hide') + '</button>' +
            '</div>' +
          '</div>' +
          '<p class="mt-3 whitespace-pre-line text-sm text-body">' + e(c.body) + '</p>' +
        '</div>'
      );
    }).join('') + '</div>';
  }

  function render() {
    var host = document.querySelector('[data-admin-panel]');
    if (state.tab === 'overview') host.innerHTML = renderOverview();
    else if (state.tab === 'items') host.innerHTML = renderItems();
    else host.innerHTML = renderComments();

    var pending = state.comments.filter(function (c) { return !c.hidden; }).length;
    var badge = document.querySelector('[data-admin-count="comments"]');
    if (badge) badge.textContent = pending;
    var itemCount = document.querySelector('[data-admin-count="items"]');
    if (itemCount) itemCount.textContent = state.items.length;

    host.querySelectorAll('[data-toggle]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        btn.disabled = true;
        request('/api/admin/comments/' + btn.dataset.toggle + '/toggle', { method: 'POST' })
          .then(function (r) {
            state.comments.forEach(function (c) {
              if (String(c.id) === String(btn.dataset.toggle)) c.hidden = r.hidden;
            });
            render();
            LF.toast.success(r.hidden ? 'Comment hidden.' : 'Comment restored.');
          })
          .catch(function (err) {
            btn.disabled = false;
            LF.toast.error(err.message);
          });
      });
    });

    host.querySelectorAll('[data-remove]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var ref = btn.dataset.remove;
        if (!window.confirm('Remove ' + ref + ' permanently? This cannot be undone.')) return;
        btn.disabled = true;
        request('/api/admin/items/' + encodeURIComponent(ref), { method: 'DELETE' })
          .then(function () {
            state.items = state.items.filter(function (i) { return i.reference !== ref; });
            render();
            LF.toast.success(ref + ' removed.');
          })
          .catch(function (err) {
            btn.disabled = false;
            LF.toast.error(err.message);
          });
      });
    });
  }

  /* ------------------------------------------------------------------
     Boot
     ------------------------------------------------------------------ */

  document.addEventListener('lf:ready', function () {
    initGate();

    document.querySelectorAll('[role="tab"]').forEach(function (tab) {
      tab.addEventListener('click', function () {
        document.querySelectorAll('[role="tab"]').forEach(function (t) {
          t.setAttribute('aria-selected', t === tab ? 'true' : 'false');
        });
        state.tab = tab.dataset.tab;
        render();
      });
    });

    /* A key already in this session skips the gate. */
    if (key()) {
      request('/api/admin/session', { method: 'POST' }).then(showApp).catch(function () {
        showGate('');
      });
    } else {
      showGate('');
    }
  });
})();
