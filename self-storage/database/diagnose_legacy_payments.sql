-- Read-only review before any reconciliation of old UC10 combined invoices.
-- Run against a copy or the intended SQL Server database; this changes no data.
SELECT c.contract_id, c.contract_number, c.terms_version,
       i.invoice_id, i.invoice_type, i.total_amount, i.paid_amount, i.status,
       p.transaction_id, p.payment_type, p.amount AS transaction_amount, p.status AS transaction_status,
       p.reference_code
FROM dbo.RENTAL_CONTRACTS c
JOIN dbo.INVOICES i ON i.contract_id = c.contract_id
LEFT JOIN dbo.PAYMENT_TRANSACTIONS p ON p.invoice_id = i.invoice_id
WHERE EXISTS (SELECT 1 FROM dbo.INVOICES legacy
              WHERE legacy.contract_id = c.contract_id
                AND legacy.invoice_type = N'INITIAL_PAYMENT')
ORDER BY c.contract_id, i.issued_at, p.transaction_date;

-- An old combined invoice with money recorded needs manual allocation to
-- deposit/rent components; never infer that allocation from invoice status.
SELECT c.contract_id, c.contract_number,
       SUM(CASE WHEN i.invoice_type = N'INITIAL_PAYMENT' THEN i.paid_amount ELSE 0 END) AS legacy_paid,
       SUM(CASE WHEN i.invoice_type = N'DEPOSIT' THEN i.paid_amount ELSE 0 END) AS deposit_invoice_paid,
       SUM(CASE WHEN i.invoice_type IN (N'INITIAL_RENT', N'REMAINING_RENT') THEN i.paid_amount ELSE 0 END) AS rent_invoice_paid
FROM dbo.RENTAL_CONTRACTS c
JOIN dbo.INVOICES i ON i.contract_id = c.contract_id
GROUP BY c.contract_id, c.contract_number
HAVING SUM(CASE WHEN i.invoice_type = N'INITIAL_PAYMENT' THEN i.paid_amount ELSE 0 END) > 0
    OR SUM(CASE WHEN i.invoice_type = N'INITIAL_PAYMENT' AND i.status = N'PAID' THEN 1 ELSE 0 END) > 0;

-- Focused, read-only diagnosis for the reported contract. Run all result sets.
-- Result 1: contract terms and stored deposit obligation.
DECLARE @contract_id uniqueidentifier = '7668d76b-48c9-4998-93c8-06dc8ed1396a';
SELECT c.contract_id, c.contract_number, c.status, c.terms_version,
       c.start_date, c.end_date, c.monthly_rental_fee, c.deposit_amount
FROM dbo.RENTAL_CONTRACTS c
WHERE c.contract_id = @contract_id;

-- Result 2: every invoice, including unpaid obsolete combined invoices.
SELECT i.invoice_id, i.invoice_number, i.invoice_type, i.status,
       i.total_amount, i.paid_amount, i.issued_at, i.due_date
FROM dbo.INVOICES i
WHERE i.contract_id = @contract_id
ORDER BY i.issued_at, i.invoice_id;

-- Result 3: every transaction linked by invoice or contract. Older UC10
-- transactions may have a NULL contract_id but still reference the invoice.
SELECT p.transaction_id, p.invoice_id, i.invoice_type, p.contract_id,
       p.transaction_type, p.payment_type, p.status, p.amount,
       p.payment_method, p.reference_code, p.transaction_date
FROM dbo.PAYMENT_TRANSACTIONS p
LEFT JOIN dbo.INVOICES i ON i.invoice_id = p.invoice_id
WHERE p.contract_id = @contract_id OR i.contract_id = @contract_id
ORDER BY p.transaction_date, p.transaction_id;

-- Result 4: canonical payment totals used by PaymentService.component.
-- Compare the DEPOSIT successful total less completed refunds with the
-- stored deposit_amount. INITIAL_PAYMENT never enters the v2 balance.
SELECT p.payment_type, p.status, COUNT(*) AS transaction_count,
       SUM(p.amount) AS transaction_total
FROM dbo.PAYMENT_TRANSACTIONS p
LEFT JOIN dbo.INVOICES i ON i.invoice_id = p.invoice_id
WHERE p.contract_id = @contract_id OR i.contract_id = @contract_id
GROUP BY p.payment_type, p.status
ORDER BY p.payment_type, p.status;

-- Result 5: the deposit balance that PaymentService reports. This excludes
-- legacy combined payments by design; compare with the frontend's 2,376,000.
SELECT c.deposit_amount,
       COALESCE(p.deposit_success, 0) AS deposit_success,
       COALESCE(r.completed_refunds, 0) AS completed_refunds,
       c.deposit_amount - COALESCE(p.deposit_success, 0)
           + COALESCE(r.completed_refunds, 0) AS deposit_outstanding
FROM dbo.RENTAL_CONTRACTS c
OUTER APPLY (
    SELECT SUM(t.amount) AS deposit_success
    FROM dbo.PAYMENT_TRANSACTIONS t
    WHERE t.contract_id = c.contract_id
      AND t.payment_type = N'DEPOSIT' AND t.status = N'SUCCESS'
) p
OUTER APPLY (
    SELECT SUM(d.approved_amount) AS completed_refunds
    FROM dbo.DEPOSIT_REFUNDS d
    WHERE d.contract_id = c.contract_id AND d.status = N'COMPLETED'
) r
WHERE c.contract_id = @contract_id;

-- Result 6: actual predicate for requiring legacy reconciliation.
-- Any PAID legacy invoice, positive paid_amount, or linked positive SUCCESS
-- transaction is a conflict. An UNPAID zero-paid invoice alone is not.
SELECT i.invoice_id, i.status AS invoice_status, i.paid_amount,
       CASE WHEN i.status = N'PAID' OR i.paid_amount > 0 OR EXISTS (
           SELECT 1 FROM dbo.PAYMENT_TRANSACTIONS p
           WHERE p.invoice_id = i.invoice_id
             AND p.status = N'SUCCESS' AND p.amount > 0)
           THEN 1 ELSE 0 END AS requires_reconciliation
FROM dbo.INVOICES i
WHERE i.contract_id = @contract_id AND i.invoice_type = N'INITIAL_PAYMENT'
ORDER BY i.issued_at, i.invoice_id;
