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
import com.charleskorn.kaml.YamlException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.nio.file.Files
import java.nio.file.Path

sealed interface TypeInformation {
    val yamlName: String
    val cName: String
    val golangName: String
    val cgoTypeName: String
    val jvmName: String
    val isPointer: Boolean
}

@Serializable
sealed class TypeFromConfigFile() {
    abstract val name: String

    abstract fun resolve(userDefinedTypes: Map<String, TypeFromConfigFile>): TypeInformation
}

@Serializable
@SerialName("alias")
data class AliasType(
    override val name: String,
    val nativeType: String,
    override val jvmName: String,
    val jnrType: String = nativeType,
    override val isPointer: Boolean = false
) : TypeFromConfigFile(), TypeInformation {
    override val yamlName: String = name
    override val cName: String = name
    override val golangName: String = name
    override val cgoTypeName: String = "C.$name"
    val cgoConversionFunctionName: String = "C.$nativeType"

    override fun resolve(userDefinedTypes: Map<String, TypeFromConfigFile>): TypeInformation = this
}

@Serializable
@SerialName("struct")
data class StructTypeFromConfigFile(
    override val name: String,
    val fields: List<FieldInfo>
) : TypeFromConfigFile() {
    override fun resolve(userDefinedTypes: Map<String, TypeFromConfigFile>): TypeInformation {
        return StructType(
            name,
            fields.map { (fieldName, fieldValue) -> StructMember(fieldName, resolveTypeReference("field", fieldName, fieldValue, userDefinedTypes, "struct", this.name)) }
        )
    }

    @Serializable
    data class FieldInfo(
        val name: String,
        val type: String
    )
}

data class StructType(
    val name: String,
    val fields: List<StructMember>
) : TypeInformation {
    override val isPointer: Boolean = true
    override val cName: String = "$name*"
    override val cgoTypeName: String = "*C.$name"
    override val yamlName: String = name
    override val golangName: String = name
    override val jvmName: String = name
}

data class StructMember(
    val name: String,
    val type: TypeInformation
)

enum class PrimitiveType(
    override val yamlName: String,
    override val golangName: String,
    override val cName: String,
    override val jvmName: String,
    val jvmNameInStruct: String = jvmName,
    override val isPointer: Boolean = false,
    override val cgoTypeName: String = "C.$golangName",
    val cgoConversionFunctionName: String = "C.$golangName",
    val alternativeCNames: Set<String> = emptySet(),
    val jnrPointerAccessorFunctionName: String? = null
) : TypeInformation {
    StringType("string", "string", "char*", "kotlin.String", jvmNameInStruct = "UTF8StringRef", isPointer = true, cgoConversionFunctionName = "C.CString", jnrPointerAccessorFunctionName = "getString"),
    BooleanType("boolean", "bool", "bool", "Boolean", alternativeCNames = setOf("_Bool")),
    Int64Type("int64", "int64", "int64_t", "Long", jvmNameInStruct = "int64_t", cgoConversionFunctionName = "C.int64_t"),
    GenericPointerType("void*", "unsafe.Pointer", "void*", "Pointer?");

    companion object {
        val yamlNamesToValues: Map<String, PrimitiveType> = values().associateBy { it.yamlName }
        val cNamesToValues: Map<String, PrimitiveType> = values().fold(emptyMap()) { acc, type ->
            val names = type.alternativeCNames + type.cName

            acc + names.associateWith { type }
        }
    }
}

data class ArrayType(
    val elementType: TypeInformation
) : TypeInformation {
    override val yamlName: String = "${elementType.yamlName}[]"
    override val cName: String = "${elementType.cName}*"
    override val golangName: String = "[]${elementType.golangName}"
    override val isPointer: Boolean = true
    override val cgoTypeName: String = "*${elementType.cgoTypeName}"
    override val jvmName: String = "Array<${elementType.jvmName}>"
}

@Serializable
@SerialName("callback")
data class CallbackTypeFromConfigFile(
    override val name: String,
    val parameters: List<ParameterInfo>
) : TypeFromConfigFile() {
    override fun resolve(userDefinedTypes: Map<String, TypeFromConfigFile>): TypeInformation {
        return CallbackType(
            name,
            parameters.map { (parameterName, parameterType) -> CallbackParameter(parameterName, resolveTypeReference("parameter", parameterName, parameterType, userDefinedTypes, "callback", this.name)) }
        )
    }

    @Serializable
    data class ParameterInfo(
        val name: String,
        val type: String
    )
}

data class CallbackType(
    val name: String,
    val parameters: List<CallbackParameter>
) : TypeInformation {
    override val isPointer: Boolean = false
    override val cName: String = name
    override val cgoTypeName: String = "C.$name"
    override val yamlName: String = name
    override val golangName: String = name
    override val jvmName: String = name
}

data class CallbackParameter(
    val name: String,
    val type: TypeInformation
)

internal fun loadTypeConfigurationFile(path: Path): List<TypeInformation> {
    val content = Files.readString(path)
    val yaml = Yaml(configuration = YamlConfiguration(polymorphismStyle = PolymorphismStyle.Property))

    try {
        val types = yaml.decodeFromString(ListSerializer(TypeFromConfigFile.serializer()), content)

        return types.map { it.resolve(types.associateBy { it.name }) }
    } catch (e: YamlException) {
        throw RuntimeException("Could not load types from $path: $e", e)
    }
}

private fun resolveTypeReference(
    itemType: String,
    itemName: String,
    typeName: String,
    allDefinedTypes: Map<String, TypeFromConfigFile>,
    parentTypeDescription: String,
    parentTypeName: String
): TypeInformation {
    if (typeName.endsWith("[]")) {
        val elementType = resolveTypeReference(itemType, itemName, typeName.removeSuffix("[]"), allDefinedTypes, parentTypeDescription, parentTypeName)
        return ArrayType(elementType)
    }

    val userDefinedType = allDefinedTypes[typeName]

    if (userDefinedType != null) {
        return userDefinedType.resolve(allDefinedTypes)
    }

    if (PrimitiveType.yamlNamesToValues.containsKey(typeName)) {
        return PrimitiveType.yamlNamesToValues[typeName]!!
    }

    throw IllegalArgumentException("Unknown type '$typeName' for $itemType '$itemName' in $parentTypeDescription '$parentTypeName'")
}
