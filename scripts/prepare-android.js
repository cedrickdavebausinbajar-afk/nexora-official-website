const fs = require('node:fs');
const path = require('node:path');
const source = path.join(__dirname, '..', 'fit', 'index.html');
const targetDir = path.join(__dirname, '..', 'www');
if (!fs.existsSync(source)) throw new Error('fit/index.html was not found');
fs.rmSync(targetDir, { recursive: true, force: true });
fs.mkdirSync(targetDir, { recursive: true });
fs.copyFileSync(source, path.join(targetDir, 'index.html'));
console.log('Prepared NEXORA FIT web app for Android.');
