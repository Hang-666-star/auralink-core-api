#!/usr/bin/env python3
"""Disposable Assistant foundation PG/application integration harness.

Only this checkout's target/assistant-isolated is writable. Explicit PostgreSQL
binary and immutable V001-V013 source paths are required at setup. No inherited
environment, production credential, dataset, provider, or migration is used.
Private generated credentials never appear in stdout or command arguments.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import signal
import socket
import struct
import subprocess
import time
import urllib.error
import urllib.request
import uuid
import zlib

REPO = Path(__file__).resolve().parents[1]
ROOT = REPO / 'target/assistant-isolated'
SOCKET = REPO / 'target/as-socket'
OLD_SHA = 'e71d6793ecaac7ec10d8ba16bdef8ffd3c829e02a484c3cd2704c9b4b59386d2'
DATABASES = ['assistant_foundation_old', 'assistant_foundation_expanded', 'assistant_foundation_test', 'assistant_foundation_final', 'assistant_foundation_verified']
PORT = 55743
APP_PORT = 20543


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def private(path, value):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, 'w') as out:
        json.dump(value, out, ensure_ascii=False, indent=2)
        out.write('\n')
    path.chmod(0o600)


def settings():
    return json.loads((ROOT / 'private/connection.json').read_text())


def environment():
    cfg = settings()
    return {'PATH': '/usr/bin:/bin', 'LANG': 'C.UTF-8', 'LC_ALL': 'C.UTF-8',
            'LD_LIBRARY_PATH': str(Path(cfg['pg_prefix']) / 'usr/lib/x86_64-linux-gnu')}


def command(args, content=None, timeout=60, env=None):
    done = subprocess.run([str(x) for x in args], input=content, text=True,
                          capture_output=True, timeout=timeout, env=env or environment())
    if done.returncode:
        # Error messages are not blindly printed: SQL/application logs may have
        # private bind parameters. Full diagnostics stay in task-private files.
        target = ROOT / 'private/last-command-failure.json'
        private(target, {'returncode': done.returncode, 'stdout': done.stdout, 'stderr': done.stderr})
        raise RuntimeError(f'Isolated command failed ({done.returncode}); inspect {target}')
    return done.stdout.strip()


def sql(database, statement):
    assert database in DATABASES or database == 'postgres'
    cfg = settings()
    binary = Path(cfg['pg_prefix']) / 'usr/lib/postgresql/16/bin/psql'
    return command([binary, '-X', '-q', '-w', '-h', SOCKET, '-p', cfg['port'],
                    '-U', 'assistant_migrator', '-d', database, '-At', '-v', 'ON_ERROR_STOP=1'], statement)


def quote(value):
    return "'" + str(value).replace("'", "''") + "'"


def png():
    def chunk(kind, value):
        return struct.pack('>I', len(value)) + kind + value + struct.pack('>I', zlib.crc32(kind + value) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 2, 2, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(b'\0\x88\xab\x91\x88\xab\x91' * 2)) + chunk(b'IEND', b'')


def baseline(database):
    tables = ['catalog_batches', 'catalog_batch_revisions', 'catalog_batch_current_revisions',
              'catalog_paintings', 'catalog_media_assets', 'catalog_source_records',
              'catalog_users', 'catalog_painting_critic_tasks', 'creations']
    return {table: {'count': int(sql(database, f'SELECT count(*) FROM {table}')),
                    'sha256': hashlib.sha256(sql(database, f'SELECT row_to_json(t)::text FROM {table} t ORDER BY row_to_json(t)::text').encode()).hexdigest()}
            for table in tables}


def fixture(database):
    image_hash = digest(ROOT / 'fixture-media/synthetic.png')
    sql(database, f"""
BEGIN;
INSERT INTO catalog_users(id,username,password_hash,full_name,email,created_at,updated_at)
VALUES (901,'assistant_fixture_existing','not-a-login-password','Synthetic preserved user','preserved@example.invalid','2026-01-01','2026-01-01');
SELECT setval(pg_get_serial_sequence('catalog_users','id'),901,true);
INSERT INTO catalog_batches(batch_id,first_label) VALUES ('00000000-0000-4000-8000-000000000101','SYNTHETIC_ONLY');
INSERT INTO catalog_batch_revisions(batch_id,revision,input_fingerprint,contract_version,manifest_json,preview_summary,publication_status,published_at)
VALUES ('00000000-0000-4000-8000-000000000101',1,repeat('a',64),'synthetic-only','{{}}','{{}}','PUBLISHED','2026-01-01');
INSERT INTO catalog_batch_current_revisions(batch_id,revision) VALUES ('00000000-0000-4000-8000-000000000101',1);
INSERT INTO catalog_media_assets(public_id,storage_uri,relative_path,content_sha256,source_batch_id,source_revision,media_status)
VALUES ('00000000-0000-4000-8000-000000000301','batch://00000000-0000-4000-8000-000000000101/r1/synthetic.png','synthetic.png','{image_hash}','00000000-0000-4000-8000-000000000101',1,'ACTIVE');
INSERT INTO catalog_paintings(public_id,current_batch_id,current_revision,image_asset_id,image_display_status,catalog_status,title,author_name,category)
VALUES ('00000000-0000-4000-8000-000000000201','00000000-0000-4000-8000-000000000101',1,'00000000-0000-4000-8000-000000000301','READY','ACTIVE','Synthetic preserved painting','Fixture author','Synthetic category');
INSERT INTO catalog_source_records(batch_id,revision,annotation_file_id,record_number,source_record_key,record_state,painting_id,image_asset_id,image_resolution,raw_record,issues)
VALUES ('00000000-0000-4000-8000-000000000101',1,'synthetic',1,'synthetic-only','VALID','00000000-0000-4000-8000-000000000201','00000000-0000-4000-8000-000000000301','EXACT','{{"source":"SYNTHETIC_ONLY"}}','[]');
INSERT INTO catalog_painting_critic_tasks(public_id,owner_user_id,painting_id,image_asset_id,source_image_sha256,input_batch_id,input_revision,input_snapshot,profile,input_fingerprint,evaluator_version,model_identity,prompt_schema_version,status,legacy_original_status,created_at,updated_at)
VALUES ('00000000-0000-4000-8000-000000000401',901,'00000000-0000-4000-8000-000000000201','00000000-0000-4000-8000-000000000301','{image_hash}','00000000-0000-4000-8000-000000000101',1,'{{"synthetic":true}}','synthetic',repeat('b',64),'synthetic','NEVER_CALLED','synthetic','HELD_LEGACY','QUEUED','2026-01-01','2026-01-01');
INSERT INTO creations(public_id,user_id,workflow_snapshot,source_modality,source_text,status,created_at,updated_at)
VALUES ('00000000-0000-4000-8000-000000000501',901,'{{"synthetic":true}}','TEXT','Synthetic preserved task','HELD_LEGACY','2026-01-01','2026-01-01');
COMMIT;
""")


def setup(args):
    if ROOT.exists() and not args.resume_initialized:
        raise RuntimeError('Refusing existing isolated environment; use status/compatibility')
    if args.resume_initialized:
        assert (ROOT / 'postgres/data/PG_VERSION').read_text().strip() == '16'
        assert not (ROOT / 'postgres/data/postmaster.pid').exists()
        assert not (ROOT / 'evidence/environment.json').exists()
    pg_prefix = Path(args.pg_prefix).resolve(strict=True)
    migration_dir = Path(args.legacy_migrations).resolve(strict=True)
    migrations = sorted(migration_dir.glob('V*.sql'))
    assert [p.name[:4] for p in migrations] == [f'V{i:03}' for i in range(1, 14)]
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', PORT))
    for directory in ['private', 'evidence', 'postgres', 'socket', 'fixture-media', 'app-work']:
        (ROOT / directory).mkdir(parents=True, mode=0o700, exist_ok=args.resume_initialized)
    SOCKET.mkdir(mode=0o700, exist_ok=False)
    cfg = {'pg_prefix': str(pg_prefix), 'legacy_migrations': str(migration_dir), 'port': PORT,
           'host': '127.0.0.1', 'username': 'assistant_fixture_app', 'password': secrets.token_hex(24),
           'test_username': 'assistant_fixture_test', 'test_password': secrets.token_hex(24),
           'jwt_key': base64.b64encode(secrets.token_bytes(48)).decode(),
           'account_password': secrets.token_urlsafe(24)}
    if args.resume_initialized:
        cfg = settings()
        assert cfg['pg_prefix'] == str(pg_prefix) and cfg['legacy_migrations'] == str(migration_dir)
    else:
        private(ROOT / 'private/connection.json', cfg)
    binary = pg_prefix / 'usr/lib/postgresql/16/bin'
    if not args.resume_initialized:
        command([binary / 'initdb', '-D', ROOT / 'postgres/data', '-L', pg_prefix / 'usr/share/postgresql/16',
             '-U', 'assistant_migrator', '--encoding=UTF8', '--locale=C', '--auth-local=trust', '--auth-host=scram-sha-256'])
    command([binary / 'pg_ctl', '-D', ROOT / 'postgres/data', '-l', ROOT / 'postgres/postgresql.log',
             '-o', f'-h 127.0.0.1 -p {PORT} -k {SOCKET}', '-w', 'start'])
    sql('postgres', f"CREATE ROLE assistant_fixture_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD {quote(cfg['password'])}; CREATE ROLE assistant_fixture_test LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD {quote(cfg['test_password'])};")
    (ROOT / 'fixture-media/synthetic.png').write_bytes(png())
    (ROOT / 'fixture-media/images/original').mkdir(parents=True, mode=0o700)
    (ROOT / 'fixture-media/images/original/synthetic.png').write_bytes(png())
    before = {}
    for database in DATABASES[:3]:
        command([binary / 'createdb', '-h', SOCKET, '-p', PORT, '-U', 'assistant_migrator', database])
        sql(database, 'CREATE TABLE prototype_schema_migrations(version text primary key,checksum text not null,applied_at timestamptz not null default now());')
        for path in migrations:
            sql(database, 'BEGIN;\n' + path.read_text() + f'\nINSERT INTO prototype_schema_migrations(version,checksum) VALUES({quote(path.name[:4])},{quote(digest(path))});\nCOMMIT;')
        fixture(database)
        sql(database, f"REVOKE CREATE ON SCHEMA public FROM PUBLIC; GRANT CONNECT ON DATABASE {database} TO assistant_fixture_app,assistant_fixture_test; GRANT USAGE ON SCHEMA public TO assistant_fixture_app,assistant_fixture_test; GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO assistant_fixture_app,assistant_fixture_test; GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO assistant_fixture_app,assistant_fixture_test; GRANT CREATE ON DATABASE {database} TO assistant_fixture_test;")
        before[database] = baseline(database)
    private(ROOT / 'evidence/baseline-fixtures.json', before)
    private(ROOT / 'evidence/environment.json', {'postgres': command([binary / 'postgres', '--version']), 'port': PORT,
            'databases': DATABASES[:3], 'migration_sources': [{'file': str(p), 'sha256': digest(p)} for p in migrations],
            'production_access': False, 'providers_enabled': False, 'records': 'synthetic-only, nonempty users/catalog/critic tasks/creations'})
    private(ROOT / 'private/junit-env.json', {'ASSISTANT_TEST_JDBC_URL': f'jdbc:postgresql://127.0.0.1:{PORT}/assistant_foundation_test', 'ASSISTANT_TEST_JDBC_USER': cfg['test_username'], 'ASSISTANT_TEST_JDBC_PASSWORD': cfg['test_password'], 'ASSISTANT_TEST_JDBC_USERNAME': cfg['test_username'], 'ASSISTANT_TEST_USER': cfg['test_username'], 'ASSISTANT_TEST_PASSWORD': cfg['test_password']})
    print(json.dumps({'created': True, 'port': PORT, 'databases': DATABASES[:3], 'private_junit_environment': str(ROOT / 'private/junit-env.json')}))


def migrate(args):
    path = Path(args.migration).resolve(strict=True)
    assert path.is_relative_to(REPO) and path.name.startswith('V014__')
    results = {}
    for database in [args.database]:
        before = baseline(database)
        recorded = sql(database, "SELECT checksum FROM prototype_schema_migrations WHERE version='V014'")
        if recorded:
            if recorded != digest(path):
                raise RuntimeError('V014 recorded checksum differs; refusing changed migration')
            results[database] = 'NOOP_SAME_CHECKSUM'
            continue
        sql(database, 'BEGIN;\n' + path.read_text() + f"\nINSERT INTO prototype_schema_migrations(version,checksum) VALUES('V014',{quote(digest(path))});\nCOMMIT;")
        sql(database, 'GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO assistant_fixture_app,assistant_fixture_test; GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO assistant_fixture_app,assistant_fixture_test;')
        assert before == baseline(database), 'Pre-existing fixture drift during migration'
        results[database] = 'APPLIED_EXISTING_FIXTURES_UNCHANGED'
    private(ROOT / 'evidence' / ('migration-' + args.database + '.json'), {'migration': str(path), 'sha256': digest(path), 'results': results,
            'old_schema_assistant_tables': sql('assistant_foundation_old', "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'assistant_%'")})
    print(json.dumps(results))


def status(_):
    cfg = settings()
    existing = sql('postgres', "SELECT datname FROM pg_database WHERE datname LIKE 'assistant_foundation_%'").splitlines()
    print(json.dumps({'postgres': sql('postgres', 'SHOW server_version'), 'port': cfg['port'],
                      'databases': {db: {'migrations': sql(db, 'SELECT count(*) FROM prototype_schema_migrations'), 'fixture_paintings': sql(db, 'SELECT count(*) FROM catalog_paintings')} for db in DATABASES if db in existing}}))


def app_config(database, enabled, name):
    cfg = settings()
    jdbc = f'jdbc:postgresql://127.0.0.1:{cfg["port"]}/{database}'
    work = ROOT / 'app-work' / name
    work.mkdir(parents=True, exist_ok=True, mode=0o700)
    values = {
        'server.address': '127.0.0.1', 'server.port': APP_PORT,
        'spring.config.import': 'optional:file:' + str(ROOT / 'private/empty.env') + '[.properties]',
        'spring.datasource.url': jdbc, 'spring.datasource.username': cfg['username'],
        'spring.datasource.password': cfg['password'], 'spring.datasource.driver-class-name': 'org.postgresql.Driver',
        'spring.datasource.hikari.maximum-pool-size': 6, 'spring.datasource.hikari.minimum-idle': 0,
        'spring.datasource.hikari.connection-init-sql': '',
        'spring.jpa.database-platform': 'org.hibernate.dialect.PostgreSQLDialect',
        'spring.jpa.hibernate.ddl-auto': 'none', 'spring.flyway.enabled': 'false',
        'auralink.jwt.secret': cfg['jwt_key'], 'auralink.jwt.expiration': 3600000,
        'auralink.unified-import.require-succeeded': 'false',
        'auralink.paintings.import-enabled': 'false',
        'auralink.paintings.picture-dir': str(ROOT / 'fixture-media'),
        'auralink.paintings.metadata-csv-path': str(work / 'disabled.csv'),
        'auralink.storage.upload-dir': str(work / 'uploads'),
        'auralink.storage.audio-dir': str(work / 'audio'),
        'auralink.storage.legacy-frontend-audio-dir': str(work / 'legacy-audio'),
        'auralink.media-assets.managed-dir': str(work / 'managed'),
        'auralink.knowledge.profile': 'poetry-only',
        'auralink.knowledge.snapshot-root': '',
    }
    for feature in ['guide', 'critic', 'workflows', 'creations', 'creation-providers', 'catalog-critic', 'catalog-guide']:
        values['auralink.' + feature + '.enabled'] = 'false'
    for feature in ['catalog-read', 'catalog-auth']:
        values['auralink.' + feature + '.enabled'] = 'true'
        for key, value in [('jdbc-url', jdbc), ('username', cfg['username']), ('password', cfg['password'])]:
            values[f'auralink.{feature}.datasource.{key}'] = value
    values.update({
        'auralink.catalog-read.batch-media-roots[0].batch-id': '00000000-0000-4000-8000-000000000101',
        'auralink.catalog-read.batch-media-roots[0].revision': 1,
        'auralink.catalog-read.batch-media-roots[0].root': str(ROOT / 'fixture-media')})
    if enabled:
        values['auralink.assistant.enabled'] = 'true'
        values['auralink.assistant.attachment-root'] = str(ROOT / 'attachments')
    path = ROOT / 'private' / (name + '.properties')
    path.write_text('\n'.join(str(key) + '=' + str(value) for key, value in values.items()) + '\n')
    path.chmod(0o600)
    return path


def http(method, path, body=None, token=None, raw=None, content_type=None):
    assert path.startswith('/api/')
    headers = {'Accept': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if body is not None:
        raw = json.dumps(body).encode()
        headers['Content-Type'] = 'application/json'
    elif content_type:
        headers['Content-Type'] = content_type
    req = urllib.request.Request(f'http://127.0.0.1:{APP_PORT}' + path, data=raw, method=method, headers=headers)
    client = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        response = client.open(req, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    payload = response.read()
    return response.status, dict(response.headers), json.loads(payload) if 'json' in response.headers.get('Content-Type', '') else payload


def process_ticks(pid):
    return Path(f'/proc/{pid}/stat').read_text().rsplit(') ', 1)[1].split()[19]


def start_app(jar, database, enabled, name):
    jar = Path(jar).resolve(strict=True)
    assert database in DATABASES
    assert jar.is_relative_to(REPO) or digest(jar) == OLD_SHA
    if not enabled:
        assert not (ROOT / ('forbidden-assistant-' + name)).exists()
    with socket.socket() as port:
        port.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        port.bind(('127.0.0.1', APP_PORT))
    config = app_config(database, enabled, name)
    namespace = ROOT / 'namespace-root' / name
    preview = Path('/root/autodl-tmp/artlive/backend-preview/assistant-foundation')
    mapped = namespace / preview.relative_to('/root')
    for path in [mapped / 'work', mapped / 'release', ROOT / 'java-home']:
        path.mkdir(parents=True, mode=0o700, exist_ok=True)
    copied = mapped / 'release/backend.jar'
    shutil.copyfile(jar, copied)
    assert digest(copied) == digest(jar)
    java = ['/usr/bin/java', '-Duser.home=' + str(ROOT / 'java-home'), '-jar', str(preview / 'release/backend.jar'),
            '--spring.profiles.active=catalog-postgres-production',
            '--spring.config.additional-location=file:' + str(config),
            '--auralink.round51.isolated-preview.enabled=true',
            '--auralink.round51.isolated-preview.root=' + str(preview),
            '--auralink.round51.isolated-preview.working-directory=' + str(preview / 'work')]
    args = ['unshare', '--user', '--map-root-user', '--mount', '--propagation', 'private', 'bash', '-c',
            'mount --bind "$1" /root; shift; cd "$1"; shift; exec "$@"',
            'assistant-owned-java', str(namespace), str(preview / 'work'), *java]
    env = {'PATH': '/usr/bin:/bin', 'LANG': 'C.UTF-8', 'LC_ALL': 'C.UTF-8',
           'AURALINK_ENV_FILE': str(ROOT / 'private/empty.env')}
    logfile = ROOT / 'private' / (name + '.log')
    with logfile.open('ab') as log:
        child = subprocess.Popen(args, cwd=ROOT / 'app-work', env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    record = {'pid': child.pid, 'start_ticks': process_ticks(child.pid), 'jar_sha256': digest(jar),
              'database': database, 'port': APP_PORT, 'assistant_enabled': enabled,
              'guard': 'unmodified guard accepted explicit isolated-preview in private user/mount namespace'}
    private(ROOT / 'private/app-process.json', record)
    for _ in range(120):
        if child.poll() is not None:
            raise RuntimeError(f'Isolated app exited; private log {logfile}')
        try:
            code, _, body = http('GET', '/api/v1/paintings?size=1')
            if code == 200:
                assert body['totalElements'] == 1
                return record
        except (OSError, TimeoutError):
            pass
        time.sleep(.5)
    stop_app(record)
    raise RuntimeError(f'Isolated app startup timeout; private log {logfile}')


def stop_app(record):
    pid = record['pid']
    if not Path(f'/proc/{pid}').exists():
        return
    assert process_ticks(pid) == record['start_ticks'], 'PID identity changed; refusing termination'
    os.killpg(pid, signal.SIGTERM)
    for _ in range(80):
        if not Path(f'/proc/{pid}').exists() or Path(f'/proc/{pid}/stat').read_text().rsplit(') ', 1)[1].startswith('Z'):
            return
        time.sleep(.25)
    raise RuntimeError('Isolated process did not stop gracefully; no broad or forced termination attempted')


def accounts(database):
    cfg = settings()
    result = []
    for suffix in ['alice', 'bob']:
        name = 'assistant_' + suffix
        exists = sql(database, f'SELECT count(*) FROM catalog_users WHERE username={quote(name)}') == '1'
        path = '/api/auth/login' if exists else '/api/auth/register'
        payload = {'username': name, 'password': cfg['account_password']}
        if not exists:
            payload.update({'fullName': 'Isolated ' + suffix, 'email': suffix + '@assistant.example.invalid'})
        code, _, body = http('POST', path, payload)
        assert code == 200, 'Isolated auth failed'
        result.append(body['data']['token'])
    return result


def compatibility(args):
    jar = Path(args.jar).resolve(strict=True)
    if args.kind == 'old-expanded':
        assert digest(jar) == OLD_SHA
        database = args.database or 'assistant_foundation_expanded'
        assert sql(database, "SELECT count(*) FROM prototype_schema_migrations WHERE version='V014'") == '1'
    else:
        database = 'assistant_foundation_old'
        assert sql(database, "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'assistant_%'") == '0'
    before = baseline(database)
    record = start_app(jar, database, False, args.kind)
    results = {}
    try:
        token = accounts(database)[0]
        results['gallery'] = http('GET', '/api/v1/paintings?size=1')[0]
        code, _, image = http('GET', '/api/v1/assets/00000000-0000-4000-8000-000000000301/content', token=token)
        results['media_status'] = code
        results['media_hash_match'] = isinstance(image, bytes) and hashlib.sha256(image).hexdigest() == digest(ROOT / 'fixture-media/synthetic.png')
        assert results['gallery'] == 200 and results['media_status'] == 200 and results['media_hash_match']
        results['assistant_status'] = http('GET', '/api/v1/assistant/sessions', token=token)[0]
        if args.kind == 'new-disabled-old':
            assert results['assistant_status'] == 503
            assert sql(database, "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'assistant_%'") == '0'
        after = baseline(database)
        # Account registration is an intentional isolated fixture operation;
        # original user row and all non-auth fixtures must remain unchanged.
        results['existing_catalog_tasks_preserved'] = all(before[key] == after[key] for key in before if key != 'catalog_users')
        assert results['existing_catalog_tasks_preserved']
        results['existing_user_preserved'] = sql(database, "SELECT password_hash FROM catalog_users WHERE id=901") == 'not-a-login-password'
        assert results['existing_user_preserved']
    finally:
        stop_app(record)
    private(ROOT / 'evidence' / (args.kind + '-' + database + '.json'), {'process': record, 'results': results,
            'applied_migrations': sql(database, 'SELECT version||\':\'||checksum FROM prototype_schema_migrations ORDER BY version').splitlines(),
            'runtime_flags': 'Assistant enabled flag absent (safe default); Flyway false; DDL none; no Assistant attachment directory/token/provider configuration',
            'scope': 'real exact-JAR HTTP + PostgreSQL16 with synthetic catalog/users/nonempty held tasks; not production acceptance'})
    print(json.dumps({'kind': args.kind, 'jar_sha256': digest(jar), 'results': results}))


def final_schema(args):
    """Preserve earlier candidate schema/evidence; clone only the untouched V013 test DB."""
    assert sql('assistant_foundation_old', "SELECT count(*) FROM prototype_schema_migrations WHERE version='V014'") == '0'
    target = args.database
    assert target in ['assistant_foundation_final', 'assistant_foundation_verified']
    assert sql('postgres', f"SELECT count(*) FROM pg_database WHERE datname={quote(target)}") == '0'
    cfg = settings()
    binary = Path(cfg['pg_prefix']) / 'usr/lib/postgresql/16/bin/createdb'
    command([binary, '-h', SOCKET, '-p', cfg['port'], '-U', 'assistant_migrator',
             '--template=assistant_foundation_old', target])
    sql(target, f'GRANT CONNECT ON DATABASE {target} TO assistant_fixture_app,assistant_fixture_test;')
    private(ROOT / 'evidence' / (target + '-before.json'), baseline(target))
    print(json.dumps({'database': target, 'migrations': 13, 'fixture_only': True}))


def verify_migration(args):
    """Executable checksum/refusal proof; tampered bytes are task-private, never applied."""
    database = args.database
    path = Path(args.migration).resolve(strict=True)
    cfg = settings()
    before = baseline(database)
    ledger_before = sql(database, 'SELECT version||\':\'||checksum FROM prototype_schema_migrations ORDER BY version').splitlines()
    expected = [p.name[:4] + ':' + digest(p) for p in sorted(Path(cfg['legacy_migrations']).glob('V*.sql'))]
    assert ledger_before[:13] == expected, 'Historical migration checksum drift'
    assert ledger_before[13:] == ['V014:' + digest(path)]
    migrate(argparse.Namespace(database=database, migration=str(path)))
    conflict = ROOT / 'private' / ('V014__intentional_checksum_conflict_' + uuid.uuid4().hex + '.sql')
    conflict.write_text(path.read_text() + '\n-- intentionally changed isolated checksum probe\n')
    conflict.chmod(0o600)
    refused = False
    try:
        migrate(argparse.Namespace(database=database, migration=str(conflict)))
    except RuntimeError as error:
        assert str(error) == 'V014 recorded checksum differs; refusing changed migration'
        refused = True
    assert refused and baseline(database) == before
    assert sql(database, 'SELECT version||\':\'||checksum FROM prototype_schema_migrations ORDER BY version').splitlines() == ledger_before
    proof = {'migration': str(path), 'sha256': digest(path), 'database': database,
             'historical_v001_v013_checksums_match': True, 'identical_checksum_noop': True,
             'different_checksum_rejected_before_sql': True, 'fixture_hashes_unchanged_by_replay_or_conflict': True,
             'ledger_unchanged': True, 'ledger': ledger_before,
             'execution': 'scripts/test-assistant-foundation.py verify-migration; no production migration tool claimed'}
    private(ROOT / 'evidence/migration-verified-validation.json', proof)
    print(json.dumps({key: value for key, value in proof.items() if key != 'ledger'}))


def preservation(_):
    database = 'assistant_foundation_verified'
    before = json.loads((ROOT / 'evidence' / (database + '-before.json')).read_text())
    after = baseline(database)
    checks = {key: before[key] == after[key] for key in before if key != 'catalog_users'}
    old_user = sql(database, 'SELECT row_to_json(t)::text FROM catalog_users t WHERE id=901 ORDER BY row_to_json(t)::text')
    checks['catalog_original_user'] = before['catalog_users']['sha256'] == hashlib.sha256(old_user.encode()).hexdigest()
    checks['old_schema_has_no_assistant_tables'] = sql('assistant_foundation_old', "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'assistant_%'") == '0'
    checks['fixture_image_original'] = digest(ROOT / 'fixture-media/synthetic.png') == digest(ROOT / 'fixture-media/images/original/synthetic.png')
    assert all(checks.values())
    proof = {'checks': checks, 'added_test_accounts': 2, 'record_scope': 'existing synthetic fixture rows, not production',
             'jar_sha256': digest(REPO / 'target/auralink-backend-0.0.1-SNAPSHOT.jar')}
    private(ROOT / 'evidence/final-preservation.json', proof)
    print(json.dumps(proof))


def pg_control(start):
    cfg = settings()
    data = ROOT / 'postgres/data'
    assert data.resolve().is_relative_to(REPO / 'target') and (data / 'PG_VERSION').read_text().strip() == '16'
    pidfile = data / 'postmaster.pid'
    if pidfile.exists():
        fields = pidfile.read_text().splitlines()
        pid = int(fields[0])
        assert fields[1] == str(data) and int(fields[3]) == cfg['port']
        commandline = Path(f'/proc/{pid}/cmdline').read_bytes().split(b'\0')
        assert str(data).encode() in commandline and str(Path(cfg['pg_prefix']) / 'usr/lib/postgresql/16/bin/postgres').encode() == commandline[0]
        if start:
            print(json.dumps({'already_running': True, 'pid': pid, 'port': cfg['port']}))
            return
    elif not start:
        print(json.dumps({'already_stopped': True, 'data_preserved': True}))
        return
    binary = Path(cfg['pg_prefix']) / 'usr/lib/postgresql/16/bin/pg_ctl'
    if start:
        with socket.socket() as listener:
            listener.bind(('127.0.0.1', cfg['port']))
        command([binary, '-D', data, '-l', ROOT / 'postgres/postgresql.log', '-o',
                 f'-h 127.0.0.1 -p {cfg["port"]} -k {SOCKET}', '-w', 'start'])
    else:
        command([binary, '-D', data, '-w', '-m', 'fast', 'stop'])
    proof = {'operation': 'start' if start else 'stop', 'port': cfg['port'], 'data': str(data),
             'data_preserved': True, 'existing_previews_untouched': True}
    private(ROOT / 'evidence' / ('pg-' + proof['operation'] + '.json'), proof)
    print(json.dumps(proof))


def upload(session, token, key, filename='synthetic.png', image=None):
    boundary = 'assistant-' + uuid.uuid4().hex
    image = png() if image is None else image
    raw = (f'--{boundary}\r\nContent-Disposition: form-data; name="requestKey"\r\n\r\n{key}\r\n'
           f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
           'Content-Type: image/png\r\n\r\n').encode() + image + f'\r\n--{boundary}--\r\n'.encode()
    return http('POST', '/api/v1/assistant/sessions/' + session + '/attachments', token=token,
                raw=raw, content_type='multipart/form-data; boundary=' + boundary)


def seed_history(database, session, branch, attachment):
    """Explicit synthetic persistence fixture. Never an HTTP/mock provider endpoint."""
    ids = {key: str(uuid.uuid4()) for key in ['user', 'answer', 'old_answer', 'run', 'branch']}
    sql(database, f"""
BEGIN;
INSERT INTO assistant_runs(id,session_id,branch_id,kind,status,context_version,created_at,updated_at)
VALUES ('{ids['run']}','{session}','{branch}','CHAT','COMPLETED',1,'2026-01-01','2026-01-01');
INSERT INTO assistant_messages(id,session_id,version_group_id,role,body,state,created_at)
VALUES ('{ids['user']}','{session}','{ids['user']}','USER','=1+1','ACCEPTED','2026-01-01');
INSERT INTO assistant_messages(id,session_id,parent_message_id,version_group_id,role,body,state,run_id,created_at)
VALUES ('{ids['answer']}','{session}','{ids['user']}','{ids['answer']}','ASSISTANT','SYNTHETIC_FIXTURE_NOT_PROVIDER','COMPLETED','{ids['run']}','2026-01-01T00:00:01Z');
INSERT INTO assistant_messages(id,session_id,parent_message_id,version_group_id,role,body,state,created_at)
VALUES ('{ids['old_answer']}','{session}','{ids['user']}','{ids['old_answer']}','ASSISTANT','SUPERSEDED_SYNTHETIC_BRANCH','COMPLETED','2026-01-01T00:00:02Z');
UPDATE assistant_runs SET input_message_id='{ids['user']}' WHERE id='{ids['run']}';
UPDATE assistant_branches SET head_message_id='{ids['answer']}',version=1 WHERE id='{branch}';
INSERT INTO assistant_branches(id,session_id,parent_branch_id,fork_message_id,head_message_id,version,created_at)
VALUES ('{ids['branch']}','{session}','{branch}','{ids['user']}','{ids['old_answer']}',1,'2026-01-01T00:00:02Z');
INSERT INTO assistant_message_attachments(session_id,message_id,attachment_id,position)
VALUES ('{session}','{ids['user']}','{attachment}',0);
COMMIT;
""")
    return ids


def api_round(args):
    database = args.database or 'assistant_foundation_verified'
    assert sql(database, "SELECT count(*) FROM prototype_schema_migrations WHERE version='V014'") == '1'
    record = start_app(args.jar, database, True, 'new-enabled')
    records = [record]
    checks = {}
    try:
        alice, bob = accounts(database)
        prefix = 'http-' + uuid.uuid4().hex
        def expect(method, path, expected, body=None, token=alice):
            code, headers, payload = http(method, path, body, token=token)
            assert code == expected, f'{method} {path} expected {expected}, actual {code}'
            return payload
        status = expect('GET', '/api/v1/assistant/status', 200)
        assert status['enabled'] and not status['executionAvailable']
        expect('GET', '/api/v1/assistant/sessions', 401, token=None)
        first = expect('POST', '/api/v1/assistant/sessions', 200, {'requestKey': prefix, 'title': '合成隔离会话'})
        sid, branch = first['id'], first['activeBranchId']
        replay = expect('POST', '/api/v1/assistant/sessions', 200, {'requestKey': prefix, 'title': '合成隔离会话'})
        assert replay['id'] == sid
        expect('POST', '/api/v1/assistant/sessions', 409, {'requestKey': prefix, 'title': 'conflicting payload'})
        other = expect('POST', '/api/v1/assistant/sessions', 200, {'requestKey': prefix + '-bob', 'title': '另一账号'}, token=bob)
        own_other = expect('POST', '/api/v1/assistant/sessions', 200, {'requestKey': prefix + '-other', 'title': '同账号不同会话'})
        checks['session_idempotency_replay_conflict'] = True
        expect('PATCH', '/api/v1/assistant/sessions/' + sid, 404,
               {'expectedVersion': first['version'], 'activeBranchId': own_other['activeBranchId']})
        expect('GET', '/api/v1/assistant/sessions/' + sid + '/messages?branchId=' + own_other['activeBranchId'], 404)
        checks['same_owner_cross_session_branch_rejected'] = True
        code, _, attachment = upload(sid, alice, prefix + '-attachment')
        assert code == 200, f'upload status={code}'
        aid = attachment['id']
        code, _, replay = upload(sid, alice, prefix + '-attachment')
        assert code == 200 and replay['id'] == aid
        assert upload(sid, alice, prefix + '-attachment', 'changed.png')[0] == 409
        assert upload(sid, bob, prefix + '-foreign')[0] == 404
        assert upload(sid, alice, prefix + '-invalid', image=b'not an image')[0] == 400
        checks['attachment_validation_idempotency'] = True
        routes = [f'/sessions/{sid}', f'/sessions/{sid}/branches', f'/sessions/{sid}/messages',
                  f'/sessions/{sid}/export?format=json', f'/sessions/{sid}/export?format=csv',
                  f'/attachments/{aid}', f'/attachments/{aid}/content']
        for route in routes:
            expect('GET', '/api/v1/assistant' + route, 404, token=bob)
        expect('PATCH', '/api/v1/assistant/sessions/' + sid, 404, {'expectedVersion': 0, 'title': 'blocked'}, token=bob)
        expect('DELETE', '/api/v1/assistant/sessions/' + sid, 404, token=bob)
        expect('DELETE', '/api/v1/assistant/attachments/' + aid, 404, token=bob)
        expect('GET', '/api/v1/assistant/attachments/' + aid + '/content', 401, token=None)
        checks['two_account_read_edit_export_delete_image_isolation'] = True
        fixture = seed_history(database, sid, branch, aid)
        current = expect('GET', f'/api/v1/assistant/sessions/{sid}/messages', 200)
        history = expect('GET', f'/api/v1/assistant/sessions/{sid}/messages?includeHistory=true', 200)
        assert len(current['messages']) == 2 and len(history['messages']) == 3
        exported = expect('GET', f'/api/v1/assistant/sessions/{sid}/export', 200)
        assert len(exported['messages']) == 2
        csv = expect('GET', f'/api/v1/assistant/sessions/{sid}/export?format=csv', 200)
        assert b"'=1+1" in csv and b'SUPERSEDED_SYNTHETIC_BRANCH' not in csv
        expect('DELETE', '/api/v1/assistant/attachments/' + aid, 409)
        checks['explicit_fixture_current_branch_history_csv_safety'] = True
        code, headers, image = http('GET', '/api/v1/assistant/attachments/' + aid + '/content', token=alice)
        assert code == 200 and headers['Content-Type'] == 'image/png' and 'no-store' in headers['Cache-Control']
        assert hashlib.sha256(image).hexdigest() == digest(ROOT / 'fixture-media/synthetic.png')
        assert 'storageKey' not in json.dumps(attachment) and str(ROOT) not in json.dumps(exported)
        checks['private_image_bytes_headers_and_no_internal_paths'] = True
        stop_app(record)
        record = start_app(args.jar, database, True, 'new-enabled-restart')
        records.append(record)
        alice2, _ = accounts(database)
        restored = expect('GET', f'/api/v1/assistant/sessions/{sid}/messages', 200, token=alice2)
        assert [m['id'] for m in restored['messages']] == [m['id'] for m in current['messages']]
        code, _, image = http('GET', '/api/v1/assistant/attachments/' + aid + '/content', token=alice2)
        assert code == 200 and hashlib.sha256(image).hexdigest() == digest(ROOT / 'fixture-media/synthetic.png')
        checks['actual_process_restart_second_authenticated_client_history_image'] = True
        expect('DELETE', '/api/v1/assistant/sessions/' + sid, 204, token=alice2)
        for route in routes:
            expect('GET', '/api/v1/assistant' + route, 404, token=alice2)
        expect('POST', '/api/v1/assistant/sessions', 410, {'requestKey': prefix, 'title': '合成隔离会话'}, token=alice2)
        expect('POST', '/api/v1/assistant/sessions', 409, {'requestKey': prefix, 'title': 'different'}, token=alice2)
        assert upload(sid, alice2, prefix + '-attachment')[0] == 410
        assert upload(sid, alice2, prefix + '-attachment', 'changed.png')[0] == 409
        assert upload(sid, alice2, prefix + '-after-delete-new')[0] == 404
        checks['deletion_immediate_revocation_key_tombstone'] = True
        assert sql(database, f"SELECT count(*) FROM assistant_messages WHERE session_id='{sid}' AND body<>''") == '0'
        checks['deleted_message_content_scrubbed'] = True
        checks['gallery_after'] = http('GET', '/api/v1/paintings?size=1')[0] == 200
        code, _, image = http('GET', '/api/v1/assets/00000000-0000-4000-8000-000000000301/content', token=alice2)
        checks['existing_media_after'] = code == 200 and hashlib.sha256(image).hexdigest() == digest(ROOT / 'fixture-media/synthetic.png')
        assert checks['gallery_after'] and checks['existing_media_after']
    finally:
        stop_app(record)
        private(ROOT / 'evidence/http-foundation.json', {'jar_sha256': digest(args.jar), 'processes': records, 'checks': checks,
                'scope': 'real loopback HTTP, fresh PostgreSQL16 and synthetic account/login/image/history fixture; no provider dispatcher or production access',
                'history_source': 'explicit SQL fixture, not simulated model responses on a production endpoint'})
    print(json.dumps({'jar_sha256': digest(args.jar), 'checks': checks}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='mode', required=True)
    init = sub.add_parser('setup')
    init.add_argument('--resume-initialized', action='store_true', help='Only resume a never-started initialized cluster after a setup failure')
    init.add_argument('--pg-prefix', required=True)
    init.add_argument('--legacy-migrations', required=True)
    migration = sub.add_parser('migrate')
    migration.add_argument('--migration', required=True)
    migration.add_argument('--database', choices=['assistant_foundation_verified', 'assistant_foundation_final', 'assistant_foundation_expanded', 'assistant_foundation_test'], default='assistant_foundation_final')
    sub.add_parser('status')
    compat = sub.add_parser('compatibility')
    compat.add_argument('--jar', required=True)
    compat.add_argument('--kind', choices=['old-expanded', 'new-disabled-old'], required=True)
    compat.add_argument('--database', choices=DATABASES)
    clone = sub.add_parser('final-schema')
    clone.add_argument('--database', choices=['assistant_foundation_final', 'assistant_foundation_verified'], default='assistant_foundation_verified')
    verify = sub.add_parser('verify-migration')
    verify.add_argument('--database', choices=['assistant_foundation_verified', 'assistant_foundation_final'], default='assistant_foundation_verified')
    verify.add_argument('--migration', default=str(REPO / 'db/postgres/migrations/V014__assistant_records.sql'))
    sub.add_parser('preservation')
    sub.add_parser('pg-stop')
    sub.add_parser('pg-start')
    api = sub.add_parser('api')
    api.add_argument('--jar', required=True)
    api.add_argument('--database', choices=DATABASES)
    args = parser.parse_args()
    {'setup': setup, 'migrate': migrate, 'status': status, 'compatibility': compatibility,
     'final-schema': final_schema, 'verify-migration': verify_migration, 'preservation': preservation,
     'pg-stop': lambda _: pg_control(False), 'pg-start': lambda _: pg_control(True), 'api': api_round}[args.mode](args)


if __name__ == '__main__':
    main()
