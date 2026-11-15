const fs = require('node:fs');
const path = require('node:path');

const projectRoot = path.join(__dirname, '..');
const sourceDir = path.join(projectRoot, 'android-studio-project', 'app', 'src', 'main', 'assets', 'fit');
const targetDir = path.join(projectRoot, 'www');
const requiredFiles = ['index.html', 'logo.svg'];

for (const file of requiredFiles) {
  const source = path.join(sourceDir, file);
  if (!fs.existsSync(source) || !fs.statSync(source).isFile()) {
    throw new Error(`Required Android web asset is missing: ${path.relative(projectRoot, source)}`);
  }
}

fs.rmSync(targetDir, { recursive: true, force: true });
fs.mkdirSync(targetDir, { recursive: true });

for (const file of requiredFiles) {
  fs.copyFileSync(path.join(sourceDir, file), path.join(targetDir, file));
}

const versionFile = path.join(projectRoot, 'android-studio-project', 'version.json');
if (fs.existsSync(versionFile)) {
  fs.copyFileSync(versionFile, path.join(targetDir, 'version.json'));
}

console.log('Prepared NEXORA FIT web assets for Android/Capacitor.');
