# What is known about MONETA API

**Partly verified.** MONETA publishes the specification only to organisations registered in its
[developer portal](https://apiportal.moneta.cz/pages/moneta/home.html). The paths, headers and response
shapes started from the one public client, [sloukapetr/moneta-laravel-package](https://github.com/sloukapetr/moneta-laravel-package);
on 2026-11-06 they were checked against a live business account with pending incoming and outgoing
payments, and held, except the pending dates now handled. Booked movements are not yet checked. Not on Maven
Central until they are.

## MONETA API and PSD2

MONETA runs two APIs over the same accounts:

| | MONETA API | PSD2 |
|---|---|---|
| Who uses it | the account holder, or an application they give a token to | a licensed AISP or PISP only |
| Price | free | — |
| Specification | behind the [developer portal](https://apiportal.moneta.cz/pages/moneta/home.html) | ČOBS v7 with deviations ([PDF](https://www.moneta.cz/documents/20143/12541023/mmb-psd2-differences.pdf), 2025-08-14) |

This client is for MONETA API. What it offers, per the portal: balances, transactions (card ones included),
payment orders, standing orders, pending card transactions, electronic statements, CZK payment orders including
bulk. The client reads accounts and transactions so far. Getting a token and a portal account:
[access](access.md).

MONETA's PSD2 deviations from ČOBS, as a hint of what MONETA API looks like (an inference; several held on the
live API):

- Transactions are `GET /my/accounts/{id}/transactions`; the id is `transactionIdentification`, not `paymentId`;
  `additionalRemittanceInformation`, `charges`, `amountDetails` and others are missing; `enteredDate` is extra.
- Payment symbols are one string, `"VS:123456,KS:456789,SS:879213546"`, not ČOBS's array.
- Not found is a 404, not ČOBS's 400.

## Where the first guesses came from

The only public client before this one is [sloukapetr/moneta-laravel-package](https://github.com/sloukapetr/moneta-laravel-package)
(PHP, MIT). Its fixtures are its author's own, so it was read as leads, not a specification: the host and paths
under `api/v4/vip/aisp/my/`, `Authorization: Bearer`, the partner headers, a transaction's keys, the symbols in
`creditorReferenceInformation.reference`, paging by `pageNumber`, `pageSize` and `pageCount`. It also names
payments under `…/vip/pisp/…` and OAuth client credentials at `/auth/oauth/v2/token` with scope `vip_sandbox`,
which this client does not use. It does not page; this client does.

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
