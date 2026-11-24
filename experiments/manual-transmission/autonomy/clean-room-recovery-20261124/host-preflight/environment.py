"""Host-only environment for the isolated Manual recovery; no repository writes."""
from pathlib import Path
from urllib.parse import urlsplit
import json
import os
import shlex

HOST = Path(__file__).resolve().parent


def build_environment():
    jdk = json.loads((HOST / "jdk-preflight.json").read_text())
    node = json.loads((HOST / "node22-preflight.json").read_text())
    if jdk["state"] != "PASS" or node["state"] != "PASS":
        raise RuntimeError("Pinned host runtime preflight has not passed")
    env = os.environ.copy()
    env["JAVA_HOME"] = jdk["jdk_root"]
    env["GRADLE_USER_HOME"] = str(HOST / "gradle-user-home")
    env["PATH"] = os.pathsep.join([
        str(Path(jdk["jdk_root"]) / "bin"),
        str(Path(node["node_root"]) / "bin"),
        env["PATH"],
    ])
    env["npm_config_cache"] = str(HOST / "npm22-cache")
    env["GRADLE_LOCK_SLOTS"] = "1"
    options = shlex.split(env.get("GRADLE_OPTS", ""))
    options.extend([
        "-Dorg.gradle.daemon=false",
        "-Djavax.net.ssl.trustStore=" + str(HOST / "java-platform-cacerts"),
    ])
    for scheme in ("http", "https"):
        value = env.get(scheme.upper() + "_PROXY") or env.get(scheme + "_proxy")
        if not value:
            continue
        proxy = urlsplit(value)
        if proxy.username or proxy.password or not proxy.hostname:
            raise RuntimeError("Refusing to copy authenticated or invalid proxy settings")
        options.extend([
            "-D" + scheme + ".proxyHost=" + proxy.hostname,
            "-D" + scheme + ".proxyPort=" + str(proxy.port or 80),
        ])
    env["GRADLE_OPTS"] = shlex.join(options)
    return env


def write_gradle_host_properties(env=None):
    """Configure only this task's isolated Gradle user home, preserving project bytes."""
    env = build_environment() if env is None else env
    lines = [
        "# Host-only Manual clean-room proxy, trust and exact JDK discovery.",
        "org.gradle.java.installations.paths=" + env["JAVA_HOME"],
        "org.gradle.java.installations.auto-detect=false",
        "org.gradle.java.installations.auto-download=false",
        "systemProp.javax.net.ssl.trustStore=" + str(HOST / "java-platform-cacerts"),
        "systemProp.http.nonProxyHosts=localhost|127.*|[::1]",
    ]
    for scheme in ("http", "https"):
        value = env.get(scheme.upper() + "_PROXY") or env.get(scheme + "_proxy")
        if not value:
            continue
        proxy = urlsplit(value)
        if proxy.username or proxy.password or not proxy.hostname:
            raise RuntimeError("Refusing to copy authenticated or invalid proxy settings")
        lines.extend([
            "systemProp." + scheme + ".proxyHost=" + proxy.hostname,
            "systemProp." + scheme + ".proxyPort=" + str(proxy.port or 80),
        ])
    destination = Path(env["GRADLE_USER_HOME"]) / "gradle.properties"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text("\n".join(lines) + "\n")
    return destination


def prepare_environment():
    """Refresh invocation-local proxy endpoints immediately before launching Gradle.

    The platform proxy port may change for each executor command. Do not reuse a
    previous command's serialized proxy properties. Call this in the same Python
    process that starts the child command, before adding temporary memory options.
    """
    env = build_environment()
    write_gradle_host_properties(env)
    return env
