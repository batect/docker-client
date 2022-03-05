import batect.dockerclient.buildtools.golang.VerifyChecksumFromMultiChecksumFile

plugins {
    id("batect.dockerclient.buildtools.golang")
}

tasks.create<VerifyChecksumFromMultiChecksumFile>("verifyMatchingFile") {
    checksumFile.set(file("checksums.txt"))
    fileToVerify.set(file("file-1.txt"))
}

tasks.create<VerifyChecksumFromMultiChecksumFile>("verifyNonMatchingFile") {
    checksumFile.set(file("checksums.txt"))
    fileToVerify.set(file("file-2.txt"))
}

tasks.create<VerifyChecksumFromMultiChecksumFile>("verifyFileWithNoChecksum") {
    checksumFile.set(file("checksums.txt"))
    fileToVerify.set(file("file-3.txt"))
}
