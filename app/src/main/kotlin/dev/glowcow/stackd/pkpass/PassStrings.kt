package dev.glowcow.stackd.pkpass

/** Parser for Apple `.strings` files (`"key" = "value";`), UTF-8 or UTF-16 with BOM. */
object PassStrings {

    fun decode(bytes: ByteArray): String = when {
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
            String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
            String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        else -> String(bytes, Charsets.UTF_8)
    }

    fun parse(bytes: ByteArray): Map<String, String> = parse(decode(bytes))

    fun parse(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        var i = 0
        val tokens = ArrayList<String>(3)

        fun readQuoted(): String {
            val sb = StringBuilder()
            i++ // opening quote
            while (i < text.length && text[i] != '"') {
                val c = text[i]
                if (c == '\\' && i + 1 < text.length) {
                    i++
                    when (val e = text[i]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        'U', 'u' -> {
                            val hex = text.substring(i + 1, minOf(i + 5, text.length))
                            hex.toIntOrNull(16)?.let { sb.append(it.toChar()); i += 4 } ?: sb.append(e)
                        }
                        else -> sb.append(e)
                    }
                } else {
                    sb.append(c)
                }
                i++
            }
            i++ // closing quote
            return sb.toString()
        }

        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' -> tokens += readQuoted()
                c == '/' && text.startsWith("/*", i) -> i = text.indexOf("*/", i + 2).let { if (it < 0) text.length else it + 2 }
                c == '/' && text.startsWith("//", i) -> i = text.indexOf('\n', i).let { if (it < 0) text.length else it + 1 }
                c == ';' -> {
                    if (tokens.size == 2) out[tokens[0]] = tokens[1]
                    tokens.clear()
                    i++
                }
                else -> i++
            }
        }
        return out
    }
}
