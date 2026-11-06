# What is known about MONETA API

**Partly verified.** MONETA publishes the specification only to organisations registered in its
[developer portal](https://apiportal.moneta.cz/pages/moneta/home.html). The paths, headers and response
shapes started from the one public client, [sloukapetr/moneta-laravel-package](https://github.com/sloukapetr/moneta-laravel-package);
on 2026-11-06 they were checked against a live business account with pending incoming and outgoing
payments, and held, except the pending dates now handled. Booked movements are not yet checked. Not on Maven
Central until they are.

## What the live API showed (2026-11-06)

- `fromDate`, `toDate`, `page` and `size` are the real names: `size=100` comes back as `pageSize: 100`, a
  reversed range is a 400 `DT01`. An unknown name is ignored without an error, so a typo returns the account
  unfiltered.
- `toDate` is inclusive: `fromDate` = `toDate` = a day returns that day's movements.
- Pages count from 0. A page past the last is a 404 `PAGE_NOT_FOUND`, not an empty page; the client stops by
  `pageCount`, which MONETA always sends.
- An empty range is `"transactions": []`.
- The partner headers are not required: every call above was made without them.
- A pending movement (`PDNG`) has `bookingDate` and `valueDate` as `{"date": null}`; only `enteredDate` is set.
- A debit comes as `DBIT` with a positive value, as assumed.
- The symbols are one string: `"VS:1337 KS:1000 SS:76789"`, `"VS:1337  SS:76789"` with KS left out,
  `"VS:"` with none.
- No counterparty name is sent, only its account number. The message for the recipient is "Okamžitá platba"
  on instant payments, in Internet Banka's own export as well.
- An account has no `nameI18N` unless one is set, so `Account.name()` is null; `productI18N` names the
  product.
- A nonsense date is a 500, not a 400.

## What is still open

- Booked movements: when a pending one becomes `BOOK`, its dates, whether a message appears, and whether its
  `entryReference` stays the same.
- The time of a movement: none is sent anywhere ([#9](https://github.com/landsman/moneta-api-java/issues/9)).
- Which date the range filters a booked movement on (booking or value); a pending one is filtered by its
  `enteredDate`.
- How `business_name` is sent with diacritics; the client accepts only ASCII there for now.
- Whether a token can be read-only, and how long it lives.
- The rate limits: per token, per application or per partner, over what window, and whether a 429 carries
  `Retry-After`. Until they are known a 429 fails the whole read. If a normal read can hit them, the client
  will retry the page it stopped on, only after a short `Retry-After` and a few times, and that can be turned
  off; a limit shared across merchants still needs a limiter on the caller's side.

## Not covered yet

Payments (PISP), balances, statements, OAuth client credentials for the sandbox. Each waits for the
specification.

GraalVM native image is untested. The JSON records are bound by reflection and the jar carries no
reachability metadata, so in a native image every call would likely fail as `InvalidResponse`. Supporting it
means shipping `META-INF/native-image/…/reachability-metadata.json` and running the tests once natively.
