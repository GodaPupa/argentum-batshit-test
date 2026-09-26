import com.wingedsheep.gym.ferocity.*;
import com.wingedsheep.sdk.dsl.GiftDslKt;
import com.wingedsheep.sdk.model.CardDefinition;
import com.wingedsheep.sdk.scripting.GiftKind;
import com.wingedsheep.sdk.scripting.effects.Effect;
import kotlin.Pair;
import kotlinx.serialization.json.*;

import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;

/** Offline Java source-file launcher. It never joins the gameplay classpath or initializes a game. */
public class FerocityFinitePoolExport {
    private static final FerocityJournalCodec CODEC = FerocityJournalCodec.INSTANCE;
    private static final String BUNDLE = "ferocity/runtime-card-definition-bundle/v1";
    private static final String CLASSPATH = "ferocity/runtime-classpath/v1";
    private static final String JAVA = "ferocity/runtime-java-executable/v1";
    private static final String INLINE = "ferocity/inline-tokens/gift-fish/v1";

    public static void main(String[] args) throws Exception {
        require(args.length == 3, "Expected exact resolved plan path, SHA-256 and existing output directory");
        Path planPath = Path.of(args[0]);
        require(sha(planPath).equals(args[1]), "Resolved plan changed");
        JsonObject plan = object(CODEC.getJson().parseToJsonElement(Files.readString(planPath)));
        Path output = Path.of(args[2]);
        require(output.isAbsolute() && Files.isDirectory(output), "Output directory missing");
        Path repository = Path.of(string(plan, "repositoryPath"));
        Map<String, String> sourceMap = stringMap(object(plan.get("sourceMap")));
        verifySources(repository, sourceMap);

        List<FerocityRuntimePathPin> classPath = new ArrayList<>();
        for (JsonElement element : array(plan.get("classPath"))) {
            JsonObject row = object(element);
            classPath.add(new FerocityRuntimePathPin(string(row, "path"),
                Boolean.parseBoolean(string(row, "directory")), string(row, "sha256"),
                stringMap(object(row.get("files")))));
        }
        List<String> actualPaths = FerocityBundleArchiveKt.currentFerocityClassPath().stream().map(Path::toString).toList();
        require(actualPaths.equals(classPath.stream().map(FerocityRuntimePathPin::getPath).toList()),
            "Source-file launcher changed the qualified ordered classpath");
        for (FerocityRuntimePathPin entry : classPath) {
            require(FerocityBundleArchiveKt.captureFerocityRuntimePath(Path.of(entry.getPath())).equals(entry),
                "Classpath entry differs before definition loading");
        }
        String javaPath = Path.of(System.getProperty("java.home"), "bin", "java").toRealPath().toString();
        require(javaPath.equals(string(plan, "javaExecutable")) && sha(Path.of(javaPath)).equals(string(plan, "javaExecutableSha256")),
            "Actual JVM differs from the qualified executable");

        Map<String, String> dependencies = new TreeMap<>(stringMap(object(plan.get("dependencies"))));
        List<Pair<CardDefinition, FerocityDefinitionOrigin>> definitions = new ArrayList<>();
        Map<String, JsonElement> loadedLocations = new TreeMap<>();
        Set<String> names = new TreeSet<>();
        for (JsonElement element : array(plan.get("definitions"))) {
            JsonObject row = object(element);
            String name = string(row, "name"), sourcePath = string(row, "sourcePath");
            String sourceHash = Objects.requireNonNull(sourceMap.get(sourcePath), "Getter source absent from compiled-input map");
            Class<?> holder = Class.forName(string(row, "className"));
            var getter = holder.getMethod(string(row, "getter"));
            require(getter.getParameterCount() == 0 && getter.getReturnType().equals(CardDefinition.class),
                "Getter is not an exact no-argument CardDefinition declaration");
            boolean singleton = Boolean.parseBoolean(string(row, "singleton"));
            require(Modifier.isStatic(getter.getModifiers()) != singleton, "Getter invocation kind changed");
            Object receiver = singleton ? holder.getField("INSTANCE").get(null) : null;
            CardDefinition definition = (CardDefinition) getter.invoke(receiver);
            require(definition.getName().equals(name) && names.add(name), "Getter name is wrong or duplicated");
            String key = "ferocity/card-source/" + sourcePath;
            putExact(dependencies, key, sourceHash);
            FerocityDefinitionOriginKind origin = FerocityDefinitionOriginKind.valueOf(string(row, "origin"));
            require(origin != FerocityDefinitionOriginKind.DETERMINISTIC_FIXTURE, "Synthetic card origin prohibited");
            definitions.add(new Pair<>(definition, new FerocityDefinitionOrigin(origin, key, sourceHash)));
            Path location = Path.of(holder.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            require(actualPaths.contains(location.toString()), "Getter was loaded outside the qualified classpath");
            loadedLocations.put(name, literal(location.toString()));
        }
        Set<String> expectedNames = new TreeSet<>();
        for (JsonElement name : array(plan.get("expectedNames"))) expectedNames.add(((JsonPrimitive) name).getContent());
        require(definitions.size() == 36 && names.equals(expectedNames), "Export is not the exact 33-card plus 3-token closure");

        FerocityDefinitionBundle bundle = FerocityDefinitionBundleKt.captureFerocityDefinitions(definitions,
            string(plan, "sourceCommit"), string(plan, "sourceTreeSha256"), string(plan, "serializerSha256"),
            stringMap(object(plan.get("externalDeckAliases"))));
        String bundleSha = FerocityBundleArchiveKt.writeFerocityBundle(output.resolve("definition-bundle.json"), bundle);
        putExact(dependencies, BUNDLE, bundleSha);
        putExact(dependencies, CLASSPATH, FerocityBundleArchiveKt.ferocityClassPathDigest(classPath));
        putExact(dependencies, JAVA, string(plan, "javaExecutableSha256"));

        Map<String, String> inlineSources = stringMap(object(plan.get("inlineSources")));
        inlineSources.forEach((key, path) -> putExact(dependencies, key,
            Objects.requireNonNull(sourceMap.get(path), "Inline source absent from compiled-input map")));
        Map<String, String> inlineHashes = new TreeMap<>();
        inlineSources.keySet().forEach(key -> inlineHashes.put(key, dependencies.get(key)));
        FerocityInlineTokenAdmission inline = new FerocityInlineTokenAdmission(1, "gift-fish/v1", "Sazacap's Brew",
            Objects.requireNonNull(bundle.getRegistryBindings().get("Sazacap's Brew")),
            CODEC.payload(Effect.Companion.serializer(), GiftDslKt.giftEffect(GiftKind.TAPPED_FISH)), inlineHashes);
        String inlineSha = FerocityBundleArchiveKt.ferocityWriteNewJson(output.resolve("inline-token-admission.json"),
            FerocityInlineTokenAdmission.Companion.serializer(), inline);
        putExact(dependencies, INLINE, inlineSha);

        // These pins validate offline artifacts only. The export-plan digest is NOT a research
        // admission, and the explicit no-ledger marker is never an allocated random stream.
        FerocitySourcePins artifactPins = new FerocitySourcePins(string(plan, "sourceCommit"),
            string(plan, "sourceTreeSha256"), dependencies, stringMap(object(plan.get("deckHashes"))),
            stringMap(object(plan.get("policySha256"))), bundle.getRegistryBindings(), string(plan, "serializerSha256"),
            string(plan, "protocolSha256"), string(plan, "exportPlanSha256"),
            CODEC.sha("FEROCITY_RECYCLING/D2/NO_LEDGER_CREATED"));
        FerocityBundleArchiveKt.verifyFerocityClassPath(classPath, artifactPins);
        FerocityRestoredDefinitions restored = FerocityBundleArchiveKt.readFerocityBundle(
            output.resolve("definition-bundle.json"), bundleSha, artifactPins);
        FerocityInlineTokenAdmission restoredInline = FerocityBundleArchiveKt.ferocityReadExactJson(
            output.resolve("inline-token-admission.json"), inlineSha, FerocityInlineTokenAdmission.Companion.serializer());
        FerocityInlineTokenProvenanceKt.verifyFerocityInlineTokenAdmission(restoredInline, artifactPins, restored.getRegistry());
        require(restored.getRegistry().allCardNames().equals(expectedNames), "Restored registry closure differs");
        for (String name : expectedNames) restored.resolveDeckName(name);
        String pinsSha = FerocityBundleArchiveKt.ferocityWriteNewJson(output.resolve("artifact-validation-pins.json"),
            FerocitySourcePins.Companion.serializer(), artifactPins);

        verifySources(repository, sourceMap);
        FerocityBundleArchiveKt.verifyFerocityClassPath(classPath, artifactPins);
        require(sha(planPath).equals(args[1]) && sha(Path.of(javaPath)).equals(string(plan, "javaExecutableSha256")),
            "Plan or JVM changed during export");
        Map<String, JsonElement> receipt = new TreeMap<>();
        receipt.put("scope", literal("OFFLINE_ARTIFACT_EXPORT_NO_GAME_NO_ENTROPY"));
        receipt.put("status", literal("RAW_BUNDLE_AND_INLINE_DESCRIPTOR_RELOADED_WITH_QUALIFIED_VALIDATORS"));
        receipt.put("resolvedPlanSha256", literal(args[1]));
        receipt.put("logicalSourceCommit", literal(string(plan, "sourceCommit")));
        receipt.put("sourceTreeSha256", literal(string(plan, "sourceTreeSha256")));
        receipt.put("definitionBundleSha256", literal(bundleSha));
        receipt.put("inlineTokenAdmissionSha256", literal(inlineSha));
        receipt.put("artifactValidationPinsSha256", literal(pinsSha));
        receipt.put("definitions", JsonElementKt.JsonPrimitive(36));
        receipt.put("classpathSha256", literal(dependencies.get(CLASSPATH)));
        receipt.put("loadedGetterLocations", new JsonObject(loadedLocations));
        receipt.put("requiresFinalDevelopmentAdmissionVerificationBeforeEntropy", JsonElementKt.JsonPrimitive(true));
        String receiptSha = FerocityBundleArchiveKt.ferocityWriteNewJson(output.resolve("export-receipt.json"),
            JsonElement.Companion.serializer(), new JsonObject(receipt));
        System.out.println("OFFLINE_EXPORT_COMPLETE " + receiptSha);
    }

    private static void verifySources(Path repository, Map<String, String> sources) throws Exception {
        for (var source : sources.entrySet()) {
            Path path = repository.resolve(source.getKey()).normalize();
            require(path.startsWith(repository) && path.equals(path.toRealPath()) && sha(path).equals(source.getValue()),
                "Compiled source differs during export");
        }
    }
    private static void putExact(Map<String, String> map, String key, String value) {
        String old = map.putIfAbsent(key, value);
        require(old == null || old.equals(value), "Conflicting runtime dependency binding");
    }
    private static String sha(Path path) { return FerocityBundleArchiveKt.ferocityFileSha256(path); }
    private static JsonObject object(JsonElement element) { require(element instanceof JsonObject, "Expected JSON object"); return (JsonObject) element; }
    private static JsonArray array(JsonElement element) { require(element instanceof JsonArray, "Expected JSON array"); return (JsonArray) element; }
    private static String string(JsonObject object, String key) {
        JsonElement value = Objects.requireNonNull(object.get(key), "Missing field " + key);
        require(value instanceof JsonPrimitive && !(value instanceof JsonNull), "Expected primitive " + key);
        return ((JsonPrimitive) value).getContent();
    }
    private static Map<String, String> stringMap(JsonObject object) {
        Map<String, String> values = new TreeMap<>();
        object.keySet().forEach(key -> values.put(key, string(object, key)));
        return values;
    }
    private static JsonElement literal(String value) { return JsonElementKt.JsonPrimitive(value); }
    private static void require(boolean accepted, String reason) { if (!accepted) throw new IllegalArgumentException(reason); }
}
