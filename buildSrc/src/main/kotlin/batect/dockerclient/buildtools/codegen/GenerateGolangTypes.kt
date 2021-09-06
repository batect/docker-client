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

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.builtins.ListSerializer
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files

abstract class GenerateGolangTypes : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceFile: RegularFileProperty

    @get:OutputFile
    abstract val headerFile: RegularFileProperty

    @get:OutputFile
    abstract val cFile: RegularFileProperty

    init {
        group = "code generation"

        sourceFile.convention(
            project.provider {
                project.rootProject.layout.projectDirectory.file("codegen/types.yml")
            }
        )

        headerFile.convention(
            project.provider {
                project.layout.projectDirectory.file("src/types.h")
            }
        )

        cFile.convention(
            project.provider {
                project.layout.projectDirectory.file("src/types.c")
            }
        )
    }

    @TaskAction
    fun run() {
        val types = loadTypes()
        generateHeaderFile(types)
        generateCFile(types)
    }

    private fun loadTypes(): List<TypeInformation> {
        val path = sourceFile.get()
        val content = Files.readString(path.asFile.toPath())
        val yaml = Yaml(configuration = YamlConfiguration(polymorphismStyle = PolymorphismStyle.Property))

        val types = yaml.decodeFromString(ListSerializer(TypeFromConfigFile.serializer()), content)

        return types.map { it.resolve(types.associateBy { it.name }) }
    }

    private fun generateHeaderFile(types: List<TypeInformation>) {
        val builder = StringBuilder()

        builder.appendLine(fileHeader)

        builder.appendLine(
            """
            #include <stdint.h>
            #include <stdbool.h>

            #ifndef TYPES_H
            #define TYPES_H

            #ifdef WINDOWS
            #define EXPORTED_FUNCTION extern __declspec(dllexport)
            #else
            #define EXPORTED_FUNCTION
            #endif

            """.trimIndent()
        )

        types.forEach { type ->
            generateHeaderFileContentForType(builder, type, types)
        }

        builder.appendLine(
            """
            #endif
            """.trimIndent()
        )

        Files.writeString(headerFile.get().asFile.toPath(), builder, Charsets.UTF_8)
    }

    private fun generateHeaderFileContentForType(builder: StringBuilder, type: TypeInformation, types: List<TypeInformation>) {
        when (type) {
            is AliasType -> {
                builder.appendLine("typedef ${type.nativeType} ${type.name};")
                builder.appendLine()
            }
            is StructType -> {
                builder.appendLine("typedef struct {")

                type.fields.forEach { (fieldName, fieldType) ->
                    builder.appendLine("    ${fieldType.cName} $fieldName;")
                }

                builder.appendLine("} ${type.name};")
                builder.appendLine()
                builder.appendLine("EXPORTED_FUNCTION ${type.name}* Alloc${type.name}();")
                builder.appendLine("EXPORTED_FUNCTION void Free${type.name}(${type.name}* value);")
                builder.appendLine()
            }
        }
    }

    private fun generateCFile(types: List<TypeInformation>) {
        val builder = StringBuilder()

        builder.appendLine(fileHeader)
        builder.appendLine(
            """
                #include <stdlib.h>
                #include "${headerFile.get().asFile.name}"

            """.trimIndent()
        )

        val structTypes = types.filterIsInstance<StructType>()

        structTypes.forEach { structType ->
            generateStructAllocAndFree(builder, structType)
        }

        Files.writeString(cFile.get().asFile.toPath(), builder, Charsets.UTF_8)
    }

    private fun generateStructAllocAndFree(builder: StringBuilder, type: StructType) {
        val pointerFields = type.fields.filterValues { it.isPointer }

        builder.appendLine(
            """
            ${type.name}* Alloc${type.name}() {
                ${type.name}* value = malloc(sizeof(${type.name}));
            """.trimIndent()
        )

        pointerFields.forEach { builder.appendLine("    value->${it.key} = NULL;") }

        builder.appendLine(
            """

                return value;
            }

            """.trimIndent()
        )

        builder.appendLine(
            """
            void Free${type.name}(${type.name}* value) {
                if (value == NULL) {
                    return;
                }

            """.trimIndent()
        )

        pointerFields.forEach { (fieldName, fieldType) ->
            when (fieldType) {
                is PrimitiveType -> builder.appendLine("    free(value->$fieldName);")
                is StructType -> builder.appendLine("    Free${fieldType.name}(value->$fieldName);")
                else -> throw UnsupportedOperationException("Don't know how to clean up pointer type of ${fieldType::class.simpleName!!}")
            }
        }

        builder.appendLine(
            """
                free(value);
            }

            """.trimIndent()
        )
    }

    private val fileHeader: String
        get() =
            """
            // Copyright 2017-2021 Charles Korn.
            //
            // Licensed under the Apache License, Version 2.0 (the "License");
            // you may not use this file except in compliance with the License.
            // You may obtain a copy of the License at
            //
            //     http://www.apache.org/licenses/LICENSE-2.0
            //
            // Unless required by applicable law or agreed to in writing, software
            // distributed under the License is distributed on an "AS IS" BASIS,
            // WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
            // See the License for the specific language governing permissions and
            // limitations under the License.

            // AUTOGENERATED
            // This file is autogenerated by the ${this.path} Gradle task.
            // Do not edit this file, as it will be regenerated automatically next time this project is built.

            """.trimIndent()
}
