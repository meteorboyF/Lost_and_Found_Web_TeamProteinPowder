/**
 * home.js — landing page: live counts, category tiles, the floating hero
 * stage, and the newest posts.
 */
(function () {
  'use strict';

  var LF = window.LF;
  var e = LF.escapeHtml;

  function setApiStatus(state, label) {
    var dot = document.querySelector('[data-api-dot]');
    var text = document.querySelector('[data-api-label]');
    if (!dot || !text) return;
    dot.className = 'inline-block h-1.5 w-1.5 rounded-full ' +
      (state === 'up' ? 'bg-found' : state === 'down' ? 'bg-lost' : 'bg-faint');
    text.textContent = label;
  }

  /** Counts up to the real figure — live feel without inventing a number. */
  function countTo(el, target) {
    var reduced = window.matchMedia &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduced || target <= 0) {
      el.textContent = String(target);
      return;
    }
    var start = performance.now();
    (function frame(now) {
      var t = Math.min(1, ((now || start) - start) / 700);
      el.textContent = String(Math.round(target * (1 - Math.pow(1 - t, 3))));
      if (t < 1) requestAnimationFrame(frame);
    })(start);
  }

  function loadStats() {
    return LF.api.get('/api/items/stats').then(function (stats) {
      ['open', 'resolved', 'found', 'lost'].forEach(function (key) {
        var el = document.querySelector('[data-stat="' + key + '"]');
        if (el) countTo(el, stats[key]);
      });
    });
  }

  /* ------------------------------------------------------------------
     Category tiles
     ------------------------------------------------------------------ */

  function loadCategories() {
    var host = document.querySelector('[data-category-tiles]');
    if (!host) return Promise.resolve();

    return LF.api.get('/api/items/categories').then(function (categories) {
      host.innerHTML = categories
        .slice(0, 8)
        .map(function (c) {
          var cat = LF.category(c.value);
          return (
            '<a href="/browse.html?category=' + encodeURIComponent(c.value) + '" ' +
            'class="card card-interactive flex flex-col items-start gap-3 p-4">' +
            '<span class="grid h-10 w-10 place-items-center rounded-xl ' + cat.tint + '">' +
            '<svg class="icon h-5 w-5" aria-hidden="true"><use href="/assets/icons.svg#' + cat.icon + '"></use></svg>' +
            '</span>' +
            '<span class="text-sm font-semibold text-heading">' + e(c.label) + '</span>' +
            '</a>'
          );
        })
        .join('');
    });
  }

  /* ------------------------------------------------------------------
     Recent
     ------------------------------------------------------------------ */

  function loadRecent() {
    var host = document.querySelector('[data-recent]');
    if (!host) return Promise.resolve();

    host.innerHTML = LF.skeletonGrid(3);

    return LF.api.get('/api/items?size=6&sort=recent').then(function (page) {
      if (!page.content.length) {
        host.innerHTML = LF.emptyState({
          title: 'Nothing on the board yet',
          message: host.dataset.emptyMessage || '',
          actionHref: '/report.html',
          actionLabel: 'Post the first item'
        });
        return;
      }
      host.innerHTML = LF.itemGrid(page.content);
    });
  }

  /* ------------------------------------------------------------------
     Hero tilt. The stage leans toward the pointer and each object shifts
     by its own depth, which is what turns flat art into a diorama.
     Touch gets the float animation only; reduced motion gets stillness.
     ------------------------------------------------------------------ */

  function initHeroTilt() {
    var stage = document.querySelector('[data-hero-stage]');
    var tilt = document.querySelector('[data-hero-tilt]');
    if (!stage || !tilt || !window.matchMedia) return;
    if (matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    if (matchMedia('(pointer: coarse)').matches) return;

    var objects = Array.prototype.slice.call(stage.querySelectorAll('[data-depth]'));

    stage.addEventListener('pointermove', function (ev) {
      var rect = stage.getBoundingClientRect();
      var nx = ((ev.clientX - rect.left) / rect.width) * 2 - 1;
      var ny = ((ev.clientY - rect.top) / rect.height) * 2 - 1;

      tilt.style.setProperty('--tilt-x', (nx * 7).toFixed(2) + 'deg');
      tilt.style.setProperty('--tilt-y', (-ny * 7).toFixed(2) + 'deg');

      objects.forEach(function (el) {
        var depth = Number(el.dataset.depth) || 0;
        el.style.setProperty('--px', (nx * depth * 0.6).toFixed(1) + 'px');
        el.style.setProperty('--py', (ny * depth * 0.6).toFixed(1) + 'px');
      });
    });

    stage.addEventListener('pointerleave', function () {
      tilt.style.setProperty('--tilt-x', '0deg');
      tilt.style.setProperty('--tilt-y', '0deg');
      objects.forEach(function (el) {
        el.style.setProperty('--px', '0px');
        el.style.setProperty('--py', '0px');
      });
    });
  }

  function load() {
    setApiStatus('checking', 'Checking the registry…');

    Promise.all([loadStats(), loadCategories(), loadRecent()])
      .then(function () {
        setApiStatus('up', 'Registry online');
      })
      .catch(function (err) {
        setApiStatus('down', 'Registry unreachable');
        var host = document.querySelector('[data-recent]');
        if (host) {
          host.innerHTML = LF.errorState(err.message);
          var retry = host.querySelector('[data-retry]');
          if (retry) retry.addEventListener('click', load);
        }
        document.querySelectorAll('[data-stat]').forEach(function (el) {
          el.textContent = '—';
        });
      });
  }

  document.addEventListener('lf:ready', function () {
    initHeroTilt();
    load();
  });
})();
