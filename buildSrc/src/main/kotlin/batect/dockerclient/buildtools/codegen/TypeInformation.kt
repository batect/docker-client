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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed interface TypeInformation {
    val yamlName: String
    val cName: String
    val golangName: String
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
    override val isPointer: Boolean = false
) : TypeFromConfigFile(), TypeInformation {
    override val cName: String = name
    override val yamlName: String = name
    override val golangName: String = name

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
    override val yamlName: String = name
    override val golangName: String = name
}

enum class PrimitiveType(
    override val yamlName: String,
    override val golangName: String,
    override val cName: String,
    override val isPointer: Boolean = false
) : TypeInformation {
    StringType("string", "string", "char*", isPointer = true),
    BooleanType("boolean", "bool", "bool");

    companion object {
        val yamlNamesToValues: Map<String, PrimitiveType> = values().associateBy { it.yamlName }
    }
}
