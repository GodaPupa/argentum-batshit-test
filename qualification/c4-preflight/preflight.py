#!/usr/bin/env python3
"""One cold compile per isolated hosted job. Never starts a test JVM."""
import hashlib, json, os, pathlib, re, shutil, signal, subprocess, sys, time, traceback, threading

HERE = pathlib.Path(__file__).resolve().parent
OUT = pathlib.Path(os.environ['RUNNER_TEMP']) / 'c4-evidence'
OUT.mkdir(exist_ok=True)
REV = os.environ['C4_REVISION']
PINS = {'R5': ('b5195ad2d090d26a533aef5bf3fa6a486b5128c6', 'e8e719c42829eeae631b3efeb48e86468f18209f'),
        'R6': ('1e334b9625478689c15a7d153e74d3d805791506', '9e66bc429228cebc7a9dd13c93075d30c99aefba')}

def write(name, value):
    (OUT / name).write_text(json.dumps(value, indent=2) + '\n')

def run(args, **kwargs):
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT, **kwargs)

def snapshot():
    result = {'monotonic': time.monotonic()}
    for name in ['/proc/meminfo', '/proc/pressure/memory', '/sys/fs/cgroup/memory.events',
                 '/sys/fs/cgroup/memory.current', '/sys/fs/cgroup/memory.peak']:
        p = pathlib.Path(name)
        result[name] = p.read_text() if p.exists() else None
    result['processes'] = run(['ps', '-eo', 'pid,ppid,pgid,rss,etimes,args'])
    return result

def main():
    if REV not in PINS:
        raise ValueError('unknown revision')
    sha, tree = PINS[REV]
    root = pathlib.Path(os.environ['RUNNER_TEMP']) / ('c4-source-' + REV)
    home = pathlib.Path(os.environ['RUNNER_TEMP']) / ('c4-gradle-' + REV)
    if root.exists() or home.exists():
        raise ValueError('cold paths already exist')
    start = time.monotonic()
    fetched = run(['python3', str(HERE / 'anonymous-fetch.py'), REV, str(root), str(OUT / 'fetch.log')])
    (OUT / 'fetch.json').write_text(fetched)
    if run(['git', 'rev-parse', 'HEAD'], cwd=root).strip() != sha or run(['git', 'rev-parse', 'HEAD^{tree}'], cwd=root).strip() != tree:
        raise ValueError('frozen identity mismatch')
    write('source.json', {'revision': REV, 'commit': sha, 'tree': tree, 'fetch_seconds': time.monotonic()-start})
    (OUT / 'overlay.json').write_text(run(['python3', str(HERE / 'overlay.py'), str(root)]))
    for rel in ['gradle.properties', 'gradle/wrapper/gradle-wrapper.properties', 'gradle/libs.versions.toml', 'settings.gradle.kts']:
        shutil.copy2(root / rel, OUT / rel.replace('/', '_'))
    (OUT / 'java-settings.txt').write_text(run(['java', '-XshowSettings:all', '-version']))
    (OUT / 'just-version.txt').write_text(run(['just', '--version']))
    (OUT / 'process-limits.txt').write_text(run(['bash', '-c', 'ulimit -a']))
    home.mkdir()
    env = dict(os.environ, GRADLE_USER_HOME=str(home), GRADLE_BUILD_CACHE_DIR=str(home / 'build-cache'))
    # Fresh project checkout + empty Gradle home; no cache action or restore exists.
    args = ['bash', str(HERE / 'compile.sh'), str(root), '--no-build-cache']
    write('command.json', {'argv': args, 'gradle_home': str(home), 'initial_home_entries': list(home.iterdir()),
                           'limit_seconds': 600, 'kill_grace_seconds': 15, 'qualification_seconds': 450})
    before = snapshot()
    write('memory-before.json', before)
    start = time.monotonic()
    timed_out = False
    with (OUT / 'compile.log').open('w') as log, (OUT / 'telemetry.jsonl').open('w') as telemetry:
        proc = subprocess.Popen(args, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True, text=True, errors='replace')
        def capture():
            with (OUT / 'compiler-timeline.jsonl').open('w') as timeline:
                for line in proc.stdout:
                    log.write(line); log.flush()
                    timeline.write(json.dumps({'seconds': time.monotonic()-start, 'line': line})+'\n'); timeline.flush()
        reader = threading.Thread(target=capture, daemon=True); reader.start()
        while proc.poll() is None:
            telemetry.write(json.dumps(snapshot()) + '\n'); telemetry.flush()
            if time.monotonic() - start >= 600:
                timed_out = True
                os.killpg(proc.pid, signal.SIGTERM)
                try: proc.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    os.killpg(proc.pid, signal.SIGKILL); proc.wait()
                break
            time.sleep(min(2, max(0.01, 600 - (time.monotonic()-start))))
        reader.join(timeout=5)
    elapsed = time.monotonic() - start
    after = snapshot()
    write('memory-after.json', after)
    for path in home.glob('daemon/**/*.log'):
        dest = OUT / 'daemon' / path.relative_to(home / 'daemon'); dest.parent.mkdir(parents=True, exist_ok=True); shutil.copy2(path, dest)
    for base in [root, home, pathlib.Path('/tmp')]:
        for pattern in ['hs_err_pid*.log', 'kotlin-daemon*.log']:
            for path in base.rglob(pattern):
                if path.is_file(): shutil.copy2(path, OUT / (hashlib.sha256(str(path).encode()).hexdigest()[:12] + '-' + path.name))
    text = (OUT / 'compile.log').read_text(errors='replace')
    graph = re.findall(r'COMPILE_ONLY_TASK_GRAPH=(.*)', text)
    (OUT / 'task-graphs.json').write_text(json.dumps(graph, indent=2)+'\n')
    downloads = [line for line in text.splitlines() if re.search(r'download|https?://', line, re.I)]
    (OUT / 'download-lines.txt').write_text('\n'.join(downloads)+'\n')
    bad = bool(re.search(r'daemon disappeared|daemon.*lost|OutOfMemoryError|unable to connect to Kotlin compile daemon|daemon compilation failed|using fallback strategy', text, re.I))
    compiled = ':ai:compileTestKotlin' in '\n'.join(graph) and 'BUILD SUCCESSFUL' in text and bool(list(root.glob('ai/build/classes/kotlin/test/**/R5R6DecisionDifferentialTest.class')))
    # Never promote timeout, missing task/class evidence, or daemon fallback to PASS.
    def oom(s):
        return sum(int(line.split()[1]) for line in (s['/sys/fs/cgroup/memory.events'] or '').splitlines() if line.split()[0] in ['oom', 'oom_kill'])
    oom_delta = oom(after) - oom(before)
    telemetry_complete = before['/sys/fs/cgroup/memory.events'] is not None and after['/sys/fs/cgroup/memory.events'] is not None and not reader.is_alive()
    status = 'PASS' if proc.returncode == 0 and not timed_out and elapsed <= 450 and compiled and not bad and oom_delta == 0 and telemetry_complete else 'INCOMPLETE'
    if bad or (proc.returncode != 0 and not timed_out and re.search(r'Compilation error|OutOfMemoryError', text)):
        status = 'FAIL'
    write('result.json', {'status': status, 'revision': REV, 'exit_code': proc.returncode, 'wall_seconds': elapsed,
                         'within_450': elapsed <= 450, 'timed_out': timed_out, 'compiled_class_and_graph': compiled,
                         'daemon_or_oom_evidence': bad, 'oom_delta': oom_delta, 'telemetry_complete': telemetry_complete, 'warm_cache': False})
    return 0 if status == 'PASS' else 1

if __name__ == '__main__':
    try: code = main()
    except Exception as exc:
        write('result.json', {'status': 'INCOMPLETE', 'revision': REV, 'reason': str(exc)})
        (OUT / 'exception.txt').write_text(traceback.format_exc()); code = 2
    finally:
        entries = [f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.relative_to(OUT)}' for p in sorted(OUT.rglob('*')) if p.is_file() and p.name != 'SHA256SUMS']
        (OUT / 'SHA256SUMS').write_text('\n'.join(entries)+'\n')
    sys.exit(code)
