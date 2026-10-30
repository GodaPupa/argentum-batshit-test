from pathlib import Path
import ast,json,tempfile,types
H=Path(__file__).resolve().parent
node=next(n for n in ast.parse((H/'run.py').read_text()).body if isinstance(n,ast.FunctionDef) and n.name=='stage')
with tempfile.TemporaryDirectory() as tmp:
 root=Path(tmp);(root/'current-ir').mkdir();(root/'current-ir/input').write_bytes(b'preserved');calls=[]
 def fake(*args,**kwargs):
  calls.append(args);kwargs['stdout'].write(b'emitter output');return types.SimpleNamespace(returncode=0)
 env=dict(OUT=root,ROOT=root,HERE=H,subprocess=types.SimpleNamespace(run=fake));exec(compile(ast.Module(body=[node],type_ignores=[]),'stage','exec'),env)
 try:env['stage']('current-ir','current-ir')
 except FileExistsError:pass
 else:raise AssertionError('original collision not reproduced')
 assert not calls
 env['stage']('emit','current-ir');assert len(calls)==1 and (root/'current-ir/input').read_bytes()==b'preserved'
 try:env['stage']('emit','current-ir')
 except FileExistsError:pass
 else:raise AssertionError('duplicate stage overwrite allowed')
 assert len(calls)==1
print(json.dumps({'synthetic_only':True,'original_collision_reproduced':True,'separate_emit_directory_passed':True,'raw_input_preserved':True,'duplicate_stage_rejected':True}))
