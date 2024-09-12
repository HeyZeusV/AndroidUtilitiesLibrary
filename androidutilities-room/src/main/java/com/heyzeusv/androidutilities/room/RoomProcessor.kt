package com.heyzeusv.androidutilities.room

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ksp.toTypeName

class RoomProcessor(private val environment: SymbolProcessorEnvironment) : SymbolProcessor {
    private val logger = environment.logger
    private val tcInfoMap: Map<RoomTypes, MutableList<TypeConverterInfo>> = mapOf(
        RoomTypes.ACCEPTED to mutableListOf(),
        RoomTypes.COMPLEX to mutableListOf()
    )

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // get all symbols
        val symbols = resolver.getSymbolsWithAnnotation("androidx.room.Entity")
            .plus(resolver.getSymbolsWithAnnotation("androidx.room.ColumnInfo"))
            .plus(resolver.getSymbolsWithAnnotation("androidx.room.Ignore"))
            .plus(resolver.getSymbolsWithAnnotation("androidx.room.Embedded"))
            .plus(resolver.getSymbolsWithAnnotation("androidx.room.TypeConverter"))
            .toSet()

        symbols.filterIsInstance<KSFunctionDeclaration>().forEach { functionDeclaration ->
            val packageName = functionDeclaration.containingFile?.packageName?.asString().orEmpty()
            val parentClass = functionDeclaration.parentDeclaration?.simpleName?.getShortName().orEmpty()
            val functionName = functionDeclaration.simpleName.getShortName()
            val parameterType = functionDeclaration.parameters.first().type.toTypeName().toString()
            val returnType = functionDeclaration.returnType?.toTypeName().toString()
            val info = TypeConverterInfo(
                packageName = packageName,
                parentClass = parentClass,
                functionName = functionName,
                parameterType = parameterType,
                returnType = returnType
            )
            if (RoomTypes.ACCEPTED.types.containsNullableType(parameterType)) {
                tcInfoMap[RoomTypes.ACCEPTED]!!.add(info)
            } else {
                tcInfoMap[RoomTypes.COMPLEX]!!.add(info)
            }
            logger.info("info $info")
            logger.info("tcInfoMap $tcInfoMap")
        }

        // filter out symbols that are not classes
        symbols.filterIsInstance<KSClassDeclaration>().forEach { symbol ->
            (symbol as? KSClassDeclaration)?.let { classDeclaration ->
                val packageName = classDeclaration.containingFile?.packageName?.asString().orEmpty()
                val fileName = classDeclaration.utilName()

                logger.info("class name: $fileName")

                // use KotlinPoet for code generation
                val fileSpecBuilder = FileSpec.builder(packageName, fileName)

                val classBuilder = recreateEntityClass(
                    tcInfoMap = tcInfoMap,
                    classDeclaration = classDeclaration,
                    logger = logger,
                )

                fileSpecBuilder.addType(classBuilder.build())

                // writing the file
                environment.codeGenerator.createNewFile(
                    dependencies = Dependencies(false, symbol.containingFile!!),
                    packageName = packageName,
                    fileName = fileName,
                    extensionName = "kt",
                ).bufferedWriter().use {
                    fileSpecBuilder.build().writeTo(it)
                }
            }
        }

        // filter out symbols that are not valid
        val ret = symbols.filterNot { it.validate() }.toList()
        return ret
    }
}

fun KSClassDeclaration.utilName(): String = "${simpleName.getShortName()}RoomUtil"