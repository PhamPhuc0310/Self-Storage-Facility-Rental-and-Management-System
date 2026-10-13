USE SelfStorageFacilityDB;
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
GO

-- The column is added once. WITH VALUES backfills only accounts present at this moment.
-- Re-running this script never marks a newly registered unverified account verified.
IF COL_LENGTH('dbo.USERS', 'email_verified') IS NULL
BEGIN
    ALTER TABLE dbo.USERS ADD email_verified BIT NOT NULL
        CONSTRAINT DF_USERS_email_verified DEFAULT (1) WITH VALUES;
END;
GO

IF OBJECT_ID('dbo.AUTH_TOKENS', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.AUTH_TOKENS (
        token_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_AUTH_TOKENS PRIMARY KEY,
        user_id UNIQUEIDENTIFIER NOT NULL,
        purpose NVARCHAR(20) NOT NULL,
        token_hash CHAR(64) NOT NULL,
        expires_at DATETIME2 NOT NULL,
        created_at DATETIME2 NOT NULL,
        sent_at DATETIME2 NULL,
        used_at DATETIME2 NULL,
        CONSTRAINT FK_AUTH_TOKENS_USERS FOREIGN KEY (user_id) REFERENCES dbo.USERS(user_id),
        CONSTRAINT CK_AUTH_TOKENS_purpose CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
        CONSTRAINT UQ_AUTH_TOKENS_user_purpose UNIQUE (user_id, purpose),
        CONSTRAINT UQ_AUTH_TOKENS_hash UNIQUE (token_hash)
    );
END;
GO
