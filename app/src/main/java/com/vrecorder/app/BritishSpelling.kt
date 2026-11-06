package com.vrecorder.app

/**
 * The English speech models write American spelling. This swaps the common differences for
 * British ones, keeping the original capitalisation. Deliberately a fixed word list: blanket rules
 * (every -ize to -ise) would break words like "size" and "prize".
 */
object BritishSpelling {

    private val roots = mapOf(
        "color" to "colour", "favor" to "favour", "favorite" to "favourite", "honor" to "honour",
        "humor" to "humour", "labor" to "labour", "neighbor" to "neighbour", "rumor" to "rumour",
        "behavior" to "behaviour", "flavor" to "flavour", "harbor" to "harbour", "endeavor" to "endeavour",
        "center" to "centre", "theater" to "theatre", "liter" to "litre", "fiber" to "fibre",
        "organize" to "organise", "organization" to "organisation", "realize" to "realise", "recognize" to "recognise",
        "apologize" to "apologise", "prioritize" to "prioritise", "summarize" to "summarise", "emphasize" to "emphasise",
        "criticize" to "criticise", "minimize" to "minimise", "maximize" to "maximise", "optimize" to "optimise",
        "finalize" to "finalise", "authorize" to "authorise", "authorization" to "authorisation", "categorize" to "categorise",
        "specialize" to "specialise", "standardize" to "standardise", "utilize" to "utilise", "analyze" to "analyse",
        "paralyze" to "paralyse", "civilization" to "civilisation", "globalization" to "globalisation",
        "modernize" to "modernise", "visualize" to "visualise", "memorize" to "memorise", "customize" to "customise",
        "defense" to "defence", "offense" to "offence", 
        "catalog" to "catalogue", "dialog" to "dialogue", "analog" to "analogue",
        "traveling" to "travelling", "traveled" to "travelled", "traveler" to "traveller", "canceled" to "cancelled",
        "canceling" to "cancelling", "modeling" to "modelling", "modeled" to "modelled", "labeled" to "labelled",
        "labeling" to "labelling", "fueled" to "fuelled", "counselor" to "counsellor", "jewelry" to "jewellery",
        "enrollment" to "enrolment", "fulfill" to "fulfil", "skillful" to "skilful",
        "gray" to "grey", "aluminum" to "aluminium", "pajamas" to "pyjamas", "plow" to "plough",
        "mom" to "mum", "percent" to "per cent",
        "aging" to "ageing", "judgment" to "judgement", "acknowledgment" to "acknowledgement",
        "maneuver" to "manoeuvre", "pediatric" to "paediatric", "estrogen" to "oestrogen", "anemia" to "anaemia",
        "leukemia" to "leukaemia", "diarrhea" to "diarrhoea", "orthopedic" to "orthopaedic", "esthetic" to "aesthetic",
    )

    // Endings that keep a root's British form, e.g. color -> colors, colored, coloring, colorful.
    private val suffixes = listOf("", "s", "ed", "ing", "ful", "less", "er", "ers", "ation", "ations", "ally")

    // Left out on purpose, as both spellings are right in British English depending on meaning:
    // meter (gas meter), tire (to get tired), license (the verb), program (computer program).

    private val table: Map<String, String> = buildMap {
        for ((us, uk) in roots) {
            for (s in suffixes) put(us + s, uk + s)
            // Doubled-consonant and dropped-e forms, e.g. realize -> realizing/realising.
            if (us.endsWith("e")) {
                put(us.dropLast(1) + "ing", uk.dropLast(1) + "ing")
                put(us.dropLast(1) + "ation", uk.dropLast(1) + "ation")
            }
        }
    }

    private val word = Regex("[A-Za-z]+")

    fun apply(text: String): String = word.replace(text) { m ->
        val w = m.value
        val uk = table[w.lowercase()] ?: return@replace w
        when {
            w.all { it.isUpperCase() } -> uk.uppercase()
            w[0].isUpperCase() -> uk.replaceFirstChar { it.uppercase() }
            else -> uk
        }
    }
}
