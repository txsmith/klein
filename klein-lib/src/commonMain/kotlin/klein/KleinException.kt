package klein

class KleinException(
    val errors: List<HostError>,
) : Exception(errors.joinToString("\n") { it.message })
