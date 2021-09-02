package batect.dockerclient.buildtools

enum class BinaryType {
    // .so / .dylib / .dll
    Shared,

    // .a / .lib
    Archive
}
