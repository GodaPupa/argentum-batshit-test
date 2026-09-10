package io.kotest.provided

import com.wingedsheep.engine.support.TestHangGuard
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.extensions.spring.SpringExtension
import kotlin.time.Duration

/**
 * Project-wide Kotest config for `:game-server` tests.
 *
 * Keeps the Spring integration ([SpringExtension]) and adds the shared anti-hang guard
 * ([TestHangGuard]) so a runaway test fails fast instead of hanging the test JVM for hours.
 */
class ProjectConfig : AbstractProjectConfig() {
    override val timeout: Duration = TestHangGuard.defaultTestTimeout
    override val extensions: List<Extension> = TestHangGuard.extensions() + SpringExtension()
}
