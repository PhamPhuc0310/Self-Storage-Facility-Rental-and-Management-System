const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const base = process.env.SAFEBOX_E2E_BASE_URL || 'http://localhost:18080';
const password = process.env.SAFEBOX_E2E_PASSWORD;
const contractId = process.env.SAFEBOX_E2E_CONTRACT_ID;
if (!password || !contractId) throw new Error('SAFEBOX_E2E_PASSWORD and SAFEBOX_E2E_CONTRACT_ID are required');
const evidence = path.resolve(__dirname, '../../../../docs/testing/evidence');
const evidencePrefix = process.env.SAFEBOX_E2E_EVIDENCE_PREFIX || 'checkin';
const events = [];
const result = {contractId, fullHandover: false};

async function login(browser, email) {
  const context = await browser.newContext({viewport:{width:1440,height:900}});
  const page = await context.newPage();
  page.on('response', response => {
    if (response.url().startsWith(base + '/api/')) events.push({actor:email,method:response.request().method(),path:new URL(response.url()).pathname,status:response.status()});
  });
  await page.goto(base + '/stitch/ng_nh_p_ng_k_safebox_storage.html', {waitUntil:'domcontentloaded'});
  await page.locator('#login-email').fill(email);
  await page.locator('#login-password').fill(password);
  await page.locator('#btn-login-submit').click();
  await page.waitForURL(url => !url.pathname.endsWith('/ng_nh_p_ng_k_safebox_storage.html'));
  return {context,page};
}

async function main() {
  const browser = await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
  try {
    const customer = await login(browser,'customer3@safebox.vn');
    await customer.page.goto(base + '/stitch/uc11-customer-payments-appointments.html?contractId=' + contractId,{waitUntil:'domcontentloaded'});
    await customer.page.locator('#contract-detail').waitFor({state:'visible'});
    if (!process.env.SAFEBOX_E2E_DEPOSIT_ALREADY_PAID) {
      assert.equal(await customer.page.locator('#appointment-create').isDisabled(),true);
      result.preDepositAppointmentDisabled = true;
      const payButton = customer.page.locator('.pay-button[data-type="DEPOSIT"]');
      await payButton.click();
      const depositAmount = Number(await customer.page.locator('#payment-amount').inputValue());
      result.depositAmount = depositAmount;
      assert.ok(depositAmount > 0);
      const payResponse = customer.page.waitForResponse(r => new URL(r.url()).pathname === `/api/contracts/${contractId}/payments` && r.request().method()==='POST');
      await customer.page.locator('#payment-submit').click();
      result.depositPostStatus = (await payResponse).status();
      assert.equal(result.depositPostStatus,201);
    } else result.depositAlreadyPaid = true;
    if (!process.env.SAFEBOX_E2E_APPOINTMENT_ALREADY_CREATED) {
      await customer.page.locator('#appointment-create').waitFor({state:'visible'});
      await customer.page.waitForFunction(() => !document.getElementById('appointment-create').disabled,undefined,{timeout:12000});
    }
    await customer.page.screenshot({path:path.join(evidence,`${evidencePrefix}-customer-deposit.png`),fullPage:true});
    if (!process.env.SAFEBOX_E2E_APPOINTMENT_ALREADY_CREATED) {
      await customer.page.locator('#appointment-create').click();
      await customer.page.locator('#appointment-date').fill(process.env.SAFEBOX_E2E_APPOINTMENT_DATE || '03/11/2026');
      await customer.page.locator('#appointment-slot').selectOption(process.env.SAFEBOX_E2E_APPOINTMENT_SLOT || '10:00');
      const appointmentResponse = customer.page.waitForResponse(r => new URL(r.url()).pathname === `/api/contracts/${contractId}/pickup-appointment` && r.request().method()==='POST');
      await customer.page.locator('#appointment-submit').click();
      result.appointmentPostStatus = (await appointmentResponse).status();
      assert.equal(result.appointmentPostStatus,201);
    } else result.appointmentAlreadyCreated = true;
    await customer.page.locator('#appointment-info').getByText('Đã đặt lịch').waitFor();
    await customer.page.screenshot({path:path.join(evidence,`${evidencePrefix}-customer-appointment.png`),fullPage:true});
    await customer.context.close();

    const manager = await login(browser,'manager.q7@safebox.vn');
    await manager.page.goto(base + '/staff-tasks.html',{waitUntil:'domcontentloaded'});
    const assign = manager.page.locator(`#awaiting-list .show-staff-options[data-id="${contractId}"]`);
    await assign.waitFor();
    await assign.click();
    const select = assign.locator('xpath=following-sibling::div').locator('.choose-staff');
    await select.waitFor();
    const staffId = await select.locator('option').nth(1).getAttribute('value');
    assert.ok(staffId);
    result.staffId = staffId;
    await select.selectOption(staffId);
    const assignResponse = manager.page.waitForResponse(r => new URL(r.url()).pathname === `/api/manager/contracts/${contractId}/pickup-appointment/assignment` && r.request().method()==='PUT');
    await assign.locator('xpath=following-sibling::div').locator('.confirm-staff-assignment').click();
    result.assignmentStatus = (await assignResponse).status();
    assert.equal(result.assignmentStatus,200);
    await manager.page.screenshot({path:path.join(evidence,`${evidencePrefix}-manager-assigned.png`),fullPage:true});
    await manager.context.close();
  } finally {
    fs.writeFileSync(path.join(evidence,`${evidencePrefix}-results.json`),JSON.stringify({result,events},null,2));
    await browser.close();
  }
  console.log(JSON.stringify(result));
}
main().catch(e=>{console.error(e.stack||String(e));process.exitCode=1;});
