const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const base = process.env.SAFEBOX_E2E_BASE_URL || 'http://localhost:18080';
const password = process.env.SAFEBOX_E2E_PASSWORD;
const contractId = process.env.SAFEBOX_E2E_RENEWAL_CONTRACT_ID || 'be72e253-7fb9-48fe-961c-a22b5366d75b';
if (!password) throw new Error('SAFEBOX_E2E_PASSWORD is required');
const evidence = path.resolve(__dirname, '../../../../docs/testing/evidence');
const events = [];
const result = {contractId};
async function login(browser,email) {
  const context = await browser.newContext({viewport:{width:1440,height:900}});
  const page = await context.newPage();
  page.on('response',response => {if (response.url().startsWith(base+'/api/')) events.push({actor:email,method:response.request().method(),path:new URL(response.url()).pathname,status:response.status()});});
  await page.goto(base+'/stitch/ng_nh_p_ng_k_safebox_storage.html',{waitUntil:'domcontentloaded'});
  await page.locator('#login-email').fill(email); await page.locator('#login-password').fill(password);
  await page.locator('#btn-login-submit').click();
  await page.waitForURL(url=>!url.pathname.endsWith('/ng_nh_p_ng_k_safebox_storage.html'));
  return {context,page};
}
async function main() {
  const browser = await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
  try {
    const customer = await login(browser,'customer3@safebox.vn');
    await customer.page.goto(base+'/stitch/gia_han_hop_dong_safebox_storage.html',{waitUntil:'domcontentloaded'});
    await customer.page.locator(`#select-active-contract option[value="${contractId}"]`).waitFor({state:'attached'});
    const previewResponse = customer.page.waitForResponse(r=>new URL(r.url()).pathname===`/api/customer/contracts/${contractId}/renewals/preview`);
    await customer.page.locator('#select-active-contract').selectOption(contractId);
    const preview = await previewResponse;
    result.previewStatus = preview.status();
    result.preview = await preview.json();
    await customer.page.locator('#btn-submit-renewal').waitFor({state:'visible'});
    result.previewWarning = await customer.page.locator('#contract-warning-text').textContent();
    result.previewSubmitDisabled = await customer.page.locator('#btn-submit-renewal').isDisabled();
    assert.equal(result.previewSubmitDisabled,false, result.previewWarning);
    await customer.page.screenshot({path:path.join(evidence,'renewal-customer-preview.png'),fullPage:true});
    if (!process.env.SAFEBOX_E2E_RENEWAL_ID) {
      const response = customer.page.waitForResponse(r=>new URL(r.url()).pathname===`/api/customer/contracts/${contractId}/renewals` && r.request().method()==='POST');
      await customer.page.locator('#btn-submit-renewal').click();
      const res = await response; result.createStatus=res.status(); assert.equal(res.status(),201);
      result.renewalId=(await res.json()).renewalId.toLowerCase();
    } else result.renewalId=process.env.SAFEBOX_E2E_RENEWAL_ID.toLowerCase();
    await customer.page.screenshot({path:path.join(evidence,'renewal-customer-requested.png'),fullPage:true});
    await customer.context.close();

    const other = await login(browser,'manager.thuduc@safebox.vn');
    await other.page.goto(base+'/stitch/quan_ly_gia_han_safebox_manager.html',{waitUntil:'domcontentloaded'});
    await other.page.locator('#manager-renewals-tbody').waitFor();
    assert.equal(await other.page.locator(`button[onclick="openReviewModal('${result.renewalId}')"]`).count(),0);
    await other.page.screenshot({path:path.join(evidence,'renewal-other-manager-hidden.png'),fullPage:true});
    await other.context.close();

    const manager = await login(browser,'manager.q7@safebox.vn');
    await manager.page.goto(base+'/stitch/quan_ly_gia_han_safebox_manager.html',{waitUntil:'domcontentloaded'});
    const review = manager.page.locator(`button[onclick="openReviewModal('${result.renewalId}')"]`).first();
    await review.waitFor(); await review.click();
    await manager.page.locator('#review-modal').waitFor({state:'visible'});
    const approve = manager.page.locator(`#review-modal button[onclick="executeApproval('${result.renewalId}')"]`);
    const response = manager.page.waitForResponse(r=>new URL(r.url()).pathname===`/api/manager/renewals/${result.renewalId}/approve`);
    await approve.click();
    const res=await response; result.approveStatus=res.status(); assert.equal(res.status(),200);
    await manager.page.screenshot({path:path.join(evidence,'renewal-manager-approved.png'),fullPage:true});
    await manager.context.close();

    const payCustomer = await login(browser,'customer3@safebox.vn');
    await payCustomer.page.goto(base+'/stitch/gia_han_hop_dong_safebox_storage.html',{waitUntil:'domcontentloaded'});
    await payCustomer.page.locator('button[onclick*="switchMainTab(\'history\')"]').first().click();
    const payButton=payCustomer.page.locator(`button[onclick^="openPaymentModal('${result.renewalId}'"]`).first();
    await payButton.waitFor(); await payButton.click();
    payCustomer.page.once('dialog',dialog=>dialog.dismiss());
    const payResponse=payCustomer.page.waitForResponse(r=>new URL(r.url()).pathname===`/api/customer/renewals/${result.renewalId}/pay`);
    await payCustomer.page.locator('#btn-confirm-pay').click();
    const payment=await payResponse; result.payStatus=payment.status(); assert.equal(payment.status(),200);
    await payCustomer.page.screenshot({path:path.join(evidence,'renewal-customer-completed.png'),fullPage:true});
    await payCustomer.context.close();
  } finally {
    fs.writeFileSync(path.join(evidence,'renewal-results.json'),JSON.stringify({result,events},null,2));
    await browser.close();
  }
  console.log(JSON.stringify(result));
}
main().catch(e=>{console.error(e.stack||String(e));process.exitCode=1;});
