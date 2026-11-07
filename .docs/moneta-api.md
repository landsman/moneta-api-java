# What is known about MONETA API

**Partly verified.** MONETA publishes the specification only to organisations registered in its
[developer portal](https://apiportal.moneta.cz/pages/moneta/home.html). The paths, headers and response
shapes started from the one public client, [sloukapetr/moneta-laravel-package](https://github.com/sloukapetr/moneta-laravel-package);
on 2026-11-06 and 2026-11-07 they were checked against a live business account with incoming and outgoing
payments, pending and then booked, and held, except what the two sections below record and the client now
handles. Published on GitHub Packages, not Maven Central.

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
- While pending, no counterparty name is sent, only its account number, and the message for the recipient is
  "Okamžitá platba" on instant payments, in Internet Banka's own export as well. Both change once booked, below.
- An account has no `nameI18N` unless one is set, so `Account.name()` is null; `productI18N` names the
  product.
- A nonsense date is a 500, not a 400.

## What the booked movements showed (2026-11-07)

The same payments the next day, booked:

- **`entryReference` changes.** Pending it is 19 digits (`2606928699810906118`); booked it is
  `account:bookingDate:sequence:reference` (`0285985332:20261109:00001:26110641224473791`, 43 to 45 characters).
  None of the pending references remains. **Deduplicate by `entryReference` and every payment counts twice**,
  once pending and once booked; `examples/cafe` shows one way round it, matching the booked payment to the bill
  its pending one paid by symbol and amount.
- **`bookingDate` can be a later business day**: payments made on Friday 2026-11-06 were booked for Monday
  2026-11-09, read on the Saturday. `valueDate` is the payment's day, and the date range filters a booked
  movement by it.
- **`enteredDate` is null on a booked credit**; a booked debit keeps it. The day a payment was made is
  `valueDate` once booked, `enteredDate` while pending.
- **The payer's message and name arrive once booked**: `unstructured` holds what the payer typed (or a QR
  payment's `MSG`), and `relatedParties.debtor.name` the payer, as `Surname, Name`. The client maps them as
  `message()` and `counterpartyName()`.
- **No symbols is `""`** once booked; `clearingSystemReference` becomes `PAYMENT_ORDER_DOMESTIC`; an amount can
  come as `1`, which `Money` writes as `1.00`.

## What is still open

- The time of a movement: none is sent anywhere ([#9](https://github.com/landsman/moneta-api-java/issues/9)).
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
