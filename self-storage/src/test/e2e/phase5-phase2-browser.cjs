const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const base = process.env.SAFEBOX_E2E_BASE_URL;
const password = process.env.SAFEBOX_E2E_PASSWORD;
if (base !== 'http://127.0.0.1:18081' || !password) throw Error('Isolated app URL and test password required');
const managerId = '3b4e5607-b0b4-f111-a965-932c2c6addb7';
const ph15 = 'ede766ca-d3d3-460d-b027-1220305f6e98';
const nvq = '414e5607-b0b4-f111-a965-932c2c6addb7';
const outside = '404e5607-b0b4-f111-a965-932c2c6addb7';
const evidence = path.resolve(__dirname, '../../../../docs/testing/evidence/phase5');
fs.mkdirSync(evidence, { recursive: true });
const result = { database: 'SelfStorageFacilityDB_Test', events: [], checks: {} };
const save = () => fs.writeFileSync(path.join(evidence, 'phase2-browser.json'), JSON.stringify(result, null, 2));

async function main() {
  const browser = await chromium.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true });
  const sessions = [];
  let granted = false;
  async function login(email) {
    const context = await browser.newContext({ viewport: { width: 1366, height: 900 } });
    const page = await context.newPage();
    page.setDefaultTimeout(12000);
    page.on('response', response => {
      if (response.url().startsWith(base + '/api/')) result.events.push({ actor: email, method: response.request().method(), path: new URL(response.url()).pathname, status: response.status() });
    });
    sessions.push(context);
    await page.goto(base + '/stitch/ng_nh_p_ng_k_safebox_storage.html', { waitUntil: 'domcontentloaded' });
    await page.locator('#login-email').fill(email);
    await page.locator('#login-password').fill(password);
    const response = page.waitForResponse(r => new URL(r.url()).pathname === '/api/auth/login' && r.request().method() === 'POST');
    await page.locator('#btn-login-submit').click();
    assert.equal((await response).status(), 200);
    await page.waitForURL(url => !url.pathname.endsWith('/ng_nh_p_ng_k_safebox_storage.html'));
    return page;
  }
  async function clickApi(page, selector, pathPart, method = 'POST') {
    const response = page.waitForResponse(r => new URL(r.url()).pathname.includes(pathPart) && r.request().method() === method);
    await page.locator(selector).click();
    const r = await response;
    return { status: r.status(), body: await r.json().catch(() => ({})) };
  }
  async function browserApi(page, url, method, body) {
    return page.evaluate(async ({ url, method, body }) => {
      const r = await fetch(url, { method, headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${SafeBoxAuth.getToken()}` }, body: body ? JSON.stringify(body) : undefined });
      return { status: r.status, body: await r.json().catch(() => ({})) };
    }, { url, method, body });
  }
  let admin;
  try {
    admin = await login('admin@safebox.vn');
    await admin.goto(base + '/management-admin.html');
    await admin.locator(`input[data-facility="${managerId}"]`).fill(ph15);
    const grant = await clickApi(admin, `button[data-action="grant-facility"][data-id="${managerId}"]`, `/api/admin/access/managers/${managerId}/facilities/${ph15}`);
    assert.equal(grant.status, 200); granted = true; result.checks.adminGrant = grant.status; save();

    const manager = await login('manager.q7@safebox.vn');
    await manager.goto(base + '/management-pricing.html');
    await manager.locator('#facility').selectOption(ph15);
    await manager.locator('#type option[value="1"]').waitFor({ state: 'attached' });
    let priceProposalId = process.env.SAFEBOX_PHASE5_PRICE_PROPOSAL_ID;
    if (priceProposalId) {
      result.checks.priceProposal = { id: priceProposalId, status: 'SUBMITTED', reused: true };
    } else {
      await manager.locator('#type').selectOption('1');
      await manager.locator('#price').fill('900000');
      await manager.locator('#from').fill('2027-01-01');
      await manager.locator('#to').fill('2027-01-31');
      await manager.locator('#reason').fill('Đề xuất kiểm tra quy trình giá cho cơ sở thử nghiệm.');
      const submittedPrice = await clickApi(manager, '#submit', `/api/manager/facilities/${ph15}/price-proposals`);
      assert.equal(submittedPrice.status, 200);
      assert.equal(submittedPrice.body.status, 'SUBMITTED');
      priceProposalId = submittedPrice.body.proposalId;
      result.checks.priceProposal = { id: priceProposalId, status: submittedPrice.body.status, effectiveFrom: submittedPrice.body.effectiveFrom };
    }
    await manager.screenshot({ path: path.join(evidence, '01-manager-price-proposal.png'), fullPage: true }); save();

    const directPrice = await browserApi(manager, `/api/business/facilities/${ph15}/prices`, 'POST', { typeId: 1, monthlyPrice: 900000, effectiveFrom: '2027-01-01', effectiveTo: '2027-01-31' });
    assert.equal(directPrice.status, 403); result.checks.managerDirectPricingDenied = directPrice.status;
    const crossFacility = await browserApi(manager, `/api/manager/facilities/${outside}/price-proposals`, 'POST', { schedule: { typeId: 1, monthlyPrice: 900000, effectiveFrom: '2027-01-01', effectiveTo: '2027-01-31' }, reason: 'Đề xuất kiểm tra phạm vi cơ sở khác.' });
    assert.equal(crossFacility.status, 403); result.checks.managerCrossFacilityDenied = crossFacility.status;

    const business = await login('business@safebox.vn');
    await business.goto(base + '/management-pricing.html');
    await business.locator('#facility').selectOption(ph15);
    const applyButton = business.locator(`button[data-proposal="${priceProposalId}"][data-action="apply"]`);
    await applyButton.waitFor();
    const appliedPrice = await clickApi(business, `button[data-proposal="${priceProposalId}"][data-action="apply"]`, `/api/business/price-proposals/${priceProposalId}/apply`);
    assert.equal(appliedPrice.status, 200); assert.equal(appliedPrice.body.status, 'APPLIED');
    result.checks.priceApplied = { status: appliedPrice.body.status, pricingId: appliedPrice.body.pricingId };
    await business.locator(`#prices article`).filter({ hasText: '2027-01-01' }).first().waitFor();
    await business.screenshot({ path: path.join(evidence, '02-business-price-applied.png'), fullPage: true }); save();

    await manager.locator('#type').selectOption('2');
    await manager.locator('#price').fill('1440000');
    await manager.locator('#from').fill('2027-02-01');
    await manager.locator('#to').fill('2027-02-28');
    await manager.locator('#reason').fill('Đề xuất dùng để kiểm tra quyết định từ chối giá.');
    const rejectedCandidate = await clickApi(manager, '#submit', `/api/manager/facilities/${ph15}/price-proposals`);
    assert.equal(rejectedCandidate.status, 200);
    await business.reload();
    await business.locator('#facility').selectOption(ph15);
    const rejectedPrice = await clickApi(business, `button[data-proposal="${rejectedCandidate.body.proposalId}"][data-action="reject"]`, `/api/business/price-proposals/${rejectedCandidate.body.proposalId}/reject`);
    assert.equal(rejectedPrice.status, 200); assert.equal(rejectedPrice.body.status, 'REJECTED');
    result.checks.priceRejected = rejectedPrice.body.proposalId; save();

    await manager.goto(base + '/management-policies.html');
    await manager.locator('#facility').selectOption(nvq);
    await manager.locator('#base-policy option').first().waitFor({ state: 'attached' });
    const basePolicyId = await manager.locator('#base-policy').inputValue();
    assert.ok(basePolicyId);
    async function makePolicy(from, reason) {
      await manager.locator('#grace').fill('3');
      await manager.locator('#penalty-type').selectOption('PERCENT_DAILY');
      await manager.locator('#daily-rate').fill('0.05');
      await manager.locator('#fixed-amount').fill('0');
      await manager.locator('#from').fill(from);
      await manager.locator('#reason').fill(reason);
      const draft = await clickApi(manager, '#proposal-form button[type="submit"], #proposal-form button:not([type])', `/api/manager/facilities/${nvq}/overdue-policy-proposals`);
      assert.equal(draft.status, 200); assert.equal(draft.body.status, 'DRAFT');
      const id = draft.body.proposalId;
      const submitted = await clickApi(manager, `#proposals article[data-id="${id}"] button[data-action="submit"]`, `/api/manager/facilities/${nvq}/overdue-policy-proposals/${id}/submit`);
      assert.equal(submitted.status, 200); assert.equal(submitted.body.status, 'SUBMITTED');
      return id;
    }
    const policyId = await makePolicy('2027-04-01', 'Kiểm tra phê duyệt phiên bản tương lai, giữ nguyên mức hiện hành.');
    result.checks.policySubmitted = { id: policyId, basePolicyId, from: '2027-04-01' };
    await manager.screenshot({ path: path.join(evidence, '03-manager-policy-submitted.png'), fullPage: true }); save();

    const managerApprove = await browserApi(manager, `/api/business/facilities/${nvq}/overdue-policy-proposals/${policyId}/approve`, 'POST', { reason: 'Kiểm tra quyền duyệt' });
    assert.equal(managerApprove.status, 403); result.checks.managerPolicyApprovalDenied = 403;
    const adminApprove = await browserApi(admin, `/api/business/facilities/${nvq}/overdue-policy-proposals/${policyId}/approve`, 'POST', { reason: 'Kiểm tra quyền duyệt' });
    assert.equal(adminApprove.status, 403); result.checks.adminPolicyApprovalDenied = 403;
    const businessHandover = await browserApi(business, '/api/staff/contracts/6f8c8a98-8ef4-40fa-93ee-1720bd51f1dc/handover', 'POST', {});
    assert.equal(businessHandover.status, 403); result.checks.businessStaffHandoverDenied = 403;

    await business.goto(base + '/management-policies.html');
    await business.locator('#facility').selectOption(nvq);
    const policyCard = business.locator(`#proposals article[data-id="${policyId}"]`);
    await policyCard.locator('.decision-reason').fill('Đã đối chiếu với chính sách đang áp dụng.');
    const approved = await clickApi(business, `#proposals article[data-id="${policyId}"] button[data-action="approve"]`, `/api/business/facilities/${nvq}/overdue-policy-proposals/${policyId}/approve`);
    assert.equal(approved.status, 200); assert.equal(approved.body.status, 'APPROVED');
    result.checks.policyApproved = { id: policyId, policyId: approved.body.approvedPolicyId, status: approved.body.status };
    await business.screenshot({ path: path.join(evidence, '04-business-policy-approved.png'), fullPage: true }); save();

    const rejectPolicyId = await makePolicy('2027-05-01', 'Kiểm tra từ chối đề xuất mà không đổi chính sách hiện hành.');
    await business.reload(); await business.locator('#facility').selectOption(nvq);
    const rejectCard = business.locator(`#proposals article[data-id="${rejectPolicyId}"]`);
    await rejectCard.locator('.decision-reason').fill('Đề xuất kiểm thử không được áp dụng.');
    const rejected = await clickApi(business, `#proposals article[data-id="${rejectPolicyId}"] button[data-action="reject"]`, `/api/business/facilities/${nvq}/overdue-policy-proposals/${rejectPolicyId}/reject`);
    assert.equal(rejected.status, 200); assert.equal(rejected.body.status, 'REJECTED');
    result.checks.policyRejected = rejectPolicyId;
    await business.screenshot({ path: path.join(evidence, '05-business-policy-rejected.png'), fullPage: true }); save();

    const customer = await login('customer3@safebox.vn');
    const customerPrice = await browserApi(customer, `/api/business/facilities/${ph15}/prices`, 'GET');
    assert.equal(customerPrice.status, 403); result.checks.customerBusinessPricingDenied = 403;
    const staff = await login('staff.q7@safebox.vn');
    const staffPolicy = await browserApi(staff, `/api/business/facilities/${nvq}/overdue-policy-proposals/${policyId}/approve`, 'POST', { reason: 'Không có quyền' });
    assert.equal(staffPolicy.status, 403); result.checks.staffPolicyApprovalDenied = 403;
    save();
  } finally {
    if (granted && admin) {
      try {
        await admin.goto(base + '/management-admin.html');
        await admin.locator(`input[data-facility="${managerId}"]`).fill(ph15);
        const revoke = await clickApi(admin, `button[data-action="revoke-facility"][data-id="${managerId}"]`, `/api/admin/access/managers/${managerId}/facilities/${ph15}`, 'DELETE');
        result.checks.adminRevoke = revoke.status;
      } catch (error) { result.checks.adminRevokeError = String(error); }
    }
    save();
    for (const session of sessions) await session.close().catch(() => {});
    await browser.close();
  }
  console.log(JSON.stringify(result.checks));
}
main().catch(error => { console.error(error.stack || String(error)); process.exitCode = 1; });
