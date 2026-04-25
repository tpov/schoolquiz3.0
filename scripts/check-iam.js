const sa = require('/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json');
const {google} = require('googleapis');

(async () => {
  const auth = new google.auth.GoogleAuth({ credentials: sa, scopes: ['https://www.googleapis.com/auth/cloud-platform'] });
  const client = await auth.getClient();
  const url = `https://cloudresourcemanager.googleapis.com/v1/projects/${sa.project_id}:getIamPolicy`;
  try {
    const res = await client.request({ url, method: 'POST', data: {} });
    const me = `serviceAccount:${sa.client_email}`;
    const myRoles = res.data.bindings.filter(b => b.members.includes(me));
    console.log('Roles for', me);
    myRoles.forEach(b => console.log('  ', b.role, b.condition ? `(condition: ${b.condition.title})` : ''));
  } catch (e) {
    console.log('ERROR:', e.response?.data?.error?.message || e.message);
  }
})();
