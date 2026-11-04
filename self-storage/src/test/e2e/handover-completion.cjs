const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const base=process.env.SAFEBOX_E2E_BASE_URL||'http://localhost:18080';
const password=process.env.SAFEBOX_E2E_PASSWORD;
const contractId=process.env.SAFEBOX_E2E_CONTRACT_ID;
const mode=process.env.SAFEBOX_E2E_MODE||'pay';
if(!password||!contractId||!['pay','handover','negative_unpaid','negative_early'].includes(mode))throw new Error('SAFEBOX_E2E_PASSWORD, SAFEBOX_E2E_CONTRACT_ID and valid mode required');
const evidence=path.resolve(__dirname,'../../../../docs/testing/evidence');
const result={contractId,mode,events:[]};
async function main(){
  if(mode==='handover'&&Date.now()<Date.parse(process.env.SAFEBOX_E2E_EARLIEST_UTC||'2026-10-27T08:00:00Z'))
    throw new Error('Scheduled appointment time has not arrived; physical handover not attempted');
  const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
  try{
    const context=await browser.newContext({viewport:{width:1440,height:900}});
    const page=await context.newPage();
    page.on('response',r=>{if(r.url().startsWith(base+'/api/'))result.events.push({path:new URL(r.url()).pathname,method:r.request().method(),status:r.status()});});
    await page.goto(base+'/stitch/ng_nh_p_ng_k_safebox_storage.html',{waitUntil:'domcontentloaded'});
    await page.locator('#login-email').fill('staff.q7@safebox.vn');
    await page.locator('#login-password').fill(password);
    await page.locator('#btn-login-submit').click();
    await page.waitForURL(url=>!url.pathname.endsWith('/ng_nh_p_ng_k_safebox_storage.html'));
    await page.goto(base+'/staff-tasks.html',{waitUntil:'domcontentloaded'});
    if(mode==='negative_unpaid'){
      await page.locator('#task-date').fill(process.env.SAFEBOX_E2E_APPOINTMENT_DATE||'03/11/2026');
      await page.locator('#task-date').dispatchEvent('change');
    }
    const task=page.locator(`#task-rows tr:has([data-id="${contractId}"])`);
    await task.waitFor();
    if(mode==='pay'){
      await task.locator(`.open-payment[data-id="${contractId}"]`).click();
      await page.locator('#task-payment-dialog').waitFor({state:'visible'});
      result.paymentType=await page.locator('#task-payment-type').inputValue();
      result.paymentAmount=Number(await page.locator('#task-payment-amount').inputValue());
      assert.equal(result.paymentType,'REMAINING_RENT'); assert.ok(result.paymentAmount>0);
      await page.locator('#task-payment-method').selectOption('CASH');
      page.once('dialog',dialog=>dialog.accept());
      const response=page.waitForResponse(r=>new URL(r.url()).pathname===`/api/contracts/${contractId}/payments`&&r.request().method()==='POST');
      await page.locator('#task-payment-submit').click();
      result.paymentStatus=(await response).status(); assert.equal(result.paymentStatus,201);
      await page.screenshot({path:path.join(evidence,'handover-staff-rent-paid.png'),fullPage:true});
    }else{
      await task.locator(`.open-handover[data-id="${contractId}"]`).click();
      await page.locator('#handover-dialog').waitFor({state:'visible'});
      await page.locator('#handover-access-identifier').fill('E2E-Q7-KEY-20261027');
      await page.locator('#handover-customer-signature').fill('Customer 3 test confirmation');
      await page.locator('#handover-staff-signature').fill('Q7 staff test confirmation');
      const response=page.waitForResponse(r=>new URL(r.url()).pathname===`/api/staff/contracts/${contractId}/handover`&&r.request().method()==='POST');
      await page.locator('#handover-submit').click();
      result.handoverStatus=(await response).status();
      assert.equal(result.handoverStatus,['negative_unpaid','negative_early'].includes(mode)?409:201);
      await page.screenshot({path:path.join(evidence,mode==='negative_unpaid'?'handover-unpaid-rejected.png':mode==='negative_early'?'handover-early-rejected.png':'handover-staff-completed.png'),fullPage:true});
    }
    await context.close();
  }finally{
    fs.writeFileSync(path.join(evidence,`handover-${mode}-results.json`),JSON.stringify(result,null,2));
    await browser.close();
  }
  console.log(JSON.stringify(result));
}
main().catch(e=>{console.error(e.stack||String(e));process.exitCode=1;});
