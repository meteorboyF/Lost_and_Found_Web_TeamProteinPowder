/**
 * pages/styleguide.js — drives the component library page only.
 *
 * Most of this file exists to make the styleguide self-auditing: the swatches,
 * the contrast table, and the icon grid are all generated from the live
 * stylesheet and the live sprite, so the page cannot drift out of sync with
 * the system it documents.
 */
(function (global) {
  'use strict';

  var LF = global.LF;

  /* ---------------------------------------------------------------------
     Colour maths — WCAG 2.1 relative luminance and contrast ratio.
     --------------------------------------------------------------------- */

  function readVar(name) {
    return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  }

  /** Resolve any CSS colour to [r,g,b] by letting the browser normalise it. */
  var probe = document.createElement('span');
  probe.style.display = 'none';
  document.body.appendChild(probe);

  function toRgb(colour) {
    probe.style.color = '';
    probe.style.color = colour;
    var computed = getComputedStyle(probe).color;
    var m = computed.match(/-?[\d.]+/g);
    if (!m) return [0, 0, 0];
    return [Number(m[0]), Number(m[1]), Number(m[2])];
  }

  function luminance(rgb) {
    var channels = rgb.map(function (c) {
      c = c / 255;
      return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    });
    return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
  }

  function contrast(a, b) {
    var la = luminance(toRgb(a));
    var lb = luminance(toRgb(b));
    var hi = Math.max(la, lb);
    var lo = Math.min(la, lb);
    return (hi + 0.05) / (lo + 0.05);
  }

  /* ---------------------------------------------------------------------
     Swatches
     --------------------------------------------------------------------- */

  var SURFACE_SWATCHES = [
    { name: 'Page', token: '--bg-page' },
    { name: 'Surface', token: '--bg-surface' },
    { name: 'Sunken', token: '--bg-sunken' },
    { name: 'Ink / primary', token: '--fg-primary', text: true },
    { name: 'Secondary', token: '--fg-secondary', text: true },
    { name: 'Muted', token: '--fg-muted', text: true },
    { name: 'Disabled', token: '--fg-disabled', text: true, nonText: true },
    { name: 'Hairline', token: '--line', nonText: true }
  ];

  var ACCENT_SWATCHES = [
    { name: 'Found', token: '--accent-found', text: true },
    { name: 'Found wash', token: '--accent-found-wash' },
    { name: 'Lost', token: '--accent-lost', text: true },
    { name: 'Lost wash', token: '--accent-lost-wash' }
  ];

  function renderSwatches(host, list) {
    if (!host) return;
    var page = readVar('--bg-page');

    host.innerHTML = list
      .map(function (s) {
        var value = readVar(s.token);
        var ratio = contrast(value, page);
        var note;
        if (s.nonText) {
          note = 'non-text only';
        } else if (s.text) {
          note = ratio.toFixed(2) + ':1 on page';
        } else {
          note = value;
        }
        return (
          '<div class="swatch">' +
          '<div class="swatch__chip" style="background-color:var(' + s.token + ')"></div>' +
          '<div class="swatch__meta">' +
          '<span class="swatch__name">' + s.name + '</span>' +
          '<span class="swatch__value">' + s.token + '</span>' +
          '<span class="swatch__ratio">' + note + '</span>' +
          '</div></div>'
        );
      })
      .join('');
  }

  /* ---------------------------------------------------------------------
     Contrast audit
     --------------------------------------------------------------------- */

  var AUDIT_FG = [
    ['Primary', '--fg-primary'],
    ['Secondary', '--fg-secondary'],
    ['Muted', '--fg-muted'],
    ['Found accent', '--accent-found'],
    ['Lost accent', '--accent-lost']
  ];

  var AUDIT_BG = [
    ['Page', '--bg-page'],
    ['Surface', '--bg-surface'],
    ['Sunken', '--bg-sunken']
  ];

  function renderAudit() {
    var body = document.querySelector('[data-audit]');
    if (!body) return;

    var rows = [];
    AUDIT_FG.forEach(function (fg) {
      AUDIT_BG.forEach(function (bg) {
        var ratio = contrast(readVar(fg[1]), readVar(bg[1]));
        var pass = ratio >= 7 ? 'AAA' : ratio >= 4.5 ? 'AA' : ratio >= 3 ? 'Large text only' : 'Fail';
        var cls = ratio >= 4.5 ? 'audit__pass' : 'audit__fail';
        rows.push(
          '<tr>' +
          '<th scope="row" data-label="Foreground">' + fg[0] + '</th>' +
          '<td data-label="Surface">' + bg[0] + '</td>' +
          '<td data-label="Ratio" class="table__num">' + ratio.toFixed(2) + '</td>' +
          '<td data-label="WCAG" class="' + cls + '">' + pass + '</td>' +
          '</tr>'
        );
      });
    });
    body.innerHTML = rows.join('');
  }

  /* ---------------------------------------------------------------------
     Spacing ruler
     --------------------------------------------------------------------- */

  function renderRuler() {
    var host = document.querySelector('[data-ruler]');
    if (!host) return;

    var steps = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
    host.innerHTML = steps
      .map(function (n) {
        var token = '--sp-' + n;
        var value = readVar(token);
        var px = parseFloat(value) * 16;
        return (
          '<div class="ruler__row">' +
          '<span>' + token + '</span>' +
          '<span>' + px + 'px</span>' +
          '<span class="ruler__bar" style="width:' + Math.min(px, 320) + 'px"></span>' +
          '</div>'
        );
      })
      .join('');
  }

  /* ---------------------------------------------------------------------
     Status chip matrix — generated so the styleguide always shows every
     state the CSS defines, in lifecycle order.
     --------------------------------------------------------------------- */

  var STATUSES = [
    ['reported', 'Reported', 'i-st-reported', 'Report filed, object not yet in hand'],
    ['in-storage', 'In Storage', 'i-st-storage', 'Physically held, catalogued, binned'],
    ['match-suggested', 'Match Suggested', 'i-st-match', 'System proposes a lost/found pair'],
    ['claim-pending', 'Claim Pending', 'i-st-pending', 'Someone is answering the challenge'],
    ['verified', 'Verified', 'i-st-verified', 'Identity proven, awaiting collection'],
    ['returned', 'Returned', 'i-st-returned', 'Handed back. Terminal success'],
    ['archived', 'Archived', 'i-st-archived', 'Unclaimed past the holding period'],
    ['disposed', 'Disposed', 'i-st-disposed', 'Donated or destroyed. Record remains'],
    ['disputed', 'Disputed', 'i-st-disputed', 'Two claimants. Locked pending staff'],
    ['self-closed', 'Self-Closed', 'i-st-self-closed', 'Owner found it themselves']
  ];

  function chipMarkup(status) {
    return (
      '<span class="status-chip" data-status="' + status[0] + '">' +
      '<svg class="icon" aria-hidden="true" focusable="false"><use href="' +
      LF.spritePath + '#' + status[2] + '"></use></svg>' +
      '<span class="status-chip__label">' + status[1] + '</span></span>'
    );
  }

  function renderChips() {
    var host = document.querySelector('[data-chip-matrix]');
    if (host) {
      host.innerHTML = STATUSES.map(function (s) {
        return (
          '<div class="chip-cell">' +
          chipMarkup(s) +
          '<span class="chip-cell__key">data-status="' + s[0] + '"</span>' +
          '<span class="meta">' + s[3] + '</span>' +
          '</div>'
        );
      }).join('');
    }

    var grey = document.querySelector('[data-chip-matrix-grey]');
    if (grey) {
      grey.innerHTML = STATUSES.map(function (s) {
        return '<div class="chip-cell">' + chipMarkup(s) + '</div>';
      }).join('');
    }
  }

  /* ---------------------------------------------------------------------
     Icon grid — read the sprite so a new icon appears here automatically.
     --------------------------------------------------------------------- */

  function renderIcons() {
    var host = document.querySelector('[data-icon-grid]');
    if (!host) return;

    if (LF.api.isFileProtocol) {
      host.innerHTML =
        '<p class="meta">The sprite cannot be read over <code>file://</code>. ' +
        'Serve the project over HTTP to see the icon inventory.</p>';
      return;
    }

    fetch(LF.spritePath)
      .then(function (res) {
        if (!res.ok) throw new Error('sprite ' + res.status);
        return res.text();
      })
      .then(function (text) {
        var ids = [];
        var re = /<symbol[^>]*\bid="([^"]+)"/g;
        var match;
        while ((match = re.exec(text)) !== null) ids.push(match[1]);

        host.innerHTML = ids
          .map(function (id) {
            return (
              '<div class="icon-cell">' +
              '<svg class="icon" aria-hidden="true" focusable="false"><use href="' +
              LF.spritePath + '#' + id + '"></use></svg>' +
              '<span>' + id + '</span></div>'
            );
          })
          .join('');
      })
      .catch(function () {
        host.innerHTML = '<p class="meta">Could not read the icon sprite.</p>';
      });
  }

  /* ---------------------------------------------------------------------
     Meters — paint the segments from data-meter.
     --------------------------------------------------------------------- */

  function renderMeters(root) {
    var meters = (root || document).querySelectorAll('[data-meter]');
    Array.prototype.forEach.call(meters, function (meter) {
      var track = meter.querySelector('.meter__track');
      if (!track) return;
      var value = Number(meter.dataset.meter) || 0;
      var on = Math.round(value * 10);
      var cells = [];
      for (var i = 0; i < 10; i++) {
        cells.push(
          '<span class="meter__seg" data-on="' + (i < on) + '" style="--i:' + i + '"></span>'
        );
      }
      track.innerHTML = cells.join('');
    });
  }

  /* ---------------------------------------------------------------------
     Stepper demo
     --------------------------------------------------------------------- */

  function initStepper() {
    var stepper = document.querySelector('[data-stepper]');
    if (!stepper) return;

    var steps = Array.prototype.slice.call(stepper.querySelectorAll('.stepper__step'));
    var status = document.querySelector('[data-stepper-status]');
    var prev = document.querySelector('[data-step-prev]');
    var next = document.querySelector('[data-step-next]');
    var current = 0;

    function paint() {
      steps.forEach(function (step, i) {
        var state = i < current ? 'done' : i === current ? 'current' : 'todo';
        step.dataset.state = state;
        var marker = step.querySelector('.stepper__marker');
        if (state === 'done') {
          marker.innerHTML =
            '<svg class="icon" aria-hidden="true" focusable="false"><use href="' +
            LF.spritePath + '#i-check"></use></svg>';
        } else {
          marker.textContent = String(i + 1);
        }
      });
      if (status) status.textContent = 'Step ' + (current + 1) + ' of ' + steps.length;
      if (prev) prev.disabled = current === 0;
      if (next) next.textContent = current === steps.length - 1 ? 'Submit report' : 'Continue';
    }

    if (prev) prev.addEventListener('click', function () {
      if (current > 0) current--;
      paint();
    });

    if (next) next.addEventListener('click', function () {
      if (current < steps.length - 1) {
        current++;
        paint();
      } else {
        LF.toast.success('Report filed. Reference LF-2026-0501-T4W9.', {
          title: 'Submitted'
        });
      }
    });

    paint();
  }

  /* ---------------------------------------------------------------------
     Form demos — character counter and inline email validation, both
     writing into the pre-reserved message row so nothing reflows.
     --------------------------------------------------------------------- */

  function initFormDemos() {
    var titled = document.getElementById('sg-title');
    var counter = document.querySelector('[data-count-for="sg-title"]');
    if (titled && counter) {
      titled.addEventListener('input', function () {
        counter.textContent = String(titled.value.length);
        counter.parentElement.dataset.over = titled.value.length >= 60 ? 'true' : 'false';
      });
    }

    var email = document.querySelector('[data-validate="email"]');
    if (email) {
      var msg = document.getElementById('sg-email-msg');
      var check = function () {
        var value = email.value.trim();
        if (!value) {
          email.removeAttribute('aria-invalid');
          email.removeAttribute('data-valid');
          msg.textContent = '';
          return;
        }
        var ok = /^[^\s@]+@[^\s@]+\.[a-z]{2,}$/i.test(value);
        var university = /\.edu(\.[a-z]{2,})?$|\.ac\.[a-z]{2,}$/i.test(value);

        if (!ok) {
          email.setAttribute('aria-invalid', 'true');
          email.removeAttribute('data-valid');
          msg.innerHTML =
            '<svg class="icon" aria-hidden="true" focusable="false"><use href="' +
            LF.spritePath + '#i-alert"></use></svg> That is not a valid email address.';
        } else if (!university) {
          email.setAttribute('aria-invalid', 'true');
          email.removeAttribute('data-valid');
          msg.innerHTML =
            '<svg class="icon" aria-hidden="true" focusable="false"><use href="' +
            LF.spritePath + '#i-alert"></use></svg> Use your university address.';
        } else {
          email.removeAttribute('aria-invalid');
          email.setAttribute('data-valid', 'true');
          msg.textContent = '';
        }
      };
      email.addEventListener('blur', check);
      email.addEventListener('input', function () {
        if (email.getAttribute('aria-invalid') === 'true') check();
      });
    }
  }

  /* ---------------------------------------------------------------------
     Table selection → bulk bar
     --------------------------------------------------------------------- */

  function initTableDemo() {
    var body = document.querySelector('[data-selectable]');
    var bar = document.querySelector('[data-bulkbar]');
    var count = document.querySelector('[data-bulkbar-count]');
    if (!body || !bar) return;

    body.addEventListener('change', function (event) {
      if (event.target.type !== 'checkbox') return;
      var row = event.target.closest('tr');
      if (row) row.setAttribute('aria-selected', event.target.checked ? 'true' : 'false');

      var selected = body.querySelectorAll('input[type="checkbox"]:checked').length;
      bar.hidden = selected === 0;
      if (count) count.textContent = selected + ' selected';
    });
  }

  /* ---------------------------------------------------------------------
     Misc demo wiring
     --------------------------------------------------------------------- */

  function initDemos() {
    Array.prototype.forEach.call(document.querySelectorAll('[data-toast]'), function (btn) {
      btn.addEventListener('click', function () {
        var kind = btn.dataset.toast;
        if (kind === 'success') {
          LF.toast.success('Item LF-2026-0418-K7Q3 moved to bin B-14.', { title: 'Saved' });
        } else if (kind === 'error') {
          LF.toast.error('That challenge answer did not match. Two attempts remaining.', {
            title: 'Verification failed',
            action: { label: 'Try again', onClick: function () {} }
          });
        } else {
          LF.toast('A new item matches your saved search “black backpack”.', {
            action: { label: 'View', onClick: function () {} }
          });
        }
      });
    });

    var loader = document.querySelector('[data-demo-loading]');
    if (loader) {
      loader.addEventListener('click', function () {
        loader.dataset.loading = 'true';
        global.setTimeout(function () {
          loader.dataset.loading = 'false';
          loader.removeAttribute('data-loading');
        }, 1800);
      });
    }

    /* Buttons that only toggle their own pressed state in the demo. */
    Array.prototype.forEach.call(
      document.querySelectorAll('[aria-pressed]:not([data-theme-toggle])'),
      function (btn) {
        btn.addEventListener('click', function () {
          if (btn.closest('.btn-group')) {
            Array.prototype.forEach.call(
              btn.closest('.btn-group').querySelectorAll('[aria-pressed]'),
              function (sibling) {
                sibling.setAttribute('aria-pressed', 'false');
              }
            );
          }
          btn.setAttribute(
            'aria-pressed',
            btn.getAttribute('aria-pressed') === 'true' && !btn.closest('.btn-group')
              ? 'false'
              : 'true'
          );
        });
      }
    );

    Array.prototype.forEach.call(document.querySelectorAll('.filter-pill__remove'), function (btn) {
      btn.addEventListener('click', function () {
        btn.closest('.filter-pill').remove();
      });
    });
  }

  /* ---------------------------------------------------------------------
     Boot. Re-render the colour-dependent panels when the theme changes,
     because every ratio in them is theme-specific.
     --------------------------------------------------------------------- */

  function renderColourPanels() {
    renderSwatches(document.querySelector('[data-swatches]'), SURFACE_SWATCHES);
    renderSwatches(document.querySelector('[data-swatches-accent]'), ACCENT_SWATCHES);
    renderAudit();
  }

  LF.ready(function () {
    renderColourPanels();
    renderRuler();
    renderChips();
    renderIcons();
    renderMeters(document);
    initStepper();
    initFormDemos();
    initTableDemo();
    initDemos();

    LF.store.subscribe('theme', function () {
      /* Let the attribute land and styles recompute before measuring. */
      global.requestAnimationFrame(renderColourPanels);
    });

    if (global.matchMedia) {
      var mq = global.matchMedia('(prefers-color-scheme: dark)');
      if (mq.addEventListener) {
        mq.addEventListener('change', function () {
          global.requestAnimationFrame(renderColourPanels);
        });
      }
    }
  });
})(window);
