const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const base = process.env.SAFEBOX_E2E_BASE_URL || 'http://localhost:18080';
const password = process.env.SAFEBOX_E2E_PASSWORD;
if (!password) throw new Error('SAFEBOX_E2E_PASSWORD is required');
const evidence = path.resolve(__dirname, '../../../../docs/testing/evidence');
fs.mkdirSync(evidence, { recursive: true });
const events = [];
const results = [];
const title = `Browser support ${Date.now()}`;
const facility = '414e5607-b0b4-f111-a965-932c2c6addb7';
const staffId = '384E5607-B0B4-F111-A965-932C2C6ADDB7';

async function login(browser, email) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  page.on('response', response => {
    if (response.url().startsWith(`${base}/api/`))
      events.push({ actor: email, method: response.request().method(), path: new URL(response.url()).pathname,
        status: response.status() });
  });
  await page.goto(`${base}/stitch/ng_nh_p_ng_k_safebox_storage.html`, { waitUntil: 'domcontentloaded' });
  await page.locator('#login-email').fill(email);
  await page.locator('#login-password').fill(password);
  await page.locator('#btn-login-submit').click();
  await page.waitForFunction(() => !!localStorage.getItem('safebox.token') || !!sessionStorage.getItem('safebox.token'));
  await page.waitForURL(url => !url.pathname.endsWith('/ng_nh_p_ng_k_safebox_storage.html'));
  return { context, page };
}

async function screenshot(page, name) {
  await page.screenshot({ path: path.join(evidence, name), fullPage: true });
}

async function main() {
  const browser = await chromium.launch({ executablePath: process.env.SAFEBOX_EDGE_PATH ||
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true });
  try {
    const bom = await login(browser, 'business@safebox.vn');
    await bom.page.goto(`${base}/role-home.html`, { waitUntil: 'domcontentloaded' });
    await bom.page.locator('#reports-link').click();
    await bom.page.locator('#facility option').nth(1).waitFor({ state: 'attached' });
    await bom.page.locator('#start-date').fill('2026-10-01');
    await bom.page.locator('#end-date').fill('2026-10-27');
    await bom.page.locator('#facility').selectOption(facility);
    await bom.page.locator('#report-form button').click();
    await bom.page.locator('#results').waitFor({ state: 'visible' });
    const rentText = await bom.page.locator('[data-metric="rentalCollections"]').innerText();
    const depositText = await bom.page.locator('[data-metric="depositCollections"]').innerText();
    assert.match(rentText, /29[.,\s]592[.,\s]000/);
    assert.match(depositText, /10[.,\s]296[.,\s]000/);
    await screenshot(bom.page, 'uc24-browser-report.png');
    results.push({ flow: 'Fee / Revenue', check: 'BOM browser report', status: 'PASS', rentText, depositText });
    await bom.context.close();

    const customer = await login(browser, 'customer3@safebox.vn');
    await customer.page.goto(`${base}/stitch/ho_tro_safebox.html`, { waitUntil: 'domcontentloaded' });
    await customer.page.locator('#contract option').nth(1).waitFor({ state: 'attached' });
    await customer.page.locator('#contract').selectOption('be72e253-7fb9-48fe-961c-a22b5366d75b');
    await customer.page.locator('#title').fill(title);
    await customer.page.locator('#description').fill('Created from a real browser against isolated SQL Server');
    await customer.page.locator('#create-form button').click();
    const customerCard = customer.page.locator('article').filter({ hasText: title });
    await customerCard.waitFor();
    await screenshot(customer.page, 'support-customer-created.png');

    const otherCustomer = await login(browser, 'customer1@safebox.vn');
    await otherCustomer.page.goto(`${base}/stitch/ho_tro_safebox.html`, { waitUntil: 'domcontentloaded' });
    await otherCustomer.page.locator('#tickets').waitFor({ state: 'visible' });
    assert.equal(await otherCustomer.page.locator('article').filter({ hasText: title }).count(), 0);
    await screenshot(otherCustomer.page, 'support-other-customer-hidden.png');
    await otherCustomer.context.close();

    const manager = await login(browser, 'manager.q7@safebox.vn');
    await manager.page.goto(`${base}/stitch/ho_tro_safebox.html`, { waitUntil: 'domcontentloaded' });
    const managerCard = manager.page.locator('article').filter({ hasText: title });
    await managerCard.waitFor();
    manager.page.once('dialog', dialog => dialog.accept(staffId));
    await managerCard.locator('[data-action="assign"]').click();
    await managerCard.getByText('Phụ trách:', { exact: false }).waitFor();
    await screenshot(manager.page, 'support-manager-assigned.png');
    await manager.context.close();

    const staff = await login(browser, 'staff.q7@safebox.vn');
    await staff.page.goto(`${base}/stitch/ho_tro_safebox.html`, { waitUntil: 'domcontentloaded' });
    const staffCard = staff.page.locator('article').filter({ hasText: title });
    await staffCard.waitFor();
    staff.page.once('dialog', dialog => dialog.accept('Investigating access'));
    await staffCard.locator('[data-next="IN_PROGRESS"]').click();
    await staffCard.locator('[data-next="RESOLVED"]').waitFor();
    staff.page.once('dialog', dialog => dialog.accept('Access restored by browser test'));
    await staffCard.locator('[data-next="RESOLVED"]').click();
    await staffCard.getByText('Đã giải quyết').waitFor();
    await screenshot(staff.page, 'support-staff-resolved.png');
    await staff.context.close();

    await customer.page.reload({ waitUntil: 'domcontentloaded' });
    await customer.page.locator('article').filter({ hasText: title }).getByText('Đã giải quyết').waitFor();
    await screenshot(customer.page, 'support-customer-resolved.png');
    const closer = await login(browser, 'manager.q7@safebox.vn');
    await closer.page.goto(`${base}/stitch/ho_tro_safebox.html`, { waitUntil: 'domcontentloaded' });
    const closingCard = closer.page.locator('article').filter({ hasText: title });
    await closingCard.waitFor();
    closer.page.once('dialog', dialog => dialog.accept('Customer informed of resolution'));
    await closingCard.locator('[data-next="CLOSED"]').click();
    await closingCard.getByText('Đã đóng').waitFor();
    await screenshot(closer.page, 'support-manager-closed.png');
    await closer.context.close();
    await customer.page.goto(`${base}/stitch/ho_tro_khach_hang_safebox_storage.html`, { waitUntil: 'domcontentloaded' });
    await customer.page.locator('#tickets-list-container').getByText(title).waitFor();
    await screenshot(customer.page, 'support-customer-uc21-closed.png');
    results.push({ flow: 'Support Request', check: 'Customer UC22 create, manager assign, staff resolve, manager close, customer UC21 visibility', status: 'PASS', title });
    await customer.context.close();

    const probes = [
      ['Reservation', 'customer3@safebox.vn', '/stitch/t_m_kho_l_u_tr_safebox_storage.html'],
      ['Check-in / Handover', 'customer3@safebox.vn', '/stitch/uc11-customer-payments-appointments.html'],
      ['Unit Management', 'manager.q7@safebox.vn', '/stitch/qu_n_l_lo_i_kho_kho_v_t_l_safebox_manager.html'],
      ['Facility & Staff', 'admin@safebox.vn', '/stitch/quan_ly_pham_vi_nhan_vien.html'],
      ['Renewal / Overdue', 'customer3@safebox.vn', '/stitch/gia_han_hop_dong_safebox_storage.html']
    ];
    for (let i = 0; i < probes.length; i++) {
      const [flow, actor, url] = probes[i];
      const session = await login(browser, actor);
      const response = await session.page.goto(base + url, { waitUntil: 'domcontentloaded' });
      await screenshot(session.page, `flow-probe-${i+1}.png`);
      results.push({ flow, check: 'Browser page probe only; complete journey not executed', status: 'BLOCKED', http: response.status() });
      await session.context.close();
    }
  } finally {
    fs.writeFileSync(path.join(evidence, 'browser-results.json'), JSON.stringify({ results, events }, null, 2));
    await browser.close();
  }
  console.log(JSON.stringify(results, null, 2));
}

main().catch(error => { console.error(error.stack || String(error)); process.exitCode = 1; });
