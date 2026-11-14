"""
Copies the RailLens database between two Neon databases over Neon's HTTPS SQL API (no psql or open port 5432 needed).
Used for the Sydney -> Singapore move on 13-14 Nov 2026.

  SRC_DATABASE_URL=postgresql://...  DST_DATABASE_URL=postgresql://...  python3 scripts/neon_copy.py schema|data|verify

schema: replays db/migration on the EMPTY destination and copies Flyway's history, so the app sees it as migrated.
data:   copies every table in foreign-key order keeping ids, then moves the id sequences past the copied rows.
reset:  empties the DESTINATION (drops its public schema) for a fresh copy. Needs CONFIRM_RESET=destination and
        refuses when both URLs point at the same host.
verify: compares row counts and an md5 of every row, table by table.
Credentials only come from the environment; never put them in this file.
"""
import sys, re, glob, os, json, urllib.request, urllib.parse
def _conn(which):
    s=os.environ['SRC_DATABASE_URL' if which=='src' else 'DST_DATABASE_URL'].strip()
    return s, urllib.parse.urlparse(s).hostname
def _sql(which, query, params=None, batch=None):
    cs, host = _conn(which)
    body = {"queries":[{"query":q,"params":p or []} for q,p in batch]} if batch else {"query":query,"params":params or []}
    req = urllib.request.Request(f"https://{host}/sql", data=json.dumps(body).encode(), method="POST",
        headers={"Neon-Connection-String":cs,"Content-Type":"application/json","Neon-Array-Mode":"true"})
    try:
        with urllib.request.urlopen(req, timeout=120) as r: return json.load(r)
    except urllib.error.HTTPError as e:
        raise RuntimeError(e.read().decode()[:500])

class neon:
    sql=staticmethod(_sql)

MIG=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','src','main','resources','db','migration')
TABLES=['stations','trains','train_schedule','users','refresh_tokens','security_audit']  # FK order

def split(sqltext):
    out, buf, dollar = [], [], False
    for line in sqltext.splitlines():
        if line.strip().startswith('--') and not dollar: continue
        buf.append(line)
        if line.count('$$') % 2: dollar = not dollar
        if not dollar and line.rstrip().endswith(';'):
            s='\n'.join(buf).strip().rstrip(';').strip()
            if s: out.append(s)
            buf=[]
    s='\n'.join(buf).strip().rstrip(';').strip()
    if s: out.append(s)
    return out

def schema():
    files=sorted(glob.glob(MIG+'/V*__*.sql'), key=lambda f:int(re.match(r'V(\d+)__',os.path.basename(f)).group(1)))
    for f in files:
        stmts=split(open(f).read())
        neon.sql('dst',None,batch=[(s,[]) for s in stmts]); print('applied',os.path.basename(f),len(stmts),'statements')
    neon.sql('dst',None,batch=[('''CREATE TABLE "flyway_schema_history" ("installed_rank" INT NOT NULL, "version" VARCHAR(50),
        "description" VARCHAR(200) NOT NULL, "type" VARCHAR(20) NOT NULL, "script" VARCHAR(1000) NOT NULL, "checksum" INT,
        "installed_by" VARCHAR(100) NOT NULL, "installed_on" TIMESTAMP NOT NULL DEFAULT now(), "execution_time" INT NOT NULL,
        "success" BOOLEAN NOT NULL, CONSTRAINT "flyway_schema_history_pk" PRIMARY KEY ("installed_rank"))''',[]),
        ('CREATE INDEX "flyway_schema_history_s_idx" ON "flyway_schema_history" ("success")',[])])
    copy_table('flyway_schema_history','installed_rank')

def cols(which,table):
    r=neon.sql(which,"select column_name, format_type(a.atttypid,a.atttypmod) from information_schema.columns c join pg_attribute a on a.attrelid=('public.'||c.table_name)::regclass and a.attname=c.column_name where table_schema='public' and table_name=$1 order by ordinal_position",[table])
    return r['rows']

def copy_table(t,key='id',chunk=4000):
    cs=cols('src',t); names=[c for c,_ in cs]; types=[ty for _,ty in cs]
    assert [c for c,_ in cols('dst',t)]==names, (t,'column mismatch')
    ident = 'OVERRIDING SYSTEM VALUE ' if t not in ('flyway_schema_history','security_audit') else ''
    last=None; total=0
    while True:
        where=f'where x."{key}" > $1' if last is not None else ''
        sel=', '.join(f'"{n}"::text' for n in names)
        r=neon.sql("src",f"select {sel} from \"{t}\" x {where} order by x.\"{key}\" limit {chunk}",[last] if last is not None else [])
        rows=r['rows']
        if not rows: break
        params=[]; vals=[]
        for row in rows:
            ph=[]
            for v,ty in zip(row,types):
                params.append(v); ph.append(f'${len(params)}::{ty}')
            vals.append('('+','.join(ph)+')')
        neon.sql('dst',f'insert into "{t}" ({", ".join(chr(34)+n+chr(34) for n in names)}) {ident}values '+','.join(vals),params)
        total+=len(rows); last=rows[-1][names.index(key)]
        if len(rows)<chunk: break
    print('copied',t,total)

def data():
    for t in TABLES: copy_table(t)
    for t in TABLES:   # identity/serial sequences continue after the copied ids
        neon.sql('dst',f"select setval(pg_get_serial_sequence('{t}','id'), coalesce((select max(id) from \"{t}\"),1), (select count(*)>0 from \"{t}\"))")
    print('sequences set')

def verify():
    ok=True
    for t in TABLES+['flyway_schema_history']:
        q=f'select count(*), md5(string_agg(md5(x::text), \'\' order by x::text)) from "{t}" x'
        a=neon.sql('src',q)['rows'][0]; b=neon.sql('dst',q)['rows'][0]
        print(t, a[0], 'match' if a==b else f'MISMATCH {b[0]}'); ok&= a==b
    print('ALL MATCH' if ok else 'DIFFERENCES')

def reset():
    if os.environ.get('CONFIRM_RESET')!='destination':
        sys.exit('refusing: set CONFIRM_RESET=destination to empty the destination database')
    if _conn('src')[1].replace('-pooler','')==_conn('dst')[1].replace('-pooler',''):
        sys.exit('refusing: source and destination are the same host')
    neon.sql('dst',None,batch=[('drop schema public cascade',[]),('create schema public',[])])
    print('destination emptied')

{'schema':schema,'data':data,'verify':verify,'reset':reset}[sys.argv[1]]()
