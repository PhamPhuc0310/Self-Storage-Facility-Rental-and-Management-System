-- Read-only. Run against the exact database used by the running SafeBox backend.
-- Share all result sets, including the zero-row ones. No data is changed.
DECLARE @contract_number nvarchar(50) = N'SB-CT-FA2D279E-C55';
DECLARE @posted_contract_id uniqueidentifier = '55de77c6-bcad-4e68-8da6-77ff6056cd09';

-- 1. Compare the displayed contract with the UUID in the failing POST.
SELECT c.contract_id, c.contract_number, c.reservation_id, c.customer_id,
       c.status, c.terms_version, c.customer_signed_at, c.approved_at,
       c.deposit_amount, c.monthly_rental_fee, c.start_date, c.end_date,
       CASE WHEN c.contract_number = @contract_number THEN 1 ELSE 0 END AS matches_displayed_number,
       CASE WHEN c.contract_id = @posted_contract_id THEN 1 ELSE 0 END AS matches_post_uuid
FROM dbo.RENTAL_CONTRACTS c
WHERE c.contract_number = @contract_number OR c.contract_id = @posted_contract_id;

-- 2. All obligations for this UUID. Newly approved v2 contracts should have
-- exactly one UNPAID DEPOSIT and one UNPAID REMAINING_RENT, both paid_amount=0.
SELECT i.contract_id, i.invoice_id, i.invoice_number, i.invoice_type,
       i.status, i.total_amount, i.paid_amount, i.issued_at, i.due_date
FROM dbo.INVOICES i
JOIN dbo.RENTAL_CONTRACTS c ON c.contract_id = i.contract_id
WHERE c.contract_id = @posted_contract_id
ORDER BY i.issued_at, i.invoice_id;

-- 3. Transactions joined by either contract UUID or its invoice IDs. The
-- mismatch columns expose transactions attached to another contract.
SELECT p.transaction_id, p.invoice_id, i.contract_id AS invoice_contract_id,
       p.contract_id AS transaction_contract_id, p.transaction_type,
       p.payment_type, p.status, p.amount, p.payment_method,
       p.reference_code, p.transaction_date,
       CASE WHEN i.invoice_id IS NOT NULL AND p.contract_id IS NOT NULL
                 AND p.contract_id <> i.contract_id THEN 1 ELSE 0 END
           AS contract_id_mismatch
FROM dbo.PAYMENT_TRANSACTIONS p
LEFT JOIN dbo.INVOICES i ON i.invoice_id = p.invoice_id
WHERE p.contract_id = @posted_contract_id OR i.contract_id = @posted_contract_id
ORDER BY p.transaction_date, p.transaction_id;

-- 4. Each legacy invoice and the precise reason the current guard blocks.
-- Zero rows means this contract cannot trigger this guard for its own UUID.
SELECT i.contract_id, i.invoice_id, i.invoice_type, i.status,
       i.paid_amount, p.transaction_id AS successful_transaction_id,
       p.status AS transaction_status, p.amount AS transaction_amount,
       CASE WHEN i.status = N'PAID' THEN N'PAID invoice'
            WHEN i.paid_amount > 0 THEN N'positive paid_amount'
            WHEN p.transaction_id IS NOT NULL THEN N'positive SUCCESS transaction'
            ELSE N'no recorded legacy payment' END AS guard_reason
FROM dbo.INVOICES i
JOIN dbo.RENTAL_CONTRACTS c ON c.contract_id = i.contract_id
OUTER APPLY (
    SELECT TOP (1) t.transaction_id, t.status, t.amount
    FROM dbo.PAYMENT_TRANSACTIONS t
    WHERE t.invoice_id = i.invoice_id AND t.status = N'SUCCESS' AND t.amount > 0
    ORDER BY t.transaction_date, t.transaction_id
) p
WHERE c.contract_id = @posted_contract_id AND i.invoice_type = N'INITIAL_PAYMENT'
ORDER BY i.issued_at, i.invoice_id;

-- 5. Canonical summary inputs. INITIAL_PAYMENT transactions never count in
-- the v2 deposit or remaining rent balances.
SELECT p.contract_id, p.payment_type, p.status,
       COUNT(*) AS transaction_count, SUM(p.amount) AS total_amount
FROM dbo.PAYMENT_TRANSACTIONS p
JOIN dbo.RENTAL_CONTRACTS c ON c.contract_id = p.contract_id
WHERE c.contract_id = @posted_contract_id
GROUP BY p.contract_id, p.payment_type, p.status
ORDER BY p.payment_type, p.status;

-- 6. Completed refunds affect the deposit balance.
SELECT d.refund_id, d.contract_id, d.status, d.approved_amount, d.completed_at
FROM dbo.DEPOSIT_REFUNDS d
JOIN dbo.RENTAL_CONTRACTS c ON c.contract_id = d.contract_id
WHERE c.contract_id = @posted_contract_id
ORDER BY d.created_at, d.refund_id;
