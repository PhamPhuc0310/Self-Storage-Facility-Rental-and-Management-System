/*
    SELF-STORAGE FACILITY RENTAL AND MANAGEMENT SYSTEM
    Target DBMS: Microsoft SQL Server
    Source: ERD.drawio

    Relationship fixes applied:
    1) STORAGE_UNITS.unit_number is unique per facility, not globally.
    2) Nullable unique columns use filtered unique indexes in SQL Server.
    3) RESERVATIONS -> PRICING_POLICIES is reinforced so pricing_id must belong
       to the same facility_id and type_id selected by the reservation.
    4) RENTAL_CONTRACTS -> RESERVATIONS is reinforced so customer_id must match.
    5) HANDOVER_RECORDS / INSPECTION_REPORTS -> RENTAL_CONTRACTS are reinforced
       so a referenced unit must be the unit of that contract.
*/

IF DB_ID(N'SelfStorageFacilityDB') IS NULL
BEGIN
    CREATE DATABASE SelfStorageFacilityDB;
END;
GO

USE SelfStorageFacilityDB;
GO

/* =========================================================
   1. MASTER / ACCOUNT TABLES
   ========================================================= */

CREATE TABLE dbo.ROLES (
    role_id INT IDENTITY(1,1) NOT NULL,
    role_name NVARCHAR(50) NOT NULL,
    description NVARCHAR(MAX) NULL,

    CONSTRAINT PK_ROLES PRIMARY KEY (role_id),
    CONSTRAINT UQ_ROLES_role_name UNIQUE (role_name)
);
GO

CREATE TABLE dbo.USERS (
    user_id UNIQUEIDENTIFIER NOT NULL
        CONSTRAINT DF_USERS_user_id DEFAULT NEWSEQUENTIALID(),
    role_id INT NOT NULL,
    email NVARCHAR(255) NOT NULL,
    password_hash NVARCHAR(255) NOT NULL,
    full_name NVARCHAR(100) NOT NULL,
    phone NVARCHAR(20) NOT NULL,
    identity_number NVARCHAR(30) NULL,
    address NVARCHAR(MAX) NULL,
    status NVARCHAR(20) NOT NULL,
    created_at DATETIME2 NOT NULL
        CONSTRAINT DF_USERS_created_at DEFAULT SYSDATETIME(),

    CONSTRAINT PK_USERS PRIMARY KEY (user_id),
    CONSTRAINT UQ_USERS_email UNIQUE (email),
    CONSTRAINT FK_USERS_ROLES
        FOREIGN KEY (role_id) REFERENCES dbo.ROLES(role_id)
);
GO

-- SQL Server UNIQUE constraints allow only one NULL, therefore a filtered
-- unique index is required when many users may have no identity number yet.
CREATE UNIQUE INDEX UX_USERS_identity_number_not_null
ON dbo.USERS(identity_number)
WHERE identity_number IS NOT NULL;
GO

CREATE TABLE dbo.ACTIVITY_LOGS (
    log_id UNIQUEIDENTIFIER NOT NULL
        CONSTRAINT DF_ACTIVITY_LOGS_log_id DEFAULT NEWSEQUENTIALID(),
    user_id UNIQUEIDENTIFIER NULL,
    action_type NVARCHAR(100) NOT NULL,
    entity_type NVARCHAR(100) NULL,
    entity_id UNIQUEIDENTIFIER NULL,
    description NVARCHAR(MAX) NULL,
    ip_address NVARCHAR(50) NULL,
    created_at DATETIME2 NOT NULL
        CONSTRAINT DF_ACTIVITY_LOGS_created_at DEFAULT SYSDATETIME(),

    CONSTRAINT PK_ACTIVITY_LOGS PRIMARY KEY (log_id),
    CONSTRAINT FK_ACTIVITY_LOGS_USERS
        FOREIGN KEY (user_id) REFERENCES dbo.USERS(user_id)
);
GO

/* =========================================================
   2. FACILITY / STAFF / POLICY TABLES
   ========================================================= */

CREATE TABLE dbo.FACILITIES (
    facility_id UNIQUEIDENTIFIER NOT NULL
        CONSTRAINT DF_FACILITIES_facility_id DEFAULT NEWSEQUENTIALID(),
    name NVARCHAR(255) NOT NULL,
    address NVARCHAR(MAX) NOT NULL,
    latitude DECIMAL(10,8) NULL,
    longitude DECIMAL(11,8) NULL,
    phone NVARCHAR(20) NULL,
    opening_time TIME NULL,
    closing_time TIME NULL,
    status NVARCHAR(20) NOT NULL,

    CONSTRAINT PK_FACILITIES PRIMARY KEY (facility_id),
    CONSTRAINT CK_FACILITIES_latitude
        CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT CK_FACILITIES_longitude
        CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);
GO

CREATE TABLE dbo.USER_FACILITY_ASSIGNMENTS (
    assignment_id UNIQUEIDENTIFIER NOT NULL
        CONSTRAINT DF_USER_FACILITY_ASSIGNMENTS_assignment_id DEFAULT NEWSEQUENTIALID(),
    user_id UNIQUEIDENTIFIER NOT NULL,
    facility_id UNIQUEIDENTIFIER NOT NULL,
    assigned_by UNIQUEIDENTIFIER NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NULL,
    status NVARCHAR(20) NOT NULL,

    CONSTRAINT PK_USER_FACILITY_ASSIGNMENTS PRIMARY KEY (assignment_id),
    CONSTRAINT FK_UFA_USERS
        FOREIGN KEY (user_id) REFERENCES dbo.USERS(user_id),
    CONSTRAINT FK_UFA_FACILITIES
        FOREIGN KEY (facility_id) REFERENCES dbo.FACILITIES(facility_id),
    CONSTRAINT FK_UFA_ASSIGNED_BY
        FOREIGN KEY (assigned_by) REFERENCES dbo.USERS(user_id),
    CONSTRAINT CK_UFA_date_range
        CHECK (end_date IS NULL OR end_date >= start_date)
);
GO

CREATE TABLE dbo.RENTAL_POLICIES (
    policy_id UNIQUEIDENTIFIER NOT NULL
        CONSTRAINT DF_RENTAL_POLICIES_policy_id DEFAULT NEWSEQUENTIALID(),
    facility_id UNIQUEIDENTIFIER NULL,
    renewal_open_days INT NOT NULL,
    priority_deadline_days INT NOT NULL,
    renewal_payment_hours INT NOT NULL,
    reservation_hold_minutes INT NOT NULL,
    overdue_grace_days INT NOT NULL,
    cancellation_policy NVARCHAR(MAX) NULL,
    return_policy NVARCHAR(MAX) NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    status NVARCHAR(20) NOT NULL,

    CONSTRAINT PK_RENTAL_POLICIES PRIMARY KEY (policy_id),
    CONSTRAINT FK_RENTAL_POLICIES_FACILITIES
        FOREIGN KEY (facility_id) REFERENCES dbo.FACILITIES(facility_id),
    CONSTRAINT CK_RENTAL_POLICIES_days
        CHECK (
            renewal_open_days >= 0 AND
            priority_deadline_days >= 0 AND
            renewal_payment_hours >= 0 AND
            reservation_hold_minutes >= 0 AND
            overdue_grace_days >= 0
        ),
    CONSTRAINT CK_RENTAL_POLICIES_date_range
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
GO

/* =========================================================
   3. STORAGE UNIT / COMMODITY / PRICING TABLES
   ========================================================= */

CREATE TABLE dbo.STORAGE_UNIT_TYPES (
    type_id INT IDENTITY(1,1) NOT NULL,
    type_name NVARCHAR(100) NOT NULL,
    storage_mode NVARCHAR(30) NOT NULL,
    size_name NVARCHAR(50) NOT NULL,
    width DECIMAL(6,2) NOT NULL,
    length DECIMAL(6,2) NOT NULL,
    height DECIMAL(6,2) NOT NULL,
    max_weight DECIMAL(12,2) NULL,
    min_temperature DECIMAL(6,2) NULL,
    max_temperature DECIMAL(6,2) NULL,
    min_humidity DECIMAL(5,2) NULL,
    max_humidity DECIMAL(5,2) NULL,
    features NVARCHAR(MAX) NULL,
    status NVARCHAR(20) NOT NULL,

    CONSTRAINT PK_STORAGE_UNIT_TYPES PRIMARY KEY (type_id),
    CONSTRAINT CK_STORAGE_UNIT_TYPES_dimensions
        CHECK (width > 0 AND length > 0 AND height > 0),
    CONSTRAINT CK_STORAGE_UNIT_TYPES_weight
        CHECK (max_weight IS NULL OR max_weight >= 0),
    CONSTRAINT CK_STORAGE_UNIT_TYPES_temperature
        CHECK (
            min_temperature IS NULL OR max_temperature IS NULL
            OR min_temperature <= max_temperature
        ),
    CONSTRAINT CK_STORAGE_UNIT_TYPES_humidity
        CHECK (
            (min_humidity IS NULL OR (min_humidity BETWEEN 0 AND 100)) AND
            (max_humidity IS NULL OR (max_humidity BETWEEN 0 AND 100)) AND
            (min_humidity IS NULL OR max_humidity IS NULL OR min_humidity <= max_humidity)
        )
);
GO

CREATE TABLE dbo.STORAGE_UNITS (
    unit_id UNIQUEIDENTIFIER NOT NULL
        CONSTRAINT DF_STORAGE_UNITS_unit_id DEFAULT NEWSEQUENTIALID(),
    facility_id UNIQUEIDENTIFIER NOT NULL,
    type_id INT NOT NULL,
    unit_number NVARCHAR(50) NOT NULL,
    floor NVARCHAR(20) NULL,
    zone NVARCHAR(50) NULL,
    status NVARCHAR(20) NOT NULL,
    condition_note NVARCHAR(MAX) NULL,

    CONSTRAINT PK_STORAGE_UNITS PRIMARY KEY (unit_id),
    CONSTRAINT FK_STORAGE_UNITS_FACILITIES
        FOREIGN KEY (facility_id) REFERENCES dbo.FACILITIES(facility_id),
    CONSTRAINT FK_STORAGE_UNITS_TYPES
        FOREIGN KEY (type_id) REFERENCES dbo.STORAGE_UNIT_TYPES(type_id),
    CONSTRAINT UQ_STORAGE_UNITS_facility_unit_number
        UNIQUE (facility_id, unit_number)
);
GO

CREATE TABLE dbo.COMMODITY_CATEGORIES (
    category_id INT IDENTITY(1,1) NOT NULL,
    category_name NVARCHAR(100) NOT NULL,
    description NVARCHAR(MAX) NULL,
    storage_requirement NVARCHAR(MAX) NULL,
    is_prohibited BIT NOT NULL,

    CONSTRAINT PK_COMMODITY_CATEGORIES PRIMARY KEY (category_id),
    CONSTRAINT UQ_COMMODITY_CATEGORIES_category_name UNIQUE (category_name)
);
GO

CREATE TABLE dbo.UNIT_TYPE_COMMODITIES (
    type_id INT NOT NULL,
    category_id INT NOT NULL,
    compatibility_level NVARCHAR(30) NOT NULL,
    condition_note NVARCHAR(MAX) NULL,

    CONSTRAINT PK_UNIT_TYPE_COMMODITIES PRIMARY KEY (type_id, category_id),
    CONSTRAINT FK_UTC_TYPES
        FOREIGN KEY (type_id) REFERENCES dbo.STORAGE_UNIT_TYPES(type_id),
    CONSTRAINT FK_UTC_CATEGORIES
        FOREIGN KEY (category_id) REFERENCES dbo.COMMODITY_CATEGORIES(category_id)
);
GO

CREATE TABLE dbo.PRICING_POLICIES (
    pricing_id UNIQUEIDENTIFIER NOT NULL
        CONSTRAINT DF_PRICING_POLICIES_pricing_id DEFAULT NEWSEQUENTIALID(),
    facility_id UNIQUEIDENTIFIER NOT NULL,
    type_id INT NOT NULL,
    created_by UNIQUEIDENTIFIER NOT NULL,
    monthly_price DECIMAL(18,2) NOT NULL,
    deposit_amount DECIMAL(18,2) NOT NULL,
    daily_overdue_rate DECIMAL(18,2) NOT NULL,
    late_payment_rate DECIMAL(18,2) NOT NULL,
    early_termination_fee DECIMAL(18,2) NOT NULL,
    discount_rate DECIMAL(5,2) NULL,
    fee_waiver_allowed BIT NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    status NVARCHAR(20) NOT NULL,

    CONSTRAINT PK_PRICING_POLICIES PRIMARY KEY (pricing_id),
    CONSTRAINT FK_PRICING_POLICIES_FACILITIES
        FOREIGN KEY (facility_id) REFERENCES dbo.FACILITIES(facility_id),
    CONSTRAINT FK_PRICING_POLICIES_TYPES
        FOREIGN KEY (type_id) REFERENCES dbo.STORAGE_UNIT_TYPES(type_id),
    CONSTRAINT FK_PRICING_POLICIES_CREATED_BY
        FOREIGN KEY (created_by) REFERENCES dbo.USERS(user_id),
    CONSTRAINT CK_PRICING_POLICIES_amounts
        CHECK (
            monthly_price >= 0 AND
            deposit_amount >= 0 AND
            daily_overdue_rate >= 0 AND
            late_payment_rate >= 0 AND
            early_termination_fee >= 0
        ),
    CONSTRAINT CK_PRICING_POLICIES_discount
        CHECK (discount_rate IS NULL OR discount_rate BETWEEN 0 AND 100),
    CONSTRAINT CK_PRICING_POLICIES_date_range
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
GO

-- Supports a composite FK from RESERVATIONS and guarantees that the chosen
-- pricing policy belongs to the same facility + unit type.
CREATE UNIQUE INDEX UX_PRICING_POLICIES_identity
ON dbo.PRICING_POLICIES(pricing_id, facility_id, type_id);
GO

/* =========================================================
   4. RESERVATION / CONTRACT TABLES
   ========================================================= */

CREATE TABLE dbo.RESERVATIONS (
    reservation_id UNIQUEIDENTIFIER NOT NULL
        CONSTRAINT DF_RESERVATIONS_reservation_id DEFAULT NEWSEQUENTIALID(),
    customer_id UNIQUEIDENTIFIER NOT NULL,
    facility_id UNIQUEIDENTIFIER NOT NULL,
    type_id INT NOT NULL,
    pricing_id UNIQUEIDENTIFIER NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    hold_expires_at DATETIME2 NULL,
    estimated_rental_amount DECIMAL(18,2) NOT NULL,
    estimated_deposit_amount DECIMAL(18,2) NOT NULL,
    status NVARCHAR(30) NOT NULL,
    created_at DATETIME2 NOT NULL
        CONSTRAINT DF_RESERVATIONS_created_at DEFAULT SYSDATETIME(),
    cancelled_at DATETIME2 NULL,
    cancellation_reason NVARCHAR(MAX) NULL,

    CONSTRAINT PK_RESERVATIONS PRIMARY KEY (reservation_id),
    CONSTRAINT FK_RESERVATIONS_CUSTOMER
        FOREIGN KEY (customer_id) REFERENCES dbo.USERS(user_id),
    CONSTRAINT FK_RESERVATIONS_FACILITY
        FOREIGN KEY (facility_id) REFERENCES dbo.FACILITIES(facility_id),
    CONSTRAINT FK_RESERVATIONS_TYPE
        FOREIGN KEY (type_id) REFERENCES dbo.STORAGE_UNIT_TYPES(type_id),
    CONSTRAINT FK_RESERVATIONS_PRICING_MATCH
        FOREIGN KEY (pricing_id, facility_id, type_id)
        REFERENCES dbo.PRICING_POLICIES(pricing_id, facility_id, type_id),
    CONSTRAINT CK_RESERVATIONS_date_range
        CHECK (end_date >= start_date),
    CONSTRAINT CK_RESERVATIONS_amounts
        CHECK (estimated_rental_amount >= 0 AND estimated_deposit_amount >= 0)
);
GO

CREATE UNIQUE INDEX UX_RESERVATIONS_reservation_customer
ON dbo.RESERVATIONS(reservation_id, customer_id);
GO

CREATE TABLE dbo.RENTAL_CONTRACTS (
    contract_id UNIQUEIDENTIFIER NOT NULL
        CONSTRAINT DF_RENTAL_CONTRACTS_contract_id DEFAULT NEWSEQUENTIALID(),
    contract_number NVARCHAR(50) NOT NULL,
    reservation_id UNIQUEIDENTIFIER NOT NULL,
    customer_id UNIQUEIDENTIFIER NOT NULL,
    unit_id UNIQUEIDENTIFIER NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    actual_start_date DATE NULL,
    actual_end_date DATE NULL,
    monthly_rental_fee DECIMAL(18,2) NOT NULL,
    deposit_amount DECIMAL(18,2) NOT NULL,
    billing_cycle NVARCHAR(20) NOT NULL,
    terms_version NVARCHAR(30) NOT NULL,
    customer_signed_at DATETIME2 NULL,
    customer_signature NVARCHAR(MAX) NULL,
    approved_by UNIQUEIDENTIFIER NULL,
    approved_at DATETIME2 NULL,
    company_signature NVARCHAR(MAX) NULL,
    status NVARCHAR(30) NOT NULL,
    created_at DATETIME2 NOT NULL
        CONSTRAINT DF_RENTAL_CONTRACTS_created_at DEFAULT SYSDATETIME(),

    CONSTRAINT PK_RENTAL_CONTRACTS PRIMARY KEY (contract_id),
    CONSTRAINT UQ_RENTAL_CONTRACTS_contract_number UNIQUE (contract_number),
    CONSTRAINT UQ_RENTAL_CONTRACTS_reservation UNIQUE (reservation_id),
    CONSTRAINT FK_RENTAL_CONTRACTS_RESERVATION_CUSTOMER
        FOREIGN KEY (reservation_id, customer_id)
        REFERENCES dbo.RESERVATIONS(reservation_id, customer_id),
    CONSTRAINT FK_RENTAL_CONTRACTS_CUSTOMER
        FOREIGN KEY (customer_id) REFERENCES dbo.USERS(user_id),
    CONSTRAINT FK_RENTAL_CONTRACTS_UNIT
        FOREIGN KEY (unit_id) REFERENCES dbo.STORAGE_UNITS(unit_id),
    CONSTRAINT FK_RENTAL_CONTRACTS_APPROVED_BY
        FOREIGN KEY (approved_by) REFERENCES dbo.USERS(user_id),
    CONSTRAINT CK_RENTAL_CONTRACTS_date_range
        CHECK (end_date >= start_date),
    CONSTRAINT CK_RENTAL_CONTRACTS_actual_date_range
        CHECK (
            actual_end_date IS NULL OR actual_start_date IS NULL
            OR actual_end_date >= actual_start_date
        ),
    CONSTRAINT CK_RENTAL_CONTRACTS_amounts
        CHECK (monthly_rental_fee >= 0 AND deposit_amount >= 0)
);
GO

CREATE UNIQUE INDEX UX_RENTAL_CONTRACTS_contract_unit
ON dbo.RENTAL_CONTRACTS(contract_id, unit_id);
GO

CREATE TABLE dbo.CONTRACT_COMMODITIES (
    contract_commodity_id UNIQUEIDENTIFIER NOT NULL
        CONSTRAINT DF_CONTRACT_COMMODITIES_id DEFAULT NEWSEQUENTIALID(),
    contract_id UNIQUEIDENTIFIER NOT NULL,
    category_id INT NOT NULL,
    item_description NVARCHAR(MAX) NOT NULL,
    estimated_quantity DECIMAL(12,2) NULL,
    estimated_weight DECIMAL(12,2) NULL,
    required_temperature DECIMAL(6,2) NULL,
    required_humidity DECIMAL(5,2) NULL,
    special_requirement NVARCHAR(MAX) NULL,

    CONSTRAINT PK_CONTRACT_COMMODITIES PRIMARY KEY (contract_commodity_id),
    CONSTRAINT FK_CONTRACT_COMMODITIES_CONTRACTS
        FOREIGN KEY (contract_id) REFERENCES dbo.RENTAL_CONTRACTS(contract_id),
    CONSTRAINT FK_CONTRACT_COMMODITIES_CATEGORIES
        FOREIGN KEY (category_id) REFERENCES dbo.COMMODITY_CATEGORIES(category_id),
    CONSTRAINT CK_CONTRACT_COMMODITIES_values
        CHECK (
            (estimated_quantity IS NULL OR estimated_quantity >= 0) AND
            (estimated_weight IS NULL OR estimated_weight >= 0) AND
            (required_humidity IS NULL OR required_humidity BETWEEN 0 AND 100)
        )
);
GO

/* =========================================================
   5. RENEWAL / TERMINATION
   ========================================================= */

CREATE TABLE dbo.RENEWALS (
    renewal_id UNIQUEIDENTIFIER NOT NULL
        CONSTRAINT DF_RENEWALS_renewal_id DEFAULT NEWSEQUENTIALID(),
    contract_id UNIQUEIDENTIFIER NOT NULL,
    old_end_date DATE NOT NULL,
    new_end_date DATE NOT NULL,
    renewal_amount DECIMAL(18,2) NOT NULL,
    requested_at DATETIME2 NOT NULL
        CONSTRAINT DF_RENEWALS_requested_at DEFAULT SYSDATETIME(),
    reviewed_at DATETIME2 NULL,
    approved_by UNIQUEIDENTIFIER NULL,
    payment_deadline DATETIME2 NULL,
    status NVARCHAR(30) NOT NULL,
    rejection_reason NVARCHAR(MAX) NULL,

    CONSTRAINT PK_RENEWALS PRIMARY KEY (renewal_id),
    CONSTRAINT FK_RENEWALS_CONTRACTS
        FOREIGN KEY (contract_id) REFERENCES dbo.RENTAL_CONTRACTS(contract_id),
    CONSTRAINT FK_RENEWALS_APPROVED_BY
        FOREIGN KEY (approved_by) REFERENCES dbo.USERS(user_id),
    CONSTRAINT CK_RENEWALS_date_range
        CHECK (new_end_date > old_end_date),
    CONSTRAINT CK_RENEWALS_amount
        CHECK (renewal_amount >= 0)
);
GO

CREATE TABLE dbo.TERMINATION_REQUESTS (
    termination_id UNIQUEIDENTIFIER NOT NULL
        CONSTRAINT DF_TERMINATION_REQUESTS_id DEFAULT NEWSEQUENTIALID(),
    contract_id UNIQUEIDENTIFIER NOT NULL,
    termination_type NVARCHAR(30) NOT NULL,
    requested_by UNIQUEIDENTIFIER NOT NULL,
    requested_at DATETIME2 NOT NULL
        CONSTRAINT DF_TERMINATION_REQUESTS_requested_at DEFAULT SYSDATETIME(),
    scheduled_return_at DATETIME2 NULL,
    reason NVARCHAR(MAX) NULL,
    deposit_amount DECIMAL(18,2) NOT NULL,
    deduction_amount DECIMAL(18,2) NOT NULL,
    refund_amount DECIMAL(18,2) NOT NULL,
    status NVARCHAR(30) NOT NULL,
    approved_by UNIQUEIDENTIFIER NULL,
    completed_at DATETIME2 NULL,

    CONSTRAINT PK_TERMINATION_REQUESTS PRIMARY KEY (termination_id),
    CONSTRAINT FK_TERMINATION_REQUESTS_CONTRACTS
        FOREIGN KEY (contract_id) REFERENCES dbo.RENTAL_CONTRACTS(contract_id),
    CONSTRAINT FK_TERMINATION_REQUESTS_REQUESTED_BY
        FOREIGN KEY (requested_by) REFERENCES dbo.USERS(user_id),
    CONSTRAINT FK_TERMINATION_REQUESTS_APPROVED_BY
        FOREIGN KEY (approved_by) REFERENCES dbo.USERS(user_id),
    CONSTRAINT CK_TERMINATION_REQUESTS_amounts
        CHECK (
            deposit_amount >= 0 AND
            deduction_amount >= 0 AND
            refund_amount >= 0
        )
);
GO

/* =========================================================
   6. INVOICE / PAYMENT
   ========================================================= */

CREATE TABLE dbo.INVOICES (
    invoice_id UNIQUEIDENTIFIER NOT NULL
        CONSTRAINT DF_INVOICES_invoice_id DEFAULT NEWSEQUENTIALID(),
    invoice_number NVARCHAR(50) NOT NULL,
    contract_id UNIQUEIDENTIFIER NOT NULL,
    renewal_id UNIQUEIDENTIFIER NULL,
    invoice_type NVARCHAR(30) NOT NULL,
    description NVARCHAR(MAX) NULL,
    subtotal DECIMAL(18,2) NOT NULL,
    penalty_amount DECIMAL(18,2) NOT NULL,
    discount_amount DECIMAL(18,2) NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL,
    paid_amount DECIMAL(18,2) NOT NULL,
    issued_at DATETIME2 NOT NULL
        CONSTRAINT DF_INVOICES_issued_at DEFAULT SYSDATETIME(),
    due_date DATETIME2 NOT NULL,
    status NVARCHAR(30) NOT NULL,

    CONSTRAINT PK_INVOICES PRIMARY KEY (invoice_id),
    CONSTRAINT UQ_INVOICES_invoice_number UNIQUE (invoice_number),
    CONSTRAINT FK_INVOICES_CONTRACTS
        FOREIGN KEY (contract_id) REFERENCES dbo.RENTAL_CONTRACTS(contract_id),
    CONSTRAINT FK_INVOICES_RENEWALS
        FOREIGN KEY (renewal_id) REFERENCES dbo.RENEWALS(renewal_id),
    CONSTRAINT CK_INVOICES_amounts
        CHECK (
            subtotal >= 0 AND
            penalty_amount >= 0 AND
            discount_amount >= 0 AND
            total_amount >= 0 AND
            paid_amount >= 0
        )
);
GO

-- ERD says renewal_id is both nullable and unique.
CREATE UNIQUE INDEX UX_INVOICES_renewal_id_not_null
ON dbo.INVOICES(renewal_id)
WHERE renewal_id IS NOT NULL;
GO

CREATE TABLE dbo.PAYMENT_TRANSACTIONS (
    transaction_id UNIQUEIDENTIFIER NOT NULL
        CONSTRAINT DF_PAYMENT_TRANSACTIONS_id DEFAULT NEWSEQUENTIALID(),
    invoice_id UNIQUEIDENTIFIER NOT NULL,
    transaction_type NVARCHAR(20) NOT NULL,
    payment_method NVARCHAR(30) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    transaction_date DATETIME2 NOT NULL
        CONSTRAINT DF_PAYMENT_TRANSACTIONS_date DEFAULT SYSDATETIME(),
    reference_code NVARCHAR(100) NULL,
    status NVARCHAR(20) NOT NULL,
    failure_reason NVARCHAR(MAX) NULL,

    CONSTRAINT PK_PAYMENT_TRANSACTIONS PRIMARY KEY (transaction_id),
    CONSTRAINT FK_PAYMENT_TRANSACTIONS_INVOICES
        FOREIGN KEY (invoice_id) REFERENCES dbo.INVOICES(invoice_id),
    CONSTRAINT CK_PAYMENT_TRANSACTIONS_amount
        CHECK (amount > 0)
);
GO

/* =========================================================
   7. HANDOVER / INSPECTION / MAINTENANCE / SUPPORT
   ========================================================= */

CREATE TABLE dbo.HANDOVER_RECORDS (
    handover_id UNIQUEIDENTIFIER NOT NULL
        CONSTRAINT DF_HANDOVER_RECORDS_id DEFAULT NEWSEQUENTIALID(),
    contract_id UNIQUEIDENTIFIER NOT NULL,
    unit_id UNIQUEIDENTIFIER NOT NULL,
    staff_id UNIQUEIDENTIFIER NOT NULL,
    handover_type NVARCHAR(20) NOT NULL,
    scheduled_at DATETIME2 NOT NULL,
    actual_at DATETIME2 NULL,
    access_method NVARCHAR(30) NULL,
    access_identifier NVARCHAR(255) NULL,
    customer_signature NVARCHAR(MAX) NULL,
    staff_signature NVARCHAR(MAX) NULL,
    note NVARCHAR(MAX) NULL,
    status NVARCHAR(20) NOT NULL,

    CONSTRAINT PK_HANDOVER_RECORDS PRIMARY KEY (handover_id),
    CONSTRAINT FK_HANDOVER_RECORDS_CONTRACT_UNIT
        FOREIGN KEY (contract_id, unit_id)
        REFERENCES dbo.RENTAL_CONTRACTS(contract_id, unit_id),
    CONSTRAINT FK_HANDOVER_RECORDS_STAFF
        FOREIGN KEY (staff_id) REFERENCES dbo.USERS(user_id)
);
GO

CREATE TABLE dbo.INSPECTION_REPORTS (
    inspection_id UNIQUEIDENTIFIER NOT NULL
        CONSTRAINT DF_INSPECTION_REPORTS_id DEFAULT NEWSEQUENTIALID(),
    unit_id UNIQUEIDENTIFIER NOT NULL,
    contract_id UNIQUEIDENTIFIER NULL,
    inspected_by UNIQUEIDENTIFIER NOT NULL,
    inspection_type NVARCHAR(30) NOT NULL,
    inspection_date DATETIME2 NOT NULL
        CONSTRAINT DF_INSPECTION_REPORTS_date DEFAULT SYSDATETIME(),
    condition_status NVARCHAR(30) NOT NULL,
    temperature DECIMAL(6,2) NULL,
    humidity DECIMAL(5,2) NULL,
    damage_description NVARCHAR(MAX) NULL,
    estimated_repair_cost DECIMAL(18,2) NOT NULL,
    customer_confirmed BIT NOT NULL,
    note NVARCHAR(MAX) NULL,

    CONSTRAINT PK_INSPECTION_REPORTS PRIMARY KEY (inspection_id),
    CONSTRAINT FK_INSPECTION_REPORTS_UNITS
        FOREIGN KEY (unit_id) REFERENCES dbo.STORAGE_UNITS(unit_id),
    CONSTRAINT FK_INSPECTION_REPORTS_CONTRACT_UNIT
        FOREIGN KEY (contract_id, unit_id)
        REFERENCES dbo.RENTAL_CONTRACTS(contract_id, unit_id),
    CONSTRAINT FK_INSPECTION_REPORTS_INSPECTED_BY
        FOREIGN KEY (inspected_by) REFERENCES dbo.USERS(user_id),
    CONSTRAINT CK_INSPECTION_REPORTS_values
        CHECK (
            (humidity IS NULL OR humidity BETWEEN 0 AND 100) AND
            estimated_repair_cost >= 0
        )
);
GO

CREATE TABLE dbo.MAINTENANCE_REQUESTS (
    maintenance_id UNIQUEIDENTIFIER NOT NULL
        CONSTRAINT DF_MAINTENANCE_REQUESTS_id DEFAULT NEWSEQUENTIALID(),
    unit_id UNIQUEIDENTIFIER NOT NULL,
    inspection_id UNIQUEIDENTIFIER NULL,
    reported_by UNIQUEIDENTIFIER NOT NULL,
    assigned_staff_id UNIQUEIDENTIFIER NULL,
    description NVARCHAR(MAX) NOT NULL,
    scheduled_start DATETIME2 NULL,
    scheduled_end DATETIME2 NULL,
    actual_end DATETIME2 NULL,
    status NVARCHAR(30) NOT NULL,
    cost DECIMAL(18,2) NULL,

    CONSTRAINT PK_MAINTENANCE_REQUESTS PRIMARY KEY (maintenance_id),
    CONSTRAINT FK_MAINTENANCE_REQUESTS_UNITS
        FOREIGN KEY (unit_id) REFERENCES dbo.STORAGE_UNITS(unit_id),
    CONSTRAINT FK_MAINTENANCE_REQUESTS_INSPECTIONS
        FOREIGN KEY (inspection_id) REFERENCES dbo.INSPECTION_REPORTS(inspection_id),
    CONSTRAINT FK_MAINTENANCE_REQUESTS_REPORTED_BY
        FOREIGN KEY (reported_by) REFERENCES dbo.USERS(user_id),
    CONSTRAINT FK_MAINTENANCE_REQUESTS_ASSIGNED_STAFF
        FOREIGN KEY (assigned_staff_id) REFERENCES dbo.USERS(user_id),
    CONSTRAINT CK_MAINTENANCE_REQUESTS_schedule
        CHECK (
            scheduled_end IS NULL OR scheduled_start IS NULL
            OR scheduled_end >= scheduled_start
        ),
    CONSTRAINT CK_MAINTENANCE_REQUESTS_cost
        CHECK (cost IS NULL OR cost >= 0)
);
GO

CREATE TABLE dbo.SUPPORT_REQUESTS (
    request_id UNIQUEIDENTIFIER NOT NULL
        CONSTRAINT DF_SUPPORT_REQUESTS_id DEFAULT NEWSEQUENTIALID(),
    contract_id UNIQUEIDENTIFIER NOT NULL,
    requested_by UNIQUEIDENTIFIER NOT NULL,
    assigned_staff_id UNIQUEIDENTIFIER NULL,
    request_type NVARCHAR(30) NOT NULL,
    title NVARCHAR(255) NOT NULL,
    description NVARCHAR(MAX) NOT NULL,
    priority NVARCHAR(20) NOT NULL,
    status NVARCHAR(30) NOT NULL,
    created_at DATETIME2 NOT NULL
        CONSTRAINT DF_SUPPORT_REQUESTS_created_at DEFAULT SYSDATETIME(),
    resolved_at DATETIME2 NULL,
    resolution_note NVARCHAR(MAX) NULL,

    CONSTRAINT PK_SUPPORT_REQUESTS PRIMARY KEY (request_id),
    CONSTRAINT FK_SUPPORT_REQUESTS_CONTRACTS
        FOREIGN KEY (contract_id) REFERENCES dbo.RENTAL_CONTRACTS(contract_id),
    CONSTRAINT FK_SUPPORT_REQUESTS_REQUESTED_BY
        FOREIGN KEY (requested_by) REFERENCES dbo.USERS(user_id),
    CONSTRAINT FK_SUPPORT_REQUESTS_ASSIGNED_STAFF
        FOREIGN KEY (assigned_staff_id) REFERENCES dbo.USERS(user_id)
);
GO

/* =========================================================
   8. PERFORMANCE INDEXES FOR FOREIGN KEYS
   ========================================================= */

CREATE INDEX IX_USERS_role_id ON dbo.USERS(role_id);
CREATE INDEX IX_ACTIVITY_LOGS_user_id ON dbo.ACTIVITY_LOGS(user_id);

CREATE INDEX IX_UFA_user_id ON dbo.USER_FACILITY_ASSIGNMENTS(user_id);
CREATE INDEX IX_UFA_facility_id ON dbo.USER_FACILITY_ASSIGNMENTS(facility_id);
CREATE INDEX IX_UFA_assigned_by ON dbo.USER_FACILITY_ASSIGNMENTS(assigned_by);

CREATE INDEX IX_RENTAL_POLICIES_facility_id ON dbo.RENTAL_POLICIES(facility_id);

CREATE INDEX IX_STORAGE_UNITS_type_id ON dbo.STORAGE_UNITS(type_id);

CREATE INDEX IX_UTC_category_id ON dbo.UNIT_TYPE_COMMODITIES(category_id);

CREATE INDEX IX_PRICING_POLICIES_facility_type
ON dbo.PRICING_POLICIES(facility_id, type_id);

CREATE INDEX IX_PRICING_POLICIES_created_by
ON dbo.PRICING_POLICIES(created_by);

CREATE INDEX IX_RESERVATIONS_customer_id ON dbo.RESERVATIONS(customer_id);
CREATE INDEX IX_RESERVATIONS_facility_type ON dbo.RESERVATIONS(facility_id, type_id);
CREATE INDEX IX_RESERVATIONS_pricing_id ON dbo.RESERVATIONS(pricing_id);

CREATE INDEX IX_RENTAL_CONTRACTS_customer_id ON dbo.RENTAL_CONTRACTS(customer_id);
CREATE INDEX IX_RENTAL_CONTRACTS_unit_id ON dbo.RENTAL_CONTRACTS(unit_id);
CREATE INDEX IX_RENTAL_CONTRACTS_approved_by ON dbo.RENTAL_CONTRACTS(approved_by);

CREATE INDEX IX_CONTRACT_COMMODITIES_contract_id
ON dbo.CONTRACT_COMMODITIES(contract_id);
CREATE INDEX IX_CONTRACT_COMMODITIES_category_id
ON dbo.CONTRACT_COMMODITIES(category_id);

CREATE INDEX IX_RENEWALS_contract_id ON dbo.RENEWALS(contract_id);
CREATE INDEX IX_RENEWALS_approved_by ON dbo.RENEWALS(approved_by);

CREATE INDEX IX_TERMINATION_REQUESTS_contract_id
ON dbo.TERMINATION_REQUESTS(contract_id);
CREATE INDEX IX_TERMINATION_REQUESTS_requested_by
ON dbo.TERMINATION_REQUESTS(requested_by);
CREATE INDEX IX_TERMINATION_REQUESTS_approved_by
ON dbo.TERMINATION_REQUESTS(approved_by);

CREATE INDEX IX_INVOICES_contract_id ON dbo.INVOICES(contract_id);

CREATE INDEX IX_PAYMENT_TRANSACTIONS_invoice_id
ON dbo.PAYMENT_TRANSACTIONS(invoice_id);

CREATE INDEX IX_HANDOVER_RECORDS_staff_id
ON dbo.HANDOVER_RECORDS(staff_id);

CREATE INDEX IX_INSPECTION_REPORTS_unit_id
ON dbo.INSPECTION_REPORTS(unit_id);
CREATE INDEX IX_INSPECTION_REPORTS_inspected_by
ON dbo.INSPECTION_REPORTS(inspected_by);

CREATE INDEX IX_MAINTENANCE_REQUESTS_unit_id
ON dbo.MAINTENANCE_REQUESTS(unit_id);
CREATE INDEX IX_MAINTENANCE_REQUESTS_inspection_id
ON dbo.MAINTENANCE_REQUESTS(inspection_id);
CREATE INDEX IX_MAINTENANCE_REQUESTS_reported_by
ON dbo.MAINTENANCE_REQUESTS(reported_by);
CREATE INDEX IX_MAINTENANCE_REQUESTS_assigned_staff_id
ON dbo.MAINTENANCE_REQUESTS(assigned_staff_id);

CREATE INDEX IX_SUPPORT_REQUESTS_contract_id
ON dbo.SUPPORT_REQUESTS(contract_id);
CREATE INDEX IX_SUPPORT_REQUESTS_requested_by
ON dbo.SUPPORT_REQUESTS(requested_by);
CREATE INDEX IX_SUPPORT_REQUESTS_assigned_staff_id
ON dbo.SUPPORT_REQUESTS(assigned_staff_id);
GO

PRINT N'SelfStorageFacilityDB schema created successfully.';
GO
