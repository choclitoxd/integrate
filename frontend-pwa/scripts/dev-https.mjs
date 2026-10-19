// `npm run dev:https`: arranca Vite con HTTPS autofirmado y publicado en la red local (ver vite.config.ts).
// Un script en lugar de `VAR=1 vite` para que funcione igual en PowerShell, cmd y bash.
import { spawn } from 'node:child_process';
import { networkInterfaces } from 'node:os';

const addresses = Object.values(networkInterfaces())
  .flat()
  .filter((entry) => entry?.family === 'IPv4' && !entry.internal)
  .map((entry) => entry.address);

console.log('\nPWA por HTTPS de desarrollo (certificado autofirmado).');
for (const address of addresses) console.log(`  En el celular abra: https://${address}:5173`);
console.log(
  '  La primera vez el navegador avisa que el certificado no es de confianza: acéptelo para continuar.\n',
);

const vite = spawn('npx', ['vite'], {
  stdio: 'inherit',
  shell: true,
  env: { ...process.env, PWA_DEV_HTTPS: '1' },
});
vite.on('exit', (code) => {
  process.exit(code ?? 0);
});
