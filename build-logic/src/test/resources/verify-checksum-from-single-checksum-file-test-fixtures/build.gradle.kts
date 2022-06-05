import batect.dockerclient.buildtools.golang.crosscompilation.VerifyChecksumFromSingleChecksumFile

plugins {
    id("batect.dockerclient.buildtools.golang.crosscompilation")
}

tasks.create<VerifyChecksumFromSingleChecksumFile>("verifyMatchingFile") {
    checksumFile.set(file("checksum.txt"))
    fileToVerify.set(file("matching.txt"))
}

tasks.create<VerifyChecksumFromSingleChecksumFile>("verifyNonMatchingFile") {
    checksumFile.set(file("checksum.txt"))
    fileToVerify.set(file("non-matching.txt"))
}
