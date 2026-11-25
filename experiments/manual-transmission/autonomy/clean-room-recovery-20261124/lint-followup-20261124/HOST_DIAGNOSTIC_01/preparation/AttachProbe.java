import com.sun.management.HotSpotDiagnosticMXBean;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.agent.ByteBuddyAgent;

/** Standalone host diagnostic: no application, MockK, Gradle, or test classes. */
public final class AttachProbe {
    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map<?, ?> values) {
            List<String> parts = new ArrayList<>();
            values.forEach((k, v) -> parts.add(json(k.toString()) + ":" + json(v)));
            return "{" + String.join(",", parts) + "}";
        }
        if (value instanceof Iterable<?> values) {
            List<String> parts = new ArrayList<>();
            values.forEach(v -> parts.add(json(v)));
            return "[" + String.join(",", parts) + "]";
        }
        StringBuilder b = new StringBuilder("\"");
        for (char c : value.toString().toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> { if (c < 32) b.append(String.format("\\u%04x", (int)c)); else b.append(c); }
            }
        }
        return b.append('"').toString();
    }

    public static void main(String[] args) {
        Map<String, Object> result = new LinkedHashMap<>();
        long started = System.nanoTime();
        int exitCode = 1;
        try {
            if (args.length != 1 || !(args[0].equals("dynamic") || args[0].equals("startup"))) {
                throw new IllegalArgumentException("Expected dynamic or startup mode");
            }
            result.put("mode", args[0]);
            result.put("started_utc", Instant.now().toString());
            result.put("pid", ProcessHandle.current().pid());
            result.put("parent_pid", ProcessHandle.current().parent().map(ProcessHandle::pid).orElse(-1L));
            result.put("runtime_input_arguments", ManagementFactory.getRuntimeMXBean().getInputArguments());
            for (String key : List.of("java.home", "java.version", "java.runtime.version", "java.vm.name",
                    "java.vm.version", "java.io.tmpdir", "jdk.attach.allowAttachSelf",
                    "sun.tools.attach.attachTimeout", "net.bytebuddy.agent.attacher.dump")) {
                result.put(key, System.getProperty(key));
            }
            Map<String, Object> flags = new LinkedHashMap<>();
            var bean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
            for (String key : List.of("DisableAttachMechanism", "EnableDynamicAgentLoading")) {
                var option = bean.getVMOption(key);
                flags.put(key, Map.of("value", option.getValue(), "origin", option.getOrigin().toString()));
            }
            result.put("effective_vm_flags", flags);
            Map<String, Object> proc = new LinkedHashMap<>();
            for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
                int colon = line.indexOf(':');
                if (colon > 0 && List.of("Uid", "Gid", "NSpid", "NoNewPrivs", "Seccomp", "CapEff").contains(line.substring(0, colon))) {
                    proc.put(line.substring(0, colon), line.substring(colon + 1).trim());
                }
            }
            proc.put("pid_namespace", Files.readSymbolicLink(Path.of("/proc/self/ns/pid")).toString());
            result.put("process_observation", proc);
            result.put("agent_code_source", ByteBuddyAgent.class.getProtectionDomain().getCodeSource().getLocation().toString());
            System.out.println("PROBE_START " + json(result));
            System.out.flush();

            Instrumentation before;
            try { before = ByteBuddyAgent.getInstrumentation(); }
            catch (IllegalStateException absent) { before = null; }
            result.put("instrumentation_present_before_install", before != null);
            if (args[0].equals("startup") && before == null) {
                throw new IllegalStateException("Startup agent did not supply instrumentation; dynamic fallback is forbidden");
            }
            if (args[0].equals("dynamic") && before != null) {
                throw new IllegalStateException("Dynamic case unexpectedly began with instrumentation");
            }
            Instrumentation after = ByteBuddyAgent.install();
            result.put("instrumentation_present_after_install", after != null);
            result.put("startup_install_reused_same_instrumentation", before != null && before == after);
            result.put("can_redefine_classes", after.isRedefineClassesSupported());
            result.put("can_retransform_classes", after.isRetransformClassesSupported());
            if (!after.isRedefineClassesSupported() || !after.isRetransformClassesSupported()
                    || (args[0].equals("startup") && before != after)) {
                throw new IllegalStateException("Required instrumentation capability or startup reuse missing");
            }
            result.put("state", "PASS");
            exitCode = 0;
        } catch (Throwable failure) {
            result.put("state", "FAIL");
            result.put("failure_type", failure.getClass().getName());
            result.put("failure_message", failure.getMessage());
            failure.printStackTrace(System.err);
        } finally {
            result.put("elapsed_milliseconds", (System.nanoTime() - started) / 1_000_000L);
            result.put("terminal_utc", Instant.now().toString());
            System.out.println("PROBE_RESULT " + json(result));
            System.out.flush();
        }
        System.exit(exitCode);
    }
}
