const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const base=process.env.SAFEBOX_E2E_BASE_URL||'http://localhost:18080';
const password=process.env.SAFEBOX_E2E_PASSWORD;
const contractId=process.env.SAFEBOX_E2E_CONTRACT_ID;
const contractCode=process.env.SAFEBOX_E2E_CONTRACT_CODE||'SB-CT-755F3766-2AB';
if(!password||!contractId)throw new Error('SAFEBOX_E2E_PASSWORD and SAFEBOX_E2E_CONTRACT_ID required');
const evidence=path.resolve(__dirname,'../../../../docs/testing/evidence');
const events=[];
async function main(){
  const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
  try{
    const page=await browser.newPage({viewport:{width:1440,height:900}});
    page.on('response',r=>{if(r.url().startsWith(base+'/api/'))events.push({path:new URL(r.url()).pathname,status:r.status()});});
    await page.goto(base+'/stitch/ng_nh_p_ng_k_safebox_storage.html',{waitUntil:'domcontentloaded'});
    await page.locator('#login-email').fill('staff.q7@safebox.vn');await page.locator('#login-password').fill(password);await page.locator('#btn-login-submit').click();
    await page.waitForURL(url=>!url.pathname.endsWith('/ng_nh_p_ng_k_safebox_storage.html'));
    await page.goto(base+'/staff-tasks.html',{waitUntil:'domcontentloaded'});
    await page.locator('#task-loading').waitFor({state:'hidden'});
    const row=page.locator('#task-rows tr').filter({hasText:contractCode});
    await row.waitFor();
    const text=await row.innerText();
    assert.match(text,/Hoàn thành/);
    assert.match(text,/Đã thanh toán đủ khi bàn giao/);
    assert.equal(await row.locator(`.open-handover[data-id="${contractId}"]`).count(),0);
    await page.screenshot({path:path.join(evidence,'handover-staff-final-state.png'),fullPage:true});
    fs.writeFileSync(path.join(evidence,'handover-final-browser-read.json'),JSON.stringify({contractId,rowText:text,events},null,2));
    console.log(JSON.stringify({contractId,completed:true}));
  }finally{await browser.close();}
}
main().catch(e=>{console.error(e.stack||String(e));process.exitCode=1;});
