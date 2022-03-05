import batect.dockerclient.buildtools.zig.VerifyZigChecksum

plugins {
    id("batect.dockerclient.buildtools.zig")
}

tasks.create<VerifyZigChecksum>("verifyMatchingFile") {
    checksumFile.set(file("checksums.json"))
    fileToVerify.set(file("valid.txt"))
    zigVersion.set("0.9.1")
    zigPlatformName.set("x86_64-linux")
}

tasks.create<VerifyZigChecksum>("verifyNonMatchingFile") {
    checksumFile.set(file("checksums.json"))
    fileToVerify.set(file("invalid.txt"))
    zigVersion.set("0.9.1")
    zigPlatformName.set("x86_64-linux")
}

tasks.create<VerifyZigChecksum>("verifyFileWithNonExistentPlatform") {
    checksumFile.set(file("checksums.json"))
    fileToVerify.set(file("invalid.txt"))
    zigVersion.set("0.9.1")
    zigPlatformName.set("x86_64-darwin")
}

tasks.create<VerifyZigChecksum>("verifyFileWithNonPlatform") {
    checksumFile.set(file("checksums.json"))
    fileToVerify.set(file("invalid.txt"))
    zigVersion.set("0.9.1")
    zigPlatformName.set("notes")
}

tasks.create<VerifyZigChecksum>("verifyFileWithNonExistentVersion") {
    checksumFile.set(file("checksums.json"))
    fileToVerify.set(file("valid.txt"))
    zigVersion.set("0.9.9")
    zigPlatformName.set("x86_64-linux")
}
