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
    val jvmType: String,
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
    val fields: Map<String, String>
) : TypeFromConfigFile() {
    override fun resolve(userDefinedTypes: Map<String, TypeFromConfigFile>): TypeInformation {
        return StructType(
            name,
            fields.mapValues { (fieldName, fieldValue) -> resolveTypeReference(fieldName, fieldValue, userDefinedTypes) }
        )
    }

    private fun resolveTypeReference(fieldName: String, typeName: String, allDefinedTypes: Map<String, TypeFromConfigFile>): TypeInformation {
        val userDefinedType = allDefinedTypes[typeName]

        if (userDefinedType != null) {
            return userDefinedType.resolve(allDefinedTypes)
        }

        if (PrimitiveType.yamlNamesToValues.containsKey(typeName)) {
            return PrimitiveType.yamlNamesToValues[typeName]!!
        }

        throw IllegalArgumentException("Unknown type '$typeName' for field '$fieldName' in struct '${this.name}'")
    }
}

data class StructType(
    val name: String,
    val fields: Map<String, TypeInformation>
) : TypeInformation {
    override val isPointer: Boolean = true
    override val cName: String = "$name*"
    override val cgoTypeName: String = "*C.$name"
    override val yamlName: String = name
    override val golangName: String = name
}

enum class PrimitiveType(
    override val yamlName: String,
    override val golangName: String,
    override val cName: String,
    val jvmName: String,
    override val isPointer: Boolean = false,
    override val cgoTypeName: String = "C.$golangName",
    val cgoConversionFunctionName: String = "C.$golangName"
) : TypeInformation {
    StringType("string", "string", "char*", "UTF8StringRef", isPointer = true, cgoConversionFunctionName = "C.CString"),
    BooleanType("boolean", "bool", "bool", "Boolean");

    companion object {
        val yamlNamesToValues: Map<String, PrimitiveType> = values().associateBy { it.yamlName }
    }
}

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
