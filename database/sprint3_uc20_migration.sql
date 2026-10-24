-- Sprint 3 - UC20 Migration Script
-- UC20: Hoàn tất trả kho, kiểm tra và đóng hợp đồng

IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_NAME = 'INSPECTION_REPORTS' AND COLUMN_NAME = 'evidence_urls'
)
BEGIN
    ALTER TABLE INSPECTION_REPORTS ADD evidence_urls NVARCHAR(MAX) NULL;
END
GO
