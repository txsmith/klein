package klein

interface Diagnostic {
    val message: String
    val span: SourceSpan
}
