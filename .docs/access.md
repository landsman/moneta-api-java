# Getting access

Two sides: the merchant opens their account to an application with a token, and the application's developer
registers with MONETA to get the documentation and a sandbox. No PSD2 licence is needed for either.

## Who can use it

**A business account, sole traders included.** The token steps on [the product page](https://www.moneta.cz/zivnostnici-a-firmy/api)
open with "Máte podnikatelský účet?", and the integrations it lists are bookkeeping systems for sole traders and
companies (POHODA, iÚčto with daňová evidence). Not personal accounts. Read 2026-11-06; not yet tried on a sole
trader's account.

No account yet: MONETA opens a business account, *Konto PRO podnikání*, [fully online](https://www.moneta.cz/ucty-a-karty/konto-pro-podnikani/online-sjednani)
([the account](https://www.moneta.cz/ucty-a-karty/konto-pro-podnikani)); the API service is then activated in its
Internet Banka as below.

The service is free ("Služba Moneta API je zcela zdarma", [FAQ](https://www.moneta.cz/caste-dotazy/odpoved/jake-jsou-naklady-na-pouzivani-api)).

## The merchant: a token

From [the product page](https://www.moneta.cz/zivnostnici-a-firmy/api) and [the FAQ](https://www.moneta.cz/caste-dotazy/odpoved/jak-propojim-aplikaci-treti-strany-s-ucty-u-moneta-money-bank):

1. Internet Banka → *Integrace a API* → activate the service and sign its agreement electronically.
2. *Nastavení → Ostatní → Správa API tokenů* → a new token, with the rights it should have. Several tokens with
   different rights may exist side by side.
3. Paste it into the application, which passes it to the client as an `AccessToken`.

Only the account owner can authorise it; for a company, its *jednatel* ([FAQ](https://www.moneta.cz/caste-dotazy/odpoved/jake-jsou-pozadavky-na-autentizaci-a-autorizaci)).
A token is revoked in the same place.

## The developer: the portal

Register the organisation at [apiportal.moneta.cz](https://apiportal.moneta.cz/pages/moneta/home.html): Sign Up
with an e-mail and the organisation as `Name - IČO` (digits only), then the service type. In the portal's words:

| Type | For |
|------|-----|
| MONETA API – TPP | "third-party providers offering services to end customers via API": an app many merchants hand their token to |
| MONETA API – FAC | "internal use and development within your organization": a company reading its own accounts |
| PSD2 | licensed partners; needs a licence from the ČNB or another EU regulator |
| Other | custom integrations not covered above |

After the form comes an e-mail to confirm, then **approval by hand**: apiportal@moneta.cz answers that the request
"is currently under review", with no time frame. One account per organisation; a colleague asks its admin to be
added rather than signing up again.

Approval opens the documentation and a sandbox, and nothing more without a PSD2 licence: the portal's Production
plan "requires a valid PSD2 license", and non-PSD2 partners get "Sandbox only". **Production is always the
merchant's token**; the portal's credentials reach only the sandbox. `make dev` runs against production.

For the sandbox, from the portal's guide (2026-11-11):

1. Ask MONETA's integrations admin for the Sandbox API plan on the service, e.g. `VipAISPPayments_v4`.
2. In the portal application, Key Overview: generate a key (the client id), set the scope to `vip_sandbox` and
   the type to Confidential.
3. Get a token, valid 1200 seconds:

       curl https://api.moneta.cz/auth/oauth/v2/token \
         --data-urlencode client_id=… --data-urlencode client_secret=… \
         --data-urlencode grant_type=client_credentials --data-urlencode scope=vip_sandbox

4. Send it as `Authorization: Bearer`, to the same host as production.

The sandbox answers fixed test data and validates almost nothing, so errors are tried against a real account.
The client has no sandbox mode: a sandbox token is passed as any `AccessToken`, and renewed by the caller.

## Contacts

- MONETA API: moneta.api@moneta.cz, +420 224 444 555 (Mon–Fri 8–17)
- The portal: qaapi@moneta.cz

moneta.cz answers automated fetches with HTTP 403 (2026-11-06); read it in a browser.
