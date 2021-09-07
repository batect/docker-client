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
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files

abstract class GenerateKotlinJVMTypes : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceFile: RegularFileProperty

    @get:OutputFile
    abstract val kotlinFile: RegularFileProperty

    init {
        group = "code generation"

        sourceFile.convention(
            project.provider {
                project.rootProject.layout.projectDirectory.file("codegen/types.yml")
            }
        )
    }

    @TaskAction
    fun run() {
        val types = loadTypeConfigurationFile(sourceFile.get().asFile.toPath())
        generate(types)
    }

    private fun generate(types: List<TypeInformation>) {
        val builder = StringBuilder()

        builder.appendLine(fileHeader)
        builder.appendLine("package batect.dockerclient.native")
        builder.appendLine()
        builder.appendLine("import jnr.ffi.Runtime")
        builder.appendLine("import jnr.ffi.Struct")

        types.forEach { type ->
            when (type) {
                is StructType -> generateStruct(builder, type)
                is AliasType -> generateAlias(builder, type)
                else -> throw UnsupportedOperationException("Unknown type: ${type::class.simpleName}")
            }
        }

        Files.writeString(kotlinFile.get().asFile.toPath(), builder, Charsets.UTF_8)
    }

    private fun generateStruct(builder: StringBuilder, type: StructType) {
        builder.appendLine(
            """

            internal class ${type.name}(runtime: Runtime) : Struct(runtime), AutoCloseable {
                constructor(pointer: Pointer) : this (pointer.memory.runtime) {
                    this.useMemory(pointer.get())
                }

            """.trimIndent()
        )

        type.fields
            .mapKeys { (fieldName, _) -> convertFieldNameToKotlinConvention(fieldName) }
            .forEach { (fieldName, fieldType) -> builder.appendLine(generateStructField(fieldName, fieldType)) }

        builder.appendLine(
            """

                override fun close() {
                    nativeAPI.Free${type.name}(this)
                }
            }
            """.trimIndent()
        )
    }

    private fun generateStructField(fieldName: String, fieldType: TypeInformation): String {
        return when (fieldType) {
            is PrimitiveType -> "    val $fieldName = ${fieldType.jvmName}()"
            is AliasType -> "    val $fieldName = ${fieldType.jnrType}()"
            is StructType ->
                """
                |    private val ${fieldName}Pointer = Pointer()
                |    val $fieldName: ${fieldType.name}? by lazy { if (${fieldName}Pointer.intValue() == 0) null else ${fieldType.name}(${fieldName}Pointer) }
                """.trimMargin()
        }
    }

    private fun generateAlias(builder: StringBuilder, type: AliasType) {
        builder.appendLine()
        builder.appendLine("internal typealias ${type.name} = ${type.jvmType}")
    }

    private fun convertFieldNameToKotlinConvention(fieldName: String): String {
        val firstLowercaseLetter = fieldName.indexOfFirst { it.isLowerCase() }

        return if (firstLowercaseLetter == 1) {
            fieldName[0].lowercase() + fieldName.substring(1)
        } else {
            fieldName.substring(0, firstLowercaseLetter - 1).lowercase() + fieldName.substring(firstLowercaseLetter - 1)
        }
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
}
