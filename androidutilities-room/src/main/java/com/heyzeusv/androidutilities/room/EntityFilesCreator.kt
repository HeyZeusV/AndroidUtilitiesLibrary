package com.heyzeusv.androidutilities.room

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.heyzeusv.androidutilities.room.csv.CsvData
import com.heyzeusv.androidutilities.room.csv.CsvInfo
import com.heyzeusv.androidutilities.room.util.addIndented
import com.heyzeusv.androidutilities.room.util.containsNullableType
import com.heyzeusv.androidutilities.room.util.equalsNullableType
import com.heyzeusv.androidutilities.room.util.getAnnotationArgumentValue
import com.heyzeusv.androidutilities.room.util.getListTypeName
import com.heyzeusv.androidutilities.room.util.getName
import com.heyzeusv.androidutilities.room.util.getPackageName
import com.heyzeusv.androidutilities.room.util.getUtilName
import com.heyzeusv.androidutilities.room.util.ifNotBlankAppend
import com.heyzeusv.androidutilities.room.util.removeKotlinPrefix
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import kotlin.Exception

internal class EntityFilesCreator(
    private val codeGenerator: CodeGenerator,
    private val symbols: Sequence<KSAnnotated>,
    private val typeConverterInfoList: List<TypeConverterInfo>,
    private val logger: KSPLogger,
) {
    /**
     *  List of primitive types in the form of [ClassName] that Room accepts without needing a
     *  TypeConverter.
     */
    private val validRoomTypes = listOf(
        ClassName("kotlin", "Boolean"), ClassName("kotlin", "Short"),
        ClassName("kotlin", "Int"), ClassName("kotlin", "Long"),
        ClassName("kotlin", "Byte"), ClassName("kotlin", "String"),
        ClassName("kotlin", "Char"), ClassName("kotlin", "Double"),
        ClassName("kotlin", "Float"), ClassName("kotlin", "ByteArray"),
    )

    private val _entityDataList = mutableListOf<EntityData>()
    val entityDataList: List<EntityData> get() = _entityDataList

    fun createEntityFiles() {
        symbols.filterIsInstance<KSClassDeclaration>().forEach { symbol ->
            (symbol as? KSClassDeclaration)?.let { classDeclaration ->
                // skip any classes that are annotated with Room.Fts4
                if (classDeclaration.annotations.any { it.shortName.getShortName() == "Fts4"}) {
                    return@forEach
                }
                val entityBuilder = EntityBuilder(classDeclaration)

                // write the file
                codeGenerator.createNewFile(
                    dependencies = Dependencies(false, symbol.containingFile!!),
                    packageName = entityBuilder.packageName,
                    fileName = entityBuilder.fileName,
                    extensionName = "kt"
                ).bufferedWriter().use { entityBuilder.fileBuilder.build().writeTo(it) }
            }
        }
    }

    private inner class EntityBuilder(private val classDeclaration: KSClassDeclaration) {
        val packageName = classDeclaration.getPackageName()
        val fileName = classDeclaration.getUtilName()
        val fileBuilder = FileSpec.builder(packageName, fileName)

        /**
         *  Get table name by first searching for tableName parameter of Room.Entity annotation.
         *  If it is blank, then use the name of the class annotated with Entity.
         */
        private val tableName = classDeclaration.getAnnotationArgumentValue("Entity", "tableName")
            .ifBlank { classDeclaration.simpleName.getShortName() }

        val classBuilder = TypeSpec.classBuilder(fileName)
            .addModifiers(KModifier.DATA)
            .addSuperinterface(CsvData::class)
            .addProperty(PropertySpec.builder("tableName", String::class)
                .initializer("%S", tableName)
                .build()
            )
        private val constructorBuilder = FunSpec.constructorBuilder()
        private val companionBuilder = TypeSpec.companionObjectBuilder()
            .addSuperinterface(CsvInfo::class)

        private val propertyInfoList = mutableListOf<PropertyInfo>()

        private fun TypeSpec.Builder.buildProperties(
            classDeclaration: KSClassDeclaration,
            embeddedPrefix: String = "",
        ) {
            classDeclaration.getAllProperties().forEach { prop ->
                val name = prop.getName()
                val annotations = prop.annotations.map { it.shortName.getShortName() }

                // print log message and continue
                if (annotations.contains("Ignore")) {
                    logger.info("Ignoring field $name of type ${prop.type}")
                } else if (annotations.contains("Embedded")) {
                    logger.info("Flattening embedded class ${prop.type}")

                    val embeddedClass = prop.type.resolve().declaration as KSClassDeclaration
                    // get prefix by getting Room.Embedded.prefix value
                    val newPrefix = prop.getAnnotationArgumentValue("Embedded", "prefix")
                    val embeddedInfo = EmbeddedInfo(name, embeddedClass)
                    propertyInfoList.add(embeddedInfo)

                    buildProperties(
                        classDeclaration = embeddedClass,
                        embeddedPrefix = "$embeddedPrefix$newPrefix",
                    )
                } else {
                    val startType: TypeName = prop.type.toTypeName()
                    val endType: TypeName =
                        if (validRoomTypes.containsNullableType(prop.type.toTypeName())) {
                            prop.type.toTypeName()
                        } else {
                            val tcInfo = typeConverterInfoList.find {
                                it.returnType.equalsNullableType(prop.type.toTypeName())
                            } ?: throw Exception("No TypeConverter found with $startType return type!!")
                            tcInfo.parameterType
                        }

                    var fieldName = "$embeddedPrefix$name"
                    // check if custom field name has been set using ColumnInfo.name
                    if (annotations.contains("ColumnInfo")) {
                        val columnName = prop.getAnnotationArgumentValue("ColumnInfo", "name")
                        // checks if columnName is different from its default value
                        if (columnName != "[field-name]") fieldName = "$embeddedPrefix$columnName"
                    }

                    constructorBuilder.addParameter(fieldName, endType)
                    addProperty(PropertySpec.builder(fieldName, endType)
                        .initializer(fieldName)
                        .build()
                    )
                    val fieldInfo = FieldInfo(
                        name = name,
                        fieldName = fieldName,
                        startType = startType,
                        endType = endType
                    )
                    propertyInfoList.add(fieldInfo)
                }
            }
            propertyInfoList.add(CloseClass())
        }

        private fun buildToFunctions() {
            val toOriginalFunBuilder = FunSpec.builder("toOriginal")
                .returns(classDeclaration.toClassName())
                .addCode(buildCodeBlock {
                    add("return %L(\n", classDeclaration.getName())
                    addIndented {
                        propertyInfoList.forEach { info -> buildToOriginalProperties(info) }
                    }
                    add(")")
                })

            val toUtilFunBuilder = FunSpec.builder("toUtil")
                .returns(ClassName(classDeclaration.getPackageName(), classDeclaration.getUtilName()))
                .addParameter("entity", classDeclaration.toClassName())
                .addCode(buildCodeBlock {
                    add("return %L(\n", classDeclaration.getUtilName())
                    addIndented {
                        val toUtilEmbeddedPrefixList = mutableListOf<String>()
                        propertyInfoList.forEach { info ->
                            buildToUtilProperties(info, toUtilEmbeddedPrefixList)
                        }
                    }
                    add(")")
                })

            classBuilder.addFunction(toOriginalFunBuilder.build())
            companionBuilder.addFunction(toUtilFunBuilder.build())
        }

        private fun CodeBlock.Builder.buildToOriginalProperties(info: PropertyInfo) {
            when (info) {
                is FieldInfo -> {
                    if (info.startType == info.endType) {
                        add("%L = %L,\n", info.name, info.fieldName)
                    } else {
                        val tcInfo = typeConverterInfoList.find {
                            it.parameterType == info.endType && it.returnType == info.startType
                        } ?: throw Exception("No TypeConverter found with ${info.endType} " +
                                "parameter and ${info.startType} return type!!")
                        val tcClass = ClassName(tcInfo.packageName, tcInfo.className)
                        add(
                            "%L = %T().%L(%L),\n",
                            info.name, tcClass , tcInfo.functionName, info.fieldName
                        )
                    }
                }
                is EmbeddedInfo -> {
                    add("%L = %L(\n", info.name, info.embeddedClass.getName())
                    indent()
                }
                is CloseClass -> {
                    unindent()
                    add("),\n")
                }
            }
        }

        private fun CodeBlock.Builder.buildToUtilProperties(
            info: PropertyInfo,
            embeddedPrefixList: MutableList<String>,
        ) {
            when (info) {
                is FieldInfo -> {
                    val prefix = embeddedPrefixList.joinToString(separator = ".")
                        .ifNotBlankAppend(".")
                    if (info.startType == info.endType) {
                        add("%L = entity.%L%L,\n", info.fieldName, prefix, info.name)
                    } else {
                        val tcInfo = typeConverterInfoList.find {
                            it.parameterType == info.startType && it.returnType == info.endType
                        } ?: throw Exception("No TypeConverter found with ${info.startType} " +
                                "parameter and ${info.endType} return type!!")
                        val tcClass = ClassName(tcInfo.packageName, tcInfo.className)
                        add(
                            "%L = %T().%L(entity.%L%L), \n",
                            info.fieldName, tcClass, tcInfo.functionName, prefix, info.name
                        )
                    }
                }
                is EmbeddedInfo -> embeddedPrefixList.add(info.name)
                is CloseClass -> embeddedPrefixList.remove(info.name)
            }
        }

        private fun buildCsvInterfaceImplementations(fieldInfoList: List<FieldInfo>) {
            val fileNamePropBuilder = PropertySpec.builder(CsvInfo::csvFileName.name, String::class)
                .addModifiers(KModifier.OVERRIDE)
                .initializer("%S", "$tableName.csv")

            val stringListClass = String::class.asTypeName().getListTypeName()
            val headerPropBuilder = PropertySpec.builder(CsvInfo::csvHeader.name, stringListClass)
                .addModifiers(KModifier.OVERRIDE)
                .initializer(buildCodeBlock {
                    add("listOf(\n")
                    fieldInfoList.forEach { add("%S, ", it.fieldName) }
                    add("\n)")
                })

            val stringMapClass = Map::class.asTypeName()
                .parameterizedBy(String::class.asTypeName(), String::class.asTypeName())
            val fieldToTypeMapPropBuilder = PropertySpec
                .builder(CsvInfo::csvFieldToTypeMap.name, stringMapClass)
                .addModifiers(KModifier.OVERRIDE)
                .initializer(buildCodeBlock {
                    add("mapOf(\n")
                    fieldInfoList.forEachIndexed { index, fieldInfo ->
                        add("%S to %S", fieldInfo.fieldName, fieldInfo.endType.removeKotlinPrefix())
                        if (index < fieldInfoList.size) add(",\n")
                    }
                    add("\n)")
                })

            companionBuilder.addProperty(fileNamePropBuilder.build())
                .addProperty(headerPropBuilder.build())
                .addProperty(fieldToTypeMapPropBuilder.build())

            val anyListClass = Any::class.asTypeName().copy(nullable = true).getListTypeName()
            val rowPropBuilder = PropertySpec.builder(CsvData::csvRow.name, anyListClass)
                .addModifiers(KModifier.OVERRIDE)
                .initializer(buildCodeBlock {
                    add("listOf(\n")
                    fieldInfoList.forEach { add("%L, ", it.fieldName) }
                    add("\n)")
                })

            classBuilder.addProperty(rowPropBuilder.build())
        }

        init {
            logger.info("Creating file $fileName...")

            classBuilder.buildProperties(classDeclaration)
            val entityData = EntityData(
                originalClassName = classDeclaration.toClassName(),
                utilClassName = ClassName(classDeclaration.getPackageName(), classDeclaration.getUtilName()),
                tableName = tableName,
                fieldInfoList = propertyInfoList.filterIsInstance<FieldInfo>()
            )
            _entityDataList.add(entityData)
            propertyInfoList.removeLast()
            buildCsvInterfaceImplementations(entityData.fieldInfoList)
            buildToFunctions()

            classBuilder.primaryConstructor(constructorBuilder.build())
            classBuilder.addType(companionBuilder.build())

            fileBuilder.addType(classBuilder.build())
        }
    }
}