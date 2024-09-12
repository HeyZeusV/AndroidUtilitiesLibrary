package com.heyzeusv.androidutilities.room

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.TypeName

interface PropertyInfo {
    val name: String
}

data class FieldInfo(
    override val name: String,
    val fieldName: String,
    val startType: TypeName,
    val endType: TypeName,
) : PropertyInfo

data class EmbeddedInfo(
    override val name: String,
    val embeddedClass: KSClassDeclaration,
) : PropertyInfo

data class CloseClass(override val name: String = "") : PropertyInfo
