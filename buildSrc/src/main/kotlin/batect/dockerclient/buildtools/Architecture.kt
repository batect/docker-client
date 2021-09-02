package batect.dockerclient.buildtools

enum class Architecture(
    val golangName: String,
    val jnrName: String // Name as per jnr.ffi.Platform.OS constants
) {
    X86("386", "I386"),
    X64("amd64", "X86_64"),
    Arm64("arm64", "AARCH64")
}
