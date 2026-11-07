-- Books a pending payment under the id MONETA gave it once booked; 1 row when this call booked it, 0 when
-- another round already had. The condition on booked is the claim: only one of two rounds turns it.
update payment
set moneta_entry_reference = :monetaEntryReference,
    booked                 = true,
    counterparty_name      = :counterpartyName,
    message                = :message,
    made_on                = coalesce(:madeOn, made_on),
    version                = version + 1
where id = :id
  and booked = false
