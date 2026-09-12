/**
 * app.js — shared chrome and helpers. Loaded by every page.
 *
 * Plain ES2017, no modules and no bundler: Spring Boot serves these files
 * directly, so what is written here is what the browser runs.
 */
(function () {
  'use strict';

  var LF = (window.LF = window.LF || {});

  /* =====================================================================
     Theme
     ===================================================================== */

  var THEME_KEY = 'lf.theme';

  function currentTheme() {
    return document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
  }

  function applyTheme(theme) {
    if (theme === 'dark') document.documentElement.setAttribute('data-theme', 'dark');
    else document.documentElement.removeAttribute('data-theme');
    try {
      localStorage.setItem(THEME_KEY, theme);
    } catch (e) {}
    syncThemeButtons();
  }

  function syncThemeButtons() {
    var dark = currentTheme() === 'dark';
    document.querySelectorAll('[data-theme-toggle]').forEach(function (btn) {
      btn.setAttribute('aria-pressed', dark ? 'true' : 'false');
      btn.setAttribute('aria-label', 'Switch to ' + (dark ? 'light' : 'dark') + ' theme');
      var sun = btn.querySelector('[data-theme-icon="light"]');
      var moon = btn.querySelector('[data-theme-icon="dark"]');
      if (sun) sun.hidden = dark;
      if (moon) moon.hidden = !dark;
    });
  }

  function initTheme() {
    document.querySelectorAll('[data-theme-toggle]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        applyTheme(currentTheme() === 'dark' ? 'light' : 'dark');
      });
    });
    syncThemeButtons();
  }

  /* =====================================================================
     Mobile navigation
     ===================================================================== */

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

    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && nav.dataset.open === 'true') {
        setOpen(false);
        toggle.focus();
      }
    });

    /* Reset when the viewport crosses into the desktop layout, so the panel
       is never left stuck open as a floating box. */
    if (window.matchMedia) {
      var mq = window.matchMedia('(min-width: 1024px)');
      if (mq.addEventListener) {
        mq.addEventListener('change', function (e) {
          if (e.matches) setOpen(false);
        });
      }
    }

    setOpen(false);
  }

  /* =====================================================================
     API helper. One place that knows how to talk to the backend, so every
     view distinguishes "request failed" from "nothing to show" the same way.
     ===================================================================== */

  function ApiError(message, status) {
    this.name = 'ApiError';
    this.message = message;
    this.status = status || 0;
  }
  ApiError.prototype = Object.create(Error.prototype);
  ApiError.prototype.constructor = ApiError;

  function request(path, options) {
    options = options || {};
    var init = {
      method: options.method || 'GET',
      headers: Object.assign({ Accept: 'application/json' }, options.headers || {})
    };

    if (options.body !== undefined) {
      if (options.body instanceof FormData) {
        init.body = options.body;
      } else {
        init.headers['Content-Type'] = 'application/json';
        init.body = JSON.stringify(options.body);
      }
    }

    return fetch(path, init).then(function (res) {
      var isJson = (res.headers.get('content-type') || '').indexOf('application/json') !== -1;
      return (isJson ? res.json() : res.text()).then(function (payload) {
        if (!res.ok) {
          var msg =
            (payload && (payload.message || payload.error)) ||
            'Request failed with status ' + res.status;
          throw new ApiError(msg, res.status);
        }
        return payload;
      });
    }, function () {
      throw new ApiError('Could not reach the server. Is the application running?', 0);
    });
  }

  LF.api = {
    ApiError: ApiError,
    get: function (path) {
      return request(path);
    },
    post: function (path, body) {
      return request(path, { method: 'POST', body: body });
    },
    put: function (path, body) {
      return request(path, { method: 'PUT', body: body });
    },
    del: function (path) {
      return request(path, { method: 'DELETE' });
    },
    health: function () {
      return request('/api/health');
    }
  };

  /* =====================================================================
     Toasts
     ===================================================================== */

  var TOAST_ICON = { success: 'i-check', error: 'i-alert', info: 'i-info' };
  var TOAST_ACCENT = {
    success: 'border-l-found',
    error: 'border-l-lost',
    info: 'border-l-brand'
  };

  function toast(message, opts) {
    opts = opts || {};
    var kind = opts.kind || 'info';
    var region = document.querySelector('[data-toast-region]');
    if (!region) return;

    region.setAttribute('aria-live', kind === 'error' ? 'assertive' : 'polite');

    var el = document.createElement('div');
    el.className =
      'pointer-events-auto flex items-start gap-3 rounded-xl border border-line border-l-4 ' +
      TOAST_ACCENT[kind] +
      ' bg-surface p-3.5 shadow-[var(--shadow-pop)] transition duration-200 translate-x-2 opacity-0';

    el.innerHTML =
      '<svg class="icon mt-0.5 ' +
      (kind === 'error' ? 'text-lost' : kind === 'success' ? 'text-found' : 'text-brand') +
      '" aria-hidden="true"><use href="/assets/icons.svg#' +
      (TOAST_ICON[kind] || TOAST_ICON.info) +
      '"></use></svg>' +
      '<div class="min-w-0 flex-1">' +
      (opts.title ? '<p class="text-sm font-semibold text-heading">' + escapeHtml(opts.title) + '</p>' : '') +
      '<p class="' + (opts.title ? 'text-sm text-muted' : 'text-sm font-semibold text-heading') + '">' +
      escapeHtml(message) +
      '</p></div>';

    var close = document.createElement('button');
    close.type = 'button';
    close.className = 'grid h-6 w-6 place-items-center rounded-lg text-faint transition hover:bg-sunken hover:text-heading';
    close.setAttribute('aria-label', 'Dismiss notification');
    close.innerHTML = '<svg class="icon" aria-hidden="true"><use href="/assets/icons.svg#i-close"></use></svg>';
    close.addEventListener('click', function () {
      dismiss(el);
    });
    el.appendChild(close);

    region.appendChild(el);
    requestAnimationFrame(function () {
      el.classList.remove('translate-x-2', 'opacity-0');
    });

    var duration = opts.duration === undefined ? (kind === 'error' ? 8000 : 4500) : opts.duration;
    var timer = null;
    function start() {
      if (duration > 0) timer = setTimeout(function () { dismiss(el); }, duration);
    }
    function stop() {
      if (timer) clearTimeout(timer);
      timer = null;
    }
    /* Reading or tabbing into a toast pauses its countdown. */
    el.addEventListener('mouseenter', stop);
    el.addEventListener('mouseleave', start);
    el.addEventListener('focusin', stop);
    el.addEventListener('focusout', start);
    start();

    return el;
  }

  function dismiss(el) {
    if (!el || el.dataset.leaving === 'true') return;
    el.dataset.leaving = 'true';
    el.classList.add('translate-x-2', 'opacity-0');
    setTimeout(function () {
      el.remove();
    }, 220);
  }

  toast.success = function (m, o) { return toast(m, Object.assign({}, o, { kind: 'success' })); };
  toast.error = function (m, o) { return toast(m, Object.assign({}, o, { kind: 'error' })); };
  toast.info = function (m, o) { return toast(m, Object.assign({}, o, { kind: 'info' })); };
  LF.toast = toast;

  /* =====================================================================
     "Mine" — what this browser has done.

     There is no sign-in yet, so the browser is the identity. We remember the
     references of items posted here and claims opened here, which is what
     lets the dashboard show your things and lets a conversation work out
     which side of it you are on.

     This is deliberately a stopgap: it does not survive a different device,
     and it is not a security boundary. Real accounts replace it.
     ===================================================================== */

  var MINE_KEY = 'lf.mine';

  function readMine() {
    try {
      var raw = JSON.parse(localStorage.getItem(MINE_KEY));
      return {
        items: Array.isArray(raw && raw.items) ? raw.items : [],
        claims: Array.isArray(raw && raw.claims) ? raw.claims : [],
        email: (raw && raw.email) || '',
        name: (raw && raw.name) || ''
      };
    } catch (e) {
      return { items: [], claims: [], email: '', name: '' };
    }
  }

  function writeMine(value) {
    try {
      localStorage.setItem(MINE_KEY, JSON.stringify(value));
    } catch (e) {}
    return value;
  }

  LF.mine = {
    all: readMine,

    addItem: function (reference) {
      var mine = readMine();
      if (mine.items.indexOf(reference) === -1) mine.items.push(reference);
      return writeMine(mine);
    },

    addClaim: function (reference) {
      var mine = readMine();
      if (mine.claims.indexOf(reference) === -1) mine.claims.push(reference);
      return writeMine(mine);
    },

    hasItem: function (reference) {
      return readMine().items.indexOf(reference) !== -1;
    },

    hasClaim: function (reference) {
      return readMine().claims.indexOf(reference) !== -1;
    },

    /* Remembering the contact details saves retyping them on every form. */
    remember: function (name, email) {
      var mine = readMine();
      if (name) mine.name = name;
      if (email) mine.email = email;
      return writeMine(mine);
    },

    clear: function () {
      try {
        localStorage.removeItem(MINE_KEY);
      } catch (e) {}
    }
  };

  /* =====================================================================
     Authentication & Session Management
     ===================================================================== */

  var authUser = null;

  function syncAuthNav() {
    var containers = document.querySelectorAll('[data-auth-nav]');
    if (!containers.length) return;

    var currentPath = window.location.pathname + window.location.search;
    var redirectParam = encodeURIComponent(currentPath);

    containers.forEach(function (container) {
      if (authUser) {
        var initial = (authUser.username || 'U').charAt(0).toUpperCase();
        var isAdmin = authUser.role === 'ADMIN';
        container.innerHTML =
          '<div class="relative" data-user-menu>' +
            '<button type="button" data-user-menu-btn aria-haspopup="true" aria-expanded="false" ' +
                    'class="flex items-center gap-2 rounded-xl border border-line bg-surface px-2.5 py-1.5 text-xs font-semibold text-heading transition hover:bg-sunken shadow-xs">' +
              '<span class="grid h-6 w-6 place-items-center rounded-full bg-brand-soft text-[0.7rem] font-bold text-brand-text">' +
                escapeHtml(initial) +
              '</span>' +
              '<span class="hidden sm:inline max-w-[90px] truncate">' + escapeHtml(authUser.username) + '</span>' +
              (isAdmin ? '<span class="rounded bg-brand px-1 py-0.5 text-[0.625rem] font-bold text-white">Admin</span>' : '') +
              '<svg class="icon h-3 w-3 text-faint" aria-hidden="true"><use href="/assets/icons.svg#i-chevron-down"></use></svg>' +
            '</button>' +
            '<div data-user-menu-panel hidden ' +
                 'class="absolute right-0 top-full z-50 mt-2 w-52 overflow-hidden rounded-xl border border-line bg-surface p-1.5 shadow-[var(--shadow-pop)]">' +
              '<div class="border-b border-line px-3 py-2 text-xs">' +
                '<p class="font-bold text-heading truncate">' + escapeHtml(authUser.username) + '</p>' +
                '<p class="text-faint truncate">' + escapeHtml(authUser.email) + '</p>' +
              '</div>' +
              '<a href="/dashboard.html" class="flex items-center gap-2 rounded-lg px-3 py-2 text-xs font-medium text-body transition hover:bg-sunken hover:text-heading">' +
                '<svg class="icon h-4 w-4 text-faint" aria-hidden="true"><use href="/assets/icons.svg#i-pin"></use></svg>' +
                'My items' +
              '</a>' +
              (isAdmin ?
                '<a href="/admin.html" class="flex items-center gap-2 rounded-lg px-3 py-2 text-xs font-medium text-body transition hover:bg-sunken hover:text-heading">' +
                  '<svg class="icon h-4 w-4 text-brand" aria-hidden="true"><use href="/assets/icons.svg#i-lock"></use></svg>' +
                  'Moderation' +
                '</a>' : '') +
              '<button type="button" data-logout-btn class="w-full flex items-center gap-2 rounded-lg px-3 py-2 text-xs font-medium text-lost transition hover:bg-lost/10">' +
                '<svg class="icon h-4 w-4 text-lost" aria-hidden="true"><use href="/assets/icons.svg#i-close"></use></svg>' +
                'Sign out' +
              '</button>' +
            '</div>' +
          '</div>';

        var menuBtn = container.querySelector('[data-user-menu-btn]');
        var panel = container.querySelector('[data-user-menu-panel]');
        var logoutBtn = container.querySelector('[data-logout-btn]');

        if (menuBtn && panel) {
          menuBtn.addEventListener('click', function (e) {
            e.stopPropagation();
            var open = panel.hidden;
            panel.hidden = !open;
            menuBtn.setAttribute('aria-expanded', open ? 'true' : 'false');
          });

          document.addEventListener('click', function (e) {
            if (!container.contains(e.target)) {
              panel.hidden = true;
              menuBtn.setAttribute('aria-expanded', 'false');
            }
          });
        }

        if (logoutBtn) {
          logoutBtn.addEventListener('click', function () {
            LF.auth.logout();
          });
        }
      } else {
        container.innerHTML =
          '<div class="flex items-center gap-1.5">' +
            '<a href="/login.html?redirect=' + redirectParam + '" ' +
               'class="rounded-lg px-3 py-1.5 text-xs sm:text-sm font-medium text-heading transition hover:bg-sunken">' +
              'Sign In' +
            '</a>' +
            '<a href="/login.html?tab=register&redirect=' + redirectParam + '" ' +
               'class="hidden sm:inline-flex rounded-lg border border-line bg-surface px-3 py-1.5 text-xs sm:text-sm font-medium text-heading shadow-xs transition hover:bg-sunken">' +
              'Register' +
            '</a>' +
          '</div>';
      }
    });
  }

  LF.auth = {
    getUser: function () { return authUser; },
    isLoggedIn: function () { return !!authUser; },
    isAdmin: function () { return authUser && authUser.role === 'ADMIN'; },

    me: function () {
      return LF.api.get('/api/auth/me')
        .then(function (user) {
          authUser = user;
          LF.mine.remember(user.username, user.email);
          syncAuthNav();
          document.dispatchEvent(new CustomEvent('lf:auth-change', { detail: { user: user } }));
          return user;
        })
        .catch(function () {
          authUser = null;
          syncAuthNav();
          document.dispatchEvent(new CustomEvent('lf:auth-change', { detail: { user: null } }));
          return null;
        });
    },

    login: function (identifier, password) {
      return LF.api.post('/api/auth/login', { identifier: identifier, password: password })
        .then(function (user) {
          authUser = user;
          LF.mine.remember(user.username, user.email);
          syncAuthNav();
          document.dispatchEvent(new CustomEvent('lf:auth-change', { detail: { user: user } }));
          return user;
        });
    },

    register: function (username, email, password) {
      return LF.api.post('/api/auth/register', { username: username, email: email, password: password })
        .then(function (user) {
          authUser = user;
          LF.mine.remember(user.username, user.email);
          syncAuthNav();
          document.dispatchEvent(new CustomEvent('lf:auth-change', { detail: { user: user } }));
          return user;
        });
    },

    logout: function () {
      return LF.api.post('/api/auth/logout', {})
        .catch(function () {})
        .then(function () {
          authUser = null;
          syncAuthNav();
          document.dispatchEvent(new CustomEvent('lf:auth-change', { detail: { user: null } }));
          LF.toast.info('You have been signed out');
          if (window.location.pathname.indexOf('admin') !== -1) {
            window.location.href = '/';
          }
        });
    },

    init: function () {
      syncAuthNav();
      LF.auth.me();
    }
  };

  /* =====================================================================
     Small shared helpers
     ===================================================================== */

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }
  LF.escapeHtml = escapeHtml;

  /** "4 hours ago", "3 days ago" — relative time without pulling in a library. */
  LF.timeAgo = function (iso) {
    if (!iso) return '';
    var then = new Date(iso).getTime();
    if (isNaN(then)) return '';
    var secs = Math.max(0, Math.round((Date.now() - then) / 1000));
    var units = [
      [31536000, 'year'],
      [2592000, 'month'],
      [604800, 'week'],
      [86400, 'day'],
      [3600, 'hour'],
      [60, 'minute']
    ];
    for (var i = 0; i < units.length; i++) {
      var n = Math.floor(secs / units[i][0]);
      if (n >= 1) return n + ' ' + units[i][1] + (n > 1 ? 's' : '') + ' ago';
    }
    return 'just now';
  };

  LF.formatDate = function (iso) {
    if (!iso) return '';
    var d = new Date(iso);
    if (isNaN(d.getTime())) return '';
    return d.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
  };

  LF.qs = function (name) {
    return new URLSearchParams(window.location.search).get(name);
  };

  /* =====================================================================
     Boot
     ===================================================================== */

  function boot() {
    initTheme();
    initNav();
    LF.auth.init();
    document.dispatchEvent(new CustomEvent('lf:ready'));
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
