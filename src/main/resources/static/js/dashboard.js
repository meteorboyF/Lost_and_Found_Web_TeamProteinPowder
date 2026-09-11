/**
 * dashboard.js — everything this browser has posted or claimed.
 *
 * Without sign-in there is no server-side "me", so the page resolves the
 * references held in localStorage. That is stated plainly in the UI rather
 * than pretending to be an account.
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
    var mine = LF.mine.all();
    var host = document.querySelector('[data-dash]');

    if (!mine.items.length && !mine.claims.length) {
      renderEmpty();
      return;
    }

    host.innerHTML = LF.skeletonGrid(3);

    /* One request per collection, not one per reference. */
    var itemReqs = mine.items.map(function (ref) {
      return LF.api.get('/api/items/' + encodeURIComponent(ref)).catch(function () {
        return null;   // a deleted post must not break the whole page
      });
    });

    var claimReq = mine.claims.length
      ? LF.api.get('/api/claims?refs=' + encodeURIComponent(mine.claims.join(','))).catch(function () { return []; })
      : Promise.resolve([]);

    /* Claims other people have opened on this browser's posts. */
    var incomingReqs = mine.items.map(function (ref) {
      return LF.api.get('/api/items/' + encodeURIComponent(ref) + '/claims').catch(function () { return []; });
    });

    Promise.all([Promise.all(itemReqs), claimReq, Promise.all(incomingReqs)])
      .then(function (results) {
        state.items = results[0].filter(Boolean);
        state.claims = results[1];
        state.incoming = results[2].flat();
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
      message: 'Anything you post or claim from this browser shows up here. ' +
               'Because there are no accounts yet, this list lives on this device.',
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
          e(claim.status.charAt(0) + claim.status.slice(1).toLowerCase()) + '</span>' +
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
    var open = (state.incoming || []).filter(function (c) { return c.status === 'OPEN'; }).length;
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
      if (!window.confirm('Forget the posts and claims remembered on this device? The posts themselves stay on the board.')) {
        return;
      }
      LF.mine.clear();
      state.items = [];
      state.claims = [];
      state.incoming = [];
      renderEmpty();
      LF.toast.info('This device no longer remembers your posts and claims.');
    });
  }

  document.addEventListener('lf:ready', function () {
    initTabs();
    initReset();

    var mine = LF.mine.all();
    var greeting = document.querySelector('[data-greeting]');
    if (greeting && mine.name) greeting.textContent = 'Hello, ' + mine.name;

    load();
  });
})();
