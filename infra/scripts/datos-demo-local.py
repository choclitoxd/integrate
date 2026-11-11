#!/usr/bin/env python3
"""Intégrate · Limpia la base LOCAL (o la de PRUEBAS) y carga datos de demostración realistas por la API.

Uso (desde la raíz del repo):
    python infra/scripts/datos-demo-local.py            # LOCAL: Docker y backend en http://localhost:8081
    python infra/scripts/datos-demo-local.py --pruebas [--emails infra/env/.env.datos-demo.correos.json]
        # PRUEBAS: backend integrate-pruebas despierto + base Neon de .env.demo.pruebas. Borra TODA esa base.

Protecciones: sin --pruebas solo acepta localhost. Con --pruebas solo acepta la base de .env.demo.pruebas y se
niega si su host es el de producción (ep-calm-firefly) o si la API no es la del contenedor de pruebas.
Los datos se crean con los mismos endpoints que usa la PWA, así cumplen las reglas de negocio.
Excepciones hechas por SQL (no hay endpoint): el rol ADMIN, las cuentas de dispersión y la limpieza inicial.
--emails: JSON {"t_camila": "correo@real", ...} para que algunos turistas reciban de verdad sus vouchers
(en pruebas el correo sale por Brevo; los @integrate-demo.test rebotan).

SEC-DIR-004: la contraseña común de demo se genera la primera vez y se guarda en
infra/env/.env.datos-demo[.pruebas] (ignorado por Git). El resultado detallado queda en
infra/env/.env.datos-demo[.pruebas].resultado.json (ignorado por Git). No se imprimen contraseñas.
"""
import datetime as dt, http.cookiejar, json, os, re, secrets, string, subprocess, sys, time, urllib.error, urllib.request, uuid

ROOT = os.getcwd()
PRUEBAS = '--pruebas' in sys.argv
EMAILS = {}
if '--emails' in sys.argv:
    EMAILS = json.load(open(sys.argv[sys.argv.index('--emails') + 1], encoding='utf-8'))
SUF = '.pruebas' if PRUEBAS else ''
PRUEBAS_API = 'https://integrate-pruebas.containers.snapdeploy.app'
PRUEBAS_DB = {}
if PRUEBAS:
    _env = {}
    for _l in open(os.path.join(ROOT, 'infra/env/.env.demo.pruebas'), encoding='utf-8-sig').read().splitlines():
        _m = re.match(r'^([A-Z0-9_]+)=(.*)$', _l)
        if _m: _env[_m.group(1)] = _m.group(2).strip().strip('"')
    _u = re.match(r'jdbc:postgresql://([^/:?]+)(?::(\d+))?/([^?]+)', _env['DATABASE_URL'])
    PRUEBAS_DB = {'host': _u.group(1), 'port': _u.group(2) or '5432', 'db': _u.group(3), 'user': _env['FLYWAY_USER'], 'pw': _env['FLYWAY_PASSWORD']}
    if 'ep-calm-firefly' in PRUEBAS_DB['host'] or 'ep-bold-mode' not in PRUEBAS_DB['host']:
        sys.exit('La base de .env.demo.pruebas no es la de pruebas esperada (ep-bold-mode). Se detiene: no se toca producción.')
    BASE = os.environ.get('INTEGRATE_BASE', PRUEBAS_API).rstrip('/')
    if BASE != PRUEBAS_API:
        sys.exit(f'Con --pruebas la API debe ser {PRUEBAS_API}.')
else:
    BASE = os.environ.get('INTEGRATE_BASE', 'http://localhost:8081').rstrip('/')
    if not (BASE.startswith('http://localhost') or BASE.startswith('http://127.0.0.1')):
        sys.exit('Sin --pruebas este script solo corre contra el backend local.')
PW_FILE = os.path.join(ROOT, f'infra/env/.env.datos-demo{SUF}')
OUT = os.path.join(ROOT, f'infra/env/.env.datos-demo{SUF}.resultado.json')
TODAY = dt.date.today()
LOG = []
PAUSE = 0.8 if PRUEBAS else 0   # SnapDeploy/Cloudflare limitan las peticiones por minuto


def demo_password():
    if os.path.exists(PW_FILE):
        for l in open(PW_FILE, encoding='utf-8'):
            if l.startswith('DEMO_PASSWORD='):
                return l.split('=', 1)[1].strip()
    a = string.ascii_letters + string.digits
    pw = 'Quindio-' + ''.join(secrets.choice(a) for _ in range(6)) + '#26'
    with open(PW_FILE, 'w', encoding='utf-8') as f:
        f.write('# Contraseña común de las cuentas de demo LOCAL (ignorado por Git)\nDEMO_PASSWORD=' + pw + '\n')
    return pw


PW = demo_password()


def psql(sql):
    if PRUEBAS:
        cmd = ['docker', 'run', '--rm', '-i', '-e', 'PGPASSWORD=' + PRUEBAS_DB['pw'], '-e', 'PGSSLMODE=require', 'postgres:16', 'psql', '-h', PRUEBAS_DB['host'],
               '-p', PRUEBAS_DB['port'], '-U', PRUEBAS_DB['user'], '-d', PRUEBAS_DB['db'], '-v', 'ON_ERROR_STOP=1', '-At']
    else:
        cmd = ['docker', 'exec', '-i', 'integrate-postgres', 'psql', '-U', 'postgres', '-d', 'integrate', '-v', 'ON_ERROR_STOP=1', '-At']
    r = subprocess.run(cmd, input=sql, capture_output=True, text=True, encoding='utf-8')
    if r.returncode != 0:
        sys.exit('SQL falló: ' + r.stderr.strip())
    return r.stdout.strip()


class Api:
    def __init__(self, label=''):
        self.token, self.label = None, label
        self.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def call(self, method, path, body=None, ok=(200, 201, 202, 204), headers=None, fatal=True):
        if PAUSE: time.sleep(PAUSE)
        h = {'Accept': 'application/json', 'User-Agent': 'Mozilla/5.0 (integrate-datos-demo)'}
        if self.token: h['Authorization'] = 'Bearer ' + self.token
        if body is not None: h['Content-Type'] = 'application/json'
        if method == 'POST': h['Idempotency-Key'] = str(uuid.uuid4())
        h.update(headers or {})
        req = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body is not None else None, headers=h, method=method)
        try:
            r = self.op.open(req, timeout=60); st, tx = r.status, r.read()
        except urllib.error.HTTPError as e:
            st, tx = e.code, e.read()
        try: js = json.loads(tx.decode('utf-8', 'replace')) if tx else None
        except Exception: js = tx.decode('utf-8', 'replace')[:300]
        if st not in ok and fatal:
            sys.exit(f'✘ {self.label} {method} {path} -> HTTP {st}\n{json.dumps(js, ensure_ascii=False, indent=1)[:1500]}')
        return st, js

    def login(self, email, password=None):
        st, js = self.call('POST', '/api/v1/auth/login', {'email': email, 'password': password or PW})
        self.token = js['accessToken']
        return self


def step(msg):
    print('  ✔ ' + msg); LOG.append(msg)


# ---------------------------------------------------------------------------
# 0. Limpieza de la base local (conserva catálogos gobernados por migraciones)
# ---------------------------------------------------------------------------
print('== 0. Limpieza de la base ' + ('de PRUEBAS (Neon)' if PRUEBAS else 'local'))
# Neon no da superusuario: no se puede usar session_replication_role. Se vacían las tablas de negocio con TRUNCATE (desactivando
# dentro de la transacción solo trg_forbid_truncate) y luego se borran los usuarios. Si algo falla, no queda nada a medias.
LIMPIEZA_PRUEBAS = """
BEGIN;
DO $$
DECLARE v_tables text; r record;
BEGIN
  SELECT string_agg(format('%I.%I', n.nspname, c.relname), ', ') INTO v_tables
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
   WHERE c.relkind IN ('r','p') AND NOT c.relispartition
     AND n.nspname IN ('audit','auth','booking','catalog','notification','offline','payments','voucher')
     AND (n.nspname||'.'||c.relname) NOT IN ('auth.users','auth.roles','auth.privacy_policies','auth.health_consent_notices',
          'catalog.categories','catalog.departments','catalog.municipalities','payments.commission_policies',
          'audit.chain_head','voucher.signing_keys');
  FOR r IN SELECT tgrelid::regclass AS t, tgname FROM pg_trigger WHERE tgname = 'trg_forbid_truncate' AND NOT tgisinternal
           AND tgrelid::regclass::text = ANY (string_to_array(replace(v_tables, '"', ''), ', ')) LOOP
    EXECUTE format('ALTER TABLE %s DISABLE TRIGGER %I', r.t, r.tgname);
  END LOOP;
  EXECUTE 'TRUNCATE TABLE ' || v_tables || ' RESTART IDENTITY';
  FOR r IN SELECT tgrelid::regclass AS t, tgname FROM pg_trigger WHERE tgname = 'trg_forbid_truncate' AND NOT tgisinternal
           AND tgrelid::regclass::text = ANY (string_to_array(replace(v_tables, '"', ''), ', ')) LOOP
    EXECUTE format('ALTER TABLE %s ENABLE TRIGGER %I', r.t, r.tgname);
  END LOOP;
END $$;
DELETE FROM auth.users;
UPDATE audit.chain_head SET last_id = 0, last_hash = repeat('0', 64), updated_at = now() WHERE id = 1;
COMMIT;
"""
psql(LIMPIEZA_PRUEBAS if PRUEBAS else """
BEGIN;
SET LOCAL session_replication_role = replica;
DO $$
DECLARE v_tables text;
BEGIN
  SELECT string_agg(format('%I.%I', n.nspname, c.relname), ', ') INTO v_tables
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
   WHERE c.relkind IN ('r','p') AND NOT c.relispartition
     AND n.nspname IN ('audit','auth','booking','catalog','notification','offline','payments','voucher')
     AND (n.nspname||'.'||c.relname) NOT IN ('auth.users','auth.roles','auth.privacy_policies','auth.health_consent_notices',
          'catalog.categories','catalog.departments','catalog.municipalities','payments.commission_policies',
          'audit.chain_head','voucher.signing_keys');
  EXECUTE 'TRUNCATE TABLE ' || v_tables || ' RESTART IDENTITY';
END $$;
DELETE FROM auth.users;
UPDATE audit.chain_head SET last_id = 0, last_hash = repeat('0', 64), updated_at = now() WHERE id = 1;
COMMIT;
""")
print('  ✔ datos de negocio borrados (usuarios, asociaciones, experiencias, reservas, pagos, vouchers, auditoría)')
if not PRUEBAS:
    try:
        urllib.request.urlopen(urllib.request.Request('http://localhost:8025/api/v1/messages', method='DELETE'), timeout=10).read()
        print('  ✔ bandeja de Mailpit vaciada')
    except Exception as e:
        print(f'  (aviso: no se pudo vaciar Mailpit: {e})')

pub = Api('público')
_pol = pub.call('GET', '/api/v1/privacy-policies/current')[1]
POLICY = _pol['version']
HEALTH = (_pol.get('healthNotice') or {}).get('noticeVersion')   # con aviso de salud publicado se admite la EPS (ADR-0054)
print(f'  política {POLICY}; aviso de salud: {HEALTH or "no publicado (la EPS no se admite)"}')
R = {'password_file': os.path.relpath(PW_FILE, ROOT), 'fecha_carga': TODAY.isoformat(), 'policy': POLICY,
     'usuarios': [], 'asociaciones': [], 'experiencias': [], 'reservas': [], 'bajas': [], 'pasos': LOG}


def register(email, name, doc, phone, doc_type='CC', role_txt='Turista', note=''):
    st, js = pub.call('POST', '/api/v1/auth/register', {'email': email, 'password': PW, 'fullName': name, 'docType': doc_type, 'docNumber': doc,
                                                       'phone': phone, 'consent': ({'policyVersion': POLICY, 'purposes': ['PERSONAL_DATA', 'HEALTH_EMERGENCY'], 'healthNoticeVersion': HEALTH}
                                                                                   if HEALTH else {'policyVersion': POLICY, 'purposes': ['PERSONAL_DATA']})})
    u = {'email': email, 'nombre': name, 'documento': f'{doc_type} {doc}', 'celular': phone, 'rol': role_txt, 'id': js['userId'], 'nota': note, 'alta': 'Registro propio en la app'}
    R['usuarios'].append(u)
    return u


# ---------------------------------------------------------------------------
# 1. Administradora de plataforma
# ---------------------------------------------------------------------------
print('== 1. Administración')
adm = register('laura.restrepo@integrate-demo.test', 'Laura Restrepo Gaviria', '1094887321', '+573104558812', role_txt='Administradora (ADMIN)')
psql(f"INSERT INTO auth.user_roles(user_id, role_id) SELECT '{adm['id']}', id FROM auth.roles WHERE code = 'ADMIN' ON CONFLICT DO NOTHING;")
adm['alta'] = 'Registro propio + rol ADMIN por SQL (la app no tiene pantalla para crear administradores)'
admin = Api('admin').login(adm['email'])
step('Administradora Laura Restrepo creada')

# ---------------------------------------------------------------------------
# 2. Asociaciones comunitarias
# ---------------------------------------------------------------------------
print('== 2. Asociaciones')
ASSOC = [
    ('pijao', '901457823-4', '72514', 'Asociación de Caficultores Víctimas del Conflicto de Pijao — ASOCAVIP', 'ASOCIACION_VICTIMAS', '63548',
     'Calle 6 # 4-21, Pijao, Quindío', 'contacto@asocavip-demo.test', '+573127745120'),
    ('salento', '900812364-1', '58321', 'Cooperativa Campesina Agroturística del Valle de Cocora — COOAGROCOCORA', 'COOPERATIVA_CAMPESINA', '63690',
     'Carrera 6 # 2-15, Salento, Quindío', 'info@cooagrococora-demo.test', '+573164428907'),
    ('filandia', '901233780-6', '64190', 'Junta de Acción Comunal Vereda La Morelia de Filandia', 'JUNTA_ACCION_COMUNAL', '63272',
     'Vereda La Morelia, km 3 vía Quimbaya, Filandia, Quindío', 'jac.lamorelia@integrate-demo.test', '+573205561438'),
    ('montenegro', '900675512-3', '47788', 'Asociación Turística Rural El Edén de Montenegro', 'OTRA', '63470',
     'Vereda El Edén, Montenegro, Quindío', 'eleden@integrate-demo.test', '+573148890215'),
]
A = {}
for ref, nit, rnt, name, typ, muni, addr, mail, phone in ASSOC:
    st, js = admin.call('POST', '/api/v1/admin/associations', {'nit': nit, 'rntNumber': rnt, 'legalName': name, 'associationType': typ,
                                                               'municipalityCode': muni, 'address': addr, 'contactEmail': mail, 'contactPhone': phone})
    A[ref] = {'id': js.get('id') or js.get('associationId'), 'nombre': name, 'nit': nit, 'rnt': rnt, 'tipo': typ, 'municipio': muni, 'direccion': addr, 'correo': mail}
    R['asociaciones'].append(A[ref])
    step(f'Asociación «{name}»')

# Cuenta de dispersión verificada por asociación: no hay endpoint ni pantalla (igual que DisbursementPayoutIntegrationTest).
# Los campos cifrados llevan bytes de relleno: la pasarela MOCK no los descifra.
PAYOUT = {'pijao': ('NEQUI', '7712'), 'salento': ('SAVINGS', '4408'), 'filandia': ('NEQUI', '3391'), 'montenegro': ('SAVINGS', '9025')}
for ref, (typ, last4) in PAYOUT.items():
    psql(f"""INSERT INTO payments.payout_accounts (association_id, account_type, bank_code, account_number_enc, account_last4, holder_name_enc,
             holder_doc_type, holder_doc_number_enc, sensitive_key_version, is_default, verified_at)
             VALUES ('{A[ref]['id']}', '{typ}', {"'1007'" if typ == 'SAVINGS' else 'NULL'}, decode(md5(random()::text) || md5(random()::text), 'hex'), '{last4}', decode(md5(random()::text) || md5(random()::text), 'hex'),
                     'NIT', decode(md5(random()::text) || md5(random()::text), 'hex'), 1, TRUE, now());""")
    A[ref]['cuenta_pago'] = f"{'Nequi' if typ == 'NEQUI' else 'Ahorros Bancolombia'} terminada en {last4} (por SQL)"
step('Cuentas de dispersión verificadas por asociación (por SQL)')

# ---------------------------------------------------------------------------
# 3. Gestores de enlace (LINK_MANAGER)
# ---------------------------------------------------------------------------
print('== 3. Gestores de enlace')
GEST = [
    ('pijao', 'andres.castano@integrate-demo.test', 'Andrés Felipe Castaño Ríos', '1094912345', '+573116672301', 210),
    ('salento', 'paola.marin@integrate-demo.test', 'Paola Andrea Marín Osorio', '1097401122', '+573185520764', 150),
    ('filandia', 'jhon.arango@integrate-demo.test', 'Jhon Jairo Arango Toro', '9773412', '+573008812245', 95),
    ('montenegro', 'mauricio.cardona@integrate-demo.test', 'Mauricio Cardona Henao', '1094779910', '+573017745530', 60),
]
G = {}
for ref, email, name, doc, phone, days in GEST:
    u = register(email, name, doc, phone, role_txt='Gestor(a) de enlace (LINK_MANAGER)')
    st, js = admin.call('POST', '/api/v1/admin/link-managers', {'userId': u['id'], 'associationId': A[ref]['id'],
                                                               'accreditedOn': (TODAY - dt.timedelta(days=days)).isoformat()})
    u.update({'asociacion': A[ref]['nombre'], 'linkManagerId': js['linkManagerId'], 'alta': 'Registro propio + acreditación del ADMIN'})
    G[ref] = u
    step(f'Gestor {name} acreditado en {ref}')

# ---------------------------------------------------------------------------
# 4. Anfitriones (HOST): registro propio + perfil asignado por el ADMIN
# ---------------------------------------------------------------------------
print('== 4. Anfitriones')
HOSTS = [
    ('salento', 'h_rosa', 'rosalba.giraldo@integrate-demo.test', 'Rosalba Giraldo de Ospina', '24578112', '+573137789014'),
    ('salento', 'h_hernan', 'hernan.quintero@integrate-demo.test', 'Hernán Darío Quintero Loaiza', '18412977', '+573122204561'),
    ('filandia', 'h_luz', 'luzmery.valencia@integrate-demo.test', 'Luz Mery Valencia Duque', '41923550', '+573113348870'),
    ('pijao', 'h_ovidio', 'ovidio.sanchez@integrate-demo.test', 'Ovidio Sánchez Bermúdez', '7547218', '+573156621093'),
    ('montenegro', 'h_fabio', 'fabio.londono@integrate-demo.test', 'Fabio Nelson Londoño Ruiz', '9810455', '+573104412287'),
]
H = {}
for ref, key, email, name, doc, phone in HOSTS:
    u = register(email, name, doc, phone, role_txt='Anfitrión(a) (HOST)')
    st, js = admin.call('POST', '/api/v1/admin/host-profiles', {'userId': u['id'], 'associationId': A[ref]['id']})
    u.update({'asociacion': A[ref]['nombre'], 'hostProfileId': js.get('hostProfileId'), 'alta': 'Registro propio + perfil de anfitrión asignado por el ADMIN'})
    H[key] = u
    step(f'Anfitrión {name} ({ref})')

# ---------------------------------------------------------------------------
# 5. Alta asistida por el gestor de Pijao (víctimas del conflicto, sin correo propio)
# ---------------------------------------------------------------------------
print('== 5. Alta asistida (gestor de enlace)')
gp = Api('gestor-pijao').login(G['pijao']['email'])
SMALL_PDF = b'%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n'


def upload(api, kind):
    st, up = api.call('POST', '/api/v1/gestor/uploads', {'kind': kind})
    try:
        req = urllib.request.Request(up['uploadUrl'], data=SMALL_PDF, method='PUT', headers={'Content-Type': up.get('contentType') or 'application/pdf'})
        urllib.request.urlopen(req, timeout=30).read()
    except Exception as e:
        print(f'    (aviso: no se pudo subir el PDF a S3 local: {e})')
    return up['s3Key']


ASSISTED = [
    ('h_maria', 'María Edilma Rojas Cárdenas', '41890233', '+573128834416', None, '1890233', 'DESPLAZAMIENTO_FORZADO'),
    ('h_jose', 'José Arley Patiño Mejía', '1094445120', '+573215567708', 'jose.patino@integrate-demo.test', '2014578', 'DESPLAZAMIENTO_FORZADO'),
]
for key, name, doc, phone, email, ruv, vtype in ASSISTED:
    consent_key = upload(gp, 'PHYSICAL_CONSENT_FORM')
    support_key = upload(gp, 'VICTIM_SUPPORT_DOC')
    body = {'fullName': name, 'docType': 'CC', 'docNumber': doc, 'phone': phone, 'email': email,
            'victimData': {'victimCondition': True, 'ruvRegistrationNumber': ruv, 'victimizationType': vtype, 'supportDocS3Key': support_key},
            'consent': {'policyVersion': POLICY, 'purposes': ['PERSONAL_DATA', 'SENSITIVE_VICTIM'], 'physicalConsentFormS3Key': consent_key}}
    st, js = gp.call('POST', '/api/v1/auth/register-assisted', body)
    u = {'email': js['email'], 'nombre': name, 'documento': f'CC {doc}', 'celular': phone, 'rol': 'Anfitrión(a) (HOST) — alta asistida',
         'id': js['userId'], 'hostProfileId': js['hostProfileId'], 'asociacion': A['pijao']['nombre'],
         'alta': f"Alta asistida por el gestor {G['pijao']['nombre']} (víctima, RUV {ruv}, consentimiento físico)",
         'correo_sintetico': js.get('emailIsSynthetic'), 'nota': ''}
    R['usuarios'].append(u); H[key] = u
    step(f'Alta asistida de {name} (correo {"sintético" if js.get("emailIsSynthetic") else "propio"})')

# José activa su cuenta con el código que le genera el gestor; María queda pendiente de activación.
st, code = gp.call('POST', f"/api/v1/gestor/hosts/{H['h_jose']['hostProfileId']}/activation-code")
pub.call('POST', '/api/v1/auth/activate', {'email': H['h_jose']['email'], 'code': code['code'], 'newPassword': PW})
H['h_jose']['nota'] = 'Activó su cuenta con el código que le generó el gestor; ya puede iniciar sesión'
st, code_m = gp.call('POST', f"/api/v1/gestor/hosts/{H['h_maria']['hostProfileId']}/activation-code")
H['h_maria']['nota'] = f"PENDIENTE DE ACTIVACIÓN: el gestor le generó el código {code_m['code']} (vence {code_m['expiresAt']}). Sirve para mostrar la activación en vivo"
step('José activó su cuenta; María queda con código de activación pendiente')

# ---------------------------------------------------------------------------
# 6. Turistas
# ---------------------------------------------------------------------------
print('== 6. Turistas')
TOUR = [
    ('t_camila', 'camila.herrera@integrate-demo.test', 'Camila Herrera Montoya', '1020458871', '+573176634120', 'CC'),
    ('t_santiago', 'santiago.mejia@integrate-demo.test', 'Santiago Mejía Uribe', '1036671245', '+573014478836', 'CC'),
    ('t_valentina', 'valentina.ruiz@integrate-demo.test', 'Valentina Ruiz Castro', '1001234987', '+573208819473', 'CC'),
    ('t_daniel', 'daniel.ortiz@integrate-demo.test', 'Daniel Esteban Ortiz Peña', '80123456', '+573157723390', 'CC'),
    ('t_sophie', 'sophie.martin@integrate-demo.test', 'Sophie Martin', 'FR19AB4471', '+33612457890', 'PA'),
    ('t_juliana', 'juliana.pardo@integrate-demo.test', 'Juliana Pardo Salazar', '1053887201', '+573192245518', 'CC'),
]
T = {}
for key, email, name, doc, phone, dtp in TOUR:
    T[key] = register(EMAILS.get(key, email), name, doc, phone, doc_type=dtp, role_txt='Turista (TOURIST)')
    step(f'Turista {name}')

# ---------------------------------------------------------------------------
# 7. Experiencias
# ---------------------------------------------------------------------------
print('== 7. Experiencias')
EXP = [
    dict(key='e_cafe', host='h_rosa', title='Del grano a la taza: ruta del café en la finca La Esperanza',
         description='Recorrido por una finca cafetera familiar en la vereda Palestina de Salento. Rosalba y su familia explican la siembra, la recolección a mano, '
                     'el beneficio húmedo y el secado al sol. Al final se tuesta y se prepara café de origen en método tradicional (chorreador) y se acompaña con '
                     'arepa de chócolo hecha en fogón de leña. El 85 % del pago va directo a la familia anfitriona.',
         safety='Calzado cerrado con buen agarre, ropa cómoda y protector solar. Terreno con pendientes moderadas. No apto para menores de 6 años. El recorrido se suspende con lluvia fuerte.',
         intensity='LOW', duration=210, muni='63690', vereda='Palestina', meeting='Parque principal de Salento, frente a la iglesia Nuestra Señora del Carmen',
         lat=4.637485, lon=-75.570938, cap=10, price=85000, cats=['AGROTURISMO', 'GASTRONOMIA_TRADICIONAL'], route='LOOP', km=3.2, rt=210,
         stops=[('Cafetal de la familia', 'Recolección del grano maduro'), ('Beneficiadero', 'Despulpado, fermentación y lavado'), ('Secadero', 'Secado al sol en marquesina'), ('Cocina de leña', 'Tostión, preparación y cata')],
         final='APPROVED'),
    dict(key='e_cocora', host='h_hernan', title='Caminata al bosque de palmas de cera del Valle de Cocora',
         description='Caminata guiada por campesinos de la cooperativa entre las palmas de cera, árbol nacional de Colombia. Se cruza el río Quindío por puentes '
                     'colgantes, se visita la reserva Acaime para ver colibríes y se regresa por el bosque de niebla. Hernán comparte cómo la comunidad pasó de la '
                     'ganadería a la conservación.',
         safety='Intensidad media: 11 km con ascensos. Botas o tenis de suela gruesa, impermeable y agua (1,5 L). No apto para personas con problemas cardíacos o de rodilla. Guía con botiquín y radio.',
         intensity='MEDIUM', duration=330, muni='63690', vereda='Cocora', meeting='Restaurante de la entrada al Valle de Cocora, junto al parqueadero',
         lat=4.638430, lon=-75.487816, cap=12, price=120000, cats=['NATURALEZA_CONSERVACION'], route='LOOP', km=11.0, rt=330,
         stops=[('Puentes del río Quindío', 'Siete puentes colgantes'), ('Reserva Acaime', 'Casa de los colibríes'), ('Mirador de las palmas', 'Bosque de palmas de cera')],
         final='APPROVED'),
    dict(key='e_memoria', host='h_ovidio', title='Sendero de la memoria: historias de regreso a Pijao',
         description='Recorrido a pie por las veredas donde las familias de ASOCAVIP regresaron tras el desplazamiento. Ovidio y otros caficultores cuentan su historia '
                     'en primera persona, se visita el mural de la memoria y la huerta comunitaria. Cierra con un círculo de palabra y un almuerzo campesino.',
         safety='Recorrido de 4 km por caminos destapados. Se tratan temas sensibles del conflicto armado: se pide escucha respetuosa y no tomar fotos de personas sin permiso.',
         intensity='LOW', duration=240, muni='63548', vereda='Río Lejos', meeting='Parque principal de Pijao, frente a la alcaldía',
         lat=4.334060, lon=-75.704660, cap=15, price=70000, cats=['SENDERISMO_MEMORIA', 'TALLER_MEMORIA'], route='OUT_AND_BACK', km=4.0, rt=240,
         stops=[('Mural de la memoria', None), ('Huerta comunitaria', 'Siembra simbólica'), ('Casa comunal', 'Círculo de palabra y almuerzo')],
         final='APPROVED'),
    dict(key='e_cocina', host='h_luz', title='Cocina campesina quindiana con Luz Mery',
         description='Taller de cocina tradicional en la vereda La Morelia de Filandia: se preparan frijoles con garra, hogao, arepas de maíz pelado y dulce de '
                     'brevas con ingredientes de la huerta. Se come en familia y cada participante se lleva el recetario de la comunidad.',
         safety='Actividad bajo techo con fogón de leña. Avise alergias alimentarias al reservar. Los menores de 12 años deben estar acompañados en la zona de cocina.',
         intensity='LOW', duration=180, muni='63272', vereda='La Morelia', meeting='Mirador Colina Iluminada de Filandia (entrada principal)',
         lat=4.676590, lon=-75.658580, cap=8, price=65000, cats=['GASTRONOMIA_TRADICIONAL'], route=None, km=None, rt=None, stops=[],
         final='APPROVED'),
    dict(key='e_aves', host='h_jose', title='Avistamiento de aves al amanecer en la cuchilla de Pijao',
         description='Salida al amanecer por los bosques andinos de la cuchilla de Pijao con José, guía local formado en monitoreo de aves. Se pueden ver tucanes, '
                     'barranqueros, tángaras y, con suerte, el loro orejiamarillo. Incluye binoculares y desayuno campesino.',
         safety='Salida a las 5:30 a. m. Lleve abrigo y linterna. Caminata suave de 3 km. Silencio durante el avistamiento.',
         intensity='LOW', duration=240, muni='63548', vereda='La Mariela', meeting='Estación de servicio a la entrada de Pijao (vía Buenavista)',
         lat=4.329880, lon=-75.708120, cap=6, price=95000, cats=['NATURALEZA_CONSERVACION'], route='OUT_AND_BACK', km=3.0, rt=240,
         stops=[('Bosque de la cuchilla', 'Avistamiento'), ('Finca La Mariela', 'Desayuno campesino')], final='APPROVED'),
    dict(key='e_platano', host='h_rosa', title='Taller de dulces y envueltos de plátano',
         description='Taller corto en la finca La Esperanza para aprender a hacer envueltos de plátano maduro y cocadas, recetas de la abuela de Rosalba.',
         safety='Actividad en cocina, sin desplazamientos. Avise alergias.', intensity='LOW', duration=120, muni='63690', vereda='Palestina',
         meeting='Parque principal de Salento, frente a la iglesia Nuestra Señora del Carmen', lat=4.637485, lon=-75.570938, cap=8, price=45000,
         cats=['GASTRONOMIA_TRADICIONAL'], route=None, km=None, rt=None, stops=[], final='PENDING_REVIEW'),
    dict(key='e_cabalgata', host='h_hernan', title='Cabalgata extrema por el páramo',
         description='Cabalgata de un día completo hasta el páramo de Romerales.', safety='Caballos de la finca.', intensity='HIGH', duration=600,
         muni='63690', vereda='Cocora', meeting='Entrada al Valle de Cocora', lat=4.638430, lon=-75.487816, cap=10, price=250000,
         cats=['NATURALEZA_CONSERVACION'], route='OUT_AND_BACK', km=24.0, rt=600, stops=[], final='REJECTED'),
    dict(key='e_guadua', host='h_luz', title='Taller de artesanías en guadua y bejuco',
         description='Taller donde los artesanos de La Morelia enseñan a tejer canastos en bejuco y a trabajar la guadua, material típico del paisaje cafetero.',
         safety='Uso de herramientas cortantes bajo supervisión. Mayores de 10 años.', intensity='LOW', duration=150, muni='63272', vereda='La Morelia',
         meeting='Mirador Colina Iluminada de Filandia (entrada principal)', lat=4.676590, lon=-75.658580, cap=10, price=55000,
         cats=['AGROTURISMO'], route=None, km=None, rt=None, stops=[], final='SUSPENDED'),
    dict(key='e_cascada', host='h_fabio', title='Ruta a la cascada El Edén y baño en el río',
         description='Caminata por la vereda El Edén de Montenegro hasta una cascada de 20 metros, con baño en pozos naturales y almuerzo de trucha.',
         safety='Caminata de 6 km con tramos resbalosos. Saber nadar. Chaleco salvavidas incluido.', intensity='MEDIUM', duration=300, muni='63470', vereda='El Edén',
         meeting='Parque principal de Montenegro', lat=4.566810, lon=-75.750320, cap=12, price=75000, cats=['NATURALEZA_CONSERVACION'],
         route='OUT_AND_BACK', km=6.0, rt=300, stops=[('Cascada El Edén', None)], final='RETIRED'),
    dict(key='e_borrador', host='h_ovidio', title='Taller de tostión artesanal de café',
         description='Borrador en preparación: taller para aprender a tostar café en paila.', safety='Pendiente por definir.', intensity='LOW', duration=120,
         muni='63548', vereda=None, meeting='Parque principal de Pijao, frente a la alcaldía', lat=4.334060, lon=-75.704660, cap=6, price=50000,
         cats=['AGROTURISMO'], route=None, km=None, rt=None, stops=[], final='DRAFT'),
]
HAPI = {}


def host_api(key):
    if key not in HAPI:
        HAPI[key] = Api('anfitrión ' + key).login(H[key]['email'])
    return HAPI[key]


E = {}


def approve(eid, hist):
    """La aprobación espera la verificación asíncrona del punto de encuentro (ADR-0030)."""
    for i in range(40):
        st, js = admin.call('POST', f'/api/v1/admin/experiences/{eid}/approve', ok=(200,), fatal=False)
        if st == 200: return
        code = (js or {}).get('code')
        if code == 'CATALOG_MEETING_POINT_NOT_VERIFIED' and (js or {}).get('meetingPointCheckStatus') not in (None, 'VERIFICANDO', 'PENDIENTE'):
            admin.call('POST', f'/api/v1/admin/experiences/{eid}/meeting-point/confirm', fatal=False)
            hist.append(f"Punto de encuentro confirmado a mano por la administradora ({js.get('meetingPointCheckStatus')})")
        elif code != 'CATALOG_MEETING_POINT_NOT_VERIFIED':
            sys.exit(f'✘ aprobar {eid}: HTTP {st} {json.dumps(js, ensure_ascii=False)[:800]}')
        time.sleep(1.5)
    sys.exit(f'✘ aprobar {eid}: el punto de encuentro nunca se verificó')


for e in EXP:
    h = host_api(e['host'])
    body = {'title': e['title'], 'description': e['description'], 'safetyGuidelines': e['safety'], 'physicalIntensity': e['intensity'],
            'durationMinutes': e['duration'], 'municipalityCode': e['muni'], 'vereda': e['vereda'], 'meetingPoint': e['meeting'],
            'latitude': e['lat'], 'longitude': e['lon'], 'maxCapacityPerSlot': e['cap'], 'unitPriceCop': e['price'], 'categoryCodes': e['cats'],
            'routeType': e['route'], 'distanceKm': e['km'], 'roundTripMinutes': e['rt'],
            'stops': [{'name': n, 'description': d} for n, d in e['stops']]}
    st, js = h.call('POST', '/api/v1/host/experiences', body)
    eid = js['id']; hist = ['Creada por el anfitrión (borrador)']
    if e['final'] != 'DRAFT':
        h.call('POST', f'/api/v1/host/experiences/{eid}/submit'); hist.append('Enviada a revisión')
    if e['final'] in ('APPROVED', 'SUSPENDED', 'RETIRED'):
        approve(eid, hist); hist.append('Aprobada por la administradora')
    if e['final'] == 'REJECTED':
        admin.call('POST', f'/api/v1/admin/experiences/{eid}/reject', {'reason': 'Duración de 10 horas a caballo en páramo sin plan de seguridad ni póliza de los animales. '
                                                                                  'Ajuste las condiciones de seguridad y vuelva a enviarla.'})
        hist.append('Rechazada por la administradora (falta plan de seguridad)')
    E[e['key']] = {'id': eid, 'titulo': e['title'], 'anfitrion': H[e['host']]['nombre'], 'host_key': e['host'], 'precio': e['price'], 'cupo': e['cap'],
                   'municipio': e['muni'], 'estado_objetivo': e['final'], 'historial': hist, 'franjas': []}
    step(f"Experiencia «{e['title']}» -> {hist[-1]}")


def slots(key, specs):
    """specs: lista de (días desde hoy, 'HH:MM', 'HH:MM', cupos)."""
    e = E[key]; h = host_api(next(x['host'] for x in EXP if x['key'] == key))
    body = {'schedules': [{'localStart': f'{(TODAY + dt.timedelta(days=d)).isoformat()}T{a}', 'localEnd': f'{(TODAY + dt.timedelta(days=d)).isoformat()}T{b}', 'totalSlots': n}
                          for d, a, b, n in specs]}
    st, js = h.call('POST', f"/api/v1/host/experiences/{e['id']}/schedules", body)
    for s in js:
        e['franjas'].append({'id': s['id'], 'inicio': s['localStartTime'], 'fin': s['localEndTime'], 'cupos': s['totalSlots']})
    return js


print('== 8. Franjas horarias')
now_h = dt.datetime.now().hour
today_start = f'{max(now_h + 2, 15):02d}:00' if now_h < 20 else None
today_end = f'{max(now_h + 2, 15) + 2:02d}:00' if today_start else None
S = {}
S['cafe'] = slots('e_cafe', [(1, '08:00', '11:30', 10), (5, '08:00', '11:30', 10), (8, '08:00', '11:30', 10), (12, '08:00', '11:30', 10), (19, '08:00', '11:30', 10)])
S['cocora'] = slots('e_cocora', [(3, '07:00', '12:30', 12), (10, '07:00', '12:30', 12), (17, '07:00', '12:30', 12)])
memoria_specs = [(4, '09:00', '13:00', 15), (11, '09:00', '13:00', 15), (18, '09:00', '13:00', 15)]
if today_start: memoria_specs.insert(0, (0, today_start, today_end, 15))
S['memoria'] = slots('e_memoria', memoria_specs)
S['cocina'] = slots('e_cocina', [(2, '10:00', '13:00', 8), (6, '10:00', '13:00', 8), (13, '10:00', '13:00', 8)])
S['aves'] = slots('e_aves', [(7, '05:30', '09:30', 6), (14, '05:30', '09:30', 6)])
S['cascada'] = slots('e_cascada', [(9, '08:00', '13:00', 12)])
S['guadua'] = slots('e_guadua', [(15, '14:00', '16:30', 10)])
step('Franjas creadas para las experiencias aprobadas' + (' (incluye una HOY para validar asistencia)' if today_start else ''))

# ---------------------------------------------------------------------------
# 9. Reservas y pagos de los turistas
# ---------------------------------------------------------------------------
print('== 9. Reservas')
TAPI = {k: Api('turista ' + k).login(v['email']) for k, v in T.items()}
COMPANIONS = {
    't_camila': [('Andrés Herrera Montoya', 'CC', '1020471190', 'Sura')],
    't_santiago': [('Mariana López Duque', 'CC', '1036699012', 'Sanitas'), ('Tomás Mejía López', 'TI', '1036812345', 'Sanitas')],
    't_valentina': [],
    't_daniel': [('Claudia Peña Rojas', 'CC', '52345678', 'Nueva EPS')],
    't_sophie': [('Lucas Bernard', 'PA', 'FR22CD8812', None)],
    't_juliana': [('Felipe Salazar Gómez', 'CC', '1053901234', 'Salud Total')],
}
EPS = {'t_camila': 'Sura', 't_santiago': 'Sanitas', 't_valentina': 'Coomeva', 't_daniel': 'Nueva EPS', 't_sophie': None, 't_juliana': 'Salud Total'}
EMERG = {'t_camila': ('Gloria Montoya', '+573104456721'), 't_santiago': ('Jorge Mejía', '+573002234455'), 't_valentina': ('Rubén Ruiz', '+573113345566'),
         't_daniel': ('Claudia Peña Rojas', '+573157789012'), 't_sophie': ('Marie Martin', '+33611223344'), 't_juliana': ('Rocío Salazar', '+573186677889')}


def book(tkey, exp_key, sched, n, method='NEQUI', pay='APPROVED', note=''):
    t = T[tkey]; api = TAPI[tkey]
    parts = [{'fullName': t['nombre'], 'docType': t['documento'].split()[0], 'docNumber': t['documento'].split()[1], 'medicalEps': EPS[tkey] if HEALTH else None, 'isHolder': True}]  # sin aviso de salud publicado, la EPS no se admite (ADR-0054)
    parts += [{'fullName': a, 'docType': b, 'docNumber': c, 'medicalEps': None, 'isHolder': False} for a, b, c, d in COMPANIONS[tkey][:n - 1]]
    st, lk = api.call('POST', '/api/v1/bookings/lock', {'scheduleId': sched['id'], 'requestedSlots': n,
                                                       'emergencyContact': {'fullName': EMERG[tkey][0], 'phone': EMERG[tkey][1]}, 'participants': parts})
    b = {'id': lk['bookingId'], 'turista': t['nombre'], 'turista_email': t['email'], 'experiencia': E[exp_key]['titulo'], 'anfitrion': E[exp_key]['anfitrion'],
         'franja': sched['localStartTime'], 'personas': n, 'total': lk['totalAmountCop'], 'metodo': method, 'historial': ['Cupos bloqueados (reserva en espera de pago)'], 'nota': note}
    if pay:
        st, ck = api.call('POST', f"/api/v1/bookings/{b['id']}/payment", {'paymentMethod': method})
        b['paymentId'] = ck['paymentId']; b['historial'].append(f'Pago iniciado por {method}')
        api.call('POST', f"/api/v1/dev/payments/{ck['paymentId']}/simulate", {'status': pay})
        b['historial'].append('Pago APROBADO (pasarela de pruebas)' if pay == 'APPROVED' else f'Pago {pay} (pasarela de pruebas)')
    R['reservas'].append(b)
    return b


def wait_booking(tkey, b, want, tries=20):
    for _ in range(tries):
        st, js = TAPI[tkey].call('GET', f"/api/v1/bookings/{b['id']}")
        if js.get('status') == want: return js
        time.sleep(1)
    return js


def voucher(tkey, b, tries=30):
    for _ in range(tries):
        st, js = TAPI[tkey].call('GET', f"/api/v1/bookings/{b['id']}/voucher", fatal=False)
        if st == 200 and js and js.get('shortCode'):
            b['voucher'] = js['shortCode']; return js
        time.sleep(1)
    return None


BK = {}
BK['camila_cafe'] = book('t_camila', 'e_cafe', S['cafe'][1], 2, 'NEQUI')
BK['santiago_cocora'] = book('t_santiago', 'e_cocora', S['cocora'][0], 3, 'CARD')
BK['valentina_memoria'] = book('t_valentina', 'e_memoria', S['memoria'][1 if today_start else 0], 1, 'PSE')
BK['daniel_cocina'] = book('t_daniel', 'e_cocina', S['cocina'][1], 2, 'DAVIPLATA')
BK['sophie_aves'] = book('t_sophie', 'e_aves', S['aves'][0], 2, 'CARD')
BK['juliana_cafe'] = book('t_juliana', 'e_cafe', S['cafe'][3], 2, 'PSE', note='Se cancela con más de 72 h: reembolso')
BK['camila_cocora'] = book('t_camila', 'e_cocora', S['cocora'][1], 1, 'CARD', pay='DECLINED', note='Pago rechazado por la pasarela')
BK['santiago_aves'] = book('t_santiago', 'e_aves', S['aves'][1], 1, pay=None, note='Cupo bloqueado sin pagar: expira solo')
BK['daniel_cafe_manana'] = book('t_daniel', 'e_cafe', S['cafe'][0], 1, 'NEQUI', note='Se cancela con menos de 72 h: sin reembolso')
BK['sophie_cascada'] = book('t_sophie', 'e_cascada', S['cascada'][0], 2, 'CARD', note='Experiencia retirada después: la reserva queda afectada')
if today_start:
    BK['valentina_hoy'] = book('t_valentina', 'e_memoria', S['memoria'][0], 1, 'NEQUI', note='Franja de HOY: el gestor valida la asistencia')
    BK['juliana_hoy'] = book('t_juliana', 'e_memoria', S['memoria'][0], 2, 'NEQUI', note='Franja de HOY: queda para validar en vivo con el escáner o el código')
for k, b in BK.items():
    tkey = next(t for t in T if T[t]['nombre'] == b['turista'])
    if b.get('paymentId'):
        js = wait_booking(tkey, b, 'CONFIRMED' if 'APROBADO' in b['historial'][-1] else 'X', tries=10 if 'APROBADO' in b['historial'][-1] else 2)
        b['estado'] = js.get('status')
        if b['estado'] == 'CONFIRMED':
            voucher(tkey, b); b['historial'].append(f"Reserva CONFIRMADA; voucher {b.get('voucher', '(en generación)')} enviado por correo (Mailpit)")
    else:
        b['estado'] = 'PENDING_PAYMENT'
    step(f"Reserva {b['turista']} -> {b['experiencia'][:40]}… [{b['estado']}]")

print('== 10. Cancelaciones')
for k, tkey, reason in [('juliana_cafe', 't_juliana', 'Cambio de fechas del viaje familiar; reprogramaremos para diciembre.'),
                        ('daniel_cafe_manana', 't_daniel', 'Se presentó una cita médica que no puedo mover.')]:
    st, js = TAPI[tkey].call('POST', f"/api/v1/bookings/{BK[k]['id']}/cancel", {'reason': reason})
    BK[k]['historial'].append(f'Cancelada por el turista: «{reason}»'); BK[k]['cancelacion'] = js
    BK[k]['estado'] = (js or {}).get('status') or 'CANCELLED'
    step(f"Cancelación {BK[k]['turista']} ({BK[k]['nota']})")

# ---------------------------------------------------------------------------
# 11. Validación de asistencia por el gestor (canje) -> liberación del 85 %
# ---------------------------------------------------------------------------
if today_start and BK['valentina_hoy'].get('voucher'):
    print('== 11. Validación de asistencia')
    st, js = gp.call('POST', '/api/v1/offline/contingency/redeem', {'shortCode': BK['valentina_hoy']['voucher']},
                     headers={'X-Device-Id': str(uuid.uuid4())})
    BK['valentina_hoy']['historial'].append(f"Asistencia validada por el gestor {G['pijao']['nombre']} con el código {BK['valentina_hoy']['voucher']}")
    BK['valentina_hoy']['estado'] = 'EXECUTED (asistió)'
    step('Gestor de Pijao validó la asistencia de Valentina (libera el 85 % para la asociación)')

# ---------------------------------------------------------------------------
# 12. Bajas, suspensiones y retiros
# ---------------------------------------------------------------------------
print('== 12. Bajas')
admin.call('POST', f"/api/v1/admin/experiences/{E['e_guadua']['id']}/suspend", {'reason': 'Reporte de un visitante: herramientas de corte sin guardas. Suspendida mientras la JAC adecúa el taller.'})
E['e_guadua']['historial'].append('Suspendida por la administradora (seguridad del taller)')
R['bajas'].append({'que': 'Experiencia suspendida', 'quien': E['e_guadua']['titulo'], 'detalle': 'Suspendida por la administradora: herramientas sin guardas. Se puede reactivar.'})
# La app no deja cancelar una franja con reservas: el anfitrión avisa, la turista cancela (>72 h, reembolso) y luego se cancela la franja vacía.
reason = 'El anfitrión me avisó que se retira de la plataforma y no podrá atender la salida.'
st, js = TAPI['t_sophie'].call('POST', f"/api/v1/bookings/{BK['sophie_cascada']['id']}/cancel", {'reason': reason})
BK['sophie_cascada']['historial'].append(f'Cancelada por la turista tras el aviso del anfitrión: «{reason}» (más de 72 h: reembolso)')
BK['sophie_cascada']['estado'] = (js or {}).get('status') or 'CANCELLED'
host_api('h_fabio').call('POST', f"/api/v1/host/schedules/{S['cascada'][0]['id']}/cancel",
                         {'reason': 'El anfitrión se retira de la plataforma por motivos de salud.'})
E['e_cascada']['historial'].append('El anfitrión canceló su franja ya sin reservas')
admin.call('POST', f"/api/v1/admin/experiences/{E['e_cascada']['id']}/retire", {'reason': 'El anfitrión se retiró de la plataforma y la asociación fue suspendida.'})
E['e_cascada']['historial'].append('Retirada definitivamente por la administradora')
admin.call('POST', f"/api/v1/admin/associations/{A['montenegro']['id']}/suspend", {'reason': 'La asociación dejó de operar: su único anfitrión se retiró.'}, fatal=False)
A['montenegro']['estado'] = 'SUSPENDIDA'
R['bajas'].append({'que': 'Anfitrión dado de baja', 'quien': H['h_fabio']['nombre'],
                   'detalle': 'Se retiró su experiencia «Ruta a la cascada El Edén» y se suspendió su asociación (El Edén de Montenegro). '
                              'Su cuenta se conserva por trazabilidad (no hay borrado físico), pero ya no publica ni recibe reservas.'})
H['h_fabio']['nota'] = 'DADO DE BAJA: experiencia retirada y asociación suspendida'
admin.call('POST', f"/api/v1/admin/link-managers/{G['montenegro']['linkManagerId']}/revoke", {'reason': 'Asociación suspendida; se revoca la acreditación del gestor.'}, fatal=False)
G['montenegro']['nota'] = 'ACREDITACIÓN REVOCADA (definitiva)'
R['bajas'].append({'que': 'Gestor de enlace revocado', 'quien': G['montenegro']['nombre'], 'detalle': 'Acreditación revocada de forma definitiva al suspender su asociación.'})
admin.call('POST', f"/api/v1/admin/link-managers/{G['filandia']['linkManagerId']}/suspend", {'reason': 'Acreditación vencida; pendiente renovar el certificado de la JAC.'}, fatal=False)
G['filandia']['nota'] = 'SUSPENDIDO (se puede reactivar desde Administración)'
R['bajas'].append({'que': 'Gestor de enlace suspendido', 'quien': G['filandia']['nombre'], 'detalle': 'Suspendido temporalmente; sirve para mostrar el botón «Reactivar».'})
step('Suspensión de experiencia, retiro, baja del anfitrión de Montenegro, revocación y suspensión de gestores')

# Estado final leído de la base (fuente de verdad para el documento)
R['estado_experiencias'] = psql("SELECT title || '|' || status FROM catalog.experiences ORDER BY title;").splitlines()
R['estado_reservas'] = psql("SELECT b.id || '|' || b.status FROM booking.bookings b;").splitlines()
R['estado_asociaciones'] = psql("SELECT legal_name || '|' || status FROM catalog.community_associations ORDER BY legal_name;").splitlines()
R['estado_gestores'] = psql("SELECT u.full_name || '|' || l.accreditation_status FROM catalog.link_manager_profiles l JOIN auth.users u ON u.id = l.user_id ORDER BY 1;").splitlines()
R['experiencias'] = list(E.values())
with open(OUT, 'w', encoding='utf-8') as f:
    json.dump(R, f, ensure_ascii=False, indent=1, default=str)
print(f'\nListo. Resultado en {os.path.relpath(OUT, ROOT)}; contraseña común en {os.path.relpath(PW_FILE, ROOT)} (ambos ignorados por Git).')
