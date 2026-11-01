"""Validate by default; --apply updates only this project's collector processor and dedicated dashboard.

No application deployment, business DB access, datasource mutation, log deletion or image pull.
Run as root on the existing host using a locally prepared, reviewed bundle.
"""
import argparse,base64,datetime,hashlib,io,json,os,re,sqlite3,stat,subprocess,sys,time,types,urllib.parse,urllib.request,uuid,zipfile
try:
    import fcntl
except ModuleNotFoundError:
    fcntl=None  # File helpers can be tested on Windows; execution remains Linux-only.
from pathlib import Path
from contextlib import closing
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'deployment'))
from common import ROOT,trusted,command,fetch

CONFIG=Path('/opt/alpha/observability/loki/config.alloy')
PROVIDER=Path('/etc/grafana/provisioning/dashboards/voice-coaching-request-logs.yml')
DASHBOARD=Path('/etc/alpha-observability/dashboards/backend-requests.json')
DB=Path('/var/lib/grafana/grafana.db')
UID='vc-be-request-logs'

def require(value,code):
    if not value:raise RuntimeError(code)
def sha(data):return hashlib.sha256(data).hexdigest()
def writable_wal_disk(metrics):
    match=re.search(r'^loki_ingester_wal_disk_usage_percent\s+(\S+)',metrics,re.MULTILINE)
    require(match is not None,'LOKI_WAL_DISK_METRIC_REQUIRED')
    usage=float(match.group(1))
    require(0<=usage<0.90,'LOKI_WAL_DISK_PRESSURE_REQUIRES_SPACE')
    return usage
def run(args,**kwargs):
    result=subprocess.run(args,capture_output=True,timeout=45,**kwargs)
    require(result.returncode==0,'OBSERVABILITY_COMMAND_FAILED')
    return result.stdout

def reload_collector():
    # Signal delivery returns before reload completes. Wait for the acknowledged HTTP reload
    # inside the existing container network namespace before sending a verification log.
    inspection=json.loads(run(['docker','inspect','alpha-alloy']))
    pid=inspection[0]['State']['Pid']
    require(isinstance(pid,int) and pid>1,'ALLOY_PROCESS_UNAVAILABLE')
    prefix=['nsenter','-t',str(pid),'-n','curl','--fail','--silent','--show-error','--max-time','30']
    run(prefix+['-X','POST','http://127.0.0.1:12345/-/reload'])
    run(prefix+['http://127.0.0.1:12345/-/ready'])
def db_query(sql,args=()):
    with closing(sqlite3.connect(DB.resolve().as_uri()+'?mode=ro',uri=True,timeout=5)) as db:
        db.execute('PRAGMA query_only=ON')
        return db.execute(sql,args).fetchall()

def has_resources():
    return bool(db_query("SELECT name FROM sqlite_master WHERE type='table' AND name='resource'"))

def dashboard_document():
    legacy=db_query('SELECT data FROM dashboard WHERE uid=? AND org_id=1',(UID,))
    if legacy:return json.loads(legacy[0][0])
    if has_resources():
        rows=db_query('SELECT value FROM resource WHERE "group"=? AND resource=? AND namespace=? AND name=?',
            ('dashboard.grafana.app','dashboards','default',UID))
        if rows:return json.loads(rows[0][0]).get('spec',{})
    return None

def panel_count(doc):
    if 'panels' in doc:return len(doc['panels'])
    return sum(item.get('kind')=='Panel' for item in doc.get('elements',{}).values())
def metadata():
    dashboards=[]
    for uid,title,data in db_query("SELECT uid,title,data FROM dashboard WHERE org_id=1 AND uid NOT IN (?,?)",(UID,'vc-observability')):
        doc=json.loads(data)
        for key in ('id','version','iteration'):doc.pop(key,None)
        dashboards.append((uid,title,json.dumps(doc,sort_keys=True,separators=(',',':'))))
    sources=db_query('SELECT uid,name,type FROM data_source WHERE org_id=1 ORDER BY uid')
    if has_resources():
        for group,resource,namespace,name,value in db_query('SELECT "group",resource,namespace,name,value FROM resource WHERE "group" IN (?,?) AND namespace=?',
                ('dashboard.grafana.app','folder.grafana.app','default')):
            if name in (UID,'vc-observability'):continue
            spec=json.loads(value).get('spec',{})
            dashboards.append((group+'/'+resource+'/'+namespace+'/'+name,'resource',json.dumps(spec,sort_keys=True,separators=(',',':'))))
    return sha(json.dumps(sorted(dashboards)).encode()),sha(json.dumps(sources).encode())
def read_fd(path,write=False,append=False):
    # Walk directory descriptors without following symlinks, including the service-owned /opt/alpha parent.
    directory=os.open('/',os.O_RDONLY|os.O_DIRECTORY)
    try:
        for part in path.parts[1:-1]:
            following=os.open(part,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW,dir_fd=directory)
            os.close(directory);directory=following
        descriptor=os.open(path.name,((os.O_WRONLY|os.O_APPEND) if append else os.O_RDWR if write else os.O_RDONLY)|os.O_NOFOLLOW,dir_fd=directory)
    finally:os.close(directory)
    info=os.fstat(descriptor)
    require(stat.S_ISREG(info.st_mode) and info.st_nlink==1,'UNSAFE_OBSERVABILITY_FILE')
    if write:require(info.st_uid==0 and not info.st_mode&0o022,'UNTRUSTED_COLLECTOR_FILE')
    return descriptor
def replace_contents(descriptor,data):
    os.lseek(descriptor,0,os.SEEK_SET)
    position=0
    while position<len(data):position+=os.write(descriptor,data[position:])
    os.ftruncate(descriptor,len(data));os.fsync(descriptor)
def write_new(path,data):
    path.parent.mkdir(parents=True,exist_ok=True,mode=0o755)
    trusted(path.parent)
    temporary=path.with_name('.'+path.name+'.'+uuid.uuid4().hex+'.tmp')
    fd=os.open(temporary,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o644)
    try:
        offset=0
        while offset<len(data):offset+=os.write(fd,data[offset:])
        os.fsync(fd)
        os.link(temporary,path,follow_symlinks=False)
    finally:
        os.close(fd)
        temporary.unlink()
def loki(query,instant=False):
    args={'query':query}
    if not instant:args.update(start=str(time.time_ns()-600_000_000_000),end=str(time.time_ns()),limit='5')
    url='http://127.0.0.1:3100/loki/api/v1/'+('query' if instant else 'query_range')+'?'+urllib.parse.urlencode(args)
    with urllib.request.urlopen(url,timeout=15) as reply:doc=json.load(reply)
    require(doc.get('status')=='success','LOKI_QUERY_FAILED')
    return doc.get('data',{}).get('result',[])
def append_validation(data):
    fd=read_fd(Path('/var/log/alpha-backend/application.log'),append=True)
    try:require(os.write(fd,data)==len(data),'VALIDATION_LOG_WRITE_FAILED')
    finally:os.close(fd)
def acceptance():
    marker=str(uuid.uuid4())
    now=datetime.datetime.now(datetime.timezone.utc).isoformat()
    line=json.dumps({'@timestamp':now,'level':'INFO','service_name':'alpha-backend','event':'observability_pipeline_test',
        'request_id':marker,'route':'/api/observability-validation','http_method':'GET','http_status':200,'duration_ms':7,
        'error_code':'OBSERVABILITY_TEST','message':'Synthetic observability pipeline validation'},separators=(',',':'))
    append_validation((line+'\n'+now+' WARN observability_pipeline_legacy_test '+marker+'\n').encode())
    # Existing loki.write batches may already be in a five-minute retry backoff after disk pressure.
    for attempt in range(180):
        structured=loki('{service_name="alpha-backend",environment="prod"} | request_id="'+marker+'" | event="observability_pipeline_test" | http_status=200')
        legacy=loki('{service_name="alpha-backend",environment="prod",level="WARN"} |= "observability_pipeline_legacy_test '+marker+'"')
        if structured and legacy:
            args={'match[]':'{service_name="alpha-backend",environment="prod"}','start':str(time.time_ns()-300_000_000_000)}
            with urllib.request.urlopen('http://127.0.0.1:3100/loki/api/v1/series?'+urllib.parse.urlencode(args),timeout=10) as reply:
                series=json.load(reply).get('data',[])
            require(all(not ({'request_id','analysis_id','execution_id'} & set(item)) for item in series),'REQUEST_ID_BECAME_INDEX_LABEL')
            return {'structured_metadata_filter':True,'legacy_level_compatible':True,'validation_entries':2}
        time.sleep(2)
    raise RuntimeError('COLLECTOR_ACCEPTANCE_TIMEOUT')

def execute(archive,apply):
    require(sys.platform=='linux' and os.geteuid()==0,'ROOT_LINUX_OPERATOR_REQUIRED')
    module=types.ModuleType('observability_configuration')
    exec(compile(archive.read('configuration.py'),'observability_configuration','exec'),module.__dict__)
    request=json.loads(archive.read('request.json'))
    with trusted(ROOT/'deployment.lock').open('r') as lock:
        fcntl.flock(lock.fileno(),(fcntl.LOCK_EX if apply else fcntl.LOCK_SH)|fcntl.LOCK_NB)
        current=json.loads(trusted(ROOT/'current.json').read_text())
        require(current['releaseId']==request['expected_release'],'LIVE_RELEASE_CHANGED_REVALIDATE')
        descriptor=read_fd(CONFIG,write=apply)
        try:
            original=os.read(descriptor,512001);require(len(original)<=512000,'CONFIG_TOO_LARGE')
            require(not request.get('expected_config_sha256') or sha(original)==request['expected_config_sha256'],'COLLECTOR_CHANGED_REVALIDATE')
            updated=module.replace_backend_processor(original.decode(),archive.read('backend-process.alloy').decode()).encode()
            run(['docker','exec','-i','alpha-alloy','/bin/alloy','validate','/dev/stdin'],input=updated)
            sources=db_query("SELECT uid FROM data_source WHERE type='loki' AND org_id=1")
            require(len(sources)==1,'EXPECTED_ONE_EXISTING_LOKI_DATASOURCE')
            doc=module.dashboard(sources[0][0])
            for kind,query in module.concrete_queries(doc):loki(query,kind=='stat')
            with urllib.request.urlopen('http://127.0.0.1:3100/metrics',timeout=10) as reply:
                disk_usage=writable_wal_disk(reply.read(4*1024*1024).decode())
            before=metadata()
            pid=command(['systemctl','show','alpha-backend.service','-p','MainPID','--value']).decode().strip()
            require(json.loads(fetch('http://127.0.0.1:3000/api/health'))['database']=='ok','GRAFANA_NOT_HEALTHY')
            report={'scope':'structured_logging_observability','apply':apply,'release':current['releaseId'],
                'config_before_sha256':sha(original),'config_after_sha256':sha(updated),'datasource_uid':sources[0][0],
                'dashboard_uid':UID,'alloy_validation':True,'loki_query_validation':True,'backend_restarted':False,'business_data_written':False}
            report['wal_disk_usage_fraction']=disk_usage
            if not apply:return report
            provider=module.dashboard_provider().encode();dashboard=json.dumps(doc,ensure_ascii=False,indent=2).encode()
            if PROVIDER.exists() or DASHBOARD.exists():
                require(PROVIDER.is_file() and DASHBOARD.is_file() and PROVIDER.read_bytes()==provider and DASHBOARD.read_bytes()==dashboard,'MANAGED_DASHBOARD_DIFFERENT_REVIEW_REQUIRED')
                require(original==updated,'MANAGED_STATE_PARTIAL_REVIEW_REQUIRED')
                report.update(already_applied=True,acceptance=acceptance())
                return report
            require(dashboard_document() is None,'DASHBOARD_UID_ALREADY_EXISTS')
            backup=Path('/var/backups/alpha-observability')/('issue162-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+uuid.uuid4().hex[:8])
            backup.mkdir(parents=True,mode=0o700)
            fd=os.open(backup/'config.alloy',os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
            try:os.write(fd,original);os.fsync(fd)
            finally:os.close(fd)
            created=[];grafana_touched=False;collector_touched=False
            try:
                # Individual Docker bind mounts retain their inode: update in place, never os.replace().
                collector_touched=True;replace_contents(descriptor,updated)
                reload_collector()
                report['acceptance']=acceptance()
                write_new(DASHBOARD,dashboard);created.append(DASHBOARD)
                write_new(PROVIDER,provider);created.append(PROVIDER)
                grafana_touched=True;run(['systemctl','restart','grafana-server.service'])
                for attempt in range(25):
                    try:
                        health=json.loads(fetch('http://127.0.0.1:3000/api/health'))['database']=='ok'
                        found=dashboard_document()
                        if health and found is not None and panel_count(found)==len(doc['panels']):break
                    except Exception:pass
                    time.sleep(2)
                else:raise RuntimeError('DASHBOARD_PROVISIONING_FAILED')
                require(metadata()==before,'EXISTING_GRAFANA_OBJECTS_CHANGED')
                require(command(['systemctl','show','alpha-backend.service','-p','MainPID','--value']).decode().strip()==pid,'BACKEND_PROCESS_CHANGED')
                require(json.loads(fetch('http://127.0.0.1:9091/internal/actuator/health'))['status']=='UP','BACKEND_HEALTH_FAILED')
                report.update(dashboard_provisioned=True,existing_dashboards_and_datasources_preserved=True,backend_process_unchanged=True,backup=str(backup))
                (backup/'result.json').write_text(json.dumps(report,indent=2));os.chmod(backup/'result.json',0o600)
                return report
            except Exception:
                for path in reversed(created):
                    require(path in (PROVIDER,DASHBOARD) and not path.is_symlink(),'UNSAFE_ROLLBACK_PATH')
                    path.unlink()
                if collector_touched:
                    replace_contents(descriptor,original);reload_collector()
                if grafana_touched:run(['systemctl','restart','grafana-server.service'])
                raise
        finally:os.close(descriptor)

def main():
    if 'BUNDLE_B64' in globals():
        archive=zipfile.ZipFile(io.BytesIO(base64.b64decode(BUNDLE_B64)))
        apply=json.loads(archive.read('request.json')).get('apply',False)
    else:
        parser=argparse.ArgumentParser();parser.add_argument('--bundle',required=True);parser.add_argument('--apply',action='store_true')
        args=parser.parse_args();archive=zipfile.ZipFile(args.bundle);apply=args.apply
    print(json.dumps(execute(archive,apply)))

if __name__=='__main__':main()
