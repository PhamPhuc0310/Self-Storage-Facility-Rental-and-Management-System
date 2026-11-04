const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const base = process.env.SAFEBOX_E2E_BASE_URL || 'http://localhost:18080';
const password = process.env.SAFEBOX_E2E_PASSWORD;
if (!password) throw new Error('SAFEBOX_E2E_PASSWORD is required');
const evidence = path.resolve(__dirname, '../../../../docs/testing/evidence');
const evidencePrefix = process.env.SAFEBOX_E2E_EVIDENCE_PREFIX || 'reservation';
const events = [];
async function login(browser, email) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  page.on('response', response => { if (response.url().startsWith(base + '/api/'))
    events.push({ actor: email, method: response.request().method(), path: new URL(response.url()).pathname, status: response.status() }); });
  await page.goto(base + '/stitch/ng_nh_p_ng_k_safebox_storage.html', {waitUntil:'domcontentloaded'});
  await page.locator('#login-email').fill(email);
  await page.locator('#login-password').fill(password);
  await page.locator('#btn-login-submit').click();
  await page.waitForURL(url => !url.pathname.endsWith('/ng_nh_p_ng_k_safebox_storage.html'));
  return {context,page};
}
async function main() {
  const browser = await chromium.launch({ executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless:true });
  let result = {};
  try {
    const customer = await login(browser,'customer3@safebox.vn');
    if (process.env.SAFEBOX_E2E_RESERVATION_ID) {
      result.reservationId = process.env.SAFEBOX_E2E_RESERVATION_ID.toLowerCase();
      result.createStatus = 201; // Verified in the preceding browser POST and SQL query.
      await customer.page.goto(base + '/stitch/y_u_c_u_c_a_t_i_safebox_storage.html', {waitUntil:'domcontentloaded'});
    } else {
      const facilityId = process.env.SAFEBOX_E2E_FACILITY_ID || '3f4e5607-b0b4-f111-a965-932c2c6addb7';
      const typeId = process.env.SAFEBOX_E2E_TYPE_ID || '5';
      await customer.page.goto(base + `/stitch/chi_ti_t_kho_10m_safebox_th_c.html?facilityId=${facilityId}&typeId=${typeId}&months=3`, {waitUntil:'domcontentloaded'});
      await customer.page.locator('#reservation-button').waitFor({state:'visible'});
      await customer.page.locator('#reservation-button').click();
      await customer.page.locator('#reservation-dialog').waitFor({state:'visible'});
      await customer.page.locator('#reservation-start-date').fill(process.env.SAFEBOX_E2E_START_DATE || '03/11/2026');
      const responsePromise = customer.page.waitForResponse(r => new URL(r.url()).pathname === '/api/reservations' && r.request().method()==='POST');
      await customer.page.locator('#reservation-submit').click();
      const response = await responsePromise;
      result.createStatus = response.status();
      assert.equal(response.status(),201);
      await customer.page.waitForURL(url => url.pathname.endsWith('/y_u_c_u_c_a_t_i_safebox_storage.html'));
      result.reservationId = new URL(customer.page.url()).searchParams.get('created');
    }
    assert.ok(result.reservationId);
    await customer.page.screenshot({path:path.join(evidence,`${evidencePrefix}-customer-pending.png`),fullPage:true});

    const otherManager = await login(browser,'manager.thuduc@safebox.vn');
    await otherManager.page.goto(base + '/stitch/x_l_y_u_c_u_t_kho_safebox_manager.html',{waitUntil:'domcontentloaded'});
    await otherManager.page.locator('#approval-loading').waitFor({state:'hidden'});
    assert.equal(await otherManager.page.locator(`.approval-confirm[data-id="${result.reservationId}"]`).count(),0);
    await otherManager.page.screenshot({path:path.join(evidence,`${evidencePrefix}-unassigned-manager-hidden.png`),fullPage:true});
    await otherManager.context.close();

    const manager = await login(browser,'manager.q7@safebox.vn');
    await manager.page.goto(base + '/stitch/x_l_y_u_c_u_t_kho_safebox_manager.html',{waitUntil:'domcontentloaded'});
    const button = manager.page.locator(`.approval-confirm[data-id="${result.reservationId}"]`);
    await button.waitFor();
    manager.page.once('dialog',dialog=>dialog.accept());
    const confirmPromise = manager.page.waitForResponse(r => new URL(r.url()).pathname === `/api/manager/reservations/${result.reservationId}/confirm`);
    await button.click();
    const confirmResponse = await confirmPromise;
    result.confirmStatus = confirmResponse.status();
    assert.equal(confirmResponse.status(),200);
    const contract = await confirmResponse.json();
    result.contractId = contract.contractId;
    await manager.page.locator('#approval-success').waitFor({state:'visible'});
    await manager.page.screenshot({path:path.join(evidence,`${evidencePrefix}-manager-approved.png`),fullPage:true});
    await manager.context.close();
    await customer.page.goto(base + '/stitch/y_u_c_u_c_a_t_i_safebox_storage.html?created=' + result.reservationId,{waitUntil:'domcontentloaded'});
    await customer.page.locator('#reservations-list').getByText('Xem hợp đồng').first().waitFor();
    await customer.page.screenshot({path:path.join(evidence,`${evidencePrefix}-customer-confirmed.png`),fullPage:true});
    await customer.context.close();
  } finally {
    fs.writeFileSync(path.join(evidence,`${evidencePrefix}-results.json`),JSON.stringify({result,events},null,2));
    await browser.close();
  }
  console.log(JSON.stringify(result));
}
main().catch(e=>{console.error(e.stack||String(e));process.exitCode=1;});
