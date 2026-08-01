/**
 * toast.js — transient notifications.
 *
 * One live region for the whole page. The region is aria-live, individual
 * toasts are not, so a burst announces in order instead of interrupting.
 * Errors use role="alert" on the region clone to jump the queue.
 *
 * window.LF.toast(message, options)
 */
(function (global) {
  'use strict';

  var region = null;
  var seq = 0;

  function ensureRegion() {
    if (region && document.body.contains(region)) return region;
    region = document.querySelector('.toast-region');
    if (!region) {
      region = document.createElement('div');
      region.className = 'toast-region';
      region.setAttribute('role', 'status');
      region.setAttribute('aria-live', 'polite');
      region.setAttribute('aria-atomic', 'false');
      document.body.appendChild(region);
    }
    return region;
  }

  var GLYPH = {
    success: 'i-check',
    error: 'i-alert',
    info: 'i-info'
  };

  function iconMarkup(kind) {
    var id = GLYPH[kind] || GLYPH.info;
    return (
      '<svg class="icon toast__icon" aria-hidden="true" focusable="false">' +
      '<use href="' + (global.LF.spritePath || '') + '#' + id + '"></use></svg>'
    );
  }

  /**
   * @param {string} message
   * @param {Object} [opts]  { kind: 'info'|'success'|'error', title, duration, action:{label,onClick} }
   */
  function toast(message, opts) {
    opts = opts || {};
    var kind = opts.kind || 'info';
    var host = ensureRegion();

    /* Errors must not wait behind a queue of polite messages. */
    host.setAttribute('aria-live', kind === 'error' ? 'assertive' : 'polite');

    var el = document.createElement('div');
    el.className = 'toast toast--' + kind;
    el.dataset.toastId = String(++seq);

    var body = '<div class="toast__body">';
    if (opts.title) {
      body += '<p class="toast__title">' + escapeHtml(opts.title) + '</p>';
      body += '<p class="toast__desc">' + escapeHtml(message) + '</p>';
    } else {
      body += '<p class="toast__title">' + escapeHtml(message) + '</p>';
    }
    body += '</div>';

    el.innerHTML = iconMarkup(kind) + body;

    if (opts.action && opts.action.label) {
      var actionBtn = document.createElement('button');
      actionBtn.type = 'button';
      actionBtn.className = 'btn btn--sm btn--ghost';
      actionBtn.textContent = opts.action.label;
      actionBtn.addEventListener('click', function () {
        if (typeof opts.action.onClick === 'function') opts.action.onClick();
        dismiss(el);
      });
      el.appendChild(actionBtn);
    }

    var closeBtn = document.createElement('button');
    closeBtn.type = 'button';
    closeBtn.className = 'btn btn--icon btn--sm btn--ghost';
    closeBtn.setAttribute('aria-label', 'Dismiss notification');
    closeBtn.innerHTML =
      '<svg class="icon icon--sm" aria-hidden="true" focusable="false">' +
      '<use href="' + (global.LF.spritePath || '') + '#i-close"></use></svg>';
    closeBtn.addEventListener('click', function () {
      dismiss(el);
    });
    el.appendChild(closeBtn);

    host.appendChild(el);

    var duration = opts.duration === undefined ? (kind === 'error' ? 8000 : 5000) : opts.duration;
    var timer = null;

    function startTimer() {
      if (duration > 0) timer = global.setTimeout(function () { dismiss(el); }, duration);
    }
    function stopTimer() {
      if (timer) global.clearTimeout(timer);
      timer = null;
    }

    /* Pointer or keyboard focus inside the toast pauses its countdown, so a
       user reading or tabbing to the action never loses it mid-reach. */
    el.addEventListener('mouseenter', stopTimer);
    el.addEventListener('mouseleave', startTimer);
    el.addEventListener('focusin', stopTimer);
    el.addEventListener('focusout', startTimer);

    startTimer();

    return { element: el, dismiss: function () { dismiss(el); } };
  }

  function dismiss(el) {
    if (!el || el.dataset.leaving === 'true') return;
    el.dataset.leaving = 'true';

    var reduced = global.matchMedia && global.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduced) {
      el.remove();
      return;
    }

    var done = false;
    function remove() {
      if (done) return;
      done = true;
      el.remove();
    }
    el.addEventListener('animationend', remove);
    /* Guard against a dropped animationend (background tab, etc.). */
    global.setTimeout(remove, 600);
  }

  function escapeHtml(str) {
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  toast.success = function (msg, opts) {
    return toast(msg, Object.assign({}, opts, { kind: 'success' }));
  };
  toast.error = function (msg, opts) {
    return toast(msg, Object.assign({}, opts, { kind: 'error' }));
  };
  toast.info = function (msg, opts) {
    return toast(msg, Object.assign({}, opts, { kind: 'info' }));
  };

  global.LF = global.LF || {};
  global.LF.toast = toast;
})(window);
