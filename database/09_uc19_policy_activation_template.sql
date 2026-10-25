-- MANUAL ONLY. A business owner must approve every value before changing @Approved to 1.
-- Run against SelfStorageFacilityDB after 08_uc19_uc24_apply.sql.
-- This template deliberately performs no write in its checked-in state.
SET XACT_ABORT ON;
DECLARE @Approved bit = 0;
DECLARE @FacilityId uniqueidentifier = NULL;
DECLARE @Version nvarchar(50) = NULL;
DECLARE @EffectiveFrom date = NULL;
DECLARE @EffectiveTo date = NULL; -- NULL means no scheduled end.
DECLARE @DepositRefundable bit = NULL;
DECLARE @InspectionDeductionAllowed bit = NULL;

IF @Approved <> 1
    THROW 51024, 'Business approval is required before activating an early-return policy.', 1;
IF @FacilityId IS NULL OR @Version IS NULL OR LEN(TRIM(@Version)) = 0
    OR @EffectiveFrom IS NULL OR @DepositRefundable IS NULL OR @InspectionDeductionAllowed IS NULL
    THROW 51025, 'All required policy values must be supplied.', 1;
IF @EffectiveTo IS NOT NULL AND @EffectiveTo < @EffectiveFrom
    THROW 51026, 'Policy effective date range is invalid.', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.FACILITIES WHERE facility_id = @FacilityId)
    THROW 51027, 'Facility does not exist.', 1;
IF EXISTS (SELECT 1 FROM dbo.EARLY_RETURN_POLICIES
           WHERE facility_id = @FacilityId AND version = @Version)
    THROW 51028, 'Policy version already exists for this facility.', 1;
IF EXISTS (SELECT 1 FROM dbo.EARLY_RETURN_POLICIES
           WHERE facility_id = @FacilityId AND status = N'ACTIVE'
             AND effective_from <= COALESCE(@EffectiveTo,CONVERT(date,'99991231'))
             AND (effective_to IS NULL OR effective_to >= @EffectiveFrom))
    THROW 51029, 'Active policy dates overlap; close or review the existing version first.', 1;

BEGIN TRANSACTION;
INSERT dbo.EARLY_RETURN_POLICIES
    (policy_id,facility_id,version,effective_from,effective_to,deposit_refundable,inspection_deduction_allowed,status)
VALUES
    (NEWID(),@FacilityId,@Version,@EffectiveFrom,@EffectiveTo,@DepositRefundable,@InspectionDeductionAllowed,N'ACTIVE');
COMMIT TRANSACTION;
