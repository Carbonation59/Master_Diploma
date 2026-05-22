const fs = require('fs');
const path = require('path');

const serverFile = path.join(__dirname, 'server.js');
let content = fs.readFileSync(serverFile, 'utf8');

// Вставляем установку таймаутов сервера перед server.listen
if (!content.includes('server.timeout')) {
  content = content.replace(
    'server.listen(',
    `server.timeout = 0;
server.keepAliveTimeout = 300000;
server.headersTimeout = 300000;
server.listen(`
  );
}

fs.writeFileSync(serverFile, content, 'utf8');
console.log('server.js patched with increased timeouts');