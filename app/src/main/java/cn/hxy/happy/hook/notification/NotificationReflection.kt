package cn.hxy.happy.hook.notification

// 思路参考自 QAuxiliary: https://github.com/cinit/QAuxiliary

import cn.hxy.happy.utils.reflect.findFieldOrNull
import java.lang.reflect.Field

/**
 * 按名字递归查找字段（等价于 TCQT FieldUtils 的 recursive(true)）。
 * 查不到返回 null，不抛异常。
 */
internal fun Any.fieldOrNull(fieldName: String): Field? {
    var klass: Class<*>? = this::class.java
    while (klass != null) {
        val field = klass.findFieldOrNull { this.name = fieldName }
        if (field != null) return field
        klass = klass.superclass
    }
    return null
}

/**
 * 按类型递归查找字段。
 */
internal fun Any.fieldOrNull(fieldType: Class<*>): Field? {
    var klass: Class<*>? = this::class.java
    while (klass != null) {
        val field = klass.findFieldOrNull { this.type = fieldType }
        if (field != null) return field
        klass = klass.superclass
    }
    return null
}

internal fun <T> Any.fieldValueByType(type: Class<T>): T? {
    val value = runCatching { fieldOrNull(type)?.get(this) }.getOrNull() ?: return null
    return if (type.isInstance(value)) type.cast(value) else null
}

internal fun Any.stringField(fieldName: String): String? {
    return runCatching {
        fieldOrNull(fieldName)?.get(this) as? String
    }.getOrNull()
}

internal fun Any.intField(fieldName: String): Int? {
    return runCatching {
        (fieldOrNull(fieldName)?.get(this) as? Number)?.toInt()
    }.getOrNull()
}

internal fun Any.longField(fieldName: String): Long? {
    return runCatching {
        (fieldOrNull(fieldName)?.get(this) as? Number)?.toLong()
    }.getOrNull()
}

internal fun Any.byteField(fieldName: String): Byte? {
    return runCatching {
        (fieldOrNull(fieldName)?.get(this) as? Number)?.toByte()
    }.getOrNull()
}

internal fun Any.anyField(fieldName: String): Any? {
    return runCatching {
        fieldOrNull(fieldName)?.get(this)
    }.getOrNull()
}
