#!/usr/bin/env python3
"""Intégrate · crea las cuentas de prueba (testers) de TODOS los roles en la demo desplegada.

Uso (desde la raíz del repo, con el backend despierto):
    python infra/scripts/crear-testers.py            # crea las cuentas y escribe infra/env/.env.testers.txt

Qué crea (idempotente en la asociación; las cuentas que ya existan se omiten y no se les cambia la clave):
    - 5 turistas          tester1..5@integrate-demo.test
    - 1 anfitrión         tester.anfitrion@integrate-demo.test   (HOST)
    - 1 gestor de enlace  tester.gestor@integrate-demo.test      (LINK_MANAGER)
    - 1 administrador     tester.admin@integrate-demo.test       (ADMIN, por SQL: la app no tiene pantalla para ello)
    - la asociación «Asociación de Prueba Integrate» a la que pertenecen el anfitrión y el gestor

SEC-DIR-004: las contraseñas se generan al azar, se escriben SOLO en infra/env/.env.testers.txt (ignorado por Git)
y nunca se imprimen. Los correos .test son ficticios: no reciben mensajes ni sirven para recuperar la clave.
"""
import datetime, http.cookiejar, json, os, re, secrets, string, subprocess, sys, time, urllib.error, urllib.request

ROOT = os.getcwd()
BASE = os.environ.get('INTEGRATE_BASE', 'https://integrate-demo.pages.dev').rstrip('/')
UA = 'Mozilla/5.0 (integrate-testers)'
OUT = os.path.join(ROOT, 'infra/env/.env.testers.txt')

env = {}
for l in open(os.path.join(ROOT, 'infra/env/.env.demo'), encoding='utf-8-sig').read().splitlines():
    m = re.match(r'^#?([A-Z0-9_]+)=(.*)$', l)
    if m: env[m.group(1)] = m.group(2).strip().strip('"')

opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))


def call(method, path, body=None, token=None):
    time.sleep(0.9)  # SnapDeploy: máx. 100 peticiones/min por IP
    h = {'User-Agent': UA, 'Accept': 'application/json'}
    if body is not None: h['Content-Type'] = 'application/json'
    if token: h['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body is not None else None, headers=h, method=method)
    try: r = opener.open(req, timeout=120); st, tx = r.status, r.read()
    except urllib.error.HTTPError as e: st, tx = e.code, e.read()
    try: return st, json.loads(tx.decode('utf-8', 'replace'))
    except Exception: return st, None


def psql(sql):
    m = re.match(r'jdbc:postgresql://([^/:?]+)(?::(\d+))?/([^?]+)', env['DATABASE_URL'])
    r = subprocess.run(['docker', 'run', '--rm', '-e', 'PGPASSWORD=' + env['FLYWAY_PASSWORD'], '-e', 'PGSSLMODE=require', 'postgres:16', 'psql', '-h', m.group(1),
                        '-p', m.group(2) or '5432', '-U', env['FLYWAY_USER'], '-d', m.group(3), '-At', '-c', sql], capture_output=True, text=True)
    return r.returncode, (r.stdout + r.stderr).strip()


def password():
    a = string.ascii_letters + string.digits
    return ''.join(secrets.choice(a) for _ in range(12)) + 'aA1!'


DESC = {
    'TOURIST': 'TURISTA (TOURIST): explorar el catálogo, reservar una franja, pagar en modo MOCK (sin dinero real), ver "Mis experiencias", el historial y el voucher en PDF, y cancelar. No puede crear ni aprobar experiencias.',
    'HOST': 'ANFITRIÓN (HOST): crear experiencias, enviarlas a revisión, gestionar franjas y cupos y ver sus reservas. Una experiencia nueva queda pendiente hasta que la apruebe el ADMIN.',
    'LINK_MANAGER': 'GESTOR DE ENLACE (LINK_MANAGER): gestionar la asociación y a sus anfitriones, y ver la dispersión del 85 % de los pagos hacia la comunidad.',
    'ADMIN': 'ADMINISTRADOR (ADMIN): aprobar o rechazar experiencias, crear asociaciones, asignar roles y ver auditoría y reportes. Acceso total: úselo solo para pruebas.',
}

PEOPLE = [(f'tester{i}', f'Tester Prueba {i}', f'9000000{i:02d}', f'+57300000000{i}', 'TOURIST') for i in range(1, 6)] + [
    ('tester.anfitrion', 'Tester Anfitrion', '900000101', '+573000000101', 'HOST'),
    ('tester.gestor', 'Tester Gestor de Enlace', '900000102', '+573000000102', 'LINK_MANAGER'),
    ('tester.admin', 'Tester Administrador', '900000103', '+573000000103', 'ADMIN'),
]

st, js = call('POST', '/api/v1/auth/login', {'email': env['BOOTSTRAP_ADMIN_EMAIL'], 'password': env['BOOTSTRAP_ADMIN_PASSWORD']})
if st != 200: sys.exit(f'No pude iniciar sesión como ADMIN inicial (HTTP {st}). ¿Está el backend despierto y la base inicializada?')
tok = js['accessToken']
st, pol = call('GET', '/api/v1/privacy-policies/current')
if st != 200 or not pol: sys.exit(f'No hay política de privacidad vigente (HTTP {st}): cargue infra/postgres/seed/demo_privacy_policy.sql')
POLICY = pol['version']

assoc_body = {'nit': '900999001-3', 'rntNumber': '990001', 'legalName': 'Asociación de Prueba Integrate', 'associationType': 'COOPERATIVA_CAMPESINA', 'municipalityCode': '63001',
              'address': 'Calle 1 # 1-1 (prueba)', 'contactEmail': 'asociacion@integrate-demo.test', 'contactPhone': '+573000000100'}
st, a = call('POST', '/api/v1/admin/associations', assoc_body, tok)
if st == 409:
    st, lst = call('GET', '/api/v1/admin/associations?size=50', None, tok)
    a = next((x for x in (lst or {}).get('items', []) if x.get('nit') == assoc_body['nit']), None)
elif st != 201: sys.exit(f'No pude crear la asociación de prueba (HTTP {st}).')
aid = (a or {}).get('id') or (a or {}).get('associationId')
print(f'asociación de prueba: {aid}')

made = []
for user, name, doc, phone, role in PEOPLE:
    email = f'{user}@integrate-demo.test'; pw = password()
    st, js = call('POST', '/api/v1/auth/register', {'email': email, 'password': pw, 'fullName': name, 'docType': 'CC', 'docNumber': doc, 'phone': phone,
                                                    'consent': {'policyVersion': POLICY, 'purposes': ['PERSONAL_DATA']}})
    if st == 409: print(f'= {email}: ya existía (se omite)'); continue
    if st != 201: print(f'x {email}: registro falló (HTTP {st})'); continue
    uid = js['userId']; ok = True
    if role == 'HOST': s2, _ = call('POST', '/api/v1/admin/host-profiles', {'userId': uid, 'associationId': aid}, tok); ok = s2 in (200, 201)
    elif role == 'LINK_MANAGER': s2, _ = call('POST', '/api/v1/admin/link-managers', {'userId': uid, 'associationId': aid, 'accreditedOn': datetime.date.today().isoformat()}, tok); ok = s2 in (200, 201)
    elif role == 'ADMIN':
        rc, o = psql(f"insert into auth.user_roles(user_id, role_id) select '{uid}', r.id from auth.roles r where r.code in ('ROLE_ADMIN','ADMIN') "
                     f"and not exists (select 1 from auth.user_roles ur where ur.user_id='{uid}' and ur.role_id=r.id)")
        ok = rc == 0 and not o.startswith('ERROR')
    print(f"{'✔' if ok else 'x'} {email} [{role}]" + ('' if ok else ' (el rol falló)'))
    if ok: made.append((role, email, pw))

if made:
    txt = ['Cuentas de prueba de Intégrate · ' + BASE, '',
           'Los correos .test son ficticios: no reciben mensajes ni sirven para recuperar la contraseña.',
           'Los roles nuevos (anfitrión, gestor, admin) se ven tras cerrar sesión y volver a entrar.',
           'Asociación de prueba: «Asociación de Prueba Integrate» (Armenia). Pago: modo MOCK, sin dinero real.', '']
    for role in ('TOURIST', 'HOST', 'LINK_MANAGER', 'ADMIN'):
        rows = [(e, p) for r, e, p in made if r == role]
        if not rows: continue
        txt += ['=' * 70, f'[{role}] {DESC[role]}', '']
        txt += [f'  {e}   {p}' for e, p in rows] + ['']
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, 'w', encoding='utf-8').write('\n'.join(txt))
    print(f'\n{len(made)} cuentas creadas. Credenciales en {os.path.relpath(OUT, ROOT)} (ignorado por Git).')
else:
    print('\nNo se creó ninguna cuenta nueva; el archivo de credenciales no se modificó.')
