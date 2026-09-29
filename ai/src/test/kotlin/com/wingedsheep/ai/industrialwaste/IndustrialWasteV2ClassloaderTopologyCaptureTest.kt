package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.engine.support.GameTestDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** Seed-free test-worker loader/classpath capture; no expected-manifest or execution authority. */
class IndustrialWasteV2ClassloaderTopologyCaptureTest : FunSpec({
    test("capture actual test-worker loader chains classpath and representative code sources") {
        val output = Path.of("build/reports/industrial-classloader-topology")
        Files.createDirectories(output)

        fun chain(start: ClassLoader?): List<String> {
            val rows = mutableListOf<String>()
            var loader = start
            var ordinal = 0
            while (loader != null) {
                rows += "$ordinal\t${loader.javaClass.name}\t${loader.name ?: ""}"
                ordinal += 1
                loader = loader.parent
            }
            rows += "$ordinal\tBOOTSTRAP\t"
            return rows
        }

        val context = chain(Thread.currentThread().contextClassLoader)
        val system = chain(ClassLoader.getSystemClassLoader())
        context.isNotEmpty() shouldBe true
        system.isNotEmpty() shouldBe true
        Files.writeString(output.resolve("worker-context-loaders.tsv"),
            "ordinal\tclass\tname\n" + context.joinToString("\n", postfix="\n"))
        Files.writeString(output.resolve("worker-system-loaders.tsv"),
            "ordinal\tclass\tname\n" + system.joinToString("\n", postfix="\n"))

        val classpath = System.getProperty("java.class.path").split(File.pathSeparator)
            .filter { it.isNotBlank() }
        classpath.isNotEmpty() shouldBe true
        Files.writeString(output.resolve("worker-classpath.tsv"),
            "ordinal\tpath\n" + classpath.mapIndexed { i, p -> "$i\t${Path.of(p).toAbsolutePath().normalize()}" }
                .joinToString("\n", postfix="\n"))

        val classes = listOf(
            IndustrialWasteV2ClassloaderTopologyCaptureTest::class.java,
            IndustrialWasteV2AllocationRunner::class.java,
            IndustrialWasteV2FullHorizonRunner::class.java,
            IndustrialWasteV2R1OfficialExecutionTest::class.java,
            GameTestDriver::class.java,
            kotlin.Unit::class.java,
            io.kotest.core.spec.style.FunSpec::class.java,
        )
        val sources = classes.sortedBy { it.name }.map { cls ->
            val loader = cls.classLoader?.javaClass?.name ?: "BOOTSTRAP"
            val source = cls.protectionDomain?.codeSource?.location?.toURI()?.let {
                Path.of(it).toAbsolutePath().normalize().toString()
            } ?: "JRT_OR_BOOTSTRAP"
            "${cls.name}\t$loader\t$source"
        }
        Files.writeString(output.resolve("worker-code-sources.tsv"),
            "binary_name\tloader\tcode_source\n" + sources.joinToString("\n", postfix="\n"))

        System.getProperty("java.version").isNotBlank() shouldBe true
        Files.writeString(output.resolve("worker-runtime.txt"),
            listOf(
                "java.version=" + System.getProperty("java.version"),
                "java.home=" + System.getProperty("java.home"),
                "java.vm.name=" + System.getProperty("java.vm.name"),
            ).joinToString("\n", postfix="\n"))
    }
})
