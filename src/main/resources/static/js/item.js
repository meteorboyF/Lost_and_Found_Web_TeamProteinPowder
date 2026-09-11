/**
 * item.js — one item in detail.
 */
(function () {
  'use strict';

  var LF = window.LF;
  var e = LF.escapeHtml;
  var host = document.querySelector('[data-item]');

  function skeleton() {
    return (
      '<div class="grid gap-8 lg:grid-cols-2">' +
      '<div class="aspect-[4/3] animate-pulse rounded-2xl bg-sunken"></div>' +
      '<div class="space-y-4 py-2">' +
      '<div class="h-6 w-28 animate-pulse rounded-full bg-sunken"></div>' +
      '<div class="h-9 w-3/4 animate-pulse rounded-lg bg-sunken"></div>' +
      '<div class="h-4 w-full animate-pulse rounded bg-sunken"></div>' +
      '<div class="h-4 w-5/6 animate-pulse rounded bg-sunken"></div>' +
      '</div></div>'
    );
  }

  function media(item) {
    var cat = LF.category(item.category);
    if (item.photoUrl) {
      return '<img src="' + e(item.photoUrl) + '" alt="Photograph of ' + e(item.title) + '" ' +
        'class="h-full w-full object-cover">';
    }
    return (
      '<div class="flex h-full w-full flex-col items-center justify-center gap-3 bg-gradient-to-br ' +
      cat.grad + '">' +
      '<svg class="icon h-16 w-16 ' + cat.solid + ' opacity-70" aria-hidden="true">' +
      '<use href="/assets/icons.svg#' + cat.icon + '"></use></svg>' +
      '<span class="text-sm text-muted">No photograph provided</span></div>'
    );
  }

  function row(label, value, icon) {
    return (
      '<div class="flex items-start gap-3 py-3">' +
      '<svg class="icon mt-0.5 h-[1.15rem] w-[1.15rem] text-faint" aria-hidden="true">' +
      '<use href="/assets/icons.svg#' + icon + '"></use></svg>' +
      '<div class="min-w-0">' +
      '<p class="text-xs text-faint">' + e(label) + '</p>' +
      '<p class="text-sm font-medium text-heading">' + e(value) + '</p>' +
      '</div></div>'
    );
  }

  function render(item) {
    document.title = item.title + ' — Lost & Found';
    var crumb = document.querySelector('[data-crumb]');
    if (crumb) crumb.textContent = item.reference;

    var resolved = item.status === 'RESOLVED';

    host.innerHTML =
      '<div class="grid gap-8 lg:grid-cols-[minmax(0,1fr)_22rem] lg:gap-10">' +

        '<div>' +
          '<div class="overflow-hidden rounded-2xl border border-line bg-surface shadow-[var(--shadow-card)]">' +
            '<div class="aspect-[4/3]">' + media(item) + '</div>' +
          '</div>' +

          '<div class="mt-6 flex flex-wrap items-center gap-2">' +
            LF.kindPill(item.kind, true) +
            LF.statusPill(item.status, true) +
            LF.categoryPill(item) +
          '</div>' +

          '<h1 class="mt-4 text-3xl text-heading sm:text-4xl">' + e(item.title) + '</h1>' +

          '<p class="mt-5 whitespace-pre-line leading-relaxed text-body">' + e(item.description) + '</p>' +
        '</div>' +

        '<aside class="lg:pt-2">' +
          '<div class="card divide-y divide-line">' +
            '<div class="px-5 py-2">' +
              row('Where', item.location, 'i-pin') +
              (item.happenedOn ? row('When', LF.formatDate(item.happenedOn), 'i-calendar') : '') +
              (item.colour ? row('Colour', item.colour, 'i-tag') : '') +
              row('Posted', LF.timeAgo(item.createdAt), 'i-clock') +
              row('Posted by', item.reporterName, 'i-user') +
              row('Reference', item.reference, 'i-qr') +
            '</div>' +

            '<div class="p-5">' +
              (resolved
                ? '<div class="rounded-xl bg-found-soft p-4 text-center">' +
                  '<svg class="icon mx-auto h-6 w-6 text-found" aria-hidden="true"><use href="/assets/icons.svg#i-check"></use></svg>' +
                  '<p class="mt-2 text-sm font-semibold text-found-text">Back with its owner</p>' +
                  '<p class="mt-1 text-xs text-found-text/80">Nothing more to do here.</p></div>'
                : '<button type="button" data-claim class="btn btn-primary w-full">' +
                  '<svg class="icon h-[1.15rem] w-[1.15rem]" aria-hidden="true"><use href="/assets/icons.svg#i-hand"></use></svg>' +
                  (item.kind === 'FOUND' ? 'This is mine' : 'I think I found this') +
                  '</button>') +
              '<a href="/browse.html" class="btn btn-secondary mt-2 w-full">Back to the board</a>' +
              '<p class="mt-4 text-xs leading-relaxed text-faint">' +
                'Contact details are never shown on the board. Claiming opens a conversation through the registry.' +
              '</p>' +
            '</div>' +
          '</div>' +
        '</aside>' +
      '</div>';

    var claimBtn = host.querySelector('[data-claim]');
    if (claimBtn) claimBtn.addEventListener('click', function () { openClaimDialog(item); });

    /* If this browser posted the item, show who is asking about it rather
       than offering to claim your own thing. */
    if (LF.mine.hasItem(item.reference)) loadClaimsForOwner(item);

    if (item.status !== 'RESOLVED') loadMatches(item);
  }

  /* ------------------------------------------------------------------
     Suggested matches from the other side of the board
     ------------------------------------------------------------------ */

  function loadMatches(item) {
    LF.api
      .get('/api/items/' + encodeURIComponent(item.reference) + '/matches')
      .then(function (matches) {
        if (!matches.length) return;

        var section = document.createElement('section');
        section.className = 'mt-12 border-t border-line pt-8';
        section.innerHTML =
          '<div class="flex items-center gap-2">' +
            '<span class="grid h-8 w-8 place-items-center rounded-lg bg-brand-soft text-brand">' +
              '<svg class="icon h-4 w-4" aria-hidden="true"><use href="/assets/icons.svg#i-sparkle"></use></svg></span>' +
            '<h2 class="text-xl text-heading">' +
              (matches.length === 1 ? 'One possible match' : matches.length + ' possible matches') +
            '</h2>' +
          '</div>' +
          '<p class="mt-2 text-sm text-muted">' +
            'Found by comparing this against every ' +
            (item.kind === 'LOST' ? 'found' : 'lost') + ' post on the board.' +
          '</p>' +
          '<div class="mt-5 grid gap-3 sm:grid-cols-2">' +
            matches.map(LF.matchCard).join('') +
          '</div>';

        host.parentElement.appendChild(section);
      })
      .catch(function () {
        /* Suggestions are a bonus; never let them take the page down. */
      });
  }

  /* ------------------------------------------------------------------
     Claim dialog. A native <dialog>, so the browser supplies the top layer,
     the backdrop, Escape, and inertness for everything behind it.
     ------------------------------------------------------------------ */

  function openClaimDialog(item) {
    var known = LF.mine.all();
    var asking = item.kind === 'FOUND'
      ? 'Describe something about it that is not in the photo or the description — a mark, what was inside, where exactly you lost it.'
      : 'Describe what you found, and where. The person who lost it will recognise the details.';

    var dialog = document.createElement('dialog');
    dialog.className =
      'w-[min(34rem,calc(100vw-2rem))] rounded-2xl border border-line bg-surface p-0 text-body ' +
      'shadow-[var(--shadow-pop)] backdrop:bg-black/50 backdrop:backdrop-blur-sm';

    dialog.innerHTML =
      '<form method="dialog" class="flex items-start justify-between gap-4 border-b border-line p-5">' +
        '<div>' +
          '<h2 class="text-xl text-heading" id="claim-title">' +
            (item.kind === 'FOUND' ? 'Is this yours?' : 'Did you find this?') + '</h2>' +
          '<p class="mt-1 text-sm text-muted">' + e(item.title) + '</p>' +
        '</div>' +
        '<button class="grid h-9 w-9 shrink-0 place-items-center rounded-lg text-faint transition hover:bg-sunken hover:text-heading" ' +
                'aria-label="Close" value="cancel">' +
          '<svg class="icon" aria-hidden="true"><use href="/assets/icons.svg#i-close"></use></svg>' +
        '</button>' +
      '</form>' +

      '<form data-claim-form class="grid gap-4 p-5" novalidate>' +
        '<div class="rounded-xl bg-brand-soft p-3.5 text-sm leading-relaxed text-brand-text">' +
          '<svg class="icon mb-1 h-[1.15rem] w-[1.15rem]" aria-hidden="true"><use href="/assets/icons.svg#i-shield"></use></svg>' +
          '<p>' + asking + '</p>' +
        '</div>' +

        '<div class="grid gap-1.5">' +
          '<label for="claim-proof" class="label">What only you would know</label>' +
          '<textarea id="claim-proof" name="proof" rows="4" maxlength="2000" class="field resize-y" ' +
            'placeholder="There is a faded band sticker on the inside flap, and a maths notebook in the front pocket."></textarea>' +
          '<p class="min-h-5 text-sm text-lost" data-error="proof"></p>' +
        '</div>' +

        '<div class="grid gap-4 sm:grid-cols-2">' +
          '<div class="grid gap-1.5">' +
            '<label for="claim-name" class="label">Your name</label>' +
            '<input id="claim-name" name="claimantName" maxlength="80" class="field" value="' +
              e(known.name) + '">' +
            '<p class="min-h-5 text-sm text-lost" data-error="claimantName"></p>' +
          '</div>' +
          '<div class="grid gap-1.5">' +
            '<label for="claim-email" class="label">Email</label>' +
            '<input id="claim-email" name="claimantEmail" type="email" maxlength="160" class="field" value="' +
              e(known.email) + '" placeholder="you@university.edu">' +
            '<p class="min-h-5 text-sm text-lost" data-error="claimantEmail"></p>' +
          '</div>' +
        '</div>' +

        '<p class="text-xs leading-relaxed text-faint">' +
          'Your email is never shown to the other person. You will get a reference code for the conversation.' +
        '</p>' +

        '<div class="mt-1 flex flex-wrap gap-3">' +
          '<button type="submit" class="btn btn-primary" data-claim-submit>Send this claim</button>' +
          '<button type="button" class="btn btn-ghost" data-claim-cancel>Cancel</button>' +
        '</div>' +
      '</form>';

    dialog.setAttribute('aria-labelledby', 'claim-title');
    document.body.appendChild(dialog);
    dialog.showModal();
    dialog.querySelector('#claim-proof').focus();

    dialog.addEventListener('close', function () { dialog.remove(); });
    dialog.querySelector('[data-claim-cancel]').addEventListener('click', function () {
      dialog.close('cancel');
    });

    dialog.querySelector('[data-claim-form]').addEventListener('submit', function (ev) {
      ev.preventDefault();
      submitClaim(item, dialog);
    });
  }

  function submitClaim(item, dialog) {
    var form = dialog.querySelector('[data-claim-form]');
    var setErr = function (field, message) {
      var slot = dialog.querySelector('[data-error="' + field + '"]');
      if (slot) slot.textContent = message || '';
      var input = form.elements[field];
      if (input) {
        if (message) input.setAttribute('aria-invalid', 'true');
        else input.removeAttribute('aria-invalid');
      }
    };

    var payload = {
      proof: form.elements.proof.value.trim(),
      claimantName: form.elements.claimantName.value.trim(),
      claimantEmail: form.elements.claimantEmail.value.trim()
    };

    /* Mirrors the Bean Validation rules on ClaimRequest; the server stays
       the authority, this just saves a round trip. */
    var errors = {};
    if (payload.proof.length < 20) {
      errors.proof = 'Give at least 20 characters — enough that only the owner could have written it';
    }
    if (!payload.claimantName) errors.claimantName = 'Tell us your name';
    if (!payload.claimantEmail) errors.claimantEmail = 'We need an email address to reach you';
    else if (!/^[^\s@]+@[^\s@]+\.[a-z]{2,}$/i.test(payload.claimantEmail)) {
      errors.claimantEmail = 'That does not look like a valid email address';
    }

    ['proof', 'claimantName', 'claimantEmail'].forEach(function (f) { setErr(f, errors[f]); });
    if (Object.keys(errors).length) {
      var first = dialog.querySelector('[aria-invalid="true"]');
      if (first) first.focus();
      return;
    }

    var submit = dialog.querySelector('[data-claim-submit]');
    submit.disabled = true;
    submit.textContent = 'Sending…';

    LF.api
      .post('/api/items/' + encodeURIComponent(item.reference) + '/claims', payload)
      .then(function (claim) {
        LF.mine.addClaim(claim.reference);
        LF.mine.remember(payload.claimantName, payload.claimantEmail);
        dialog.close('sent');
        window.location.href = '/claim.html?ref=' + encodeURIComponent(claim.reference);
      })
      .catch(function (err) {
        submit.disabled = false;
        submit.textContent = 'Send this claim';
        if (err.fields) {
          Object.keys(err.fields).forEach(function (f) { setErr(f, err.fields[f]); });
        }
        LF.toast.error(err.message);
      });
  }

  /* ------------------------------------------------------------------
     Owner view: who is asking about this item
     ------------------------------------------------------------------ */

  function loadClaimsForOwner(item) {
    LF.api
      .get('/api/items/' + encodeURIComponent(item.reference) + '/claims')
      .then(function (claims) {
        if (!claims.length) return;

        var open = claims.filter(function (c) { return c.status === 'OPEN'; });
        var list = claims.map(function (c) {
          return (
            '<a href="/claim.html?ref=' + encodeURIComponent(c.reference) + '" ' +
               'class="flex items-center gap-3 rounded-xl border border-line p-3 transition hover:border-line-strong hover:bg-sunken">' +
              '<span class="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-brand-soft text-sm font-semibold text-brand-text">' +
                e(c.claimantName.charAt(0).toUpperCase()) + '</span>' +
              '<span class="min-w-0 flex-1">' +
                '<span class="block truncate text-sm font-medium text-heading">' + e(c.claimantName) + '</span>' +
                '<span class="block text-xs text-muted">' + e(LF.timeAgo(c.createdAt)) + '</span>' +
              '</span>' +
              '<span class="pill ' +
                (c.status === 'OPEN' ? 'bg-amber-soft text-amber-text' :
                 c.status === 'ACCEPTED' ? 'bg-found-soft text-found-text' :
                 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300') +
              '">' + e(c.status.charAt(0) + c.status.slice(1).toLowerCase()) + '</span>' +
            '</a>'
          );
        }).join('');

        var panel = document.createElement('div');
        panel.className = 'card mt-4 p-4';
        panel.innerHTML =
          '<div class="flex items-center justify-between gap-3">' +
            '<p class="text-sm font-semibold text-heading">' +
              claims.length + (claims.length === 1 ? ' person has' : ' people have') + ' claimed this</p>' +
            (open.length
              ? '<span class="pill bg-amber-soft text-amber-text">' + open.length + ' waiting</span>'
              : '') +
          '</div>' +
          '<div class="mt-3 grid gap-2">' + list + '</div>';

        var aside = host.querySelector('aside');
        if (aside) aside.appendChild(panel);
      })
      .catch(function () {
        /* The claim list is supplementary; failing to load it must not take
           the item page down with it. */
      });
  }

  function notFound(reference) {
    host.innerHTML = LF.emptyState({
      icon: 'i-search',
      title: 'No item with that reference',
      message: reference
        ? 'Nothing on the board matches ' + reference + '. It may have been removed, or the link may be mistyped.'
        : 'This link is missing a reference code.',
      actionHref: '/browse.html',
      actionLabel: 'Browse the board'
    });
  }

  function load() {
    var reference = LF.qs('ref');
    if (!reference) {
      notFound(null);
      return;
    }

    host.innerHTML = skeleton();

    LF.api
      .get('/api/items/' + encodeURIComponent(reference))
      .then(render)
      .catch(function (err) {
        if (err.status === 404) {
          notFound(reference);
          return;
        }
        host.innerHTML = LF.errorState(err.message);
        var retry = host.querySelector('[data-retry]');
        if (retry) retry.addEventListener('click', load);
      });
  }

  document.addEventListener('lf:ready', load);
})();
