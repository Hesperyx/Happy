package cn.hxy.happy.processor

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.symbol.Variance

/**
 * Generates a signature index of the script-facing API surface from the public functions
 * declared in `cn.hxy.happy.plugin.api.PluginMethod`.
 *
 * The index is compiled into the module, so the API reference handed to the script agent
 * cannot drift away from the implementation: adding, renaming or reordering a parameter
 * changes the generated signatures in the same build.
 *
 * The generated source is written as plain text on purpose — the shape is fixed, and text
 * emission keeps this processor independent of any code-generation DSL.
 */
class ScriptApiProcessor(
    private val codeGenerator: CodeGenerator,
) : SymbolProcessor {

    private val targetClass = "cn.hxy.happy.plugin.api.PluginMethod"

    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()

        val declaration = resolver.getClassDeclarationByName(
            resolver.getKSNameFromString(targetClass)
        ) ?: return emptyList()

        val functions = declaration.getDeclaredFunctions()
            .filter { it.isPublicApi() }
            .map { it.toSignature() }
            .toList()

        if (functions.isEmpty()) return emptyList()

        generateIndex(declaration, functions)
        generated = true
        return emptyList()
    }

    private fun KSFunctionDeclaration.isPublicApi(): Boolean {
        if (modifiers.contains(Modifier.PRIVATE)) return false
        if (modifiers.contains(Modifier.PROTECTED)) return false
        if (modifiers.contains(Modifier.INTERNAL)) return false
        return simpleName.asString().indexOf('$') < 0
    }

    private fun KSFunctionDeclaration.toSignature(): ApiFunction {
        val params = parameters.mapIndexed { index, param -> param.toApiParam(index) }
        val keyword = if (modifiers.contains(Modifier.SUSPEND)) "suspend " else ""
        return ApiFunction(
            name = simpleName.asString(),
            params = params,
            returnType = returnType?.renderType() ?: "Unit",
            keyword = keyword,
        )
    }

    private fun KSValueParameter.toApiParam(index: Int): ApiParam = ApiParam(
        name = name?.asString() ?: "arg$index",
        type = type.renderType(),
        hasDefault = hasDefault,
    )

    /** Renders a type reference without package names, keeping one level of generic arguments. */
    private fun KSTypeReference.renderType(): String {
        val text = element?.toString()?.trim().orEmpty()
        val resolved = resolve()

        // Function types render poorly through the declaration name (`Function1`); keep source text.
        val qualified = resolved.declaration.qualifiedName?.asString().orEmpty()
        if (qualified.startsWith("kotlin.Function")) return text.ifBlank { "Any" }

        val simple = resolved.declaration.simpleName.asString()
        val base = if (simple.isBlank() || simple == "<ERROR TYPE>") "" else simple
        val rendered = base.ifBlank { text.substringAfterLast('.').ifBlank { "Any" } }
        val args = resolved.arguments.map { arg ->
            val argRef = arg.type
            if (argRef != null) {
                argRef.renderType()
            } else if (arg.variance == Variance.STAR) {
                "*"
            } else {
                "Any"
            }
        }
        val nullable = if (resolved.nullability == Nullability.NULLABLE) "?" else ""
        val open = '<'
        val close = '>'
        return if (args.isEmpty()) {
            "$rendered$nullable"
        } else {
            "$rendered$open${args.joinToString(", ")}$close"
        }
    }

    private fun generateIndex(
        declaration: KSClassDeclaration,
        functions: List<ApiFunction>,
    ) {
        val builder = StringBuilder()
        builder.appendLine("// 此文件由 KSP 自动生成，请勿修改。")
        builder.appendLine("package cn.hxy.happy.generated")
        builder.appendLine()
        builder.appendLine("/**")
        builder.appendLine(" * 脚本 API 签名索引，来源：$targetClass。")
        builder.appendLine(" */")
        builder.appendLine("object ScriptApiIndex {")
        builder.appendLine()
        builder.appendLine("    /** 单个脚本 API 方法的签名信息。 */")
        builder.appendLine("    data class ApiEntry(")
        builder.appendLine("        val name: String,")
        builder.appendLine("        val signature: String,")
        builder.appendLine("        val returnType: String,")
        builder.appendLine("        val params: String,")
        builder.appendLine("    )")
        builder.appendLine()
        builder.appendLine("    /** 脚本 API 的全部公开方法签名，按源码声明顺序排列。 */")
        builder.appendLine("    val entries: List<ApiEntry> = listOf(")

        functions.forEach { fn ->
            val allParams = fn.params.joinToString("; ") { param ->
                val default = if (param.hasDefault) " = …" else ""
                "${param.name}: ${param.type}$default"
            }
            val requiredParams = fn.params
                .filterNot { it.hasDefault }
                .joinToString(", ") { "${it.name}: ${it.type}" }
            val display = "${fn.keyword}${fn.name}($requiredParams)"
            builder.append("        ApiEntry(")
            builder.append(quoted(fn.name)).append(", ")
            builder.append(quoted(display)).append(", ")
            builder.append(quoted(fn.returnType)).append(", ")
            builder.append(quoted(allParams))
            builder.appendLine("),")
        }

        builder.appendLine("    )")
        builder.appendLine("}")

        val sources = listOfNotNull(declaration.containingFile).toTypedArray()
        codeGenerator.createNewFile(
            Dependencies(true, *sources),
            "cn.hxy.happy.generated",
            "ScriptApiIndex",
        ).bufferedWriter(Charsets.UTF_8).use { it.write(builder.toString()) }
    }

    /** Kotlin string literal escaping — the generated file is source, not data. */
    private fun quoted(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '$' -> append("\\$")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }

    private data class ApiParam(
        val name: String,
        val type: String,
        val hasDefault: Boolean,
    )

    private data class ApiFunction(
        val name: String,
        val params: List<ApiParam>,
        val returnType: String,
        val keyword: String,
    )
}

class ScriptApiProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ScriptApiProcessor(environment.codeGenerator)
    }
}
