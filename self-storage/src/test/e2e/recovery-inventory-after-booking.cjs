const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const path = require('node:path');
const base = process.env.SAFEBOX_E2E_BASE_URL || 'http://127.0.0.1:18080';
const password = process.env.SAFEBOX_E2E_PASSWORD;
if (!password) throw new Error('SAFEBOX_E2E_PASSWORD is required');

(async () => {
  const browser = await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless:true});
  try {
    const page = await browser.newPage({viewport:{width:1366,height:900}});
    await page.goto(base + '/stitch/ng_nh_p_ng_k_safebox_storage.html');
    await page.locator('#login-email').fill('customer3@safebox.vn');
    await page.locator('#login-password').fill(password);
    await page.locator('#btn-login-submit').click();
    await page.waitForURL(url => !url.pathname.endsWith('/ng_nh_p_ng_k_safebox_storage.html'));
    await page.goto(base + '/storage.html?facilityId=3f4e5607-b0b4-f111-a965-932c2c6addb7&typeId=1');
    const card = page.locator('.sb-unit').filter({hasText:'24 m³'});
    await card.waitFor();
    assert.match(await card.innerText(), /Đang giữ chỗ|Đang cho thuê/);
    assert.equal(await card.locator('button.reserve').count(), 0);
    await page.screenshot({path:path.resolve(__dirname, '../../../../docs/testing/evidence/recovery-phase2-inventory-held.png'), fullPage:true});
    console.log(JSON.stringify({result:'PASS', volume:'24 m³', bookable:false}));
  } finally {
    await browser.close();
  }
})().catch(error => {console.error(error); process.exitCode = 1;});
