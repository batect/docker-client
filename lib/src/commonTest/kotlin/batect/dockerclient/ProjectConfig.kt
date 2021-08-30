package batect.dockerclient

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.test.AssertionMode

object ProjectConfig : AbstractProjectConfig() {
    override val assertionMode = AssertionMode.Error
}
