package klein

const val TEST_ENVIRONMENT = "acme"

fun contractOf(declarations: String): String = "environment $TEST_ENVIRONMENT\n$declarations"
