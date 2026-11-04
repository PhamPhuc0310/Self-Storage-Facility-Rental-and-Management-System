const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const base=process.env.SAFEBOX_E2E_BASE_URL||'http://localhost:18080';
const password=process.env.SAFEBOX_E2E_PASSWORD;
if(!password)throw new Error('SAFEBOX_E2E_PASSWORD required');
const contractId='be72e253-7fb9-48fe-961c-a22b5366d75b';
const evidence=path.resolve(__dirname,'../../../../docs/testing/evidence');
async function main(){
  const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
  try{
    const page=await browser.newPage({viewport:{width:1440,height:900}});
    await page.goto(base+'/stitch/ng_nh_p_ng_k_safebox_storage.html',{waitUntil:'domcontentloaded'});
    await page.locator('#login-email').fill('customer3@safebox.vn');await page.locator('#login-password').fill(password);await page.locator('#btn-login-submit').click();
    await page.waitForURL(url=>!url.pathname.endsWith('/ng_nh_p_ng_k_safebox_storage.html'));
    await page.goto(base+'/stitch/gia_han_hop_dong_safebox_storage.html',{waitUntil:'domcontentloaded'});
    await page.locator(`#select-active-contract option[value="${contractId}"]`).waitFor({state:'attached'});
    const response=page.waitForResponse(r=>new URL(r.url()).pathname===`/api/customer/contracts/${contractId}/renewals/preview`);
    await page.locator('#select-active-contract').selectOption(contractId);
    const res=await response;assert.equal(res.status(),200);
    const preview=await res.json();
    assert.equal(preview.canRenew,false);
    assert.match(preview.message,/lịch hẹn trả kho/i);
    assert.equal(await page.locator('#btn-submit-renewal').isDisabled(),true);
    await page.screenshot({path:path.join(evidence,'renewal-open-return-rejected.png'),fullPage:true});
    fs.writeFileSync(path.join(evidence,'renewal-negative-results.json'),JSON.stringify({contractId,status:res.status(),preview},null,2));
    console.log(JSON.stringify({contractId,canRenew:preview.canRenew,reason:preview.message}));
  }finally{await browser.close();}
}
main().catch(e=>{console.error(e.stack||String(e));process.exitCode=1;});
