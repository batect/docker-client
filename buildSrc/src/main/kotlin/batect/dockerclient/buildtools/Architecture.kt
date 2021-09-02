package batect.dockerclient.buildtools

enum class Architecture(val golangName: String) {
    X86("386"),
    X64("amd64"),
    Arm64("arm64")
}
