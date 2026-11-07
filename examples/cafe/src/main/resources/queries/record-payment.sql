-- Records a payment not seen before; 1 row when this call recorded it, 0 when another already had.
-- The unique moneta_entry_reference decides between two rounds recording it at once, not a lookup first.
-- Bare ON CONFLICT: the identity key is the table's only other unique constraint, and the database assigns it.
insert into payment (moneta_entry_reference, booked, amount, currency, variable_symbol, counterparty_account,
                     counterparty_name, message, made_on, first_seen_at, bill_id, version)
values (:monetaEntryReference, :booked, :amount, :currency, :variableSymbol, :counterpartyAccount,
        :counterpartyName, :message, :madeOn, :firstSeenAt, :billId, 0)
on conflict do nothing
