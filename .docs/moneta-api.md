# How MONETA API behaves

What a client of MONETA API has to know, whatever library it uses. MONETA publishes its specification only to
organisations registered in its [developer portal](https://apiportal.moneta.cz/pages/moneta/home.html), so
everything here was observed: on a live business account on 2026-11-06 and 2026-11-07, with instant payments
in and out, read pending and then booked. A claim carries its date where it could change. What is still
unknown, and what this library does next, is the working document [plan/roadmap.md](plan/roadmap.md).

## Watch out for

1. **A movement's `entryReference` changes when it is booked.** Deduplicate by it and every payment counts
   twice, once pending and once booked.
2. **No movement carries a time of day**, only dates. "Payments in the last hour" cannot be asked of MONETA.
3. **The date of a payment is a different field before and after booking**: `enteredDate` while pending,
   `valueDate` once booked. `bookingDate` can be a later business day than the payment.
4. **The payer's message and name arrive only once booked.** A pending instant payment says "Okamžitá platba"
   where the message would be.
5. **An unknown query parameter is ignored without an error**, so a misspelt filter returns the whole account.
6. **A page past the last is a 404**, not an empty page.
7. **A nonsense date is a 500**, which a retrying client mistakes for an outage.

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
- **Authentication:** `Authorization: Bearer <token>`, the token the merchant issued in Internet Banka.
- **The partner headers** `identification_number`, `business_name`, `application_name` are not required: every
  call was made without them (2026-11-06).
- **A response carries `X-Request-Id`**, what MONETA's support asks for about one call, and `Is-Sandbox: false`
  on production.

## Parameters and paging

- **`fromDate`, `toDate`, `page` and `size`** are the real names. `size=100` comes back as `pageSize: 100`; the
  default is 10.
- **`toDate` is inclusive**: `fromDate` = `toDate` = a day returns that day's movements. No limit on a range's
  length was met, back to 2020.
- **A pending movement is filtered by `enteredDate`, a booked one by `valueDate`**: a range ending on Saturday
  returned movements booked for the Monday after.
- **Pages count from 0**, and `pageCount` is always sent. A page past the last is a 404 `PAGE_NOT_FOUND`.
- **An empty range is `"transactions": []`**, never a missing key.
- **An unknown parameter is ignored**: `dateFrom=…` returns the account unfiltered.

## Errors

| Request | Answer |
|---------|--------|
| a page past the last | 404 `{"errors":[{"error":"PAGE_NOT_FOUND"}]}` |
| `toDate` before `fromDate` | 400 `{"errors":[{"error":"DT01","message":"Neplatné datum."}]}` |
| `fromDate=garbage` | 500 `cbl.process.request.exception`, "Zkuste zadání vašeho požadavku opakovat později." |

## A movement

```json
{
  "entryReference": "0000001337:20261109:00001:26110600000000001",
  "status": "BOOK",
  "creditDebitIndicator": "CRDT",
  "amount": {
    "value": 1,
    "currency": "CZK"
  },
  "enteredDate": {
    "date": null
  },
  "bookingDate": {
    "date": "2026-11-09"
  },
  "valueDate": {
    "date": "2026-11-06"
  },
  "reversalIndicator": false,
  "entryDetails": {
    "transactionDetails": {
      "references": {
        "clearingSystemReference": "PAYMENT_ORDER_DOMESTIC",
        "endToEndIdentification": "1007"
      },
      "relatedParties": {
        "debtor": {
          "name": "Novak, Jan"
        },
        "debtorAccount": {
          "identification": {
            "other": {
              "identification": "4040404040/0800"
            }
          }
        },
        "creditorAccount": {
          "identification": {
            "other": {
              "identification": "1337/0600"
            }
          }
        }
      },
      "remittanceInformation": {
        "structured": {
          "creditorReferenceInformation": {
            "reference": "VS:1007"
          }
        },
        "unstructured": "KAVARNA UCET 1007"
      }
    }
  }
}
```

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

### The rest

- **A debit is `DBIT` with a positive value**; the direction carries the sign.
- **Payment symbols are one string**: `"VS:1337 KS:1000 SS:76789"`; a missing one leaves two spaces
  (`"VS:1337  SS:76789"`). `endToEndIdentification` repeats the VS, `paymentInformationIdentification` the SS.
- **The counterparty's account is free text**, `number/bank`, sometimes with a space after a prefix.
- **An amount can come as `1`** rather than `1.00`.
- **No time of day exists anywhere**: not in the body, the headers or the `entryReference`, which grows with
  time but decodes to no timestamp. Internet Banka's export has none either.
- **An account has no `nameI18N` unless one is set**; `productI18N` names the product ("Běžný účet").

## Deduplicating payments

Since a payment's id changes when it is booked, a poller has three choices:

- **Act on booked movements only.** Simplest and final, but a booked payment can arrive days later.
- **Act on pending ones, and match the booked version back** by amount, symbol, payer's account and day, then
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
