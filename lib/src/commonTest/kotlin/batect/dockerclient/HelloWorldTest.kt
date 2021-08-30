package batect.dockerclient

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class HelloWorldTest : ShouldSpec({
    should("be able to do arithmetic") {
        (1+2) shouldBe 3
    }
})
