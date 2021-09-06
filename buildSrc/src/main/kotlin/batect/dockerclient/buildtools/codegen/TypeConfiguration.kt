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

interface TypeInformation {
    val yamlName: String
    val cName: String
    val isPointer: Boolean
}

@Serializable
sealed class Type() : TypeInformation {
    abstract val name: String

    override val yamlName: String
        get() = name
}

@Serializable
@SerialName("alias")
data class AliasType(
    override val name: String,
    val nativeType: String,
    override val isPointer: Boolean = false
) : Type() {
    override val cName: String = name
}

@Serializable
@SerialName("struct")
data class StructType(
    override val name: String,
    val fields: Map<String, String>
) : Type() {
    override val isPointer: Boolean = true
    override val cName: String = "$name*"
}

enum class PrimitiveType(
    override val yamlName: String,
    val golangName: String,
    override val cName: String,
    override val isPointer: Boolean = false
) : TypeInformation {
    StringType("string", "string", "char*", isPointer = true),
    BooleanType("boolean", "bool", "bool");

    companion object {
        val yamlNamesToValues: Map<String, PrimitiveType> = values().associateBy { it.yamlName }
    }
}
