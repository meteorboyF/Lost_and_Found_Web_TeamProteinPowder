/**
 * claim.js — one claim conversation.
 *
 * The server authorizes each participant and supplies the viewer's role.
 */
(function () {
  'use strict';

  var LF = window.LF;
  var e = LF.escapeHtml;
  var host = document.querySelector('[data-claim]');
  var current = null;
  var sending = false;
  var polling = false;
  var stopped = false;

  var STATUS = {
    OPEN: { label: 'Waiting for a reply', cls: 'bg-amber-soft text-amber-text', icon: 'i-st-pending' },
    APPROVED: { label: 'Approved — awaiting pickup', cls: 'bg-brand-soft text-brand-text', icon: 'i-clock' },
    ACCEPTED: { label: 'Handover complete', cls: 'bg-found-soft text-found-text', icon: 'i-check' },
    DECLINED: { label: 'Declined', cls: 'bg-lost-soft text-lost-text', icon: 'i-close' },
    WITHDRAWN: { label: 'Withdrawn', cls: 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300', icon: 'i-close' }
  };

  function statusPill(status) {
    var s = STATUS[status] || STATUS.OPEN;
    return '<span class="pill pill-lg ' + s.cls + '">' +
      '<svg class="icon h-4 w-4" aria-hidden="true"><use href="/assets/icons.svg#' + s.icon + '"></use></svg>' +
      e(s.label) + '</span>';
  }

  /* ------------------------------------------------------------------
     Message bubbles. The viewer's own messages sit on the right; system
     notices are centred and unattributed so they never read as a person
     speaking.
     ------------------------------------------------------------------ */

  function bubble(message, viewerIsPoster) {
    if (message.author === 'SYSTEM') {
      return (
        '<li class="flex justify-center py-1">' +
        '<p class="rounded-full bg-sunken px-3 py-1.5 text-center text-xs text-muted">' +
        e(message.body) + '</p></li>'
      );
    }

    var mine = viewerIsPoster ? message.author === 'POSTER' : message.author === 'CLAIMANT';

    return (
      '<li class="flex ' + (mine ? 'justify-end' : 'justify-start') + '">' +
      '<div class="max-w-[85%] sm:max-w-[75%]">' +
        '<div class="rounded-2xl px-4 py-3 ' +
          (mine
            ? 'rounded-br-md bg-brand text-white dark:text-[#11121a]'
            : 'rounded-bl-md border border-line bg-surface text-body') +
        '">' +
          '<p class="whitespace-pre-line text-sm leading-relaxed">' + e(message.body) + '</p>' +
        '</div>' +
        '<p class="mt-1 px-1 text-xs text-faint ' + (mine ? 'text-right' : '') + '">' +
          e(message.authorName) + ' · ' + e(LF.timeAgo(message.createdAt)) +
        '</p>' +
      '</div></li>'
    );
  }

  /* ------------------------------------------------------------------
     Render
     ------------------------------------------------------------------ */

  function render(claim) {
    if (!LF.auth.isLoggedIn()) return;
    current = claim;
    document.title = 'Claim ' + claim.reference + ' — Lost & Found';
    var crumb = document.querySelector('[data-crumb]');
    if (crumb) crumb.textContent = claim.reference;

    var viewerIsPoster = claim.viewerRole === 'POSTER';
    var open = claim.status === 'OPEN' || claim.status === 'APPROVED';
    var cat = LF.category(claim.item.category);

    var thumb = claim.item.photoUrl
      ? '<img src="' + e(claim.item.photoUrl) + '" alt="" class="h-16 w-16 rounded-xl object-cover">'
      : '<span class="grid h-16 w-16 place-items-center rounded-xl bg-gradient-to-br ' + cat.grad + '">' +
        '<svg class="icon h-7 w-7 ' + cat.solid + '" aria-hidden="true"><use href="/assets/icons.svg#' +
        cat.icon + '"></use></svg></span>';

    host.innerHTML =
      '<div class="claim-workspace grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem] lg:gap-8">' +

        /* ---- conversation ---- */
        '<div class="card flex flex-col overflow-hidden">' +
          '<div class="flex flex-wrap items-center justify-between gap-3 border-b border-line p-4">' +
            '<div>' +
              '<p class="text-sm font-semibold text-heading">' +
                (viewerIsPoster
                  ? e(claim.claimantName) + ' says this is theirs'
                  : 'Your claim on this item') +
              '</p>' +
              '<p class="font-mono text-xs text-faint">' + e(claim.reference) + '</p>' +
            '</div>' +
            statusPill(claim.status) +
          '</div>' +

          '<ul class="flex flex-col gap-4 overflow-y-auto p-4 sm:p-6" style="height: min(55vh, 32rem); min-height: 16rem" data-thread role="log" ' +
              'aria-live="polite" aria-label="Conversation">' +
            claim.messages.map(function (m) { return bubble(m, viewerIsPoster); }).join('') +
          '</ul>' +

          (open && claim.chatUnlocked
            ? '<form class="border-t border-line p-4" data-reply>' +
                '<label for="reply-body" class="sr-only">Your message</label>' +
                '<div class="flex items-end gap-2">' +
                  '<textarea id="reply-body" name="body" rows="1" maxlength="2000" ' +
                    'placeholder="Write a reply…" class="field max-h-40 resize-none py-2.5"></textarea>' +
                  '<button type="submit" class="btn btn-primary shrink-0" data-send>' +
                    '<svg class="icon h-[1.15rem] w-[1.15rem]" aria-hidden="true"><use href="/assets/icons.svg#i-arrow-right"></use></svg>' +
                    '<span class="sr-only">Send</span>' +
                  '</button>' +
                '</div>' +
                '<p class="mt-1.5 min-h-4 text-xs text-lost" data-error="body"></p>' +
              '</form>'
            : '<p class="border-t border-line bg-sunken p-4 text-center text-sm text-muted">' +
              (open ? 'Chat is locked while the poster reviews your ownership proof.' : 'This conversation is closed.') + '</p>') +
        '</div>' +

        /* ---- side panel ---- */
        '<aside class="flex flex-col gap-4">' +
          '<a href="/item.html?ref=' + encodeURIComponent(claim.item.reference) + '" ' +
             'class="card card-interactive flex items-center gap-3 p-3">' +
            thumb +
            '<span class="min-w-0 flex-1">' +
              '<span class="block truncate text-sm font-semibold text-heading">' + e(claim.item.title) + '</span>' +
              '<span class="block truncate text-xs text-muted">' + e(claim.item.location) + '</span>' +
              '<span class="mt-1.5 block">' + LF.kindPill(claim.item.kind) + '</span>' +
            '</span>' +
          '</a>' +

          (claim.status === 'OPEN' && viewerIsPoster
            ? '<div class="card p-4">' +
                '<p class="text-sm font-semibold text-heading">Is this theirs?</p>' +
                '<p class="mt-1 text-xs leading-relaxed text-muted">' +
                  'Review the private evidence first. Approval reserves pickup; it does not mark the item returned.' +
                '</p>' +
                (!claim.safety.evidenceReviewedAt ? '<button type="button" data-verify class="btn btn-secondary mt-3 w-full">I reviewed the private evidence</button>' : '<p class="mt-3 text-sm text-found-text">Evidence reviewed</p>') +
                '<button type="button" data-accept ' + (!claim.chatUnlocked || !claim.safety.evidenceReviewedAt || claim.safety.handoverFrozen || claim.safety.deskReviewStatus === 'PENDING' ? 'disabled ' : '') + 'class="btn btn-primary mt-3 w-full">' +
                  '<svg class="icon h-[1.15rem] w-[1.15rem]" aria-hidden="true"><use href="/assets/icons.svg#i-check"></use></svg>' +
                  'Approve for pickup</button>' +
                '<button type="button" data-decline class="btn btn-secondary mt-2 w-full text-lost">Not a match</button>' +
              '</div>'
            : '') +

          (open && !viewerIsPoster
            ? '<div class="card p-4">' +
                '<p class="text-sm font-semibold text-heading">Changed your mind?</p>' +
                '<p class="mt-1 text-xs leading-relaxed text-muted">' +
                  'Withdrawing closes your claim. It does not remove an unresolved fraud review.</p>' +
                '<button type="button" data-withdraw class="btn btn-secondary mt-3 w-full text-lost">Withdraw claim</button>' +
              '</div>'
            : '') +

          '<p class="px-1 text-xs leading-relaxed text-faint">' +
            'The signed-in participants and authorized campus staff can review this claim. New messages refresh automatically. ' +
            'Email addresses are never shared between the two of you.' +
          '</p>' +
        '</aside>' +
      '</div>';

    if (claim.item.hasPrivatePhoto && (viewerIsPoster || claim.status === 'ACCEPTED')) {
      var photo = document.createElement('div');
      photo.className = 'card p-4';
      photo.innerHTML = '<p class="text-sm font-semibold text-heading">Private photograph</p>' +
        '<img class="mt-3 w-full rounded-xl" alt="Private identifying details" src="/api/items/' + encodeURIComponent(claim.item.reference) + '/private-photo">';
      host.querySelector('aside').appendChild(photo);
    }

    LF.safety.mount(host.querySelector('aside'), claim, render);
    wire(claim, viewerIsPoster);
    scrollThread();
  }

  function scrollThread() {
    var thread = host.querySelector('[data-thread]');
    if (thread) thread.scrollTop = thread.scrollHeight;
  }

  /* ------------------------------------------------------------------
     Actions
     ------------------------------------------------------------------ */

  function wire(claim, viewerIsPoster) {
    var form = host.querySelector('[data-reply]');
    if (form) {
      var textarea = form.elements.body;

      /* Grow with the message rather than making people scroll a 1-row box. */
      textarea.addEventListener('input', function () {
        textarea.style.height = 'auto';
        textarea.style.height = Math.min(textarea.scrollHeight, 160) + 'px';
      });

      /* Enter sends, Shift+Enter makes a new line. */
      textarea.addEventListener('keydown', function (ev) {
        if (ev.key === 'Enter' && !ev.shiftKey && !ev.isComposing) {
          ev.preventDefault();
          form.requestSubmit();
        }
      });

      form.addEventListener('submit', function (ev) {
        ev.preventDefault();
        send(claim, viewerIsPoster, textarea, form);
      });
    }

    var accept = host.querySelector('[data-accept]');
    if (accept) {
      accept.addEventListener('click', function () {
        act(claim, 'accept', accept, 'Approved for pickup. Both participants must confirm handover.');
      });
    }

    var verify = host.querySelector('[data-verify]');
    if (verify) verify.addEventListener('click', function () {
      act(claim, 'verify', verify, 'Private evidence reviewed.');
    });

    var decline = host.querySelector('[data-decline]');
    if (decline) {
      decline.addEventListener('click', function () {
        act(claim, 'decline', decline, 'Claim declined.');
      });
    }

    var withdraw = host.querySelector('[data-withdraw]');
    if (withdraw) {
      withdraw.addEventListener('click', function () {
        act(claim, 'withdraw', withdraw, 'Claim withdrawn.');
      });
    }
  }

  function send(claim, viewerIsPoster, textarea, form) {
    if (sending) return;
    var body = textarea.value.trim();
    var error = form.querySelector('[data-error="body"]');
    if (!body) {
      error.textContent = 'Write a message first';
      textarea.focus();
      return;
    }
    error.textContent = '';

    var send = form.querySelector('[data-send]');
    send.disabled = true;
    sending = true;

    LF.api
      .post('/api/claims/' + encodeURIComponent(claim.reference) + '/messages',
            { body: body })
      .then(function (message) {
        if (!current || current.reference !== claim.reference || !LF.auth.isLoggedIn()) return;
        if (!current.messages.some(function (m) { return m.id === message.id; })) {
          current.messages.push(message);
          var thread = host.querySelector('[data-thread]');
          thread.insertAdjacentHTML('beforeend', bubble(message, viewerIsPoster));
        }
        if (textarea.value.trim() === body) textarea.value = '';
        textarea.style.height = 'auto';
        scrollThread();
      })
      .catch(function (err) {
        LF.toast.error(err.message);
      })
      .finally(function () {
        sending = false;
        send.disabled = false;
        textarea.focus();
      });
  }

  function act(claim, action, button, successMessage) {
    if (sending || button.disabled) return;
    sending = true;
    button.disabled = true;
    LF.api
      .post('/api/claims/' + encodeURIComponent(claim.reference) + '/' + action)
      .then(function (updated) {
        if (!LF.auth.isLoggedIn()) return;
        LF.toast.success(successMessage);
        render(updated);
      })
      .catch(function (err) {
        button.disabled = false;
        LF.toast.error(err.message);
      }).finally(function () { sending = false; });
  }

  function refresh() {
    if (stopped || polling || sending || LF.safety.busy || !current || document.hidden) return;
    polling = true;
    LF.api.get('/api/claims/' + encodeURIComponent(current.reference)).then(function (updated) {
      if (sending || LF.safety.busy || stopped || !current || !LF.auth.isLoggedIn()) return;
      if (updated.status !== current.status || updated.chatUnlocked !== current.chatUnlocked || JSON.stringify(updated.safety) !== JSON.stringify(current.safety)) {
        var textarea = host.querySelector('[data-reply] textarea');
        var draft = textarea ? textarea.value : '';
        render(updated);
        var replacement = host.querySelector('[data-reply] textarea');
        if (replacement) replacement.value = draft;
        return;
      }
      var thread = host.querySelector('[data-thread]');
      var nearBottom = thread.scrollHeight - thread.scrollTop - thread.clientHeight < 80;
      var known = new Set(current.messages.map(function (m) { return m.id; }));
      var fresh = updated.messages.filter(function (m) { return !known.has(m.id); });
      fresh.forEach(function (m) {
        thread.insertAdjacentHTML('beforeend', bubble(m, updated.viewerRole === 'POSTER'));
      });
      current.messages = updated.messages;
      current.updatedAt = updated.updatedAt;
      if (nearBottom && fresh.length) scrollThread();
    }).catch(function (err) {
      if (err.status === 401 || err.status === 403 || err.status === 404) {
        stopped = true;
        current = null;
        host.innerHTML = LF.errorState(err.message);
      }
    }).finally(function () { polling = false; });
  }

  /* ------------------------------------------------------------------
     Boot
     ------------------------------------------------------------------ */

  function load() {
    if (!LF.auth.require()) return;
    stopped = false;
    var reference = LF.qs('ref');
    if (!reference) {
      host.innerHTML = LF.emptyState({
        icon: 'i-message',
        title: 'No conversation selected',
        message: 'This link is missing a claim reference.',
        actionHref: '/dashboard.html',
        actionLabel: 'Go to my items'
      });
      return;
    }

    host.innerHTML =
      '<div class="card space-y-4 p-6">' +
      '<div class="h-6 w-40 animate-pulse rounded-full bg-sunken"></div>' +
      '<div class="h-20 w-3/4 animate-pulse rounded-2xl bg-sunken"></div>' +
      '<div class="ml-auto h-16 w-2/3 animate-pulse rounded-2xl bg-sunken"></div>' +
      '</div>';

    LF.api
      .get('/api/claims/' + encodeURIComponent(reference))
      .then(render)
      .catch(function (err) {
        if (err.status === 404) {
          host.innerHTML = LF.emptyState({
            icon: 'i-search',
            title: 'No claim with that reference',
            message: 'Nothing matches ' + reference + '. The link may be mistyped.',
            actionHref: '/dashboard.html',
            actionLabel: 'Go to my items'
          });
          return;
        }
        host.innerHTML = LF.errorState(err.message);
        var retry = host.querySelector('[data-retry]');
        if (retry) retry.addEventListener('click', load);
      });
  }

  document.addEventListener('lf:ready', load);
  setInterval(refresh, 5000);
  document.addEventListener('visibilitychange', function () { if (!document.hidden) refresh(); });
  document.addEventListener('lf:auth-change', function (event) {
    if (!event.detail.user && current) {
      stopped = true;
      current = null;
      host.innerHTML = LF.emptyState({ title: 'Sign in to view your conversations', actionHref: '/login.html', actionLabel: 'Sign in' });
    }
  });
})();
