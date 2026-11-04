const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const base = process.env.SAFEBOX_E2E_BASE_URL || 'http://localhost:18080';
const password = process.env.SAFEBOX_E2E_PASSWORD;
if (!password) throw new Error('SAFEBOX_E2E_PASSWORD is required');
const facility = '414e5607-b0b4-f111-a965-932c2c6addb7';
const evidence = path.resolve(__dirname,'../../../../docs/testing/evidence');
const result = {events:[]};
async function login(browser,email) {
  const context=await browser.newContext({viewport:{width:1440,height:900}});
  const page=await context.newPage();
  page.on('response',r=>{if(r.url().startsWith(base+'/api/'))result.events.push({actor:email,path:new URL(r.url()).pathname,method:r.request().method(),status:r.status()});});
  await page.goto(base+'/stitch/ng_nh_p_ng_k_safebox_storage.html',{waitUntil:'domcontentloaded'});
  await page.locator('#login-email').fill(email); await page.locator('#login-password').fill(password); await page.locator('#btn-login-submit').click();
  await page.waitForURL(url=>!url.pathname.endsWith('/ng_nh_p_ng_k_safebox_storage.html'));
  return {context,page};
}
async function report(browser,email,label) {
  const actor=await login(browser,email);
  await actor.page.goto(base+'/reports.html',{waitUntil:'domcontentloaded'});
  await actor.page.locator(`#facility option[value="${facility}"]`).waitFor({state:'attached'});
  await actor.page.locator('#facility').selectOption(facility);
  await actor.page.locator('#start-date').fill('01/10/2026');
  await actor.page.locator('#end-date').fill('27/10/2026');
  const response=actor.page.waitForResponse(r=>new URL(r.url()).pathname==='/api/reports/financial-summary');
  await actor.page.locator('#report-form button').click();
  const res=await response; assert.equal(res.status(),200);
  const body=await res.json();
  assert.equal(body.rentalCollections,Number(process.env.SAFEBOX_E2E_EXPECTED_RENT||31968000));
  assert.equal(body.depositCollections,Number(process.env.SAFEBOX_E2E_EXPECTED_DEPOSIT||10296000));
  result[label]={rentalCollections:body.rentalCollections,depositCollections:body.depositCollections,netCashMovement:body.netCashMovement};
  await actor.page.locator('#results').waitFor({state:'visible'});
  await actor.page.screenshot({path:path.join(evidence,`report-${label}-after-renewal.png`),fullPage:true});
  return actor;
}
async function main(){
  const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
  try{
    const bom=await report(browser,'business@safebox.vn','bom');
    await bom.page.locator('#start-date').fill('01/11/2026');
    const invalid=bom.page.waitForResponse(r=>new URL(r.url()).pathname==='/api/reports/financial-summary');
    await bom.page.locator('#report-form button').click();
    result.reversedDateStatus=(await invalid).status(); assert.equal(result.reversedDateStatus,400);
    await bom.page.screenshot({path:path.join(evidence,'report-invalid-dates.png'),fullPage:true});
    await bom.context.close();
    const manager=await report(browser,'manager.q7@safebox.vn','manager');
    assert.equal(result.manager.netCashMovement,result.bom.netCashMovement);
    await manager.context.close();
  }finally{fs.writeFileSync(path.join(evidence,'reporting-results.json'),JSON.stringify(result,null,2));await browser.close();}
  console.log(JSON.stringify({bom:result.bom,manager:result.manager,reversedDateStatus:result.reversedDateStatus}));
}
main().catch(e=>{console.error(e.stack||String(e));process.exitCode=1;});
