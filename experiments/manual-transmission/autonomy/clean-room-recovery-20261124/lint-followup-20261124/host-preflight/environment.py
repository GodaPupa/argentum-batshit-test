"""Host-only environment for the isolated Manual lint follow-up; no repository writes.

Call prepare_environment() in the same executor command/process that launches
Gradle, while holding the shared Manual build lock. Never detach that command:
its platform proxy endpoint belongs to the live executor invocation.
"""
from pathlib import Path
from urllib.parse import urlsplit
import json
import os
import shlex
import tempfile

HOST = Path(__file__).resolve().parent
OWNED_JVM_PROPERTIES = (
    "org.gradle.daemon", "javax.net.ssl.trustStore", "http.nonProxyHosts",
    "http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort",
)


def _proxy_properties(env):
    result = {}
    for scheme in ("http", "https"):
        value = env.get(scheme.upper() + "_PROXY") or env.get(scheme + "_proxy")
        if not value:
            continue
        proxy = urlsplit(value)
        if proxy.scheme not in ("http", "https") or proxy.username or proxy.password or not proxy.hostname:
            raise RuntimeError("Refusing unsupported, authenticated, or invalid proxy settings")
        if any(character in proxy.hostname for character in ("\n", "\r", "\\")):
            raise RuntimeError("Invalid proxy hostname")
        result[scheme + ".proxyHost"] = proxy.hostname
        result[scheme + ".proxyPort"] = str(proxy.port or (443 if proxy.scheme == "https" else 80))
    return result


def build_environment():
    """Read this invocation's environment; never read old serialized proxy ports."""
    jdk = json.loads((HOST / "jdk-preflight.json").read_text())
    node = json.loads((HOST / "node22-preflight.json").read_text())
    trust = json.loads((HOST / "java-platform-trust.json").read_text())
    if jdk["state"] != "PASS" or node["state"] != "PASS" or trust["state"] != "PASS":
        raise RuntimeError("Pinned host runtime/trust provisioning has not passed")
    jdk_root = Path(jdk["jdk_root"]).resolve()
    node_root = Path(node["node_root"]).resolve()
    trust_store = HOST / "java-platform-cacerts"
    if not jdk_root.is_relative_to(HOST) or not node_root.is_relative_to(HOST) or not trust_store.is_file():
        raise RuntimeError("Host runtime paths must remain inside the isolated host directory")
    if not all(path.is_file() for path in (jdk_root / "bin/java", jdk_root / "bin/javac", node_root / "bin/node")):
        raise RuntimeError("A pinned host runtime executable is unavailable")
    env = os.environ.copy()
    env["JAVA_HOME"] = str(jdk_root)
    env["GRADLE_USER_HOME"] = str(HOST / "gradle-user-home")
    env["PATH"] = os.pathsep.join([str(jdk_root / "bin"), str(node_root / "bin"), env.get("PATH", "")])
    env["npm_config_cache"] = str(HOST / "npm22-cache")
    env["GRADLE_LOCK_SLOTS"] = "1"
    # Replace our JVM properties rather than appending stale inherited duplicates.
    options = [
        option for option in shlex.split(env.get("GRADLE_OPTS", ""))
        if not any(option == "-D" + key or option.startswith("-D" + key + "=") for key in OWNED_JVM_PROPERTIES)
    ]
    properties = {
        "org.gradle.daemon": "false",
        "javax.net.ssl.trustStore": str(trust_store),
        "http.nonProxyHosts": "localhost|127.*|[::1]",
        **_proxy_properties(env),
    }
    options.extend("-D" + key + "=" + value for key, value in properties.items())
    env["GRADLE_OPTS"] = shlex.join(options)
    return env


def write_gradle_host_properties(env=None):
    """Replace only isolated host properties, discarding all prior proxy values.

    An explicit env must come from build_environment() in this invocation.
    Invoke under the shared Manual build lock before temporary memory overrides.
    """
    env = build_environment() if env is None else env
    if Path(env["GRADLE_USER_HOME"]).resolve() != HOST / "gradle-user-home":
        raise RuntimeError("Refusing to write outside this task's Gradle user home")
    properties = {
        "org.gradle.java.installations.paths": env["JAVA_HOME"],
        "org.gradle.java.installations.auto-detect": "false",
        "org.gradle.java.installations.auto-download": "false",
        "systemProp.javax.net.ssl.trustStore": str(HOST / "java-platform-cacerts"),
        "systemProp.http.nonProxyHosts": "localhost|127.*|[::1]",
    }
    properties.update(("systemProp." + key, value) for key, value in _proxy_properties(env).items())
    destination = HOST / "gradle-user-home/gradle.properties"
    destination.parent.mkdir(parents=True, exist_ok=True)
    contents = "# Host-only Manual lint-follow-up proxy, trust and exact JDK discovery.\n"
    contents += "".join(key + "=" + value + "\n" for key, value in properties.items())
    with tempfile.NamedTemporaryFile(mode="w", dir=destination.parent, prefix="gradle-properties-", delete=False) as temporary:
        temporary.write(contents)
        temporary_path = Path(temporary.name)
    temporary_path.replace(destination)
    return destination


def prepare_environment():
    """Refresh invocation-local proxy endpoints immediately before launching Gradle.

    The platform proxy port may change for each executor command. Call this in
    the same Python process that starts the child command, before adding
    temporary memory settings. Do not reuse another command's prepared env or
    serialized properties, and do not detach beyond this executor's lifetime.
    """
    env = build_environment()
    write_gradle_host_properties(env)
    return env
