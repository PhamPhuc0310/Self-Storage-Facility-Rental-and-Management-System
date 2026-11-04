const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const base = 'http://127.0.0.1:18081';
const phase = process.argv[2] || 'probe';
const evidence = path.resolve(__dirname, '../../../../docs/testing/evidence/sprint3-demo-20261101/playwright-local');
const password = process.env.SAFEBOX_E2E_PASSWORD;
assert(password, 'SAFEBOX_E2E_PASSWORD is required');
fs.mkdirSync(evidence, { recursive: true });

async function login(browser, email, label) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  page.setDefaultTimeout(10000);
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('response', response => {
    if (new URL(response.url()).pathname.startsWith('/api/') && response.status() >= 400)
      errors.push(`${response.status()} ${new URL(response.url()).pathname}`);
  });
  const response = await page.goto(base + '/stitch/ng_nh_p_ng_k_safebox_storage.html', { waitUntil: 'domcontentloaded' });
  assert.equal(response.status(), 200, `${label} login page`);
  await page.getByRole('textbox', { name: 'Email' }).fill(email);
  await page.getByRole('textbox', { name: 'Mật khẩu' }).fill(password);
  await page.getByRole('button', { name: /Đăng nhập/ }).click();
  await page.waitForURL(url => !url.pathname.includes('ng_nh_p_ng_k'), { timeout: 15000 });
  return { context, page, errors };
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const result = { server: base, database: 'SelfStorageFacilityDB_Test', roles: {}, generatedAt: new Date().toISOString() };
  try {
    if (phase === 'uc19setup' || phase === 'uc19handoff') {
      const customer = await login(browser, 'customer3@safebox.vn', 'customer');
      const manager = await login(browser, 'manager.q7@safebox.vn', 'manager');
      const contractId = '6f8c8a98-8ef4-40fa-93ee-1720bd51f1dc';
      const proposed = '2026-11-01T21:30';
      const events = [];
      try {
        if (phase === 'uc19setup') {
        await customer.page.goto(base + '/stitch/early-return.html', { waitUntil: 'domcontentloaded' });
        await customer.page.waitForFunction(() => document.querySelector('#requests')?.children.length > 0);
        if (!await customer.page.getByText('Tôi không còn nhu cầu sử dụng kho.').count()) {
          await customer.page.locator('#contract').selectOption(contractId);
          await customer.page.locator('#return-at').fill(proposed);
          await customer.page.locator('#reason').fill('Tôi không còn nhu cầu sử dụng kho.');
          const requestResponse = customer.page.waitForResponse(r => r.url().includes('/early-return') && r.request().method() === 'POST');
          await customer.page.getByRole('button', { name: 'Gửi yêu cầu' }).click();
          events.push(['request', (await requestResponse).status()]);
        }
        await customer.page.screenshot({ path: path.join(evidence, 'uc19-customer-new-request.png'), fullPage: true });
        await customer.page.goto(base + `/stitch/dat_lich_tra_kho_safebox_storage.html?contractId=${contractId}`, { waitUntil: 'domcontentloaded' });
        await customer.page.waitForFunction(() => !document.querySelector('#form-view')?.classList.contains('hidden') || !document.querySelector('#confirmed-view')?.classList.contains('hidden'));
        if (await customer.page.locator('#form-view').isVisible()) {
        await customer.page.locator('#form-view').waitFor({ state: 'visible' });
        assert.equal(await customer.page.locator('#return-date-input').inputValue(), '2026-11-01');
        assert.match(await customer.page.locator('input[name="timeSlot"]:checked').inputValue(), /^21:30/);
        await customer.page.locator('#chk-empty').check();
        await customer.page.locator('#chk-assets').check();
        await customer.page.screenshot({ path: path.join(evidence, 'uc20-customer-return-schedule-before.png'), fullPage: true });
        const scheduleResponse = customer.page.waitForResponse(r => r.url().includes('/return-schedule') && r.request().method() === 'POST');
        await customer.page.locator('#btn-submit-schedule').click();
        events.push(['schedule', (await scheduleResponse).status()]);
        await customer.page.locator('#confirmed-view').waitFor({ state: 'visible' });
        }
        await customer.page.screenshot({ path: path.join(evidence, 'uc20-customer-return-scheduled.png'), fullPage: true });
        }
        await manager.page.goto(base + '/stitch/bien_ban_tra_kho_safebox_manager.html', { waitUntil: 'domcontentloaded' });
        await manager.page.getByText('SB-CT-DD407A38-B98').first().click();
        await manager.page.locator('#return-assignment').waitFor({ state: 'visible' });
        await manager.page.waitForFunction(() => document.querySelector('#return-staff-select option') || document.querySelector('#assignment-message')?.textContent?.trim());
        const staff = await manager.page.locator('#return-staff-select option').allTextContents();
        events.push(['eligible staff', staff]);
        events.push(['assignment message', await manager.page.locator('#assignment-message').textContent()]);
        await manager.page.screenshot({ path: path.join(evidence, 'uc20-manager-return-assignment-before.png'), fullPage: true });
        if (staff.length) {
          const assignResponse = manager.page.waitForResponse(r => r.url().includes('/assignment') && r.request().method() === 'PUT');
          await manager.page.locator('#assign-return-staff').click();
          events.push(['assignment', (await assignResponse).status()]);
          await manager.page.screenshot({ path: path.join(evidence, 'uc20-manager-return-assigned.png'), fullPage: true });
        }
        await manager.page.goto(base + '/stitch/early-return.html', { waitUntil: 'domcontentloaded' });
        await manager.page.locator('article').filter({ hasText: 'Tôi không còn nhu cầu sử dụng kho.' }).getByRole('button', { name: 'Kiểm tra quyết toán' }).click();
        await manager.page.waitForFunction(() => !document.querySelector('#settlement-state')?.textContent?.includes('Đang kiểm tra'));
        events.push(['settlement state', await manager.page.locator('#settlement-state').textContent()]);
        await manager.page.screenshot({ path: path.join(evidence, 'uc19-manager-inspection-gate.png'), fullPage: true });
        console.log(JSON.stringify({ events, errors: [...customer.errors, ...manager.errors] }));
      } finally {
        fs.writeFileSync(path.join(evidence, 'uc19-20-setup.json'), JSON.stringify({ events, customerErrors: customer.errors, managerErrors: manager.errors }, null, 2));
        await customer.context.close(); await manager.context.close();
      }
      return;
    }
    if (['uc20finish', 'uc20preflight', 'uc19refundclose', 'uc20close'].includes(phase)) {
      const vietnamNow = new Date().toLocaleString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' }).replace(' ', 'T');
      if (phase === 'uc20finish') assert(vietnamNow >= '2026-11-01T21:30:00', 'Wait for the actual 21:30 Vietnam return appointment');
      const staff = await login(browser, 'staff.q7@safebox.vn', 'staff');
      const manager = await login(browser, 'manager.q7@safebox.vn', 'manager');
      staff.page.on('dialog', dialog => dialog.accept());
      const contract = 'SB-CT-DD407A38-B98';
      const events = [];
      const save = () => fs.writeFileSync(path.join(evidence, `${phase}-results.json`), JSON.stringify({ generatedAt: new Date().toISOString(), events, staffErrors: staff.errors, managerErrors: manager.errors }, null, 2));
      try {
        if (!['uc19refundclose', 'uc20close'].includes(phase)) {
        await staff.page.goto(base + '/stitch/bien_ban_tra_kho_safebox_manager.html', { waitUntil: 'domcontentloaded' });
        await staff.page.getByText(contract).first().click();
        await staff.page.locator('#inspection-form').waitFor({ state: 'visible' });
        await staff.page.screenshot({ path: path.join(evidence, 'uc20-staff-inspection-before.png'), fullPage: true });
        if (phase === 'uc20preflight') {
          events.push(['staff form visible', true], ['appointment', await staff.page.locator('#work-scheduled-time').textContent()]);
          console.log(JSON.stringify({ events, errors: staff.errors }));
          return;
        }
        for (const id of ['chk-asset-lock','chk-asset-keys','chk-asset-card','chk-unit-cleared','chk-cleanliness','chk-door','chk-customer-present'])
          await staff.page.locator(`#${id}`).check();
        await staff.page.locator('#customer-sig').fill('Sprint 3 Test Customer');
        await staff.page.locator('#staff-sig').fill('Sprint 3 Test Staff');
        await staff.page.locator('#inspection-notes').fill('Simulated inspection in isolated SelfStorageFacilityDB_Test; no physical inspection evidence.');
        const inspectionResponse = staff.page.waitForResponse(r => r.url().includes('/complete') && r.request().method() === 'POST');
        await staff.page.locator('#btn-submit-complete').click();
        const inspectionStatus = (await inspectionResponse).status();
        events.push(['simulated inspection', inspectionStatus]);
        assert.equal(inspectionStatus, 200);
        await staff.page.locator('#completed-result-card').waitFor({ state: 'visible' });
        await staff.page.screenshot({ path: path.join(evidence, 'uc20-staff-inspection-completed.png'), fullPage: true });
        save();
        }

        if (phase !== 'uc20close') {
        await manager.page.goto(base + '/stitch/early-return.html', { waitUntil: 'domcontentloaded' });
        await manager.page.locator('article').filter({ hasText: 'Tôi không còn nhu cầu sử dụng kho.' }).getByRole('button', { name: 'Kiểm tra quyết toán' }).click();
        if (phase !== 'uc19refundclose') {
        await manager.page.locator('#approval-form').waitFor({ state: 'visible' });
        await manager.page.screenshot({ path: path.join(evidence, 'uc19-manager-settlement-ready.png'), fullPage: true });
        await manager.page.locator('#deduction').fill('0');
        const approvalResponse = manager.page.waitForResponse(r => r.url().includes('/settlement/approve') && r.request().method() === 'POST');
        await manager.page.getByRole('button', { name: 'Duyệt quyết toán' }).click();
        const approvalStatus = (await approvalResponse).status();
        events.push(['settlement approval', approvalStatus]);
        assert.equal(approvalStatus, 200);
        }
        await manager.page.locator('#refund-button').waitFor({ state: 'visible' });
        await manager.page.screenshot({ path: path.join(evidence, 'uc19-manager-settlement-approved.png'), fullPage: true });
        const refundResponse = manager.page.waitForResponse(r => r.url().endsWith('/refund') && r.request().method() === 'POST');
        await manager.page.locator('#refund-button').click();
        const refundStatus = (await refundResponse).status();
        events.push(['simulated internal refund', refundStatus]);
        assert.equal(refundStatus, 200);
        await manager.page.screenshot({ path: path.join(evidence, 'uc19-manager-refund-recorded.png'), fullPage: true });
        save();
        }

        await staff.page.goto(base + '/stitch/bien_ban_tra_kho_safebox_manager.html', { waitUntil: 'domcontentloaded' });
        await staff.page.getByText(contract).first().click();
        await staff.page.locator('#close-contract-form-area').waitFor({ state: 'visible' });
        await staff.page.locator('#close-contract-notes').fill('Sprint 3 isolated test database closure after simulated settlement.');
        const closeResponse = staff.page.waitForResponse(r => r.url().includes('/complete-return') && r.request().method() === 'POST');
        await staff.page.locator('#btn-submit-close-contract').click();
        const closeStatus = (await closeResponse).status();
        events.push(['contract closure', closeStatus]);
        assert.equal(closeStatus, 200);
        await staff.page.locator('#contract-closed-banner').waitFor({ state: 'visible' });
        await staff.page.screenshot({ path: path.join(evidence, 'uc20-staff-contract-closed.png'), fullPage: true });
        console.log(JSON.stringify({ events, errors: [...staff.errors, ...manager.errors] }));
      } finally { save(); await staff.context.close(); await manager.context.close(); }
      return;
    }
    if (phase === 'finalstate') {
      const checks = [];
      for (const [role, email] of [['customer','customer3@safebox.vn'],['manager','manager.q7@safebox.vn'],['staff','staff.q7@safebox.vn']]) {
        const session = await login(browser, email, role);
        try {
          if (role === 'staff') {
            await session.page.goto(base + '/stitch/bien_ban_tra_kho_safebox_manager.html', { waitUntil: 'domcontentloaded' });
            await session.page.getByText('SB-CT-DD407A38-B98').first().click();
            await session.page.locator('#contract-closed-banner').waitFor({ state: 'visible' });
            assert.equal(await session.page.locator('#res-contract-status').textContent(), 'COMPLETED');
            assert.equal(await session.page.locator('#close-contract-form-area').isVisible(), false);
          } else {
            await session.page.goto(base + '/stitch/early-return.html', { waitUntil: 'domcontentloaded' });
            const card = session.page.locator('article').filter({ hasText: 'Tôi không còn nhu cầu sử dụng kho.' });
            await card.getByText('Đã ghi nhận hoàn cọc').waitFor();
            assert.equal(await card.locator('button,a').count(), 0);
          }
          assert.deepEqual(session.errors, []);
          await session.page.screenshot({ path: path.join(evidence, `${role}-final-state-20261102.png`), fullPage: true });
          checks.push({ role, status: 'PASS', errors: session.errors });
        } finally { await session.context.close(); }
      }
      fs.writeFileSync(path.join(evidence, 'final-state-20261102.json'), JSON.stringify({ generatedAt: new Date().toISOString(), checks }, null, 2));
      console.log(JSON.stringify(checks));
      return;
    }
    if (phase === 'nav') {
      const manager = await login(browser, 'manager.q7@safebox.vn', 'manager');
      const visited = [];
      try {
        await manager.page.goto(base + '/stitch/quan_ly_qua_han_safebox_manager.html', { waitUntil: 'domcontentloaded' });
        visited.push(manager.page.url());
        await manager.page.getByRole('link', { name: 'Trả trước hạn' }).first().click();
        await manager.page.waitForURL('**/stitch/early-return.html');
        visited.push(manager.page.url());
        await manager.page.getByRole('link', { name: 'Tiếp nhận trả kho' }).first().click();
        await manager.page.waitForURL('**/stitch/bien_ban_tra_kho_safebox_manager.html');
        visited.push(manager.page.url());
        await manager.page.getByRole('link', { name: 'Chính sách' }).first().click();
        await manager.page.waitForURL('**/management-policies.html');
        await manager.page.locator('#facility option').first().waitFor({ state: 'attached' });
        assert.equal(await manager.page.locator('#early-return-policy-form').isVisible(), false);
        visited.push(manager.page.url());
        await manager.page.screenshot({ path: path.join(evidence, 'manager-policy-read-only.png'), fullPage: true });
        assert.deepEqual(manager.errors, []);
        fs.writeFileSync(path.join(evidence, 'manager-navigation.json'), JSON.stringify({ visited, errors: manager.errors }, null, 2));
        console.log(JSON.stringify({ visited, errors: manager.errors }));
      } finally { await manager.context.close(); }
      return;
    }
    if (phase === 'policy' || phase === 'policy3') {
      const session = await login(browser, 'business@safebox.vn', 'business');
      try {
        await session.page.goto(base + '/management-policies.html', { waitUntil: 'domcontentloaded' });
        await session.page.locator('#facility option').first().waitFor({ state: 'attached' });
        const facilityId = phase === 'policy3'
          ? '3f4e5607-b0b4-f111-a965-932c2c6addb7'
          : '414e5607-b0b4-f111-a965-932c2c6addb7';
        await session.page.locator('#facility').selectOption(facilityId);
        await session.page.waitForFunction(() => document.querySelector('#returns')?.textContent?.trim());
        const version = phase === 'policy3' ? 'SPRINT3-TEST-DAO-20261101' : 'SPRINT3-TEST-Q7-20261101';
        if (!await session.page.locator('#returns').getByText(version).count()) {
          await session.page.locator('#erp-version').fill(version);
          await session.page.locator('#erp-from').fill('2026-11-01');
          await session.page.locator('#erp-to').fill('2026-11-30');
          await session.page.locator('#erp-approved').check();
          await session.page.locator('#btn-create-erp').click();
          await session.page.locator('#returns').getByText(version).waitFor();
        }
        result.policy = { version, facilityId, visible: true, message: await session.page.locator('#message').textContent(), errors: session.errors };
        await session.page.screenshot({ path: path.join(evidence, `business-early-return-policy-${phase}.png`), fullPage: true });
        fs.writeFileSync(path.join(evidence, `policy-activation-${phase}.json`), JSON.stringify(result, null, 2));
        console.log(JSON.stringify(result.policy));
      } finally { await session.context.close(); }
      return;
    }
    if (phase === 'uc18') {
      const manager = await login(browser, 'manager.q7@safebox.vn', 'manager');
      console.log('UC18 manager logged in');
      const customer = await login(browser, 'customer3@safebox.vn', 'customer');
      console.log('UC18 customer logged in');
      const events = [];
      const save = () => fs.writeFileSync(path.join(evidence, 'uc18-overdue.json'), JSON.stringify({ ...result, events, managerErrors: manager.errors, customerErrors: customer.errors }, null, 2));
      try {
        const managerUrl = base + '/stitch/quan_ly_qua_han_safebox_manager.html';
        const customerUrl = base + '/stitch/qua_han_cong_no_khach_hang.html';
        const contract = 'SB-CT-DD876E96-834';
        await manager.page.goto(managerUrl, { waitUntil: 'domcontentloaded' });
        console.log('UC18 manager overdue page opened');
        await manager.page.getByText(contract).first().waitFor();
        await manager.page.locator('tr').filter({ hasText: contract }).getByRole('button', { name: /Chi tiết/ }).click();
        console.log('UC18 contract detail opened');
        await manager.page.screenshot({ path: path.join(evidence, 'uc18-manager-before-calculation.png'), fullPage: true });
        const calcResponse = manager.page.waitForResponse(r => r.url().includes('/calculate-fee') && r.request().method() === 'POST', { timeout: 15000 });
        await manager.page.locator('#btn-calc-today').click();
        console.log('UC18 calculate action clicked');
        const calc = await calcResponse;
        console.log('UC18 calculate response', calc.status());
        events.push({ step: 'calculate incremental fee', status: calc.status() });
        assert.equal(calc.status(), 200);
        await manager.page.locator('#modal-penalties-tbody').getByText('24.000').first().waitFor();
        await manager.page.screenshot({ path: path.join(evidence, 'uc18-manager-after-calculation.png'), fullPage: true });
        save();

        manager.page.on('dialog', dialog => dialog.accept());
        for (let round = 0; round < 2; round++) {
          await customer.page.goto(customerUrl, { waitUntil: 'domcontentloaded' });
          await customer.page.getByText(contract).first().waitFor();
          await customer.page.getByRole('button', { name: /Thanh toán nợ/ }).click();
          await customer.page.locator('#payment-modal label').filter({ hasText: 'Chuyển khoản' }).click();
          await customer.page.screenshot({ path: path.join(evidence, `uc18-customer-payment-${round + 1}.png`), fullPage: true });
          const payResponse = customer.page.waitForResponse(r => r.url().includes('/api/customer/overdue/penalties/') && r.request().method() === 'POST');
          await customer.page.locator('#btn-confirm-pay').click();
          const payment = await payResponse;
          events.push({ step: `simulated customer payment ${round + 1}`, status: payment.status() });
          assert.equal(payment.status(), 200);
          save();

          await manager.page.goto(managerUrl, { waitUntil: 'domcontentloaded' });
          await manager.page.locator('tr').filter({ hasText: contract }).getByRole('button', { name: /Chi tiết/ }).click();
          const approveResponse = manager.page.waitForResponse(r => r.url().includes('/approve-payment') && r.request().method() === 'POST');
          await manager.page.getByRole('button', { name: /Duyệt nhận tiền/ }).first().click();
          const approval = await approveResponse;
          events.push({ step: `simulated manager confirmation ${round + 1}`, status: approval.status() });
          assert.equal(approval.status(), 200);
          await manager.page.screenshot({ path: path.join(evidence, `uc18-manager-confirmed-${round + 1}.png`), fullPage: true });
          save();
        }
        await customer.page.goto(customerUrl, { waitUntil: 'domcontentloaded' });
        await customer.page.getByText(contract).first().waitFor();
        await customer.page.screenshot({ path: path.join(evidence, 'uc18-customer-reconciled.png'), fullPage: true });
        console.log(JSON.stringify({ steps: events.map(e => [e.step, e.status]), errors: [...manager.errors, ...customer.errors] }));
      } finally { save(); await manager.context.close(); await customer.context.close(); }
      return;
    }
    for (const [label, email, url] of [
      ['customer', 'customer3@safebox.vn', '/stitch/qua_han_cong_no_khach_hang.html'],
      ['manager', 'manager.q7@safebox.vn', '/stitch/quan_ly_qua_han_safebox_manager.html'],
      ['business', 'business@safebox.vn', '/management-policies.html'],
      ['staff', 'staff.q7@safebox.vn', '/stitch/bien_ban_tra_kho_safebox_manager.html']
    ]) {
      if (phase !== 'probe' && phase !== label) continue;
      const session = await login(browser, email, label);
      try {
        const response = await session.page.goto(base + url, { waitUntil: 'domcontentloaded' });
        assert.equal(response.status(), 200, `${label} page`);
        if (label === 'customer') await session.page.getByText('SB-CT-DD876E96-834').first().waitFor();
        else await session.page.waitForTimeout(800);
        result.roles[label] = {
          url: session.page.url(),
          heading: await session.page.locator('h1,h2').first().textContent(),
          nav: await session.page.locator('header nav a, aside nav a').allTextContents(),
          errors: session.errors
        };
        await session.page.screenshot({ path: path.join(evidence, `${label}-sprint3.png`), fullPage: true });
      } finally { await session.context.close(); }
    }
    fs.writeFileSync(path.join(evidence, 'role-probe.json'), JSON.stringify(result, null, 2));
    console.log(JSON.stringify(result));
  } finally { await browser.close(); }
})().catch(error => { console.error(error.stack); process.exitCode = 1; });
