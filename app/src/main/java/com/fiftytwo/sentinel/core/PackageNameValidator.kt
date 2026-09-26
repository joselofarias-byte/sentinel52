package com.fiftytwo.sentinel.core

/** 包名 / pattern 的合法性校验。手输包名最容易输错，加规则前先过这里。 */
object PackageNameValidator {

    private val SEGMENT = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

    fun normalize(raw: String): String = raw.trim()

    /** 至少两段，每段以字母或下划线开头。 */
    fun isValidPackage(packageName: String): Boolean {
        val parts = normalize(packageName).split('.')
        if (parts.size < 2) return false
        return parts.all { it.isNotEmpty() && SEGMENT.matches(it) }
    }

    /** 允许末尾多一段通配（`com.foo.*`），通配段之前至少要有一个完整段。 */
    fun isValidPattern(pattern: String): Boolean {
        val p = normalize(pattern)
        if (!RuleMatcher.isWildcard(p)) return isValidPackage(p)
        val prefix = p.dropLast(2)
        if (prefix.isEmpty()) return false
        return isValidPackage(prefix)
    }

    /** 给校验失败时的提示语用：返回第一条不满足的原因。 */
    fun describeProblem(pattern: String): String? {
        val p = normalize(pattern)
        if (p.isEmpty()) return "El nombre del paquete no puede estar vacío"
        if (isValidPattern(p)) return null
        if (RuleMatcher.isWildcard(p) && p.dropLast(2).isEmpty()) return "Antes de «.*» debes indicar un paquete completo, por ejemplo com.foo.*"
        val parts = p.split('.')
        if (parts.size < 2) return "El paquete debe tener al menos dos segmentos, por ejemplo com.example.app"
        val bad = parts.firstOrNull { it.isEmpty() || !SEGMENT.matches(it) }
        return "«${bad.orEmpty()}» no es un segmento de paquete válido (solo letras, números y guion bajo; no puede comenzar con un número)"
    }
}
