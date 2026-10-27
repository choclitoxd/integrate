#!/usr/bin/env python3
"""Intégrate · Crea asociaciones y cuentas reales a partir del archivo generado por el formulario.

Uso (desde la raíz del repo):
  python infra/scripts/crear-usuarios.py                    # SIMULACIÓN: valida y muestra qué haría, sin escribir nada
  python infra/scripts/crear-usuarios.py --apply            # crea las asociaciones y las cuentas
  python infra/scripts/crear-usuarios.py --file ruta.json --base https://integrate-demo.pages.dev

Qué hace con cada persona:
  * Modo CREATE: registra la cuenta con una contraseña temporal aleatoria que NO se guarda ni se muestra,
    le asigna el rol (admin/link-managers, admin/host-profiles) y le envía el correo «restablecer contraseña»
    para que ELLA ponga la suya. Nadie conoce su contraseña.
  * Modo EXISTING: la persona ya se registró sola; se busca por correo y solo se le asigna el rol.
  * Rol ADMIN: no existe endpoint; se concede en la base de datos (user_roles) y se avisa en el informe.
SEC-DIR-004: no imprime contraseñas ni tokens. El archivo de entrada y el informe viven en infra/env/ (ignorados por Git).
"""
import argparse, base64, http.cookiejar, json, os, re, secrets, string, subprocess, sys, time, urllib.error, urllib.request

ROOT = os.getcwd()
ap = argparse.ArgumentParser()
ap.add_argument('--file', default='infra/env/.env.usuarios-reales.json')
ap.add_argument('--base', default='https://integrate-demo.pages.dev')
ap.add_argument('--apply', action='store_true')
ap.add_argument('--no-email', action='store_true', help='no enviar el correo de contraseña (solo crear y asignar roles)')
ap.add_argument('--experiencia', action='store_true', help='crea una experiencia de ejemplo (con franjas) a nombre de cada anfitrión creado en esta corrida y la aprueba')
ap.add_argument('--experiencia-file', help='JSON con el cuerpo de la experiencia (campos de ExperienceRequest) para sustituir el ejemplo')
args = ap.parse_args()
UA = 'Mozilla/5.0 (integrate-usuarios)'

env = {}
for l in open(os.path.join(ROOT, 'infra/env/.env.demo'), encoding='utf-8-sig').read().splitlines():
    m = re.match(r'^#?([A-Z0-9_]+)=(.*)$', l)
    if m: env[m.group(1)] = m.group(2).strip().strip('"')
if not os.path.exists(args.file): sys.exit(f'No existe {args.file}. Descargue el archivo desde el formulario y guárdelo ahí.')
data = json.load(open(args.file, encoding='utf-8'))

class Api:
    def __init__(self, base):
        self.base, self.token = base.rstrip('/'), None
        self.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    def call(self, method, path, body=None):
        time.sleep(0.9)  # SnapDeploy: máx. 100 peticiones/min por IP
        h = {'User-Agent': UA, 'Accept': 'application/json'}
        if self.token: h['Authorization'] = 'Bearer ' + self.token
        if body is not None: h['Content-Type'] = 'application/json'
        req = urllib.request.Request(self.base + path, data=json.dumps(body).encode() if body is not None else None, headers=h, method=method)
        try: r = self.op.open(req, timeout=120); st, tx = r.status, r.read()
        except urllib.error.HTTPError as e: st, tx = e.code, e.read()
        try: js = json.loads(tx.decode('utf-8', 'replace'))
        except Exception: js = None
        return st, js

def psql(sql):
    m = re.match(r'jdbc:postgresql://([^/:?]+)(?::(\d+))?/([^?]+)', env['DATABASE_URL'])
    r = subprocess.run(['docker', 'run', '--rm', '-e', 'PGPASSWORD=' + env['FLYWAY_PASSWORD'], '-e', 'PGSSLMODE=require', 'postgres:16', 'psql', '-h', m.group(1), '-p', m.group(2) or '5432',
                        '-U', env['FLYWAY_USER'], '-d', m.group(3), '-At', '-F', '|', '-c', sql], capture_output=True, text=True)
    return r.returncode, (r.stdout + r.stderr).strip()

def sqlq(v): return "'" + str(v).replace("'", "''") + "'"

def temp_password():
    a = string.ascii_letters + string.digits
    return ''.join(secrets.choice(a) for _ in range(28)) + 'aA1!'

api = Api(args.base)
st, js = api.call('POST', '/api/v1/auth/login', {'email': env['BOOTSTRAP_ADMIN_EMAIL'], 'password': env['BOOTSTRAP_ADMIN_PASSWORD']})
if st != 200: sys.exit(f'No pude iniciar sesión como ADMIN (HTTP {st}). ¿Está el backend despierto? Pruebe: bash infra/scripts/snapdeploy.sh status')
api.token = js['accessToken']
st, pol = api.call('GET', '/api/v1/privacy-policies/current'); POLICY = (pol or {}).get('version')
mode_txt = 'APLICANDO CAMBIOS' if args.apply else 'SIMULACIÓN (no se escribe nada)'
print(f"== {mode_txt} · base {args.base} · política vigente {POLICY}\n   {len(data['asociaciones'])} asociaciones, {len(data['personas'])} personas")

report = {'asociaciones': [], 'personas': []}
assoc_id = {}
temp_pw = {}   # contraseñas temporales: SOLO en memoria, nunca se escriben ni se imprimen

def crear_experiencia(p, assoc):
    """Crea y publica una experiencia a nombre del anfitrión, usando su contraseña temporal (en memoria)."""
    from datetime import date, timedelta
    h = Api(args.base)
    st, js = h.call('POST', '/api/v1/auth/login', {'email': p['email'], 'password': temp_pw[p['email']]})
    if st != 200: return f'login del anfitrión falló ({st})'
    h.token = js['accessToken']
    st, cats = api.call('GET', '/api/v1/catalog/categories')
    body = {'title': 'Caminata ecológica y cata de café', 'description': 'Recorrido guiado a pie por senderos cafeteros de la zona, con explicación del proceso del café, '
            'paradas para observar aves y una cata final con productores locales. Experiencia de ejemplo: puede editarla desde su cuenta.',
            'safetyGuidelines': 'Use calzado cerrado y ropa cómoda, lleve agua y protector solar. El guía acompaña todo el recorrido; no se separe del grupo.',
            'physicalIntensity': 'LOW', 'durationMinutes': 180, 'municipalityCode': assoc['municipalityCode'], 'meetingPoint': 'Plaza de Bolívar, Armenia', 'latitude': 4.533889, 'longitude': -75.681111,
            'maxCapacityPerSlot': 8, 'unitPriceCop': 60000, 'categoryCodes': [c['code'] for c in (cats or [])][:1],
            'routeType': 'LOOP', 'distanceKm': 3.5, 'roundTripMinutes': 180,
            'stops': [{'name': 'Mirador del cafetal', 'description': 'Vista del paisaje cafetero'}, {'name': 'Beneficiadero', 'description': 'Proceso del café'}, {'name': 'Cata final', 'description': None}]}
    if args.experiencia_file: body.update(json.load(open(args.experiencia_file, encoding='utf-8')))
    st, e = h.call('POST', '/api/v1/host/experiences', body)
    if st != 201: return f'crear experiencia falló ({st}) {json.dumps((e or {}).get("invalidParams"))[:200]}'
    eid = e['id']; steps = ['experiencia creada']
    st, _ = h.call('POST', f'/api/v1/host/experiences/{eid}/submit'); steps.append('enviada a revisión' if st == 200 else f'envío falló {st}')
    st, _ = api.call('POST', f'/api/v1/admin/experiences/{eid}/approve'); steps.append('aprobada por el ADMIN' if st == 200 else f'aprobación falló {st}')
    sch = [{'localStart': (date.today() + timedelta(days=d)).isoformat() + 'T08:00', 'localEnd': (date.today() + timedelta(days=d)).isoformat() + 'T11:00', 'totalSlots': 8} for d in (3, 4, 5)]
    st, s = h.call('POST', f'/api/v1/host/experiences/{eid}/schedules', {'schedules': sch}); steps.append(f'{len(s) if isinstance(s, list) else 0} franjas' if st == 201 else f'franjas fallaron {st}')
    return ', '.join(steps) + f' · id {eid}'

# ---- asociaciones
st, lst = api.call('GET', '/api/v1/admin/associations?size=50')
existing = {(x.get('nit') or ''): x for x in ((lst or {}).get('items') or [])}
for a in data['asociaciones']:
    ex = existing.get(a['nit'])
    if ex:
        assoc_id[a['ref']] = ex.get('id') or ex.get('associationId'); print(f"  = asociación «{a['legalName']}» ya existe (NIT {a['nit']}): se reutiliza"); report['asociaciones'].append({'ref': a['ref'], 'nombre': a['legalName'], 'resultado': 'ya existía'}); continue
    if not args.apply: print(f"  + crearía la asociación «{a['legalName']}» (NIT {a['nit']}, municipio {a['municipalityCode']})"); assoc_id[a['ref']] = 'SIMULADO'; continue
    body = {k: a[k] for k in ('nit', 'rntNumber', 'legalName', 'associationType', 'municipalityCode', 'contactEmail', 'contactPhone')}
    if a.get('address'): body['address'] = a['address']
    st, js = api.call('POST', '/api/v1/admin/associations', body)
    ok = st == 201; assoc_id[a['ref']] = (js or {}).get('id') or (js or {}).get('associationId')
    print(f"  {'✔' if ok else '✘'} asociación «{a['legalName']}» -> HTTP {st}" + ('' if ok else f" {(js or {}).get('code')} {json.dumps((js or {}).get('invalidParams'))[:160]}"))
    report['asociaciones'].append({'ref': a['ref'], 'nombre': a['legalName'], 'resultado': 'creada' if ok else f'error {st}', 'id': assoc_id[a['ref']]})

# ---- personas
ROLE_TXT = {'LINK_MANAGER': 'gestor de enlace', 'HOST': 'anfitrión', 'ADMIN': 'administrador'}
for p in data['personas']:
    who = f"{p['fullName']} <{p['email']}> [{ROLE_TXT[p['role']]}]"
    res = {'persona': p['fullName'], 'correo': p['email'], 'rol': p['role'], 'pasos': []}
    report['personas'].append(res)
    if not args.apply:
        print(f"  + {'registraría y ' if p['mode'] == 'CREATE' else 'buscaría a la persona y '}asignaría el rol a {who}" + ('' if p['mode'] != 'CREATE' or args.no_email else '; le enviaría el correo para crear su contraseña')); continue
    uid = None
    if p['mode'] == 'CREATE':
        body = {'email': p['email'], 'password': temp_password(), 'fullName': p['fullName'], 'docType': p['docType'], 'docNumber': p['docNumber'], 'phone': p['phone'], 'consent': {'policyVersion': POLICY, 'purposes': ['PERSONAL_DATA']}}
        pw_tmp = body['password']
        st, js = api.call('POST', '/api/v1/auth/register', body); body['password'] = None
        if st == 201: uid = js['userId']; temp_pw[p['email']] = pw_tmp; res['pasos'].append('cuenta creada'); print(f"  ✔ cuenta creada: {who}")
        elif st == 409: res['pasos'].append('la cuenta ya existía'); print(f"  = ya existe una cuenta con ese correo o documento: {who}")
        else: res['pasos'].append(f'registro falló {st}'); print(f"  ✘ registro de {who} -> HTTP {st} {(js or {}).get('code')} {json.dumps((js or {}).get('invalidParams'))[:200]}"); continue
    if not uid:
        rc, out = psql(f"select id from auth.users where lower(email) = lower({sqlq(p['email'])}) limit 1")
        uid = out.splitlines()[0] if rc == 0 and out and not out.startswith('ERROR') else None
        if not uid: res['pasos'].append('no encontrada'); print(f"  ✘ no encuentro una cuenta con el correo de {who}: pídale que se registre en /registro o use el modo CREATE"); continue
    res['userId'] = uid
    aid = assoc_id.get(p.get('associationRef'))
    if p['role'] == 'LINK_MANAGER':
        st, js = api.call('POST', '/api/v1/admin/link-managers', {'userId': uid, 'associationId': aid, 'accreditedOn': p['accreditedOn']})
    elif p['role'] == 'HOST':
        st, js = api.call('POST', '/api/v1/admin/host-profiles', {'userId': uid, 'associationId': aid})
    else:
        rc, out = psql(f"insert into auth.user_roles(user_id, role_id) select {sqlq(uid)}, r.id from auth.roles r where r.code in ('ROLE_ADMIN','ADMIN') and not exists (select 1 from auth.user_roles ur where ur.user_id = {sqlq(uid)} and ur.role_id = r.id)")
        st, js = (201 if rc == 0 and not out.startswith('ERROR') else 500), {'code': out[:160]}
    if st in (200, 201): res['pasos'].append('rol asignado'); print(f"    ✔ rol asignado")
    elif st == 409: res['pasos'].append('ya tenía el rol'); print("    = ya tenía el rol")
    else: res['pasos'].append(f'rol falló {st}'); print(f"    ✘ rol -> HTTP {st} {(js or {}).get('code')} {json.dumps((js or {}).get('detail'))[:160]}"); continue
    if args.experiencia and p['role'] == 'HOST' and p['email'] in temp_pw:
        a = next(x for x in data['asociaciones'] if x['ref'] == p['associationRef']); r = crear_experiencia(p, a)
        res['pasos'].append('experiencia: ' + r); print(f"    {'✔' if 'creada' in r and 'falló' not in r else '✘'} experiencia: {r}")
    temp_pw.pop(p['email'], None)   # la contraseña temporal deja de existir aquí; el correo de abajo la reemplaza por la que elija la persona
    if p['mode'] == 'CREATE' and not args.no_email:
        st, _ = api.call('POST', '/api/v1/auth/forgot-password', {'email': p['email']})
        res['pasos'].append('correo de contraseña ' + ('enviado' if st == 202 else f'falló {st}')); print(f"    ✔ correo para crear su contraseña encolado" if st == 202 else f"    ✘ correo -> HTTP {st}")

if args.apply:
    out = args.file.replace('.json', '') + '.resultado.json'
    json.dump(report, open(out, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print(f"\nInforme (sin contraseñas): {out}")
else:
    print("\nSimulación terminada. Si todo se ve bien: python infra/scripts/crear-usuarios.py --apply")
