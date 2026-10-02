-- Separate third-party rental advertisements from verified SafeBox facilities.
-- Safe to run against an existing SelfStorageFacilityDB; does not modify core tables.
IF OBJECT_ID(N'dbo.DEMO_SOURCE_LISTINGS', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.DEMO_SOURCE_LISTINGS (
        source_listing_id NVARCHAR(50) NOT NULL PRIMARY KEY,
        source_url NVARCHAR(1000) NOT NULL UNIQUE,
        title NVARCHAR(500) NOT NULL,
        address NVARCHAR(1000) NULL,
        source_area_m2 DECIMAL(12,2) NULL,
        quoted_price DECIMAL(18,2) NULL,
        price_unit NVARCHAR(30) NULL,
        description NVARCHAR(MAX) NULL,
        amenities NVARCHAR(MAX) NULL,
        rental_terms NVARCHAR(MAX) NULL,
        cover_image_path NVARCHAR(500) NULL,
        image_rights_note NVARCHAR(500) NOT NULL,
        posted_on DATE NULL,
        collected_on DATE NOT NULL,
        demo_only BIT NOT NULL CONSTRAINT DF_DEMO_SOURCE_LISTINGS_demo_only DEFAULT (1)
    );
END;
