/*
    Copyright 2017-2021 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.dockerclient.buildtools.codegen

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import java.nio.file.Path

abstract class GenerateKotlinJVMMethods : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val typesFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceHeaderFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val kotlinFile: RegularFileProperty

    init {
        group = "code generation"

        typesFile.convention(
            project.provider {
                project.rootProject.layout.projectDirectory.file("codegen/types.yml")
            }
        )
    }

    @TaskAction
    fun run() {
        val availableHeaderFiles = sourceHeaderFiles.files.filter { it.exists() }.map { it.toPath() }

        if (availableHeaderFiles.isEmpty()) {
            throw RuntimeException("None of the configured header files exist. Searched for: ${sourceHeaderFiles.files}")
        }

        val types = loadTypeConfigurationFile(typesFile.get().asFile.toPath())
        val functionsFromEachHeader = availableHeaderFiles.associateWith { loadFunctionsFromHeaderFile(it, types) }
        checkAllHeadersContainSameFunctions(functionsFromEachHeader)

        val functionsFromHeaders = functionsFromEachHeader.entries.first().value
        val allocAndFreeFunctions = generateAllocAndFreeFunctions(types)

        writeKotlinFile(functionsFromHeaders + allocAndFreeFunctions)
    }

    private fun loadFunctionsFromHeaderFile(headerPath: Path, types: List<TypeInformation>): Set<FunctionDefinition> {
        try {
            val headerContent = Files.readString(headerPath)
            val functionDeclarations =
                headerContent.substringAfter(headerFunctionDeclarationStart, "NOSTART").substringBefore(headerFunctionDeclarationEnd, "NOEND")

            if (functionDeclarations == "NOSTART" || functionDeclarations == "NOEND") {
                throw RuntimeException("Header file $headerPath is not in the expected format.")
            }

            val functionDeclarationLines = functionDeclarations
                .lines()
                .filterNot { it.isBlank() }

            return functionDeclarationLines
                .map { line -> parseFunctionDeclaration(line, types) }
                .toSet()
        } catch (e: Exception) {
            throw RuntimeException("Error while processing $headerPath: $e", e)
        }
    }

    private fun parseFunctionDeclaration(sourceLine: String, types: List<TypeInformation>): FunctionDefinition {
        val parsedFunction = functionDefinitionRegex.matchEntire(sourceLine) ?: throw RuntimeException("Cannot parse header line: $sourceLine")
        val functionName = parsedFunction.groups["functionName"]!!.value
        val returnTypeName = parsedFunction.groups["returnType"]!!.value
        val parametersSource = parsedFunction.groups["parameters"]?.value
        val parameters = if (parametersSource == null) emptyList() else parseFunctionParameters(parametersSource, types, functionName)

        return FunctionDefinition(
            functionName,
            resolveReturnType(returnTypeName, types, functionName),
            parameters
        )
    }

    private fun parseFunctionParameters(parametersSource: String, types: List<TypeInformation>, functionName: String): List<FunctionParameter> {
        return parametersSource
            .split(",")
            .map { it.trim() }
            .map { parameterSource ->
                val parsedParameter = parameterDefinitionRegex.matchEntire(parameterSource) ?: throw RuntimeException("Cannot parse parameter definition in function '$functionName': $parameterSource")
                val parameterName = parsedParameter.groups["parameterName"]!!.value
                val parameterType = parsedParameter.groups["parameterType"]!!.value

                FunctionParameter(
                    parameterName,
                    resolveTypeReference(parameterType, types, "type for parameter '$parameterName' in function '$functionName'")
                )
            }
    }

    private fun resolveReturnType(typeName: String, userDefinedTypes: List<TypeInformation>, functionName: String): TypeInformation? {
        if (typeName == "void") {
            return null
        }

        return resolveTypeReference(typeName, userDefinedTypes, "return type for function '$functionName'")
    }

    private fun resolveTypeReference(typeName: String, userDefinedTypes: List<TypeInformation>, context: String): TypeInformation {
        val primitiveType = PrimitiveType.cNamesToValues[typeName]

        if (primitiveType != null) {
            return primitiveType
        }

        val userDefinedType = userDefinedTypes.singleOrNull { it.cName == typeName }

        if (userDefinedType != null) {
            return userDefinedType
        }

        throw RuntimeException("Could not resolve type '$typeName' as $context.")
    }

    private fun checkAllHeadersContainSameFunctions(functionsFromEachHeader: Map<Path, Set<FunctionDefinition>>) {
        val inverted = functionsFromEachHeader.groupByValues()

        if (inverted.size <= 1) {
            return
        }

        val builder = StringBuilder()

        inverted.forEach { (functions, paths) ->
            builder.appendLine("$paths contain the following functions: ")

            functions.forEach { function -> builder.appendLine(" - $function") }

            builder.appendLine()
        }

        throw RuntimeException("Header files contain different function definitions:\n$builder")
    }

    private fun generateAllocAndFreeFunctions(types: List<TypeInformation>): Set<FunctionDefinition> {
        return types
            .filterIsInstance<StructType>()
            .flatMap { type ->
                listOf(
                    FunctionDefinition(
                        "Free${type.name}",
                        null,
                        listOf(FunctionParameter("value", type))
                    ),
                    FunctionDefinition(
                        "Alloc${type.name}",
                        type,
                        emptyList()
                    )
                )
            }
            .toSet()
    }

    private fun writeKotlinFile(functions: Set<FunctionDefinition>) {
        val builder = StringBuilder()

        builder.appendLine(fileHeader)
        builder.appendLine(
            """
            package batect.dockerclient.native

            import jnr.ffi.annotations.In

            @Suppress("FunctionName")
            internal interface API {
            """.trimIndent()
        )

        functions.forEach { function -> builder.appendLine("    ${function.kotlinDefinition}") }

        builder.appendLine("}")

        Files.writeString(kotlinFile.get().asFile.toPath(), builder, Charsets.UTF_8)
    }

    data class FunctionDefinition(
        val name: String,
        val returnType: TypeInformation?,
        val parameters: List<FunctionParameter>
    ) {
        val kotlinDefinition: String
            get() {
                val builder = StringBuilder()

                builder.append("fun ")
                builder.append(name)
                builder.append("(")
                builder.append(parameters.joinToString(", ") { it.kotlinDefinition })
                builder.append(")")

                if (returnType != null) {
                    builder.append(": ")
                    builder.append(returnType.yamlName)

                    if (returnType.isPointer) {
                        builder.append("?")
                    }
                }

                return builder.toString()
            }
    }

    data class FunctionParameter(
        val name: String,
        val type: TypeInformation
    ) {
        val kotlinDefinition: String = "@In $name: ${type.jvmName}"
    }

    private fun <K, V> Map<K, V>.groupByValues(): Map<V, Set<K>> {
        val grouped = mutableMapOf<V, Set<K>>()

        this.forEach { (key, value) ->
            val existing = grouped.getOrDefault(value, emptySet())

            grouped[value] = existing + key
        }

        return grouped
    }

    private val fileHeader: String
        get() =
            """
            /*
                Copyright 2017-2021 Charles Korn.

                Licensed under the Apache License, Version 2.0 (the "License");
                you may not use this file except in compliance with the License.
                You may obtain a copy of the License at

                    http://www.apache.org/licenses/LICENSE-2.0

                Unless required by applicable law or agreed to in writing, software
                distributed under the License is distributed on an "AS IS" BASIS,
                WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                See the License for the specific language governing permissions and
                limitations under the License.
            */

            // AUTOGENERATED
            // This file is autogenerated by the ${this.path} Gradle task.
            // Do not edit this file, as it will be regenerated automatically next time this project is built.

            """.trimIndent()

    companion object {
        private val headerFunctionDeclarationStart = """
            /* End of boilerplate cgo prologue.  */

            #ifdef __cplusplus
            extern "C" {
            #endif
        """.trimIndent()

        private val headerFunctionDeclarationEnd = """
            #ifdef __cplusplus
            }
            #endif
        """.trimIndent()

        private val functionDefinitionRegex: Regex = """extern(?: __declspec\(dllexport\))? (?<returnType>[a-zA-Z]+\*?) (?<functionName>[a-zA-Z]+)\((?<parameters>[a-zA-z,* ]+)?\);""".toRegex()
        private val parameterDefinitionRegex: Regex = """(?<parameterType>[a-zA-Z]+\*?) (?<parameterName>[a-zA-Z]+)""".toRegex()
    }
}
