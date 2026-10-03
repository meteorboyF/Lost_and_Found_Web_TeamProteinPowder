/**
 * dashboard.js — the signed-in account's posts and conversations.
 */
(function () {
  'use strict';

  var LF = window.LF;
  var e = LF.escapeHtml;

  var state = { items: [], claims: [], tab: 'posts' };

  /* ------------------------------------------------------------------
     Loading
     ------------------------------------------------------------------ */

  function load() {
    if (!LF.auth.require()) return;
    var host = document.querySelector('[data-dash]');
    host.innerHTML = LF.skeletonGrid(3);
    Promise.all([LF.api.get('/api/items/mine'), LF.api.get('/api/claims')])
      .then(function (results) {
        if (!LF.auth.isLoggedIn()) return;
        state.items = results[0];
        state.claims = results[1].filter(function (c) { return c.viewerRole === 'CLAIMANT'; });
        state.incoming = results[1].filter(function (c) { return c.viewerRole === 'POSTER'; });
        render();
      })
      .catch(function (err) {
        host.innerHTML = LF.errorState(err.message);
        var retry = host.querySelector('[data-retry]');
        if (retry) retry.addEventListener('click', load);
      });
  }

  function renderEmpty() {
    document.querySelector('[data-dash]').innerHTML = LF.emptyState({
      icon: 'i-inbox',
      title: 'Nothing here yet',
      message: 'Your account’s posts and conversations appear here on every device.',
      actionHref: '/report.html',
      actionLabel: 'Post an item'
    });
    paintCounts();
  }

  /* ------------------------------------------------------------------
     Rendering
     ------------------------------------------------------------------ */

  var CLAIM_STATUS = {
    OPEN: 'bg-amber-soft text-amber-text',
    APPROVED: 'bg-brand-soft text-brand-text',
    ACCEPTED: 'bg-found-soft text-found-text',
    DECLINED: 'bg-lost-soft text-lost-text',
    WITHDRAWN: 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300'
  };

  function claimRow(claim, showClaimant) {
    var cat = LF.category(claim.item.category);
    var thumb = claim.item.photoUrl
      ? '<img src="' + e(claim.item.photoUrl) + '" alt="" class="h-12 w-12 rounded-lg object-cover">'
      : '<span class="grid h-12 w-12 shrink-0 place-items-center rounded-lg bg-gradient-to-br ' + cat.grad + '">' +
        '<svg class="icon h-6 w-6 ' + cat.solid + '" aria-hidden="true"><use href="/assets/icons.svg#' +
        cat.icon + '"></use></svg></span>';

    return (
      '<a href="/claim.html?ref=' + encodeURIComponent(claim.reference) + '" ' +
         'class="card card-interactive flex items-center gap-3 p-3">' +
        thumb +
        '<span class="min-w-0 flex-1">' +
          '<span class="block truncate text-sm font-semibold text-heading">' + e(claim.item.title) + '</span>' +
          '<span class="block truncate text-xs text-muted">' +
            (showClaimant ? e(claim.claimantName) + ' · ' : '') +
            e(LF.timeAgo(claim.updatedAt)) +
          '</span>' +
        '</span>' +
        '<span class="pill ' + (CLAIM_STATUS[claim.status] || CLAIM_STATUS.OPEN) + '">' +
          e(claim.status === 'APPROVED' ? 'Awaiting pickup' : claim.status === 'ACCEPTED' ? 'Returned' : claim.status.charAt(0) + claim.status.slice(1).toLowerCase()) + '</span>' +
      '</a>'
    );
  }

  function render() {
    paintCounts();

    var host = document.querySelector('[data-dash]');
    var panels = '';

    /* -- my posts -- */
    if (state.tab === 'posts') {
      panels = state.items.length
        ? LF.itemGrid(state.items)
        : LF.emptyState({
            title: 'You have not posted anything',
            message: 'Report something you lost, or hand in something you found.',
            actionHref: '/report.html',
            actionLabel: 'Post an item'
          });
    }

    /* -- claims I made -- */
    if (state.tab === 'claims') {
      panels = state.claims.length
        ? '<div class="grid gap-3">' + state.claims.map(function (c) { return claimRow(c, false); }).join('') + '</div>'
        : LF.emptyState({
            icon: 'i-hand',
            title: 'You have not claimed anything',
            message: 'When you recognise something on the board, claiming it starts a conversation.',
            actionHref: '/browse.html',
            actionLabel: 'Browse the board'
          });
    }

    /* -- claims on my posts -- */
    if (state.tab === 'incoming') {
      panels = state.incoming && state.incoming.length
        ? '<div class="grid gap-3">' + state.incoming.map(function (c) { return claimRow(c, true); }).join('') + '</div>'
        : LF.emptyState({
            icon: 'i-message',
            title: 'Nobody has claimed your posts yet',
            message: 'When someone recognises something you posted, their message appears here.'
          });
    }

    host.innerHTML = panels;
  }

  function paintCounts() {
    var open = (state.incoming || []).filter(function (c) { return c.status === 'OPEN' || c.status === 'APPROVED'; }).length;
    var map = {
      posts: state.items.length,
      claims: state.claims.length,
      incoming: (state.incoming || []).length
    };
    Object.keys(map).forEach(function (key) {
      var el = document.querySelector('[data-count="' + key + '"]');
      if (el) el.textContent = map[key];
    });

    var badge = document.querySelector('[data-incoming-badge]');
    if (badge) {
      badge.textContent = open;
      badge.hidden = open === 0;
    }
  }

  /* ------------------------------------------------------------------
     Tabs — the full ARIA pattern: roving tabindex plus arrow keys.
     ------------------------------------------------------------------ */

  function initTabs() {
    var tabs = Array.prototype.slice.call(document.querySelectorAll('[role="tab"]'));

    function select(tab, focus) {
      tabs.forEach(function (t) {
        var on = t === tab;
        t.setAttribute('aria-selected', on ? 'true' : 'false');
        t.tabIndex = on ? 0 : -1;
      });
      state.tab = tab.dataset.tab;
      render();
      if (focus) tab.focus();
    }

    tabs.forEach(function (tab, i) {
      tab.tabIndex = tab.getAttribute('aria-selected') === 'true' ? 0 : -1;
      tab.addEventListener('click', function () { select(tab, false); });
      tab.addEventListener('keydown', function (ev) {
        var next = null;
        if (ev.key === 'ArrowRight') next = tabs[(i + 1) % tabs.length];
        else if (ev.key === 'ArrowLeft') next = tabs[(i - 1 + tabs.length) % tabs.length];
        else if (ev.key === 'Home') next = tabs[0];
        else if (ev.key === 'End') next = tabs[tabs.length - 1];
        if (!next) return;
        ev.preventDefault();
        select(next, true);
      });
    });
  }

  function initReset() {
    var button = document.querySelector('[data-forget]');
    if (!button) return;
    button.addEventListener('click', function () {
      if (!window.confirm('Clear local suggestions? Your account posts and conversations remain available.')) {
        return;
      }
      LF.mine.clear();
      load();
      LF.toast.info('Local suggestions were cleared. Your account records are still available.');
    });
  }

  document.addEventListener('lf:ready', function () {
    if (!LF.auth.require()) return;
    initTabs();
    initReset();

    var mine = LF.mine.all();
    var greeting = document.querySelector('[data-greeting]');
    if (greeting && mine.name) greeting.textContent = 'Hello, ' + mine.name;

    load();
  });
  document.addEventListener('lf:auth-change', function (event) {
    if (!event.detail.user) {
      state.items = [];
      state.claims = [];
      state.incoming = [];
      renderEmpty();
    }
  });
})();
