const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const base = process.env.SAFEBOX_E2E_BASE_URL;
const password = process.env.SAFEBOX_E2E_PASSWORD;
const contractId = process.env.SAFEBOX_E2E_CONTRACT_ID;
if (base !== 'http://127.0.0.1:18081' || !password || !contractId) {
  throw new Error('Explicit isolated test URL, password and contract ID required');
}
const evidence = path.resolve(__dirname, '../../../../docs/testing/evidence/phase34-closure');
const result = { contractId, actor: 'customer3@safebox.vn', events: [] };

async function main() {
  const browser = await chromium.launch({
    executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    headless: true,
  });
  try {
    const page = await browser.newPage({ viewport: { width: 1366, height: 900 } });
    page.setDefaultTimeout(12000);
    page.on('response', response => {
      if (response.url().startsWith(base + '/api/')) result.events.push({
        method: response.request().method(),
        path: new URL(response.url()).pathname,
        status: response.status(),
      });
    });
    await page.goto(base + '/stitch/ng_nh_p_ng_k_safebox_storage.html', { waitUntil: 'domcontentloaded' });
    await page.locator('#login-email').fill(result.actor);
    await page.locator('#login-password').fill(password);
    await page.locator('#btn-login-submit').click();
    await page.waitForURL(url => !url.pathname.endsWith('/ng_nh_p_ng_k_safebox_storage.html'));

    const rentalRequest = page.waitForResponse(response => {
      const url = new URL(response.url());
      return url.pathname === '/api/customer/rentals' && url.searchParams.get('size') === '10'
        && url.searchParams.get('page') === '0' && response.status() === 200;
    });
    await page.goto(base + '/stitch/kho_dang_thue_safebox_storage.html', { waitUntil: 'domcontentloaded' });
    const rentalResponse = await rentalRequest;
    const rentals = await rentalResponse.json();
    result.rentalApiStatus = rentalResponse.status();
    result.rental = rentals.content?.find(item => item.contractId?.toLowerCase() === contractId.toLowerCase());
    assert.ok(result.rental, 'Contract absent from customer rentals API');
    assert.equal(result.rental.status, 'ACTIVE');
    assert.equal(result.rental.unitNumber, 'DT-S03');
    const card = page.locator('.rental-card').filter({ hasText: 'DT-S03' });
    await card.waitFor();
    result.cardText = await card.innerText();
    assert.match(result.cardText, /Đang thuê|Đang hoạt động|ACTIVE/i);
    await page.screenshot({ path: path.join(evidence, '05-customer-active-contract.png'), fullPage: true });
    fs.writeFileSync(path.join(evidence, 'handover-customer-final.json'), JSON.stringify(result, null, 2));
    await page.close();
  } finally {
    await browser.close();
  }
  console.log(JSON.stringify({ contractId, rentalApiStatus: result.rentalApiStatus, status: result.rental.status, unitNumber: result.rental.unitNumber }));
}
main().catch(error => { console.error(error.stack || String(error)); process.exitCode = 1; });
