/** Add local claims without changing progress on existing reports. */
const base = new URL(process.env.SEED_BASE_URL || 'http://localhost:8080');
if (!['localhost', '127.0.0.1', '[::1]'].includes(base.hostname)) throw new Error('Report seeding is local-only.');
async function data(response) {
  const result = await response.json();
  if (!response.ok) throw new Error(result.message || `HTTP ${response.status}`);
  return result;
}
async function login(username, password) {
  const response = await fetch(new URL('/api/auth/login', base), {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({identifier:username,password})});
  const user = await data(response);
  const cookie = response.headers.getSetCookie().find(value=>value.startsWith('JSESSIONID='))?.split(';')[0];
  if (!cookie) throw new Error('No login session returned.');
  return {user, async call(path, body, multipart) {
    return data(await fetch(new URL(path,base), {method:body===undefined?'GET':'POST',headers:{Cookie:cookie,...(body!==undefined&&!multipart?{'Content-Type':'application/json'}:{})},body:body===undefined?undefined:multipart?body:JSON.stringify(body)}));
  }};
}
const admin = await login(process.env.SEED_ADMIN_USERNAME || 'admin', process.env.SEED_ADMIN_PASSWORD || 'admin123');
const accounts = await admin.call('/api/admin/users');
const accountPassword = 'campus123';
async function account(username, id) {
  let user = accounts.find(user=>user.username===username);
  if (!user) {
    user = await data(await fetch(new URL('/api/auth/register',base), {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,email:username+'@example.edu',studentId:id,password:accountPassword})}));
    await admin.call('/api/admin/users/'+user.id+'/approve',{});
  } else if (user.approvalStatus !== 'APPROVED' || user.email !== username+'@example.edu') {
    throw new Error(`Existing ${username} account is not the approved seeding account; it will not be modified.`);
  }
  return login(username,accountPassword);
}
const finder = await account('finder','STU-2026-101');
const owner = await account('owner','STU-2026-102');
const cases = [
  {
    "title": "Spiral notebook",
    "category": "BOOKS",
    "description": "Spiral notebook found on the west side of campus. Handwritten notes and identifying details will be checked privately before collection.",
    "location": "UIU campus — west side",
    "latitude": 23.79795,
    "longitude": 90.44935,
    "stage": "normal",
    "proof": "My initials DP are written inside the back cover, beside the number 4821."
  },
  {
    "title": "Black smartphone",
    "category": "ELECTRONICS",
    "description": "Black smartphone found near the south side of campus. Collection requires ownership evidence and campus-desk clearance.",
    "location": "UIU campus — south side",
    "latitude": 23.7976,
    "longitude": 90.4497,
    "stage": "valuable",
    "proof": "The initials DP are inside the case, and the serial number ends in 4821. I can bring a redacted receipt to the campus desk."
  },
  {
    "title": "Navy backpack",
    "category": "BAGS",
    "description": "Navy backpack found in the northern outdoor area. Identifying details are kept private while the claim is reviewed.",
    "location": "UIU campus — northern outdoor area",
    "latitude": 23.79825,
    "longitude": 90.44985,
    "stage": "disputed",
    "proof": "The initials DP and the number 4821 are written on the label inside the main compartment."
  }
];
const existing = [];
for (let page=0; ; page++) {
  const result = await finder.call('/api/items?size=60&page='+page);
  existing.push(...result.content);
  if (result.last) break;
}
for (const sample of cases) {
  const old = existing.find(item=>item.title===sample.title && item.reporterName===finder.user.username);
  if (old) { console.log(`Preserved existing report: ${old.reference} ${sample.title}`); continue; }
  const {proof,stage,...report} = sample;
  const payload = {...report,kind:'FOUND',searchRadiusMeters:25,reporterName:finder.user.username,reporterEmail:finder.user.email,securityQuestion:'What initials and identifying number are concealed on this item?',securityAnswer:'DP 4821'};
  const multipart = new FormData(); multipart.append('item',new Blob([JSON.stringify(payload)],{type:'application/json'}));
  const item = await finder.call('/api/items',multipart,true);
  const claim = await owner.call('/api/items/'+item.reference+'/claims',{claimantName:owner.user.username,claimantEmail:owner.user.email,securityAnswer:'DP 4821',proof:sample.proof});
  if (sample.stage==='disputed') {
    await finder.call('/api/claims/'+claim.reference+'/verify',{});
    await finder.call('/api/claims/'+claim.reference+'/accept',{});
    await finder.call('/api/claims/'+claim.reference+'/flag',{reason:'The identifying details in the claim do not match the item. Please review the concealed marking before pickup.'});
  }
  console.log(`Added ${sample.stage}: ${item.reference} — ${new URL('/claim.html?ref='+claim.reference,base)}`);
}
console.log('Local accounts: finder and owner; password: campus123');
console.log(`Collection guide: ${new URL('/help.html',base)}`);
