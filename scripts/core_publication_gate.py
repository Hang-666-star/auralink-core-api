#!/usr/bin/env python3
"""Fail-closed retired direct publisher; build artifacts may be staged only.

This entry replaces the direct installer, never calls its historical fallback,
and never starts Java. Automatic publication remains paused. A separately
approved R25 stage is the only manual production publication interface.
"""
from __future__ import annotations
import argparse, hashlib, json, os, re, shutil, stat, subprocess, sys, time
from pathlib import Path

ROOT=Path('/root/autodl-tmp/auralink-core-deploy')
R25=Path('/root/autodl-tmp/artlive-r25-native-pg16/handoff-production-20260923T062101Z')
POLICY=b'ARTLIVE_CORE_PRODUCTION_PUBLICATION=paused-r25-only\n'
class Refused(RuntimeError):pass
def must(value,reason):
    if not value:raise Refused(reason)
def sha(path):
    value=hashlib.sha256()
    with Path(path).open('rb') as source:
        for block in iter(lambda:source.read(1048576),b''):value.update(block)
    return value.hexdigest()
def check(path,mode=0o600,directory=False):
    path=Path(path);must(path.is_absolute() and '..' not in path.parts,'unsafe_path')
    for part in [path,*path.parents]:must(not part.is_symlink(),'symlink_path')
    value=path.lstat();must((stat.S_ISDIR if directory else stat.S_ISREG)(value.st_mode)
        and (value.st_uid,value.st_gid,stat.S_IMODE(value.st_mode))==(0,0,mode),'object_identity')
def strict_json(raw):
    def pairs(items):
        result={}
        for key,value in items:must(key not in result,'duplicate_json_key');result[key]=value
        return result
    return json.loads(raw,object_pairs_hook=pairs,parse_constant=lambda _:(_ for _ in ()).throw(Refused('nonfinite_json')))
def paused_policy(root=ROOT):
    path=root/'runtime/production-publication-policy.env';check(path)
    must(path.read_bytes()==POLICY,'production_publication_policy_invalid')
def stage_artifact(commit,artifact,expected,root=ROOT):
    # Neither builds nor this copy operation consume application credentials.
    # Only caller-built bytes inside a dedicated incoming tree may be staged.
    paused_policy(root);must(re.fullmatch('[0-9a-f]{40}',commit) is not None,'commit_identity')
    must(re.fullmatch('[0-9a-f]{64}',expected) is not None,'artifact_hash')
    artifact=Path(artifact);incoming=root/'incoming';check(incoming,0o700,True)
    must(artifact.parent==incoming,'artifact_must_be_in_isolated_incoming')
    check(artifact);must(sha(artifact)==expected,'artifact_identity')
    staging=root/'staging';check(staging,0o700,True)
    target=staging/(commit+'-'+expected+'.jar')
    if target.exists():check(target);must(sha(target)==expected,'staged_identity_conflict');return {'outcome':'STAGED_NOOP','artifact':str(target),'sha256':expected}
    with target.open('xb') as destination,artifact.open('rb') as source:
        shutil.copyfileobj(source,destination,1048576);destination.flush();os.fchmod(destination.fileno(),0o600);os.fsync(destination.fileno())
    check(target);must(sha(target)==expected,'staged_identity_conflict')
    return {'outcome':'STAGED_ONLY','artifact':str(target),'sha256':expected,'production_publication':'PAUSED'}
def delegated_activate(stage,contract,root=ROOT,r25=R25):
    paused_policy(root)
    # The private contract is installed with the independently accepted R25
    # deployment revision. It binds one exact stage and its entry/inventory.
    contract=Path(contract);must(contract==root/'runtime/r25-publication-contract.json','contract_path');check(contract)
    binding=strict_json(contract.read_text());stage=Path(stage)
    must(set(binding)=={'schema','stage','entry_sha256','inventory_sha256','backend_sha256'},'contract_schema')
    must(binding['schema']=='ARTLIVE_CORE_R25_MANUAL_PUBLICATION_V1' and binding['stage']==str(stage),'stage_binding')
    must(stage.parent==r25/'runtime' and stage.name.startswith('huaniao-backend-projection-'),'stage_path')
    check(stage,0o700,True);entry=stage/'entry/operations/deploy_huaniao_backend.py'
    check(entry,0o600);check(stage/'entry/DEPLOYMENT_INVENTORY.json')
    must(sha(entry)==binding['entry_sha256'] and sha(stage/'entry/DEPLOYMENT_INVENTORY.json')==binding['inventory_sha256'],'delegated_artifact_identity')
    inventory=strict_json((stage/'entry/DEPLOYMENT_INVENTORY.json').read_text())
    must(inventory['backend_sha256']==binding['backend_sha256'],'delegated_backend_binding')
    # Do not acquire a second lease here: the R25 coordinator owns the common
    # lock, active-work gate, paired install, process identity and recovery.
    environment=dict(PATH='/usr/bin:/bin',LANG='C.UTF-8',R25_ROOT=str(r25),ARTLIVE_R25_HANDOFF_CONFIG=str(r25/'runtime/handoff-control.env'))
    return subprocess.run(['/root/miniconda3/bin/python3',str(entry),'apply',str(stage)],env=environment,check=False).returncode
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('operation',choices=['deploy','dry-run','stage','r25-activate','status']);parser.add_argument('arguments',nargs='*');args=parser.parse_args()
    try:
        must(os.geteuid()==0,'operator_identity')
        # Block before policy/filesystem reads as well as writes. A queued old
        # deploy invocation cannot trigger the original build/start/fallback.
        if args.operation in {'deploy','dry-run'}:raise Refused('independent_production_publication_paused_use_r25')
        if args.operation=='status':paused_policy();result={'outcome':'PAUSED_R25_ONLY','direct_install':'REMOVED','build_and_test':'UNCHANGED'}
        elif args.operation=='stage':must(len(args.arguments)==3,'stage_usage');result=stage_artifact(*args.arguments)
        else:
            must(len(args.arguments)==1,'r25_activate_usage');return delegated_activate(args.arguments[0],ROOT/'runtime/r25-publication-contract.json')
        print(json.dumps(result,sort_keys=True));return 0
    except (Refused,OSError,ValueError,KeyError) as error:
        print(json.dumps({'outcome':'REFUSED','reason':str(error) if isinstance(error,Refused) else type(error).__name__},sort_keys=True));return 65
if __name__=='__main__':raise SystemExit(main())
