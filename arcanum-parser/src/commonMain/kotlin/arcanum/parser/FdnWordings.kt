package arcanum.parser

/** Wordings first met in Foundations (FDN). */
internal object Fdn {
    // fdn chunk 01: a state trigger's condition (CR 603.8)
    val WHEN_THERE_ARE = Regex("""When (there are .+?), (.+)""")
    // fdn chunk 01: "If you do" after a mandatory exile (CR 118.12)
    val EXILE_IT_IF_YOU_DO = Regex("""Exile it\. If you do, ([^.]+\.)""")
}
