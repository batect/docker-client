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

abstract class GenerateGolangTypes : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceFile: RegularFileProperty

    @get:OutputFile
    abstract val headerFile: RegularFileProperty

    @get:OutputFile
    abstract val cFile: RegularFileProperty

    @get:OutputFile
    abstract val goFile: RegularFileProperty

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

        goFile.convention(
            project.provider {
                project.layout.projectDirectory.file("src/types.go")
            }
        )
    }

    @TaskAction
    fun run() {
        val types = loadTypeConfigurationFile(sourceFile.get().asFile.toPath())
        val methods = generateCMethods(types)

        generateHeaderFile(types, methods)
        generateCFile(methods)
        generateGoFile(types)
    }

    private fun generateCMethods(types: List<TypeInformation>): Set<CMethod> {
        val typeMethods = types.flatMap { type -> generateMethodsForType(type) }.toSet()
        val arrayMethods = types.findAllTypesUsedAsArrayElements().flatMap { elementType -> generateMethodsForTypeUsedAsArrayElement(elementType) }.toSet()

        return typeMethods + arrayMethods
    }

    private fun generateMethodsForType(type: TypeInformation): Set<CMethod> {
        return when (type) {
            is StructType -> setOf(
                CMethod.alloc(type),
                CMethod.free(type)
            )
            is CallbackType -> setOf(
                CMethod.invoke(type)
            )
            else -> emptySet()
        }
    }

    private fun generateMethodsForTypeUsedAsArrayElement(elementType: TypeInformation): Set<CMethod> {
        return setOf(
            CMethod.createArray(elementType),
            CMethod.setArrayElement(elementType),
            CMethod.getArrayElement(elementType)
        )
    }

    private fun generateHeaderFile(types: List<TypeInformation>, methods: Set<CMethod>) {
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

        types.forEach { generateHeaderFileContentForType(builder, it) }
        methods.forEach { it.writeToHeaderFile(builder) }

        builder.appendLine(
            """
            #endif
            """.trimIndent()
        )

        Files.writeString(headerFile.get().asFile.toPath(), builder, Charsets.UTF_8)
    }

    private fun generateHeaderFileContentForType(builder: StringBuilder, type: TypeInformation) {
        when (type) {
            is AliasType -> {
                builder.appendLine("typedef ${type.nativeType} ${type.name};")
                builder.appendLine()
            }
            is StructType -> {
                builder.appendLine("typedef struct {")

                type.fields.forEach { (fieldName, fieldType) ->
                    if (fieldType is ArrayType) {
                        builder.appendLine("    uint64_t ${fieldName}Count;")
                    }

                    builder.appendLine("    ${fieldType.cName} $fieldName;")
                }

                builder.appendLine("} ${type.name};")
                builder.appendLine()
            }
            is CallbackType -> {
                builder.appendLine("typedef bool (*${type.name}) (void*, ${type.parameters.joinToString(", ") { it.type.cName }});")
                builder.appendLine()
            }
            is PrimitiveType, is ArrayType -> {
                // Nothing to do.
            }
        }
    }

    private fun generateCFile(methods: Set<CMethod>) {
        val builder = StringBuilder()

        builder.appendLine(fileHeader)
        builder.appendLine(
            """
                #include <stdlib.h>
                #include "${headerFile.get().asFile.name}"
            """.trimIndent()
        )

        methods.forEach { method ->
            builder.appendLine()
            method.writeToDefinitionFile(builder)
        }

        Files.writeString(cFile.get().asFile.toPath(), builder, Charsets.UTF_8)
    }

    private fun generateGoFile(types: List<TypeInformation>) {
        val builder = StringBuilder()

        builder.appendLine(fileHeader)
        builder.appendLine(
            """
            package main

            /*
                #cgo windows CFLAGS: -DWINDOWS=1
                #include "types.h"
            */
            import "C"
            import "unsafe"

            """.trimIndent()
        )

        types.forEach { type ->
            builder.appendLine("type ${type.golangName} ${type.cgoTypeName}")
        }

        builder.appendLine()

        types
            .filterIsInstance<StructType>()
            .forEach { type -> generateGoConstructor(builder, type) }

        types
            .filterIsInstance<CallbackType>()
            .forEach { type -> generateGoInvoke(builder, type) }

        Files.writeString(goFile.get().asFile.toPath(), builder, Charsets.UTF_8)
    }

    private fun generateGoConstructor(builder: StringBuilder, type: StructType) {
        builder.appendLine("func new${type.golangName}(")

        type.fields.forEach { (fieldName, fieldType) -> builder.appendLine("    $fieldName ${fieldType.golangName},") }

        builder.appendLine(
            """
            ) ${type.golangName} {
                value := C.Alloc${type.name}()
            """.trimIndent()
        )

        type.fields.forEach { (fieldName, fieldType) -> builder.appendLine(generateGoConstructorSetter(type, fieldName, fieldType)) }

        builder.appendLine(
            """

                return value
            }
            """.trimIndent()
        )

        builder.appendLine()
    }

    private fun generateGoConstructorSetter(structType: StructType, fieldName: String, fieldType: TypeInformation): String {
        return when (fieldType) {
            is StructType, is AliasType, is PrimitiveType -> "    value.$fieldName = ${golangConverterToCType(fieldName, fieldType)}"
            is ArrayType ->
                """
                |
                |    value.${fieldName}Count = C.uint64_t(len($fieldName))
                |    value.$fieldName = C.Create${fieldType.elementType.yamlName}Array(value.${fieldName}Count)
                |
                |    for i, v := range $fieldName {
                |        C.Set${fieldType.elementType.yamlName}ArrayElement(value.$fieldName, C.uint64_t(i), ${golangConverterToCType("v", fieldType.elementType)})
	            |    }
                |
                """.trimMargin()
            is CallbackType -> throw UnsupportedOperationException("Embedding callback types in structs is not supported. Field $fieldName of ${structType.name} contains callback type ${fieldType.name}.")
        }
    }

    private fun golangConverterToCType(source: String, type: TypeInformation): String = when (type) {
        is StructType -> source
        is AliasType -> "${type.cgoConversionFunctionName}($source)"
        is PrimitiveType -> "${type.cgoConversionFunctionName}($source)"
        else -> throw UnsupportedOperationException("Don't know how to convert ${type::class.simpleName} from Golang type to C type.")
    }

    private fun generateGoInvoke(builder: StringBuilder, type: CallbackType) {
        builder.appendLine(
            """
                func invoke${type.name}(method ${type.golangName}, userData unsafe.Pointer, ${type.parameters.joinToString(", ") { "${it.name} ${it.type.golangName}" }}) bool {
                    return bool(C.Invoke${type.name}(method, userData, ${type.parameters.joinToString(", ") { it.name }}))
                }
            """.trimIndent()
        )

        builder.appendLine()
    }

    private data class CMethod(
        val name: String,
        val returnType: String?,
        val parameters: List<CMethodParameter>,
        val body: String
    ) {
        private val parameterList: String = parameters.joinToString(", ") { "${it.type} ${it.name}" }

        fun writeToHeaderFile(builder: StringBuilder) {
            builder.appendLine("EXPORTED_FUNCTION ${returnType ?: "void"} $name($parameterList);")
        }

        fun writeToDefinitionFile(builder: StringBuilder) {
            builder.appendLine("${returnType ?: "void"} $name($parameterList) {")

            body.trim().lines().forEach { line ->
                if (line.isEmpty()) {
                    builder.appendLine()
                } else {
                    builder.append("    ")
                    builder.appendLine(line)
                }
            }

            builder.appendLine("}")
        }

        companion object {
            fun alloc(type: StructType): CMethod {
                val builder = StringBuilder()
                val pointerFields = type.fields.filter { it.type.isPointer }

                builder.appendLine("${type.name}* value = malloc(sizeof(${type.name}));")
                pointerFields.forEach { builder.appendLine("value->${it.name} = NULL;") }

                type.fields
                    .filter { it.type is ArrayType }
                    .forEach { (fieldName, _) -> builder.appendLine("value->${fieldName}Count = 0;") }

                builder.appendLine()
                builder.appendLine("return value;")

                return CMethod(
                    "Alloc${type.name}",
                    "${type.name}*",
                    emptyList(),
                    builder.toString()
                )
            }

            fun free(type: StructType): CMethod {
                val builder = StringBuilder()
                val pointerFields = type.fields.filter { it.type.isPointer }

                builder.appendLine(
                    """
                    if (value == NULL) {
                        return;
                    }

                    """.trimIndent()
                )

                pointerFields.forEach { (fieldName, fieldType) ->
                    builder.appendLine(pointerMemberCleanupFunction("value->$fieldName", fieldType))
                }

                builder.appendLine("free(value);")

                return CMethod(
                    "Free${type.name}",
                    null,
                    listOf(CMethodParameter("value", "${type.name}*")),
                    builder.toString()
                )
            }

            private fun pointerMemberCleanupFunction(expression: String, type: TypeInformation): String {
                return when (type) {
                    is PrimitiveType -> "free($expression);"
                    is StructType -> "Free${type.name}($expression);"
                    is ArrayType ->
                        """
                        for (uint64_t i = 0; i < ${expression}Count; i++) {
                        ${pointerMemberCleanupFunction("$expression[i]", type.elementType).prependIndent("    ")}
                        }

                        free($expression);
                        """.trimIndent()
                    else -> throw UnsupportedOperationException("Don't know how to clean up pointer type of ${type::class.simpleName!!}")
                }
            }

            fun createArray(elementType: TypeInformation): CMethod = CMethod(
                "Create${elementType.yamlName}Array",
                "${elementType.cName}*",
                listOf(CMethodParameter("size", "uint64_t")),
                "return malloc(size * sizeof(${elementType.cName}));"
            )

            fun setArrayElement(elementType: TypeInformation): CMethod = CMethod(
                "Set${elementType.yamlName}ArrayElement",
                null,
                listOf(CMethodParameter("array", "${elementType.cName}*"), CMethodParameter("index", "uint64_t"), CMethodParameter("value", elementType.cName)),
                "array[index] = value;"
            )

            fun getArrayElement(elementType: TypeInformation): CMethod = CMethod(
                "Get${elementType.yamlName}ArrayElement",
                elementType.cName,
                listOf(CMethodParameter("array", "${elementType.cName}*"), CMethodParameter("index", "uint64_t")),
                "return array[index];"
            )

            fun invoke(callback: CallbackType): CMethod = CMethod(
                "Invoke${callback.name}",
                "bool",
                listOf(CMethodParameter("method", callback.cName), CMethodParameter("userData", "void*")) + callback.parameters.map { CMethodParameter(it.name, it.type.cName) },
                "return method(userData, ${callback.parameters.joinToString(", ") { it.name }});"
            )
        }
    }

    private data class CMethodParameter(
        val name: String,
        val type: String
    )

    private fun List<TypeInformation>.findAllTypesUsedAsArrayElements(): Set<TypeInformation> = this
        .filterIsInstance<StructType>()
        .flatMap { struct -> struct.fields }
        .map { field -> field.type }
        .filterIsInstance<ArrayType>()
        .map { it.elementType }
        .toSet()

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
