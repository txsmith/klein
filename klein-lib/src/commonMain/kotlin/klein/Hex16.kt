package klein

internal fun hex16(bits: Long): String = bits.toULong().toString(16).padStart(16, '0')

internal fun parseHex16(text: String?): Long? {
    if (text == null || text.length != 16 || !text.all { it in '0'..'9' || it in 'a'..'f' }) return null
    return text.toULong(16).toLong()
}
