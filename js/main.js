/**
 * main.js — boot. Runs on every page.
 *
 * Wires the chrome (theme, language, navigation), resolves the icon sprite
 * path for the current directory depth, and initialises any generic
 * behaviours found in the document. Page-specific logic lives in its own
 * file and hooks in through LF.ready().
 */
(function (global) {
  'use strict';

  var LF = (global.LF = global.LF || {});

  /* ---------------------------------------------------------------------
     Path resolution. Pages live at three depths (/, /pages/, /pages/admin/)
     so every shared asset URL is derived once, here, from this script's own
     src rather than hard-coded per page.
     --------------------------------------------------------------------- */

  LF.root = (function () {
    var scripts = document.getElementsByTagName('script');
    for (var i = scripts.length - 1; i >= 0; i--) {
      var src = scripts[i].src || '';
      var at = src.lastIndexOf('/js/');
      if (at !== -1) return src.slice(0, at + 1);
    }
    return '';
  })();

  LF.spritePath = LF.root + 'assets/icons.svg';

  /* External <use> references resolve over HTTP but are blocked on file://.
     Flag it so CSS can fall back to text labels on icon-only controls. */
  if (global.location.protocol === 'file:') {
    document.documentElement.setAttribute('data-no-sprite', 'true');
  }

  /* Rewrite sprite references authored as href="#i-x" so they point at the
     external file. Authoring stays short; resolution stays correct at depth. */
  function resolveSprite(root) {
    var uses = (root || document).querySelectorAll('use[href^="#"]');
    Array.prototype.forEach.call(uses, function (use) {
      use.setAttribute('href', LF.spritePath + use.getAttribute('href'));
    });
  }

  /* ---------------------------------------------------------------------
     Theme
     --------------------------------------------------------------------- */

  var THEME_KEY = 'theme';

  function systemTheme() {
    return global.matchMedia && global.matchMedia('(prefers-color-scheme: dark)').matches
      ? 'dark'
      : 'light';
  }

  function currentTheme() {
    return document.documentElement.getAttribute('data-theme') || systemTheme();
  }

  function applyTheme(theme) {
    if (theme === 'system') {
      document.documentElement.removeAttribute('data-theme');
      LF.store.remove(THEME_KEY);
    } else {
      document.documentElement.setAttribute('data-theme', theme);
      LF.store.set(THEME_KEY, theme);
    }
    syncThemeControls();
  }

  function syncThemeControls() {
    var theme = currentTheme();
    Array.prototype.forEach.call(document.querySelectorAll('[data-theme-toggle]'), function (btn) {
      var next = theme === 'dark' ? 'light' : 'dark';
      btn.setAttribute('aria-label', 'Switch to ' + next + ' theme');
      btn.setAttribute('aria-pressed', theme === 'dark' ? 'true' : 'false');
      var sun = btn.querySelector('[data-theme-icon="light"]');
      var moon = btn.querySelector('[data-theme-icon="dark"]');
      if (sun) sun.hidden = theme === 'dark';
      if (moon) moon.hidden = theme !== 'dark';
    });
    Array.prototype.forEach.call(document.querySelectorAll('[data-theme-set]'), function (input) {
      var stored = LF.store.get(THEME_KEY, 'system');
      input.checked = input.value === (stored || 'system');
    });
  }

  function initTheme() {
    Array.prototype.forEach.call(document.querySelectorAll('[data-theme-toggle]'), function (btn) {
      btn.addEventListener('click', function () {
        applyTheme(currentTheme() === 'dark' ? 'light' : 'dark');
      });
    });

    Array.prototype.forEach.call(document.querySelectorAll('[data-theme-set]'), function (input) {
      input.addEventListener('change', function () {
        if (input.checked) applyTheme(input.value);
      });
    });

    /* Follow the OS only while the user has not made an explicit choice. */
    if (global.matchMedia) {
      var mq = global.matchMedia('(prefers-color-scheme: dark)');
      var onChange = function () {
        if (!LF.store.get(THEME_KEY)) syncThemeControls();
      };
      if (mq.addEventListener) mq.addEventListener('change', onChange);
      else if (mq.addListener) mq.addListener(onChange);
    }

    syncThemeControls();
  }

  LF.theme = { apply: applyTheme, current: currentTheme };

  /* ---------------------------------------------------------------------
     Navigation
     --------------------------------------------------------------------- */

  function initNav() {
    var toggle = document.querySelector('[data-nav-toggle]');
    var nav = document.getElementById('primary-nav');
    if (!toggle || !nav) return;

    function setOpen(open) {
      nav.dataset.open = open ? 'true' : 'false';
      toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
      toggle.setAttribute('aria-label', open ? 'Close navigation' : 'Open navigation');
    }

    toggle.addEventListener('click', function () {
      setOpen(nav.dataset.open !== 'true');
    });

    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && nav.dataset.open === 'true') {
        setOpen(false);
        toggle.focus();
      }
    });

    /* Close when the viewport grows past the drawer breakpoint, so the nav
       does not stay stuck open as a floating panel. */
    if (global.matchMedia) {
      var mq = global.matchMedia('(min-width: 64em)');
      var onChange = function (e) {
        if (e.matches) setOpen(false);
      };
      if (mq.addEventListener) mq.addEventListener('change', onChange);
    }

    setOpen(false);
  }

  /* ---------------------------------------------------------------------
     Tabs — roving tabindex, arrow-key navigation per the ARIA pattern.
     --------------------------------------------------------------------- */

  function initTabs(root) {
    var lists = (root || document).querySelectorAll('[role="tablist"]');

    Array.prototype.forEach.call(lists, function (list) {
      if (list.dataset.bound === 'true') return;
      list.dataset.bound = 'true';

      var tabs = Array.prototype.slice.call(list.querySelectorAll('[role="tab"]'));

      function select(tab, focus) {
        tabs.forEach(function (t) {
          var selected = t === tab;
          t.setAttribute('aria-selected', selected ? 'true' : 'false');
          t.tabIndex = selected ? 0 : -1;
          var panel = document.getElementById(t.getAttribute('aria-controls'));
          if (panel) panel.hidden = !selected;
        });
        if (focus) tab.focus();
        list.dispatchEvent(new CustomEvent('tabchange', { detail: { tab: tab }, bubbles: true }));
      }

      tabs.forEach(function (tab, index) {
        tab.tabIndex = tab.getAttribute('aria-selected') === 'true' ? 0 : -1;

        tab.addEventListener('click', function () {
          select(tab, false);
        });

        tab.addEventListener('keydown', function (event) {
          var next = null;
          if (event.key === 'ArrowRight') next = tabs[(index + 1) % tabs.length];
          else if (event.key === 'ArrowLeft') next = tabs[(index - 1 + tabs.length) % tabs.length];
          else if (event.key === 'Home') next = tabs[0];
          else if (event.key === 'End') next = tabs[tabs.length - 1];
          if (!next) return;
          event.preventDefault();
          select(next, true);
        });
      });
    });
  }

  /* ---------------------------------------------------------------------
     Popovers — click to open, ESC and outside-click to close.
     --------------------------------------------------------------------- */

  function initPopovers(root) {
    var triggers = (root || document).querySelectorAll('[data-popover]');

    Array.prototype.forEach.call(triggers, function (trigger) {
      if (trigger.dataset.bound === 'true') return;
      trigger.dataset.bound = 'true';

      var panel = document.getElementById(trigger.getAttribute('aria-controls'));
      if (!panel) return;

      function setOpen(open) {
        panel.hidden = !open;
        trigger.setAttribute('aria-expanded', open ? 'true' : 'false');
      }

      trigger.addEventListener('click', function (event) {
        event.stopPropagation();
        setOpen(panel.hidden);
      });

      document.addEventListener('click', function (event) {
        if (panel.hidden) return;
        if (!panel.contains(event.target) && event.target !== trigger) setOpen(false);
      });

      document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape' && !panel.hidden) {
          setOpen(false);
          trigger.focus();
        }
      });

      setOpen(false);
    });
  }

  /* ---------------------------------------------------------------------
     Reveal-on-scroll. Used by the animated SVG charts and the timeline.
     Adds data-revealed="true" once; never removes it, so content does not
     flicker on scroll-back. Reduced motion opts out entirely.
     --------------------------------------------------------------------- */

  function initReveal(root) {
    var targets = (root || document).querySelectorAll('[data-reveal]');
    if (!targets.length) return;

    var reduced = global.matchMedia && global.matchMedia('(prefers-reduced-motion: reduce)').matches;

    if (reduced || !('IntersectionObserver' in global)) {
      Array.prototype.forEach.call(targets, function (el) {
        el.dataset.revealed = 'true';
      });
      return;
    }

    var observer = new IntersectionObserver(
      function (entries) {
        entries.forEach(function (entry) {
          if (!entry.isIntersecting) return;
          entry.target.dataset.revealed = 'true';
          observer.unobserve(entry.target);
        });
      },
      { rootMargin: '0px 0px -12% 0px', threshold: 0.15 }
    );

    Array.prototype.forEach.call(targets, function (el) {
      observer.observe(el);
    });
  }

  /* ---------------------------------------------------------------------
     Boot
     --------------------------------------------------------------------- */

  var readyCallbacks = [];
  var booted = false;

  LF.ready = function (fn) {
    if (booted) fn();
    else readyCallbacks.push(fn);
  };

  LF.enhance = function (root) {
    resolveSprite(root);
    initTabs(root);
    initPopovers(root);
    initReveal(root);
    if (LF.overlay) LF.overlay.init(root);
  };

  function boot() {
    resolveSprite(document);
    initTheme();
    initNav();
    initTabs(document);
    initPopovers(document);
    initReveal(document);
    if (LF.overlay) LF.overlay.init(document);
    if (LF.i18n) LF.i18n.init();

    booted = true;
    readyCallbacks.forEach(function (fn) {
      try {
        fn();
      } catch (err) {
        if (global.console) console.error('[main] ready callback failed', err);
      }
    });
    readyCallbacks = [];
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})(window);
