const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const base=process.env.SAFEBOX_E2E_BASE_URL||'http://localhost:18080';
const password=process.env.SAFEBOX_E2E_PASSWORD;
if(!password)throw new Error('SAFEBOX_E2E_PASSWORD is required');
const contractId='c67d1f26-d706-4573-ac18-c26e1d821f6c';
const facilityId='3f4e5607-b0b4-f111-a965-932c2c6addb7';
const evidence=path.resolve(__dirname,'../../../../docs/testing/evidence');
const result={contractId,facilityId,amount:1000000,events:[]};
async function login(browser,email){
  const context=await browser.newContext({viewport:{width:1440,height:900}});
  const page=await context.newPage();
  page.on('response',r=>{if(r.url().startsWith(base+'/api/'))result.events.push({actor:email,path:new URL(r.url()).pathname,method:r.request().method(),status:r.status()});});
  await page.goto(base+'/stitch/ng_nh_p_ng_k_safebox_storage.html',{waitUntil:'domcontentloaded'});
  await page.locator('#login-email').fill(email);await page.locator('#login-password').fill(password);await page.locator('#btn-login-submit').click();
  await page.waitForURL(url=>!url.pathname.endsWith('/ng_nh_p_ng_k_safebox_storage.html'));
  return {context,page};
}
async function main(){
  const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
  try{
    if(process.env.SAFEBOX_E2E_REFUND_ID){
      result.refundId=process.env.SAFEBOX_E2E_REFUND_ID;
      result.createStatus=201;
    }else{
      const customer=await login(browser,'customer3@safebox.vn');
      await customer.page.goto(base+'/stitch/uc11-customer-payments-appointments.html?contractId='+contractId,{waitUntil:'domcontentloaded'});
      await customer.page.locator('#contract-detail').waitFor({state:'visible'});
      await customer.page.locator('#refund-form').waitFor({state:'visible'});
      await customer.page.locator('#refund-amount').fill(String(result.amount));
      await customer.page.locator('#refund-reason').fill('Test-only partial deposit return before pickup');
      const create=customer.page.waitForResponse(r=>new URL(r.url()).pathname===`/api/contracts/${contractId}/deposit-refunds`&&r.request().method()==='POST');
      await customer.page.locator('#refund-submit').click();
      const created=await create;result.createStatus=created.status();assert.equal(result.createStatus,201);
      result.refundId=(await created.json()).id;
      await customer.page.screenshot({path:path.join(evidence,'refund-customer-requested.png'),fullPage:true});
      await customer.context.close();
    }

    const manager=await login(browser,'manager.q7@safebox.vn');
    await manager.page.goto(base+'/stitch/uc12-manager-operations.html',{waitUntil:'domcontentloaded'});
    const review=manager.page.locator(`.refund-review[data-id="${result.refundId}"]`);
    await review.waitFor();
    manager.page.once('dialog',d=>d.accept(String(result.amount)));
    const approve=manager.page.waitForResponse(r=>new URL(r.url()).pathname===`/api/manager/deposit-refunds/${result.refundId}/review`&&r.request().method()==='PUT');
    await review.click();result.approveStatus=(await approve).status();assert.equal(result.approveStatus,200);
    await manager.page.screenshot({path:path.join(evidence,'refund-manager-approved.png'),fullPage:true});
    const complete=manager.page.locator(`.refund-complete[data-id="${result.refundId}"]`);
    await complete.waitFor();
    result.reference='E2E-REFUND-'+Date.now();
    manager.page.once('dialog',d=>d.accept(result.reference));
    const finish=manager.page.waitForResponse(r=>new URL(r.url()).pathname===`/api/manager/deposit-refunds/${result.refundId}/complete`&&r.request().method()==='PUT');
    await complete.click();result.completeStatus=(await finish).status();assert.equal(result.completeStatus,200);
    await manager.page.screenshot({path:path.join(evidence,'refund-manager-completed.png'),fullPage:true});
    await manager.page.goto(base+'/reports.html',{waitUntil:'domcontentloaded'});
    await manager.page.locator(`#facility option[value="${facilityId}"]`).waitFor({state:'attached'});
    await manager.page.locator('#facility').selectOption(facilityId);
    await manager.page.locator('#start-date').fill('2026-10-01');
    await manager.page.locator('#end-date').fill('2026-10-27');
    const report=manager.page.waitForResponse(r=>new URL(r.url()).pathname==='/api/reports/financial-summary');
    await manager.page.locator('#report-form button').click();
    const res=await report;assert.equal(res.status(),200);
    const body=await res.json();result.refundOutflow=body.refundOutflow;
    assert.ok(result.refundOutflow>=result.amount);
    await manager.page.screenshot({path:path.join(evidence,'refund-manager-report.png'),fullPage:true});
    await manager.context.close();
  }finally{fs.writeFileSync(path.join(evidence,'refund-results.json'),JSON.stringify(result,null,2));await browser.close();}
  console.log(JSON.stringify({refundId:result.refundId,createStatus:result.createStatus,approveStatus:result.approveStatus,completeStatus:result.completeStatus,refundOutflow:result.refundOutflow}));
}
main().catch(e=>{console.error(e.stack||String(e));process.exitCode=1;});
