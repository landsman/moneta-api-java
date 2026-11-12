# How MONETA API behaves

What a client of MONETA API has to know, whatever library it uses. Observed on a live business account from
2026-11-06, with instant payments in and out, read pending and then booked, and checked on 2026-11-11 against
MONETA's specification, `VipAISPPayments_v4` 1.0.1 (OpenAPI 3.0.3, updated 2026-03-31), which only organisations
registered in its [developer portal](https://apiportal.moneta.cz/pages/moneta/home.html) can download. A claim
carries its date where it could change. What is still
unknown, and what this library does next, is the working document [plan/roadmap.md](plan/roadmap.md).

## Watch out for

1. **A movement's `entryReference` changes when it is booked.** Deduplicate by it and every payment counts
   twice, once pending and once booked.
2. **No movement carries a time of day**, only dates; the specification has none either. "Payments in the last hour" cannot be asked of MONETA; a
   poller records when it first saw each movement, which is as exact as its interval.
3. **The date of a payment is a different field before and after booking**: `enteredDate` while pending,
   `valueDate` once booked. `bookingDate` can be a later business day than the payment.
4. **The payer's message and name arrive only once booked.** A pending instant payment says "Okamžitá platba"
   where the message would be.
5. **`toDate` does not bound a movement booked for a later day**: every range starting on or before its booking
   day returns it, a range in 2020 included.
6. **An unknown query parameter is ignored without an error**, so a misspelt filter returns the whole account.
7. **A page past the last is a 404**, not an empty page.
8. **A nonsense date is a 500**, which a retrying client mistakes for an outage.
9. **A page can hold more than `size`**: the specification caps `size` at 100 and adds pending (blocked)
   movements on top.

## MONETA API and PSD2

MONETA runs two APIs over the same accounts:

| | MONETA API | PSD2 |
|---|---|---|
| Who uses it | the account holder, or an application they give a token to | a licensed AISP or PISP only |
| Price | free | — |
| Specification | behind the [developer portal](https://apiportal.moneta.cz/pages/moneta/home.html) | ČOBS v7 with deviations ([PDF](https://www.moneta.cz/documents/20143/12541023/mmb-psd2-differences.pdf), 2025-08-14) |

What MONETA API offers, per the portal: balances, transactions (card ones included), payment orders, standing
orders, pending card transactions, electronic statements, CZK payment orders including bulk. Getting a token
and a portal account: [access](access.md).

## Calling it

- **Host and paths:** `https://api.moneta.cz/api/v4/vip/aisp/my/accounts` and
  `…/accounts/{id}/transactions`; the account id is its 10-digit number.
- **Authentication:** `Authorization: Bearer <token>`, the token the merchant issued in Internet Banka. That
  token is the only way to production for a partner without a PSD2 licence ([access](access.md)).
- **The sandbox is the same host**, chosen by the token: one from `POST /auth/oauth/v2/token` with client
  credentials and scope `vip_sandbox`, valid 1200 seconds. It answers fixed test data and validates almost no
  parameter, so it shows the shapes, not the errors.
- **A token's expiry** is `GET /api/v1/vip/token/introspect`: `{"expiration": "2026-03-06T11:38:09+01:00"}` for
  the token sent, a 403 for one that is not a MONETA API token. Live on 2026-11-11 it answered
  `2027-02-08T17:25:00Z` for a merchant token issued in November 2026.
- **An application without an API plan in the portal gets a 401** in the sandbox, per the payment specifications.
- **The partner headers** `identification_number`, `business_name`, `application_name` are optional, per the
  specification; every live call was made without them (2026-11-06).
- **A response carries `X-Request-Id`**, what MONETA's support asks for about one call, and `Is-Sandbox: false`
  on production.

## Parameters and paging

- **`fromDate`, `toDate`, `page` and `size`** are the real names. `size=100` comes back as `pageSize: 100`; the
  default is 10.
- **`toDate` is inclusive**: `fromDate` = `toDate` = a day returned that day's pending movements (2026-11-06). No
  limit on a range's length was met, back to 2020.
- **A pending movement is filtered by `enteredDate`** (2026-11-06). The specification says the range filters on
  `valueDate`, which a pending movement does not have.
- **`sort` and `order`** exist: `sort=date` (`bookingDate`, or `enteredDate` for a card hold) or `sort=amount`,
  `order=ASC` or `DESC`. Not used by this client.
- **A movement booked for a later day comes back from every range that starts on or before its `bookingDate`**,
  whatever `toDate` says. On Saturday 2026-11-07 the account held ten movements valued 6 and 7 November and booked
  for Monday 9 November. Each of these ranges returned all ten: a single day from 3 to 9 November,
  `2020-01-01..2020-01-01`, `2026-10-01..2026-10-31`, `2026-11-09..2026-11-30`. A range starting on 10 November
  returned none. So a range is no upper bound: a poller sees such a movement on every read until its booking day
  passes. Which date filters a movement once its booking day is in the past is not checked yet
  ([#27](https://github.com/landsman/moneta-api-java/issues/27)).
- **Pages count from 0**, and `pageCount` is always sent. A page past the last is a 404 `PAGE_NOT_FOUND`.
- **An empty range is `"transactions": []`**, never a missing key.
- **An unknown parameter is ignored**: `dateFrom=…` returns the account unfiltered.

## Errors

| Request | Answer |
|---------|--------|
| a page past the last | 404 `{"errors":[{"error":"PAGE_NOT_FOUND"}]}` |
| `toDate` before `fromDate` | 400 `{"errors":[{"error":"DT01","message":"Neplatné datum."}]}` |
| `fromDate=garbage` | 500 `cbl.process.request.exception`, "Zkuste zadání vašeho požadavku opakovat později." |

Every error body is `{"errors": [{"error", "message", "parameters", "scope"}]}`, and `message` is "not for direct
output to the user". **The specification documents a 429** for too many requests, with no limit and no
`Retry-After`; none has been met yet.

## A balance

`GET …/accounts/{id}/balance` (not `balances`, which is a 400 `INVALID_REQUEST`) answers two entries, read on
2026-11-07 ([`live/balance.json`](../modules/moneta-api/src/test/resources/moneta/live/balance.json), amounts
replaced):

- **`type.codeOrProprietary.code`** is `CLBD` for the booked balance and `CLAV` for the available one.
- **The amount is unsigned beside `creditDebitIndicator`**, as a movement's.
- **`date` is `{"dateTime": "2026-11-07T15:32:12.391Z"}`**, an instant in UTC. The specification allows a bare
  date there too; this client reads one as the start of that day in Prague.
- **`creditLine`** is `{"amount": {"value": 0}, "included": true}` on an account without one, its amount
  without a currency. The specification does not list it, nor a movement's `bankTransactionCode`; both come
  live (2026-11-11).

## A movement

A booked credit as MONETA sends it, anonymised: the first movement in
[`live/booked.json`](../modules/moneta-api/src/test/resources/moneta/live/booked.json). Pending ones are in
[`live/pending-incoming.json`](../modules/moneta-api/src/test/resources/moneta/live/pending-incoming.json) and
[`live/pending-symbols-and-outgoing.json`](../modules/moneta-api/src/test/resources/moneta/live/pending-symbols-and-outgoing.json).
The tests read those files, so the example cannot drift from what the client is checked against.

### Pending and booked

The same payment, before and after booking (an instant payment made on Friday 2026-11-06, read that evening
and on the Saturday):

| Field | Pending (`PDNG`) | Booked (`BOOK`) |
|-------|------------------|-----------------|
| `entryReference` | 19 digits, `2600000000000000001` | `account:bookingDate:sequence:reference`, 43–45 characters |
| `enteredDate` | the payment's day | null on a credit; kept on a debit |
| `bookingDate` | null | a business day, possibly after the payment: Monday 2026-11-09 |
| `valueDate` | null | the payment's day |
| message (`unstructured`) | "Okamžitá platba" on an instant payment | what the payer wrote, or a QR payment's `MSG` |
| payer's name (`debtor.name`) | missing | `Surname, Name`, on a credit |
| no symbols (`reference`) | `"VS:"` | `""` |
| `clearingSystemReference` | `BLOCKED_PAYMENT_ORDER_DOMESTIC` | `PAYMENT_ORDER_DOMESTIC` |

An instant payment visible in Internet Banka at once stayed pending for at least 45 minutes, and was booked
three days later over a weekend. Internet Banka's CSV export matches the API field for field ("Bankovní
reference" is `entryReference`), and shows the same "Okamžitá platba" while pending.

### A card payment

One card payment, read while pending on 2026-11-07
([`live/pending-card.json`](../modules/moneta-api/src/test/resources/moneta/live/pending-card.json), the account,
reference and merchant replaced) and again once booked on 2026-11-10, the first working day after it
([`live/booked-card.json`](../modules/moneta-api/src/test/resources/moneta/live/booked-card.json), the account and
symbols replaced; the card number comes masked from MONETA and the merchant is kept).

| Field | Pending card payment | Pending transfer, for comparison |
|-------|----------------------|----------------------------------|
| `clearingSystemReference` | `BLOCKED_PAYMENT_CARD` | `BLOCKED_PAYMENT_ORDER_DOMESTIC` |
| `bankTransactionCode.proprietary.code` | `30000301000` | `10000301000` |
| `creditor.name` | the merchant descriptor, 40 characters | missing |
| `creditorAccount` | `{"identification": {}}` | the payee's account |
| `unstructured` | missing | "Okamžitá platba" on an instant payment |
| symbols (`reference`) | `""` | `"VS:"` |
| `amount.value` | `30.0` | `1` |

- **The pending merchant descriptor is fixed-width**: 25 characters of name, 13 of city, 2 of country, padded
  with spaces, as card networks lay out the card acceptor's name and location. A payment facilitator puts its own
  prefix before `*` in the name. Read from one payment, so the widths are an inference.
- **No card number, no merchant category, no time** while pending.
- **What the code's digits mean is not published** by MONETA or found in a ČNB or ČBA document; only the first
  digit has differed so far, 1 for a transfer and 3 for a card.
- **A pending card payment is a hold.** It may lapse without a booked payment; not observed yet.

Once booked, the same payment changed this much:

| Field | Pending | Booked |
|-------|---------|--------|
| `status`, `bookingDate` | `PDNG`, `null` | `BOOK`, `2026-11-10` |
| `clearingSystemReference` | `BLOCKED_PAYMENT_CARD` | `PAYMENT_CARD` |
| `bankTransactionCode.proprietary.code` | `30000301000` | `30000201000` |
| the merchant | `creditor.name`, 40 characters at fixed widths | `transactionDescription`, 30 characters, another text |
| `chequeNumber` | missing | the card number, masked by MONETA: `478544******1033` |
| `additionalTransactionInformation`, `purpose.proprietary` | missing | `PLATBA KARTOU ONLINE`, `Služby` |
| `endToEndIdentification`, symbols (`reference`) | missing, `""` | present |
| `amount.value`, `enteredDate`, `valueDate` | `30.0`, `2026-11-07`, `null` | `30`, `2026-11-07`, `null` |

- **The booked amount matched the hold**, and the booked movement replaced the pending one: the count stayed the
  same, nothing was left pending.
- **The merchant moves**: `counterpartyName()` reads `creditor.name` and falls back to `transactionDescription`
  for a card payment. The booked text is not the pending descriptor shortened, so neither is split into fields.
- **The code's sixth digit went from 3 to 2** on booking; a transfer's pending code ends `0301000` too, so it may
  mean pending. One payment, an inference.

### The rest

- **A debit is `DBIT` with a positive value**; the direction carries the sign.
- **Payment symbols are one string**: `"VS:1337 KS:1000 SS:76789"`; a missing one leaves two spaces
  (`"VS:1337  SS:76789"`). `endToEndIdentification` repeats the VS, `paymentInformationIdentification` the SS.
- **The counterparty's account is free text**, `number/bank`, sometimes with a space after a prefix.
- **An amount can come as `1`** rather than `1.00`.
- **No time of day exists anywhere**, pending or booked (checked again on booked movements, 2026-11-07): not in
  the body, the headers or the `entryReference`, which grows with
  time but decodes to no timestamp. Internet Banka's export has none either.
- **`reversalIndicator: true` marks a cancellation**, per the specification; none has been seen live.
- **`clearingSystemReference` is one of 25 values** in the specification: transfers, foreign payments, standing
  orders, card payments, SIPO, direct debits, interest, fees, loans and deposits, each `BLOCKED_` variant for a
  pending one.
- **An account has no `nameI18N` unless one is set**; `productI18N` names the product ("Běžný účet").

## Deduplicating payments

Since a payment's id changes when it is booked, a poller has three choices:

- **Act on booked movements only.** Simplest and final, but a booked payment can arrive days later.
- **Act on pending ones, and match the booked version back** by amount, symbol and payer's account, then
  replace the stored id. Compare the account as one value: MONETA writes it differently pending
  (`4040404040/0800`) and booked (`0 4040404040/0800`). [examples/cafe](../examples/cafe/README.md) keeps one row
  per payment this way.
- **Treat pending ones as provisional**, shown but not counted, and count only booked ones.

## Where the first guesses came from

The only public client before this one is [sloukapetr/moneta-laravel-package](https://github.com/sloukapetr/moneta-laravel-package)
(PHP, MIT). Its fixtures are its author's own, so it was read as leads, not a specification: the host and paths,
`Authorization: Bearer`, the partner headers, a transaction's keys, the symbols in
`creditorReferenceInformation.reference`, paging by `pageNumber`, `pageSize` and `pageCount`. It also names
payments under `…/vip/pisp/…` and OAuth client credentials at `/auth/oauth/v2/token` with scope `vip_sandbox`.
MONETA's own PSD2 deviations from ČOBS hinted at the rest, and held: symbols as one string, `enteredDate` as an
extra field, 404 for not found.
