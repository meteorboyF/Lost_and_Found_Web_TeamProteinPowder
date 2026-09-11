/**
 * claim.js — one claim conversation.
 *
 * There is no sign-in yet, so "which side am I on?" is decided by what the
 * browser remembers: the references it posted versus the claims it opened.
 * That is recorded by LF.mine when each action happens.
 */
(function () {
  'use strict';

  var LF = window.LF;
  var e = LF.escapeHtml;
  var host = document.querySelector('[data-claim]');
  var current = null;

  var STATUS = {
    OPEN: { label: 'Waiting for a reply', cls: 'bg-amber-soft text-amber-text', icon: 'i-st-pending' },
    ACCEPTED: { label: 'Confirmed yours', cls: 'bg-found-soft text-found-text', icon: 'i-check' },
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
    current = claim;
    document.title = 'Claim ' + claim.reference + ' — Lost & Found';
    var crumb = document.querySelector('[data-crumb]');
    if (crumb) crumb.textContent = claim.reference;

    var viewerIsPoster = LF.mine.hasItem(claim.item.reference);
    var open = claim.status === 'OPEN';
    var cat = LF.category(claim.item.category);

    var thumb = claim.item.photoUrl
      ? '<img src="' + e(claim.item.photoUrl) + '" alt="" class="h-16 w-16 rounded-xl object-cover">'
      : '<span class="grid h-16 w-16 place-items-center rounded-xl bg-gradient-to-br ' + cat.grad + '">' +
        '<svg class="icon h-7 w-7 ' + cat.solid + '" aria-hidden="true"><use href="/assets/icons.svg#' +
        cat.icon + '"></use></svg></span>';

    host.innerHTML =
      '<div class="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem] lg:gap-8">' +

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

          '<ul class="flex flex-col gap-4 overflow-y-auto p-4 sm:p-6" data-thread role="log" ' +
              'aria-live="polite" aria-label="Conversation">' +
            claim.messages.map(function (m) { return bubble(m, viewerIsPoster); }).join('') +
          '</ul>' +

          (open
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
              'This conversation is closed.</p>') +
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

          (open && viewerIsPoster
            ? '<div class="card p-4">' +
                '<p class="text-sm font-semibold text-heading">Is this theirs?</p>' +
                '<p class="mt-1 text-xs leading-relaxed text-muted">' +
                  'Read what they described. Accepting marks the item resolved and closes any other claims.' +
                '</p>' +
                '<button type="button" data-accept class="btn btn-primary mt-3 w-full">' +
                  '<svg class="icon h-[1.15rem] w-[1.15rem]" aria-hidden="true"><use href="/assets/icons.svg#i-check"></use></svg>' +
                  'Yes, it is theirs</button>' +
                '<button type="button" data-decline class="btn btn-secondary mt-2 w-full text-lost">Not a match</button>' +
              '</div>'
            : '') +

          (open && !viewerIsPoster
            ? '<div class="card p-4">' +
                '<p class="text-sm font-semibold text-heading">Changed your mind?</p>' +
                '<p class="mt-1 text-xs leading-relaxed text-muted">' +
                  'Withdrawing closes this conversation and puts the item back on the board.</p>' +
                '<button type="button" data-withdraw class="btn btn-secondary mt-3 w-full text-lost">Withdraw claim</button>' +
              '</div>'
            : '') +

          '<p class="px-1 text-xs leading-relaxed text-faint">' +
            'Keep this reference safe — it is how you get back to this conversation. ' +
            'Email addresses are never shared between the two of you.' +
          '</p>' +
        '</aside>' +
      '</div>';

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
        if (ev.key === 'Enter' && !ev.shiftKey) {
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
        act(claim, 'accept', accept, 'Confirmed. The item is marked as returned.');
      });
    }

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

    LF.api
      .post('/api/claims/' + encodeURIComponent(claim.reference) + '/messages',
            { body: body, fromPoster: viewerIsPoster })
      .then(function (message) {
        claim.messages.push(message);
        var thread = host.querySelector('[data-thread]');
        thread.insertAdjacentHTML('beforeend', bubble(message, viewerIsPoster));
        textarea.value = '';
        textarea.style.height = 'auto';
        scrollThread();
      })
      .catch(function (err) {
        LF.toast.error(err.message);
      })
      .finally(function () {
        send.disabled = false;
        textarea.focus();
      });
  }

  function act(claim, action, button, successMessage) {
    button.disabled = true;
    LF.api
      .post('/api/claims/' + encodeURIComponent(claim.reference) + '/' + action)
      .then(function (updated) {
        LF.toast.success(successMessage);
        render(updated);
      })
      .catch(function (err) {
        button.disabled = false;
        LF.toast.error(err.message);
      });
  }

  /* ------------------------------------------------------------------
     Boot
     ------------------------------------------------------------------ */

  function load() {
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
})();
