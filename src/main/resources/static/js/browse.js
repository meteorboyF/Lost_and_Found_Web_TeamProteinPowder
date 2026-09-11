/**
 * browse.js — the board, with faceted filtering, keyword search, sort, paging.
 *
 * Filter state lives in the URL query string, so a filtered board is a
 * shareable link and the back button behaves the way people expect.
 */
(function () {
  'use strict';

  var LF = window.LF;

  var results = document.querySelector('[data-results]');
  var pagerHost = document.querySelector('[data-pager]');
  var countEl = document.querySelector('[data-count]');
  var form = document.querySelector('[data-filters]');
  var searchInput = document.getElementById('f-q');
  var sortSelect = document.getElementById('f-sort');
  var categoryHost = document.querySelector('[data-categories]');

  var state = { q: '', kind: '', category: '', status: '', sort: 'recent', page: 0 };
  var requestSeq = 0;

  /* ------------------------------------------------------------------
     URL <-> state
     ------------------------------------------------------------------ */

  function readUrl() {
    var p = new URLSearchParams(location.search);
    state.q = p.get('q') || '';
    state.kind = p.get('kind') || '';
    state.category = p.get('category') || '';
    state.status = p.get('status') || '';
    state.sort = p.get('sort') || 'recent';
    state.page = Math.max(0, parseInt(p.get('page'), 10) || 0);
  }

  function writeUrl(replace) {
    var p = new URLSearchParams();
    if (state.q) p.set('q', state.q);
    if (state.kind) p.set('kind', state.kind);
    if (state.category) p.set('category', state.category);
    if (state.status) p.set('status', state.status);
    if (state.sort && state.sort !== 'recent') p.set('sort', state.sort);
    if (state.page) p.set('page', state.page);

    var url = location.pathname + (p.toString() ? '?' + p : '');
    if (replace) history.replaceState(null, '', url);
    else history.pushState(null, '', url);
  }

  function syncControls() {
    if (searchInput) searchInput.value = state.q;
    if (sortSelect) sortSelect.value = state.sort;

    form.querySelectorAll('[data-kind]').forEach(function (btn) {
      btn.setAttribute('aria-pressed', btn.dataset.kind === state.kind ? 'true' : 'false');
    });
    form.querySelectorAll('input[name="status"]').forEach(function (radio) {
      radio.checked = radio.value === state.status;
    });
    form.querySelectorAll('input[name="category"]').forEach(function (radio) {
      radio.checked = radio.value === state.category;
    });
  }

  /* ------------------------------------------------------------------
     Fetch + render
     ------------------------------------------------------------------ */

  function load() {
    var seq = ++requestSeq;

    results.innerHTML = LF.skeletonGrid(6);
    pagerHost.innerHTML = '';
    countEl.textContent = 'Searching…';

    var p = new URLSearchParams();
    if (state.q) p.set('q', state.q);
    if (state.kind) p.set('kind', state.kind);
    if (state.category) p.set('category', state.category);
    if (state.status) p.set('status', state.status);
    p.set('sort', state.sort);
    p.set('page', state.page);
    p.set('size', '12');

    LF.api
      .get('/api/items?' + p)
      .then(function (page) {
        /* A slower earlier request must not overwrite a newer result. */
        if (seq !== requestSeq) return;

        if (!page.content.length) {
          countEl.textContent = 'No items match';
          results.innerHTML = LF.emptyState({
            title: hasFilters() ? 'Nothing matches those filters' : 'The board is empty',
            message: hasFilters()
              ? 'Try removing a filter, or search a broader word — most things are handed in somewhere other than where they were lost.'
              : 'Nothing has been posted yet. Be the first.',
            actionAttr: hasFilters() ? 'data-clear-empty' : null,
            actionLabel: hasFilters() ? 'Clear filters' : 'Post an item',
            actionHref: hasFilters() ? null : '/report.html'
          });
          var clearBtn = results.querySelector('[data-clear-empty]');
          if (clearBtn) clearBtn.addEventListener('click', clearAll);
          return;
        }

        countEl.innerHTML =
          '<strong class="font-mono text-primary">' + page.totalElements + '</strong> ' +
          (page.totalElements === 1 ? 'item' : 'items') +
          (hasFilters() ? ' match your filters' : ' on the board');

        results.innerHTML = LF.itemGrid(page.content);
        pagerHost.innerHTML = LF.pager(page);

        pagerHost.querySelectorAll('[data-page]').forEach(function (btn) {
          btn.addEventListener('click', function () {
            state.page = parseInt(btn.dataset.page, 10);
            writeUrl(false);
            load();
            window.scrollTo({ top: 0, behavior: 'smooth' });
          });
        });
      })
      .catch(function (err) {
        if (seq !== requestSeq) return;
        countEl.textContent = 'Could not load the board';
        results.innerHTML = LF.errorState(err.message);
        var retry = results.querySelector('[data-retry]');
        if (retry) retry.addEventListener('click', load);
      });
  }

  function hasFilters() {
    return Boolean(state.q || state.kind || state.category || state.status);
  }

  function apply(resetPage) {
    if (resetPage !== false) state.page = 0;
    writeUrl(false);
    syncControls();
    load();
  }

  function clearAll() {
    state.q = '';
    state.kind = '';
    state.category = '';
    state.status = '';
    state.page = 0;
    apply();
  }

  /* ------------------------------------------------------------------
     Wiring
     ------------------------------------------------------------------ */

  function initCategories() {
    return LF.api.get('/api/items/categories').then(function (categories) {
      var html =
        '<label class="flex cursor-pointer items-center gap-2.5 py-1 text-sm">' +
        '<input type="radio" name="category" value="" class="h-4 w-4 accent-[var(--sc-found)]">Any</label>';

      html += categories
        .map(function (c) {
          return (
            '<label class="flex cursor-pointer items-center gap-2.5 py-1 text-sm">' +
            '<input type="radio" name="category" value="' + LF.escapeHtml(c.value) +
            '" class="h-4 w-4 accent-[var(--sc-found)]">' + LF.escapeHtml(c.label) + '</label>'
          );
        })
        .join('');

      categoryHost.innerHTML = html;
      categoryHost.querySelectorAll('input[name="category"]').forEach(function (radio) {
        radio.addEventListener('change', function () {
          state.category = radio.value;
          apply();
        });
      });
      syncControls();
    });
  }

  function initFilters() {
    /* Debounced so typing does not fire a request per keystroke. */
    var timer = null;
    searchInput.addEventListener('input', function () {
      clearTimeout(timer);
      timer = setTimeout(function () {
        state.q = searchInput.value.trim();
        apply();
      }, 300);
    });

    form.addEventListener('submit', function (ev) {
      ev.preventDefault();
      clearTimeout(timer);
      state.q = searchInput.value.trim();
      apply();
    });

    form.querySelectorAll('[data-kind]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        state.kind = btn.dataset.kind;
        apply();
      });
    });

    form.querySelectorAll('input[name="status"]').forEach(function (radio) {
      radio.addEventListener('change', function () {
        state.status = radio.value;
        apply();
      });
    });

    sortSelect.addEventListener('change', function () {
      state.sort = sortSelect.value;
      apply();
    });

    form.querySelector('[data-clear]').addEventListener('click', clearAll);

    window.addEventListener('popstate', function () {
      readUrl();
      syncControls();
      load();
    });
  }

  document.addEventListener('lf:ready', function () {
    readUrl();
    syncControls();
    initFilters();
    initCategories();
    load();
  });
})();
