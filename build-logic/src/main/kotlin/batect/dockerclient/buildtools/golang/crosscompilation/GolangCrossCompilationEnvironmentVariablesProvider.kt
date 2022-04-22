/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.dockerclient.buildtools.golang.crosscompilation

import batect.dockerclient.buildtools.Architecture
import batect.dockerclient.buildtools.OperatingSystem
import batect.dockerclient.buildtools.zig.ZigPluginExtension
import org.gradle.api.Project
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import javax.inject.Inject

class GolangCrossCompilationEnvironmentVariablesProvider @Inject constructor(
    private val crossCompilationExtension: GolangCrossCompilationPluginExtension,
    private val zigExtension: ZigPluginExtension,
    private val targetProject: Project
) {
    fun configureEnvironmentVariablesForTarget(
        environmentVariables: MapProperty<String, String>,
        operatingSystem: Provider<OperatingSystem>,
        architecture: Provider<Architecture>,
        taskName: String
    ) {
        environmentVariables.put("CGO_ENABLED", "1")
        environmentVariables.put("GOOS", operatingSystem.map { it.name.lowercase() })
        environmentVariables.put("GOARCH", architecture.map { it.golangName })
        environmentVariables.put("CC", createCompilerVariableProvider("cc", operatingSystem, architecture))
        environmentVariables.put("CXX", createCompilerVariableProvider("c++", operatingSystem, architecture))

        val zigCacheDirectory = crossCompilationExtension.rootZigCacheDirectory.map { it.dir(taskName) }
        environmentVariables.put("ZIG_LOCAL_CACHE_DIR", zigCacheDirectory.map { it.dir("local").asFile.absolutePath })
        environmentVariables.put("ZIG_GLOBAL_CACHE_DIR", zigCacheDirectory.map { it.dir("global").asFile.absolutePath })
    }

    private fun createCompilerVariableProvider(
        compilerType: String,
        operatingSystemProvider: Provider<OperatingSystem>,
        architectureProvider: Provider<Architecture>
    ): Provider<String> {
        val zigTargetProvider = operatingSystemProvider.zip(architectureProvider) { operatingSystem, architecture ->
            "${architecture.zigName}-${operatingSystem.zigName}-gnu"
        }

        val targetSpecificZigArgsProvider = createTargetSpecificZigArgsProvider(operatingSystemProvider)

        val argsProvider = zigTargetProvider.zip(targetSpecificZigArgsProvider) { zigTarget, targetSpecificZigArgs ->
            "-target $zigTarget $targetSpecificZigArgs"
        }

        return argsProvider.zip(zigExtension.zigCompilerExecutablePath) { args, zigCompilerExecutablePath ->
            """"${zigCompilerExecutablePath.asFile.absolutePath}" $compilerType $args"""
        }
    }

    private fun createTargetSpecificZigArgsProvider(operatingSystemProvider: Provider<OperatingSystem>): Provider<String> =
        operatingSystemProvider.flatMap { operatingSystem ->
            when (operatingSystem) {
                OperatingSystem.Darwin ->
                    crossCompilationExtension.macOSSystemRootDirectory.map { sysRoot ->
                        """--sysroot "$sysRoot" "-I/usr/include" "-F/System/Library/Frameworks" "-L/usr/lib""""
                    }
                else -> targetProject.provider { "" }
            }
        }
}
