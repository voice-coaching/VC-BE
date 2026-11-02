"""Pure observability configuration transformations, with no server IO or credentials."""
import json
import re

MARKER='loki.process "backend"'
def replace_backend_processor(original,fragment):
    if len(re.findall(re.escape(MARKER)+r'\s*\{',original))!=1:
        raise ValueError('EXPECTED_ONE_BACKEND_PROCESSOR')
    if fragment.count(MARKER)!=1 or 'request_id = ""' not in fragment:
        raise ValueError('INVALID_PROCESSOR_FRAGMENT')
    fragment=fragment[fragment.index(MARKER):].strip()
    start=original.index(MARKER)
    opening=original.index('{',start)
    depth=0;quote=None;escaped=False
    for index in range(opening,len(original)):
        char=original[index]
        if quote:
            if quote!='`' and escaped:escaped=False
            elif quote!='`' and char=='\\':escaped=True
            elif char==quote:quote=None
            continue
        if char in ('"',"'",'`'):quote=char
        elif char=='{':depth+=1
        elif char=='}':
            depth-=1
            if depth==0:return original[:start]+fragment.strip()+original[index+1:]
    raise ValueError('UNBALANCED_BACKEND_PROCESSOR')

def dashboard(datasource_uid):
    if not re.fullmatch(r'[A-Za-z0-9_-]{1,64}',datasource_uid):raise ValueError('INVALID_DATASOURCE_UID')
    source={'type':'loki','uid':datasource_uid}
    selector='{service_name="alpha-backend",environment="prod"}'
    requests=selector+' | event="http_request_completed" | route=~${route:doublequote} | request_id=~${request_id:doublequote}'
    doc={'id':None,'uid':'vc-be-request-logs','title':'VC Backend · Requests and Analysis Logs','tags':['voice-coaching','backend','logs'],
         'timezone':'browser','schemaVersion':41,'version':1,'refresh':'30s','editable':False,
         'time':{'from':'now-1h','to':'now'},'panels':[],
         'templating':{'list':[{'name':name,'label':label,'type':'textbox','current':{'text':'.*','value':'.*'},'options':[{'text':'.*','value':'.*','selected':True}],
             'description':'정확한 값 또는 정규식. .* 는 전체.'} for name,label in [('route','API route'),('request_id','Request ID')]]},
         'links':[{'title':'Logs Drilldown','url':'/a/grafana-lokiexplore-app','type':'link','targetBlank':True}]}
    def panel(title,kind,query,x,y,w,h):
        item={'id':len(doc['panels'])+1,'title':title,'type':kind,'datasource':source,'gridPos':{'x':x,'y':y,'w':w,'h':h},
              'targets':[{'refId':'A','datasource':source,'expr':query,'queryType':'instant' if kind=='stat' else 'range','editorMode':'code'}]}
        if kind=='logs':item['options']={'showTime':True,'showLabels':False,'wrapLogMessage':True,'prettifyLogMessage':True,'enableLogDetails':True,'sortOrder':'Descending','dedupStrategy':'none'}
        doc['panels'].append(item)
    panel('요청 수','stat','sum(count_over_time('+requests+' | keep http_status [$__range]))',0,0,8,4)
    panel('서버 오류 (5xx)','stat','sum(count_over_time('+requests+' | http_status >= 500 | keep http_status [$__range]))',8,0,8,4)
    panel('느린 요청 (1초 이상)','stat','sum(count_over_time('+requests+' | duration_ms >= 1000 | keep http_status [$__range]))',16,0,8,4)
    panel('요청 · route/status/duration/request ID','logs',requests+' | line_format "{{.http_method}} {{.route}} status={{.http_status}} duration={{.duration_ms}}ms request_id={{.request_id}} code={{.error_code}}"',0,4,24,10)
    panel('서버 및 분석 실패 · 안전한 예외 위치','logs',selector+' | event=~"api_failed|analysis_failed|analysis_dispatch_failed"',0,14,12,10)
    panel('코칭 소진·기간 만료','logs',selector+' | event="http_request_completed" | error_code=~"COACHING_QUOTA_EXCEEDED|COACHING_PERIOD_EXPIRED"',12,14,12,10)
    panel('AI 분석 흐름 · analysis/execution ID','logs',selector+' | event=~"analysis_.*"',0,24,24,10)
    panel('기존 문자열 로그 포함 전체 백엔드 로그','logs',selector,0,34,24,10)
    return doc

def dashboard_provider():
    return '''apiVersion: 1
providers:
  - name: voice-coaching-request-logs
    orgId: 1
    folder: Voice Coaching
    folderUid: vc-observability
    type: file
    disableDeletion: true
    allowUiUpdates: false
    updateIntervalSeconds: 30
    options:
      path: /etc/alpha-observability/dashboards
'''

def concrete_queries(doc):
    for panel in doc['panels']:
        for target in panel.get('targets',[]):
            query=target['expr'].replace('${route:doublequote}','".*"').replace('${request_id:doublequote}','".*"').replace('$__range','15m')
            yield panel['type'],query

if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser();parser.add_argument('--datasource-uid',required=True)
    args=parser.parse_args();print(json.dumps(dashboard(args.datasource_uid),ensure_ascii=False,indent=2))
