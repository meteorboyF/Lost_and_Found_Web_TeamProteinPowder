/** Real browser regression. Use an isolated demo-enabled preview on port 18080. */
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const path = require('node:path');
const baseURL = process.env.DEMO_BASE_URL || 'http://localhost:18080';
if (!['localhost','127.0.0.1','[::1]'].includes(new URL(baseURL).hostname)) throw new Error('Browser demo tests are local-only.');
(async () => {
  const browser = await chromium.launch({headless:true,...(process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE?{executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE}:{})});
  try {
    const errors = [], stamp = Date.now();
    async function context(username,password) {
      const ctx = await browser.newContext({baseURL,viewport:{width:1360,height:1000}});
      const response = await ctx.request.post('/api/auth/login',{data:{identifier:username,password}}); assert.equal(response.status(),200);
      const page = await ctx.newPage(); page.on('pageerror',error=>errors.push(error.stack)); page.on('dialog',dialog=>dialog.accept());
      await page.route('https://tile.openstreetmap.org/**',route=>route.abort());
      return {ctx,page,user:await response.json()};
    }
    const staff = await context('admin','admin123');
    const createdUser = await staff.ctx.request.post('/api/auth/register',{data:{username:'browser-owner-'+stamp,studentId:'BROWSER-'+stamp,email:stamp+'@example.edu',password:'browser-password'}});
    assert.equal(createdUser.status(),201); const ownerUser = await createdUser.json();
    assert.equal((await staff.ctx.request.post('/api/admin/users/'+ownerUser.id+'/approve')).status(),200);
    const owner = await context(ownerUser.username,'browser-password'), poster = await context('student','student123');
    async function item(category,label) {
      const payload = {kind:'FOUND',category,title:'[Browser test] '+label+' '+stamp,description:'Fictional browser regression report, not a real lost item.',location:'UIU campus desk (fictional test)',reporterName:poster.user.username,reporterEmail:poster.user.email,securityQuestion:'What concealed mark identifies it?',securityAnswer:'Browser Secret'};
      const response = await poster.ctx.request.post('/api/items',{multipart:{item:{name:'item.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(payload))}}});
      assert.equal(response.status(),201); return response.json();
    }
    async function waitClaim(page) { await page.locator('[data-evidence-description]').waitFor(); }
    async function state(page,expected) { await page.waitForFunction(status=>document.querySelector('[data-claim]').innerText.includes(status),expected); }
    async function open(page,ref) { await page.goto('/claim.html?ref='+ref); await waitClaim(page); }
    const ordinary = await item('BOOKS','normal handover');
    await owner.page.goto('/item.html?ref='+ordinary.reference); await owner.page.locator('[data-claim]').click();
    await owner.page.fill('#claim-answer','Browser Secret');
    await owner.page.fill('#claim-proof','Private hidden mark: DP inside the cover; partial receipt serial ends 4821.');
    const evidence = await owner.page.evaluate(()=>{
      const canvas=document.createElement('canvas');canvas.width=620;canvas.height=300;
      const drawing=canvas.getContext('2d');drawing.fillStyle='#fff';drawing.fillRect(0,0,620,300);drawing.fillStyle='#be123c';drawing.font='bold 28px sans-serif';drawing.fillText('DEMO RECEIPT — NOT REAL',30,60);
      drawing.fillStyle='#334155';drawing.font='22px sans-serif';drawing.fillText('Illustrative ownership evidence',30,120);drawing.fillText('Partial serial: ****4821',30,170);drawing.fillText('No real student or payment details',30,220);return canvas.toDataURL('image/png').split(',')[1];
    });
    await owner.page.setInputFiles('#claim-evidence',{name:'demo-receipt.png',mimeType:'image/png',buffer:Buffer.from(evidence,'base64')});
    await owner.page.locator('[data-claim-submit]').click();await owner.page.waitForURL('**/claim.html?ref=*');await waitClaim(owner.page);
    const ref = new URL(owner.page.url()).searchParams.get('ref');
    assert.equal((await owner.ctx.request.get('/api/claims/'+ref+'/evidence')).status(),200);
    const guest=await browser.newContext({baseURL});assert.equal((await guest.request.get('/api/claims/'+ref+'/evidence')).status(),401);await guest.close();
    await open(poster.page,ref); assert.ok(await poster.page.locator('[data-accept]').isDisabled());
    await poster.page.locator('[data-verify]').click();await poster.page.locator('[data-verify]').waitFor({state:'detached'});
    assert.ok(await poster.page.locator('[data-accept]').isEnabled());
    await poster.page.locator('[data-accept]').click();await state(poster.page,'Approved — awaiting pickup');
    assert.equal((await (await poster.ctx.request.get('/api/items/'+ordinary.reference)).json()).status,'PENDING');
    assert.equal(await poster.page.locator('[data-reply]').count(),1);
    await poster.page.locator('[data-handover]').click();await poster.page.waitForFunction(()=>document.querySelector('[data-handover]').disabled);
    assert.equal((await (await poster.ctx.request.get('/api/items/'+ordinary.reference)).json()).status,'PENDING');
    await open(owner.page,ref);await owner.page.locator('[data-handover]').click();await state(owner.page,'Handover complete');
    assert.equal((await (await owner.ctx.request.get('/api/items/'+ordinary.reference)).json()).status,'RESOLVED');
    async function apiClaim(item) {
      const response=await owner.ctx.request.post('/api/items/'+item.reference+'/claims',{data:{claimantName:owner.user.username,claimantEmail:owner.user.email,proof:'Private evidence describes the concealed mark DP and partial serial 4821.',securityAnswer:'Browser Secret'}});
      assert.equal(response.status(),201); return (await response.json()).reference;
    }
    const valuable=await item('ELECTRONICS','valuable campus-desk review'), valuableRef=await apiClaim(valuable);
    await open(poster.page,valuableRef);await poster.page.locator('[data-verify]').click();await poster.page.locator('[data-verify]').waitFor({state:'detached'});
    assert.ok(await poster.page.locator('[data-accept]').isDisabled());
    await staff.page.goto('/desk.html?ref='+valuableRef);await staff.page.locator('[data-desk-decision]').waitFor();
    await staff.page.fill('#desk-note','Simulated demo ID check and concealed evidence comparison; no real student involved.');
    await staff.page.locator('button[value=clear]').click();assert.match(await staff.page.locator('[data-desk-error]').innerText(),/in-person/);
    await staff.page.locator('[data-id-checked]').check();
    const cleared=staff.page.waitForResponse(response=>response.url().endsWith('/review')&&response.request().method()==='POST');
    await staff.page.locator('button[value=clear]').click();assert.equal((await cleared).status(),200);
    await staff.page.locator('[data-desk-decision]').waitFor({state:'detached'});
    await open(poster.page,valuableRef);assert.ok(await poster.page.locator('[data-accept]').isEnabled());
    await poster.page.locator('[data-accept]').click();await state(poster.page,'Approved — awaiting pickup');
    const disputed=await item('BAGS','fraud freeze'), disputeRef=await apiClaim(disputed);
    await open(poster.page,disputeRef);await poster.page.locator('[data-verify]').click();await poster.page.locator('[data-verify]').waitFor({state:'detached'});
    await poster.page.locator('[data-accept]').click();await state(poster.page,'Approved — awaiting pickup');
    await open(owner.page,disputeRef);await owner.page.fill('#safety-reason','The concealed details do not match. Staff should review possible fraud before pickup.');
    await owner.page.locator('button[value=flag]').click();await state(owner.page,'Handover frozen');assert.ok(await owner.page.locator('[data-handover]').isDisabled());
    assert.equal((await poster.ctx.request.post('/api/claims/'+disputeRef+'/handover')).status(),409);
    await owner.page.screenshot({path:path.resolve(__dirname,'../../target/fraud-browser-claim.png'),fullPage:true});
    await staff.page.goto('/desk.html?ref='+disputeRef);await staff.page.locator('[data-desk-decision]').waitFor();
    await staff.page.screenshot({path:path.resolve(__dirname,'../../target/fraud-browser-desk.png'),fullPage:true});
    await staff.page.fill('#desk-note','Conflicting concealed evidence in this fictional demonstration; rejecting the claim.');
    const rejected=staff.page.waitForResponse(response=>response.url().endsWith('/review')&&response.request().method()==='POST');
    await staff.page.locator('button[value=reject]').click();assert.equal((await rejected).status(),200);
    const final=(await (await owner.ctx.request.get('/api/claims/'+disputeRef)).json());assert.equal(final.status,'DECLINED');assert.equal(final.safety.handoverFrozen,false);
    assert.equal((await (await owner.ctx.request.get('/api/items/'+disputed.reference)).json()).status,'OPEN');
    const audit=await (await staff.ctx.request.get('/api/desk/audit')).json();assert.ok(audit.some(event=>event.claimReference===disputeRef&&event.action==='FRAUD_REPORTED'));assert.ok(audit.some(event=>event.claimReference===disputeRef&&event.action==='DESK_REJECTED'));
    await owner.page.setViewportSize({width:390,height:844});await open(owner.page,disputeRef);
    await owner.page.screenshot({path:path.resolve(__dirname,'../../target/fraud-browser-mobile.png'),fullPage:true});
    if (!(await owner.page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))) console.log(await owner.page.evaluate(()=>Array.from(document.querySelectorAll('body *')).filter(el=>el.getBoundingClientRect().right>innerWidth+1).map(el=>({tag:el.tagName,cls:el.className,text:el.textContent.slice(0,60)})).slice(0,12)));
    assert.ok(await owner.page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
    assert.equal(errors.length,0,errors.join('\n'));
    console.log(JSON.stringify({passed:true,checks:['private image through claim form','review required before approval','approval is not return','two-party handover','chat during pickup','valuable item staff gate','ID-check gate','fraud freeze','staff rejection','private audit','mobile layout','no browser errors']}));
  } finally { await browser.close(); }
})().catch(error=>{console.error(error);process.exit(1)});
