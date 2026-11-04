const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const base = process.env.SAFEBOX_E2E_BASE_URL;
const password = process.env.SAFEBOX_E2E_PASSWORD;
if (base !== 'http://127.0.0.1:18081' || !password) throw Error('Isolated app URL and test password required');
const facility = '414e5607-b0b4-f111-a965-932c2c6addb7';
const basePolicy = '00000000-0000-4000-8000-000000001018';
const evidence = path.resolve(__dirname, '../../../../docs/testing/evidence/phase5');
fs.mkdirSync(evidence, { recursive: true });
const result = { database: 'SelfStorageFacilityDB_Test', events: [], checks: {} };
const save = () => fs.writeFileSync(path.join(evidence, 'policy-browser.json'), JSON.stringify(result, null, 2));

async function main() {
  const browser = await chromium.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true });
  const contexts = [];
  async function login(email) {
    const context = await browser.newContext({ viewport: { width: 1366, height: 900 } });
    contexts.push(context);
    const page = await context.newPage(); page.setDefaultTimeout(12000);
    page.on('response', response => {
      if (response.url().startsWith(base + '/api/')) result.events.push({ actor: email, method: response.request().method(), path: new URL(response.url()).pathname, status: response.status() });
    });
    await page.goto(base + '/stitch/ng_nh_p_ng_k_safebox_storage.html');
    await page.locator('#login-email').fill(email); await page.locator('#login-password').fill(password);
    const response = page.waitForResponse(r => new URL(r.url()).pathname === '/api/auth/login' && r.request().method() === 'POST');
    await page.locator('#btn-login-submit').click(); assert.equal((await response).status(), 200);
    await page.waitForURL(url => !url.pathname.endsWith('/ng_nh_p_ng_k_safebox_storage.html'));
    return page;
  }
  async function clickApi(page, selector, pathPart) {
    const pending = page.waitForResponse(r => new URL(r.url()).pathname.includes(pathPart) && r.request().method() === 'POST');
    await page.locator(selector).click(); const response = await pending;
    return { status: response.status(), body: await response.json().catch(() => ({})) };
  }
  async function rawApi(page, url, body) {
    return page.evaluate(async ({ url, body }) => {
      const r = await fetch(url, { method: 'POST', headers: { Authorization: `Bearer ${SafeBoxAuth.getToken()}`, 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
      return r.status;
    }, { url, body });
  }
  async function selectFacility(page) {
    await page.goto(base + '/management-policies.html');
    await page.locator('#facility').selectOption(facility);
    await page.locator(`#base-policy option[value="${basePolicy}"]`).waitFor({ state: 'attached' }).catch(() => {});
  }
  try {
    const manager = await login('manager.q7@safebox.vn');
    await selectFacility(manager);
    await manager.locator('#base-policy').selectOption(basePolicy);
    async function propose(from, reason) {
      await manager.locator('#grace').fill('3');
      await manager.locator('#penalty-type').selectOption('PERCENT_DAILY');
      await manager.locator('#daily-rate').fill('0.05');
      await manager.locator('#fixed-amount').fill('0');
      await manager.locator('#from').fill(from);
      await manager.locator('#reason').fill(reason);
      const draft = await clickApi(manager, '#proposal-form button:not([type])', `/api/manager/facilities/${facility}/overdue-policy-proposals`);
      assert.equal(draft.status, 200, JSON.stringify(draft.body)); assert.equal(draft.body.status, 'DRAFT');
      const id = draft.body.proposalId;
      const submitted = await clickApi(manager, `#proposals article[data-id="${id}"] button[data-action="submit"]`, `/api/manager/facilities/${facility}/overdue-policy-proposals/${id}/submit`);
      assert.equal(submitted.status, 200, JSON.stringify(submitted.body)); assert.equal(submitted.body.status, 'SUBMITTED');
      return id;
    }
    const approvedId = await propose('2027-04-01', 'Kiểm tra phê duyệt tương lai với chính sách hiện hành không đổi.');
    result.checks.managerSubmitted = { id: approvedId, basePolicy, from: '2027-04-01' };
    await manager.screenshot({ path: path.join(evidence, '06-manager-policy-submitted.png'), fullPage: true }); save();

    assert.equal(await rawApi(manager, `/api/business/facilities/${facility}/overdue-policy-proposals/${approvedId}/approve`, { reason: 'Không có quyền duyệt' }), 403);
    result.checks.managerApprovalDenied = 403;
    const admin = await login('admin@safebox.vn');
    assert.equal(await rawApi(admin, `/api/business/facilities/${facility}/overdue-policy-proposals/${approvedId}/approve`, { reason: 'Không có quyền duyệt' }), 403);
    result.checks.adminApprovalDenied = 403;
    const staff = await login('staff.q7@safebox.vn');
    assert.equal(await rawApi(staff, `/api/business/facilities/${facility}/overdue-policy-proposals/${approvedId}/approve`, { reason: 'Không có quyền duyệt' }), 403);
    result.checks.staffApprovalDenied = 403;

    const business = await login('business@safebox.vn');
    await business.goto(base + '/management-policies.html');
    await business.locator('#facility').selectOption(facility);
    const card = business.locator(`#proposals article[data-id="${approvedId}"]`);
    await card.locator('.decision-reason').fill('Đã đối chiếu với mức hiện hành và ngày hiệu lực.');
    const approved = await clickApi(business, `#proposals article[data-id="${approvedId}"] button[data-action="approve"]`, `/api/business/facilities/${facility}/overdue-policy-proposals/${approvedId}/approve`);
    assert.equal(approved.status, 200, JSON.stringify(approved.body)); assert.equal(approved.body.status, 'APPROVED');
    result.checks.businessApproved = { id: approvedId, policyId: approved.body.approvedPolicyId, status: approved.body.status };
    await business.screenshot({ path: path.join(evidence, '07-business-policy-approved.png'), fullPage: true }); save();

    const rejectedId = await propose('2027-05-01', 'Kiểm tra từ chối đề xuất, giữ nguyên chính sách được duyệt.');
    await business.reload(); await business.locator('#facility').selectOption(facility);
    const rejectCard = business.locator(`#proposals article[data-id="${rejectedId}"]`);
    await rejectCard.locator('.decision-reason').fill('Đề xuất kiểm thử không được áp dụng.');
    const rejected = await clickApi(business, `#proposals article[data-id="${rejectedId}"] button[data-action="reject"]`, `/api/business/facilities/${facility}/overdue-policy-proposals/${rejectedId}/reject`);
    assert.equal(rejected.status, 200, JSON.stringify(rejected.body)); assert.equal(rejected.body.status, 'REJECTED');
    result.checks.businessRejected = rejectedId;
    await business.screenshot({ path: path.join(evidence, '08-business-policy-rejected.png'), fullPage: true }); save();
    const customer = await login('customer3@safebox.vn');
    assert.equal(await rawApi(customer, `/api/business/facilities/${facility}/overdue-policy-proposals/${approvedId}/approve`, { reason: 'Không có quyền duyệt' }), 403);
    result.checks.customerApprovalDenied = 403;
  } finally {
    save(); for (const context of contexts) await context.close().catch(() => {}); await browser.close();
  }
  console.log(JSON.stringify(result.checks));
}
main().catch(error => { console.error(error.stack || String(error)); process.exitCode = 1; });
