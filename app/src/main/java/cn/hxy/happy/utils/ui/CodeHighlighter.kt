package cn.hxy.happy.utils.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * BeanShell / Java 语法高亮。
 *
 * 词法扫描一次成型：注释、字符串、数字、标识符各归各类，避免正则互相蚕食。
 * 配色随模块深浅主题切换，不额外引入主题字段。
 */
object CodeHighlighter {

    data class Palette(
        val keyword: Color,
        val string: Color,
        val comment: Color,
        val number: Color,
        val api: Color,
        val type: Color,
        val global: Color,
        val marker: Color,
        val plain: Color,
        val lineNumber: Color,
    )

    private val Dark = Palette(
        keyword = Color(0xFFFF7B72),
        string = Color(0xFFA5D6FF),
        comment = Color(0xFF8B949E),
        number = Color(0xFF79C0FF),
        api = Color(0xFFD2A8FF),
        type = Color(0xFFFFA657),
        global = Color(0xFF79C0FF),
        marker = Color(0xFFE3B341),
        plain = Color(0xFFE6EDF3),
        lineNumber = Color(0xFF6E7681),
    )

    private val Light = Palette(
        keyword = Color(0xFFCF222E),
        string = Color(0xFF0A3069),
        comment = Color(0xFF6E7781),
        number = Color(0xFF0550AE),
        api = Color(0xFF8250DF),
        type = Color(0xFF953800),
        global = Color(0xFF0550AE),
        marker = Color(0xFF9A6700),
        plain = Color(0xFF1D1D1F),
        lineNumber = Color(0xFF8C959F),
    )

    fun palette(isDark: Boolean): Palette = if (isDark) Dark else Light

    private val KEYWORDS = hashSetOf(
        "if", "else", "for", "while", "do", "return", "break", "continue",
        "new", "this", "class", "interface", "extends", "implements", "import", "package",
        "public", "private", "protected", "static", "final", "void", "super",
        "int", "long", "float", "double", "boolean", "char", "byte", "short",
        "try", "catch", "finally", "throw", "throws", "switch", "case", "default",
        "enum", "instanceof", "synchronized", "volatile", "transient", "abstract",
        "true", "false", "null",
    )

    /** Happy 暴露给脚本的 API 方法。 */
    private val HappyApi = hashSetOf(
        "sendMsg", "sendPic", "sendPtt", "sendCard", "sendFile", "sendVideo",
        "sendReplyMsg", "recallMsg", "sendPai",
        "getAllFriend", "isFriend", "sendZan", "getUidFromUin", "getUinFromUid",
        "getGroupList", "getGroupMemberList", "getProhibitList", "getGroupInfo", "getMemberInfo",
        "shutUp", "shutUpAll", "kickGroup", "setGroupAdmin", "setGroupMemberTitle",
        "changeMemberName", "isShutUp", "clockIn",
        "putString", "putInt", "putLong", "putBoolean",
        "getString", "getInt", "getLong", "getBoolean",
        "getSkey", "getRealSkey", "getPskey", "getPt4Token", "getStweb",
        "getGTK", "getGroupRKey", "getFriendRKey", "getBkn",
        "log", "toast", "qqToast", "loadJava", "loadJar", "loadDex", "getNowActivity",
        "addItem", "addMenuItem",
        "onMsg", "joinGroup", "quitGroup", "shutUpGroup", "chatInterface",
        "onPaiYiPai", "getMsg", "unLoadPlugin",
    )

    /** 脚本可直接使用的全局变量。 */
    private val GLOBAL_VARS = hashSetOf(
        "context", "pluginId", "classLoader", "pluginPath", "myUin",
    )

    private val TYPES = hashSetOf(
        "MsgData", "FriendInfo", "GroupInfo", "MemberInfo", "ForbidInfo",
        "List", "Map", "ArrayList", "HashMap", "Set", "HashSet",
        "Stream", "Collections", "Math", "System", "Thread", "Runnable",
        "Comparator", "Consumer", "Function", "Exception", "Throwable",
        "Activity", "TroopInfo", "TroopMemberInfo", "MsgRecord", "Contact",
        "Object", "String", "Integer", "Long", "Boolean", "Double", "Float",
    )

    /** 高亮单行；整段代码由调用方按行切分后逐行传入。 */
    fun highlightLine(line: String, palette: Palette): AnnotatedString = buildAnnotatedString {
        if (line.isEmpty()) return@buildAnnotatedString

        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '/' && i + 1 < line.length && line[i + 1] == '/' -> {
                    val text = line.substring(i)
                    val style = if (text.contains(HappyMarker)) {
                        SpanStyle(color = palette.marker, fontWeight = FontWeight.Bold)
                    } else {
                        SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic)
                    }
                    withStyle(style) { append(text) }
                    return@buildAnnotatedString
                }

                c == '/' && i + 1 < line.length && line[i + 1] == '*' -> {
                    val close = line.indexOf("*/", i)
                    val end = if (close < 0) line.length else close + 2
                    withStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic)) {
                        append(line.substring(i, end))
                    }
                    i = end
                }

                c == '"' || c == '\'' -> {
                    val end = stringEnd(line, i)
                    withStyle(SpanStyle(color = palette.string)) { append(line.substring(i, end)) }
                    i = end
                }

                c.isLetter() || c == '_' -> {
                    var j = i
                    while (j < line.length && (line[j].isLetterOrDigit() || line[j] == '_')) j++
                    val word = line.substring(i, j)
                    withStyle(SpanStyle(color = wordColor(word, palette))) { append(word) }
                    i = j
                }

                c.isDigit() -> {
                    var j = i
                    while (j < line.length && (line[j].isDigit() || line[j] == '.' ||
                                line[j] == 'f' || line[j] == 'F' || line[j] == 'L')
                    ) j++
                    withStyle(SpanStyle(color = palette.number)) { append(line.substring(i, j)) }
                    i = j
                }

                else -> {
                    withStyle(SpanStyle(color = palette.plain)) { append(c) }
                    i++
                }
            }
        }
    }

    private fun wordColor(word: String, palette: Palette): Color = when {
        word in KEYWORDS -> palette.keyword
        word in HappyApi -> palette.api
        word in GLOBAL_VARS -> palette.global
        word in TYPES -> palette.type
        else -> palette.plain
    }

    /** 返回字符串字面量的结束下标（含引号）；未闭合则到行尾。 */
    private fun stringEnd(line: String, start: Int): Int {
        val quote = line[start]
        var i = start + 1
        while (i < line.length) {
            when (line[i]) {
                '\\' -> i += 2
                quote -> return i + 1
                else -> i++
            }
        }
        return line.length
    }

    private const val HappyMarker = "-Happy插件-"
}
