/**
 * modal.js — dialogs and drawers.
 *
 * Modals are real <dialog> elements opened with showModal(), so the browser
 * supplies the top layer, the backdrop, inert background content, and ESC.
 * We add the focus trap explicitly rather than trusting it, because engines
 * still differ on where focus lands and on what Shift+Tab does at the edges.
 *
 * Drawers are not <dialog> — they are non-blocking side panels — so they get
 * the full manual treatment: scrim, trap, ESC, focus restore.
 */
(function (global) {
  'use strict';

  var FOCUSABLE = [
    'a[href]',
    'button:not([disabled])',
    'input:not([disabled]):not([type="hidden"])',
    'select:not([disabled])',
    'textarea:not([disabled])',
    'details > summary',
    '[tabindex]:not([tabindex="-1"])'
  ].join(',');

  function focusableIn(root) {
    return Array.prototype.filter.call(root.querySelectorAll(FOCUSABLE), function (el) {
      return el.offsetParent !== null || el.getClientRects().length > 0;
    });
  }

  /* Focus is moved synchronously, not inside requestAnimationFrame: rAF does
     not fire in a backgrounded tab, which would leave an open dialog with
     focus still outside it. preventScroll stops the browser scrolling the
     page underneath while the panel animates in.

     This only works because the panels flip to visibility:visible on the same
     frame they open — .focus() is a silent no-op on a hidden element. See the
     transition note on .drawer in css/components/overlay.css. */
  function focusWithoutScroll(el) {
    if (!el) return;
    try {
      el.focus({ preventScroll: true });
    } catch (err) {
      el.focus();
    }
  }

  function trapTab(root, event) {
    if (event.key !== 'Tab') return;
    var items = focusableIn(root);
    if (!items.length) {
      event.preventDefault();
      return;
    }
    var first = items[0];
    var last = items[items.length - 1];
    var active = document.activeElement;

    if (event.shiftKey && (active === first || !root.contains(active))) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && active === last) {
      event.preventDefault();
      first.focus();
    }
  }

  /* ---------------------------------------------------------------------
     Modal
     --------------------------------------------------------------------- */

  function Modal(el) {
    this.el = el;
    this.opener = null;
    this._onKeydown = this._onKeydown.bind(this);
    this._onClick = this._onClick.bind(this);
    this._onClose = this._onClose.bind(this);

    el.addEventListener('keydown', this._onKeydown);
    el.addEventListener('click', this._onClick);
    el.addEventListener('close', this._onClose);

    Array.prototype.forEach.call(el.querySelectorAll('[data-modal-close]'), function (btn) {
      btn.addEventListener('click', function () {
        el.close('dismiss');
      });
    });
  }

  Modal.prototype.open = function (opener) {
    this.opener = opener || document.activeElement;

    if (typeof this.el.showModal === 'function') {
      this.el.showModal();
    } else {
      /* No <dialog> support: fall back to an ARIA modal. */
      this.el.setAttribute('open', '');
      this.el.setAttribute('role', 'dialog');
      this.el.setAttribute('aria-modal', 'true');
    }

    document.documentElement.style.overflow = 'hidden';

    var target =
      this.el.querySelector('[autofocus]') ||
      this.el.querySelector('.modal__body ' + FOCUSABLE) ||
      focusableIn(this.el)[0] ||
      this.el;

    focusWithoutScroll(target);
  };

  Modal.prototype.close = function (reason) {
    if (typeof this.el.close === 'function') this.el.close(reason || 'dismiss');
    else this._onClose();
  };

  Modal.prototype._onClose = function () {
    this.el.removeAttribute('open');
    document.documentElement.style.overflow = '';
    if (this.opener && document.contains(this.opener)) this.opener.focus();
    this.opener = null;
  };

  Modal.prototype._onKeydown = function (event) {
    trapTab(this.el, event);
  };

  /* Clicking the backdrop closes. The backdrop is the dialog element itself
     outside its inner box, so we test the point rather than the target. */
  Modal.prototype._onClick = function (event) {
    if (event.target !== this.el) return;
    var box = this.el.getBoundingClientRect();
    var inside =
      event.clientX >= box.left &&
      event.clientX <= box.right &&
      event.clientY >= box.top &&
      event.clientY <= box.bottom;
    if (!inside) this.el.close('backdrop');
  };

  /* ---------------------------------------------------------------------
     Drawer
     --------------------------------------------------------------------- */

  function Drawer(el) {
    this.el = el;
    this.opener = null;
    this.scrim = document.querySelector('[data-scrim="' + el.id + '"]');

    if (!this.scrim) {
      this.scrim = document.createElement('div');
      this.scrim.className = 'scrim';
      this.scrim.setAttribute('data-scrim', el.id);
      this.scrim.hidden = false;
      document.body.appendChild(this.scrim);
    }

    this._onKeydown = this._onKeydown.bind(this);
    this.close = this.close.bind(this);

    this.scrim.addEventListener('click', this.close);
    Array.prototype.forEach.call(el.querySelectorAll('[data-drawer-close]'), function (btn) {
      btn.addEventListener('click', this.close);
    }, this);

    el.setAttribute('role', 'dialog');
    el.setAttribute('aria-modal', 'true');
    el.setAttribute('tabindex', '-1');
  }

  Drawer.prototype.open = function (opener) {
    this.opener = opener || document.activeElement;
    this.el.dataset.open = 'true';
    this.scrim.dataset.open = 'true';
    document.documentElement.style.overflow = 'hidden';
    document.addEventListener('keydown', this._onKeydown);

    focusWithoutScroll(focusableIn(this.el)[0] || this.el);
  };

  Drawer.prototype.close = function () {
    if (this.el.dataset.open !== 'true') return;
    this.el.dataset.open = 'false';
    this.scrim.dataset.open = 'false';
    document.documentElement.style.overflow = '';
    document.removeEventListener('keydown', this._onKeydown);
    if (this.opener && document.contains(this.opener)) this.opener.focus();
    this.opener = null;
  };

  Drawer.prototype._onKeydown = function (event) {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.close();
      return;
    }
    trapTab(this.el, event);
  };

  /* ---------------------------------------------------------------------
     Registry + declarative wiring
     --------------------------------------------------------------------- */

  var registry = {};

  function get(id) {
    if (registry[id]) return registry[id];
    var el = document.getElementById(id);
    if (!el) return null;
    registry[id] = el.classList.contains('drawer') ? new Drawer(el) : new Modal(el);
    return registry[id];
  }

  function init(root) {
    root = root || document;

    /* <button data-opens="dialog-id"> opens it. */
    Array.prototype.forEach.call(root.querySelectorAll('[data-opens]'), function (btn) {
      if (btn.dataset.opensBound === 'true') return;
      btn.dataset.opensBound = 'true';
      btn.addEventListener('click', function () {
        var instance = get(btn.dataset.opens);
        if (instance) instance.open(btn);
      });
    });
  }

  global.LF = global.LF || {};
  global.LF.overlay = { init: init, get: get, Modal: Modal, Drawer: Drawer, focusableIn: focusableIn };
})(window);
