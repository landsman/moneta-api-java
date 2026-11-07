# Test responses

Two kinds, told apart by the folder:

- **`live/`** — recorded from the live API, then anonymised. The shape is MONETA's own; every identifying value
  is replaced as below.
- **The rest** — written by hand in the shape the only public client (a PHP package) assumes, before anything
  was recorded. Replace each with a recorded one as the live API shows it.

## The accounts every test uses

No fixture or test holds a real account number, and none invents a new one: they use these. All three pass the
Czech National Bank's modulo-11 check and their IBANs carry correct check digits, so they look real to any
validator and belong to nobody.

| Who | Account | IBAN | Why this number |
|-----|---------|------|-----------------|
| the merchant, at MONETA | `1337/0600` | `CZ0206000000000000001337` | leet |
| a payer | `4040404040/0800` | `CZ4008000000004040404040` | 404 Not Found |
| another counterparty | `1010101010/0100` | `CZ2301000000001010101010` | binary |

A balance's amounts are replaced with `1337`. A movement's `entryReference` is replaced with `2600000000000000001`, counting up for more than one; a name,
when one appears, with `Jan Novak`.

Recording one: `make record-fixture N=<number from make peek> NAME=<file>` applies these rules and refuses a party
value it has no rule for ([development](../../../../../../.docs/development.md#trying-the-live-api)). By hand: save the body, then run it through `jq` setting every `identification`, `iban`, name and
`entryReference` to the values above, and read the whole file before committing it. The repository is public.
