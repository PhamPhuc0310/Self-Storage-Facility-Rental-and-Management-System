-- Read-only checks for a failed Manager reservation confirmation.
-- Run against the same database used by the Spring Boot server.
DECLARE @reservationId UNIQUEIDENTIFIER = '73f17ba0-113a-43d8-923e-468e56fe9701';

SELECT r.reservation_id, r.status, r.customer_id, customer.status AS customer_status,
       role.role_name AS customer_role, r.facility_id, f.name AS facility_name,
       f.status AS facility_status, r.type_id, t.type_name, t.status AS type_status,
       r.start_date, r.end_date, r.hold_expires_at, r.pricing_version,
       r.quoted_monthly_price, r.estimated_rental_amount, r.estimated_deposit_amount
FROM dbo.RESERVATIONS AS r
LEFT JOIN dbo.USERS AS customer ON customer.user_id = r.customer_id
LEFT JOIN dbo.ROLES AS role ON role.role_id = customer.role_id
LEFT JOIN dbo.FACILITIES AS f ON f.facility_id = r.facility_id
LEFT JOIN dbo.STORAGE_UNIT_TYPES AS t ON t.type_id = r.type_id
WHERE r.reservation_id = @reservationId;

SELECT u.unit_id, u.facility_id, u.type_id, u.unit_number, u.status,
       u.floor, u.zone
FROM dbo.STORAGE_UNITS AS u
JOIN dbo.RESERVATIONS AS r
  ON r.facility_id = u.facility_id AND r.type_id = u.type_id
WHERE r.reservation_id = @reservationId
ORDER BY u.status, u.unit_number;

SELECT c.contract_id, c.contract_number, c.reservation_id, c.customer_id,
       c.unit_id, c.status, c.start_date, c.end_date, c.monthly_rental_fee,
       c.deposit_amount, c.terms_version, c.approved_by, c.approved_at,
       i.invoice_id, i.invoice_type, i.status AS invoice_status,
       i.total_amount, i.paid_amount
FROM dbo.RENTAL_CONTRACTS AS c
LEFT JOIN dbo.INVOICES AS i ON i.contract_id = c.contract_id
WHERE c.reservation_id = @reservationId
ORDER BY i.invoice_type;

SELECT a.user_id, a.facility_id, a.status, a.start_date, a.end_date
FROM dbo.USER_FACILITY_ASSIGNMENTS AS a
JOIN dbo.RESERVATIONS AS r ON r.facility_id = a.facility_id
WHERE r.reservation_id = @reservationId
ORDER BY a.user_id, a.start_date DESC;
