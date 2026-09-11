/**
 * home.js — landing page: live board counts and the six most recent posts.
 *
 * A failed request and an empty board are reported differently. Conflating
 * them is how a broken backend ends up looking like a quiet campus.
 */
(function () {
  'use strict';

  var LF = window.LF;

  function setApiStatus(state, label) {
    var dot = document.querySelector('[data-api-dot]');
    var text = document.querySelector('[data-api-label]');
    if (!dot || !text) return;

    dot.className = 'inline-block h-2 w-2 rounded-full ' +
      (state === 'up' ? 'bg-found' : state === 'down' ? 'bg-lost' : 'bg-faint');
    text.textContent = label;
  }

  /** Counts up to the real figure, so the strip feels live without lying. */
  function countTo(el, target) {
    var reduced = window.matchMedia &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    if (reduced || target <= 0) {
      el.textContent = String(target);
      return;
    }

    var start = performance.now();
    var duration = 700;
    function frame(now) {
      var t = Math.min(1, (now - start) / duration);
      /* ease-out cubic, matching the --ease-brand feel */
      var eased = 1 - Math.pow(1 - t, 3);
      el.textContent = String(Math.round(target * eased));
      if (t < 1) requestAnimationFrame(frame);
    }
    requestAnimationFrame(frame);
  }

  function loadStats() {
    return LF.api.get('/api/items/stats').then(function (stats) {
      var map = {
        open: stats.open,
        resolved: stats.resolved,
        found: stats.found,
        lost: stats.lost
      };
      Object.keys(map).forEach(function (key) {
        var el = document.querySelector('[data-stat="' + key + '"]');
        if (el) countTo(el, map[key]);
      });
      return stats;
    });
  }

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

  function load() {
    setApiStatus('checking', 'Checking the registry…');

    Promise.all([loadStats(), loadRecent()])
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

  document.addEventListener('lf:ready', load);
})();
