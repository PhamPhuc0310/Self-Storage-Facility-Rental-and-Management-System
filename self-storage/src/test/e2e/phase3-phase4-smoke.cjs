const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const base = process.env.SAFEBOX_E2E_BASE_URL || 'http://localhost:18081';
const password = process.env.SAFEBOX_E2E_PASSWORD;
if (!password) throw new Error('SAFEBOX_E2E_PASSWORD is required');
const output = path.resolve(__dirname, '../../../../docs/testing/evidence/phase3-phase4');
fs.mkdirSync(output, { recursive: true });
const result = { browser: [], api: [] };
async function login(browser, email) {
  const context = await browser.newContext({ viewport: { width: 1366, height: 900 } });
  const page = await context.newPage();
  await page.goto(base + '/stitch/ng_nh_p_ng_k_safebox_storage.html');
  await page.locator('#login-email').fill(email);
  await page.locator('#login-password').fill(password);
  await page.locator('#btn-login-submit').click();
  await page.waitForURL(url => !url.pathname.endsWith('/ng_nh_p_ng_k_safebox_storage.html'), { timeout: 15000 });
  return { context, page };
}
async function capture(page, name, width) {
  await page.setViewportSize({ width, height: 900 });
  await page.screenshot({ path: path.join(output, `${name}-${width}.png`), fullPage: true });
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 2);
  result.browser.push({ page: name, width, overflow });
}
async function main() {
  const browser = await chromium.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true });
  try {
    const publicPage = await browser.newPage({ viewport: { width: 1366, height: 900 } });
    await publicPage.goto(base + '/stitch/trang_ch_safebox_storage.html');
    await publicPage.locator('#home-status').waitFor({ state: 'visible' });
    await publicPage.waitForFunction(() => !document.getElementById('home-status').textContent.includes('Đang tải'));
    assert((await publicPage.locator('#home-facilities article').count()) > 0);
    for (const width of [390, 768, 1366, 1920]) await capture(publicPage, 'public-home', width);
    await publicPage.close();

    const customer = await login(browser, 'customer1@safebox.vn');
    await customer.page.goto(base + '/storage.html');
    await customer.page.locator('#results article').first().waitFor({ timeout: 15000 });
    const cards = await customer.page.locator('#results article').count();
    const bookable = await customer.page.locator('#results .reserve').count();
    assert(cards > 0); result.api.push({ scenario: 'customer discovery', cards, bookable });
    const unpriced = customer.page.locator('#results article').filter({ hasText: 'PH15-UNIT-01' });
    assert.equal(await unpriced.locator('.reserve').count(), 0);
    result.api.push({ scenario: 'maintenance unit unavailable', status: await unpriced.locator('.sb-badge').innerText() });
    for (const width of [390, 768, 1366, 1920]) await capture(customer.page, 'customer-storage', width);
    await customer.context.close();

    const business = await login(browser, 'business@safebox.vn');
    await business.page.goto(base + '/reports.html');
    await business.page.locator('#results').waitFor({ state: 'visible', timeout: 15000 });
    assert(await business.page.locator('#kpis .kpi').count() >= 8);
    result.api.push({ scenario: 'business reporting', state: await business.page.locator('#report-state').innerText() });
    for (const width of [390, 768, 1366, 1920]) await capture(business.page, 'business-reports', width);
    await business.context.close();

    const manager = await login(browser, 'manager.q7@safebox.vn');
    const denied = await manager.page.evaluate(async () => {
      const response = await fetch('/api/business/facilities/414e5607-b0b4-f111-a965-932c2c6addb7/prices', {
        method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + SafeBoxAuth.getToken() }, body: '{}'
      }); return response.status;
    });
    assert.equal(denied, 403); result.api.push({ scenario: 'manager pricing mutation denied', status: denied });
    await manager.page.goto(base + '/role-home.html');
    await manager.page.locator('#user-role-badge').waitFor({ state: 'visible' });
    await capture(manager.page, 'manager-home', 390);
    await manager.context.close();

    for (const [email, name] of [['staff.q7@safebox.vn', 'staff'], ['admin@safebox.vn', 'admin']]) {
      const actor = await login(browser, email);
      await actor.page.goto(base + '/role-home.html');
      await actor.page.locator('#user-role-badge').waitFor({ state: 'visible' });
      result.api.push({ scenario: `${name} navigation`, role: await actor.page.locator('#user-role-badge').innerText() });
      await capture(actor.page, `${name}-home`, 390);
      await actor.context.close();
    }
  } finally {
    fs.writeFileSync(path.join(output, 'results.json'), JSON.stringify(result, null, 2));
    await browser.close();
  }
  console.log(JSON.stringify(result));
}
main().catch(error => { console.error(error.stack || String(error)); process.exitCode = 1; });
