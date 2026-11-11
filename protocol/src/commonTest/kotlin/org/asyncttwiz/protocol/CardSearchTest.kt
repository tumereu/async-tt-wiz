package org.asyncttwiz.protocol

import kotlin.test.*

/** Expected results follow Scryfall's documented search behaviour (https://scryfall.com/docs/syntax). */
class CardSearchTest {
    private val w = 1; private val u = 2; private val b = 4; private val r = 8; private val g = 16
    private val classic = setOf("legacy", "vintage", "commander")

    private fun face(name: String, type: String, cost: String = "", colors: Int = 0, text: String = "",
        power: String? = null, toughness: String? = null, loyalty: String? = null) =
        CardSearchFace(name, type, text, cost, colors, power, toughness, loyalty)
    private fun card(name: String, layout: String = "normal", identity: Int, legal: Set<String> = classic,
        printings: List<Pair<String, String>> = listOf("tst" to "common"), vararg faces: CardSearchFace) =
        CardSearchCard(name, layout, faces.toList(), identity, legal, printings.map { CardSearchPrinting(it.first, it.second) })

    private val cards = listOf(
        card("Lightning Bolt", identity = r, legal = classic + setOf("modern", "pauper"),
            printings = listOf("2xm" to "uncommon", "lea" to "common", "sld" to "special"),
            faces = arrayOf(face("Lightning Bolt", "Instant", "{R}", r, "Lightning Bolt deals 3 damage to any target."))),
        card("Counterspell", identity = u, legal = classic + "pauper", printings = listOf("mh2" to "uncommon", "lea" to "uncommon"),
            faces = arrayOf(face("Counterspell", "Instant", "{U}{U}", u, "Counter target spell."))),
        card("Fire // Ice", "split", u or r, printings = listOf("mh2" to "uncommon"), faces = arrayOf(
            face("Fire", "Instant", "{1}{R}", r, "Fire deals 2 damage divided as you choose among one or two targets."),
            face("Ice", "Instant", "{1}{U}", u, "Tap target permanent.\nDraw a card."))),
        card("Delver of Secrets // Insectile Aberration", "transform", u, faces = arrayOf(
            face("Delver of Secrets", "Creature — Human Wizard", "{U}", u, "At the beginning of your upkeep, look at the top card of your library.", "1", "1"),
            face("Insectile Aberration", "Creature — Human Insect", "", u, "Flying", "3", "2"))),
        card("Westvale Abbey // Ormendahl, Profane Prince", "transform", b, faces = arrayOf(
            face("Westvale Abbey", "Land", "", 0, "{T}: Add {C}."),
            face("Ormendahl, Profane Prince", "Legendary Creature — Demon", "", b, "Flying, lifelink, indestructible, haste", "9", "7"))),
        card("Kitchen Finks", identity = g or w, printings = listOf("shm" to "uncommon"),
            faces = arrayOf(face("Kitchen Finks", "Creature — Ouphe", "{1}{G/W}{G/W}", g or w, "Persist", "3", "2"))),
        card("Birthing Pod", identity = g, legal = setOf("legacy", "vintage"), printings = listOf("nph" to "rare"),
            faces = arrayOf(face("Birthing Pod", "Artifact", "{3}{G/P}", g, "Sacrifice a creature: Search your library."))),
        card("Spectral Procession", identity = w, printings = listOf("shm" to "uncommon"),
            faces = arrayOf(face("Spectral Procession", "Sorcery", "{2/W}{2/W}{2/W}", w, "Create three 1/1 white Spirit creature tokens with flying."))),
        card("Tarmogoyf", identity = g, printings = listOf("fut" to "rare", "mm3" to "mythic"),
            faces = arrayOf(face("Tarmogoyf", "Creature — Lhurgoyf", "{1}{G}", g, "Tarmogoyf's power is equal to the number of card types.", "*", "1+*"))),
        card("Jace, the Mind Sculptor", identity = u, printings = listOf("wwk" to "mythic"),
            faces = arrayOf(face("Jace, the Mind Sculptor", "Legendary Planeswalker — Jace", "{2}{U}{U}", u, "+2: Look at the top card of target player's library.", loyalty = "3"))),
        card("Plains", identity = w, legal = classic + setOf("modern", "standard"), printings = listOf("lea" to "common", "unh" to "common"),
            faces = arrayOf(face("Plains", "Basic Land — Plains", "", 0, "({T}: Add {W}.)"))),
        card("Bonecrusher Giant // Stomp", "adventure", r, faces = arrayOf(
            face("Bonecrusher Giant", "Creature — Giant", "{2}{R}", r, "Whenever Bonecrusher Giant becomes the target of a spell, Bonecrusher Giant deals 2 damage to that spell's controller.", "4", "3"),
            face("Stomp", "Instant — Adventure", "{1}{R}", r, "Damage can't be prevented this turn. Stomp deals 2 damage to any target."))),
        card("Æther Vial", identity = 0, faces = arrayOf(face("Æther Vial", "Artifact", "{1}", 0, "At the beginning of your upkeep, you may put a charge counter on Æther Vial."))),
        card("Emrakul, the Aeons Torn", identity = 0, faces = arrayOf(face("Emrakul, the Aeons Torn", "Legendary Creature — Eldrazi", "{15}", 0, "This spell can't be countered.", "15", "15"))),
        card("Comet Storm", identity = r, faces = arrayOf(face("Comet Storm", "Instant", "{X}{R}{R}", r, "Multikicker {1}"))),
        card("Little Girl", identity = w, legal = emptySet(), faces = arrayOf(face("Little Girl", "Creature — Human Child", "{HW}", w, "", ".5", ".5"))),
        card("Thrasios, Triton Hero", identity = g or u, faces = arrayOf(face("Thrasios, Triton Hero", "Legendary Creature — Merfolk Wizard", "{G}{U}", g or u, "{4}: Scry 1.", "1", "3"))),
        card("Nicol Bolas, the Ravager", "transform", u or b or r, faces = arrayOf(
            face("Nicol Bolas, the Ravager", "Legendary Creature — Elder Dragon", "{1}{U}{B}{R}", u or b or r, "Flying", "4", "4"),
            face("Nicol Bolas, the Arisen", "Legendary Planeswalker — Bolas", "", u or b or r, "+2: Draw two cards.", loyalty = "7"))),
        card("Tazeem", "planar", 0, legal = emptySet(), faces = arrayOf(face("Tazeem", "Plane — Zendikar", "", 0, "Creatures can't block."))),
    )

    private fun search(query: String): Set<String> {
        val parsed = CardSearchQuery.parse(query)
        assertIs<CardSearchParse.Valid>(parsed, "$query: $parsed")
        return cards.filter { parsed.query.matches(it) }.map { it.name.substringBefore(" //").substringBefore(",") }.toSet()
    }

    private fun check(vararg rows: Pair<String, Set<String>>) {
        val failures = rows.mapNotNull { (query, expected) ->
            val actual = search(query)
            if (actual == expected) null else "$query\n  expected $expected\n  actual   $actual"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test fun nameWordsAreCaseAndAccentInsensitiveSubstringsAndAllMustMatch() = check(
        "bolt" to setOf("Lightning Bolt"),
        "BOLT" to setOf("Lightning Bolt"),
        "light bolt" to setOf("Lightning Bolt"),
        "\"lightning bolt\"" to setOf("Lightning Bolt"),
        "\"bolt lightning\"" to emptySet(),
        "aether" to setOf("Æther Vial"),
        "æther vial" to setOf("Æther Vial"),
        "ice" to setOf("Fire"),
        "stomp" to setOf("Bonecrusher Giant"),
        "!fire" to setOf("Fire"),
        "!\"Fire // Ice\"" to setOf("Fire"),
        "!\"lightning\"" to emptySet(),
        "!Insectile" to emptySet(),
        "!\"insectile aberration\"" to setOf("Delver of Secrets"),
    )

    @Test fun typeAndOracleTextMatchAnyFaceAndTildeIsTheName() = check(
        "t:instant" to setOf("Lightning Bolt", "Counterspell", "Fire", "Comet Storm", "Bonecrusher Giant"),
        "t:legend" to setOf("Westvale Abbey", "Jace", "Emrakul", "Thrasios", "Nicol Bolas"),
        "t:\"basic land\"" to setOf("Plains"),
        "type:planeswalker" to setOf("Jace", "Nicol Bolas"),
        "t:insect" to setOf("Delver of Secrets"),
        "o:\"draw a card\"" to setOf("Fire"),
        "o:flying t:creature" to setOf("Delver of Secrets", "Westvale Abbey", "Nicol Bolas"),
        "o:\"~ deals 3\"" to setOf("Lightning Bolt"),
        "o:\"~ deals\"" to setOf("Lightning Bolt", "Fire", "Bonecrusher Giant"),
        "o:'s" to setOf("Tarmogoyf", "Jace", "Bonecrusher Giant"),
        "t!=instant t:sorcery" to setOf("Spectral Procession"),
    )

    @Test fun coloursFollowScryfallComparisons() = check(
        "c:r" to setOf("Lightning Bolt", "Fire", "Bonecrusher Giant", "Comet Storm", "Nicol Bolas"),
        "c:ur" to setOf("Fire", "Nicol Bolas"),
        "c=r" to setOf("Lightning Bolt", "Bonecrusher Giant", "Comet Storm"),
        "c<=w" to setOf("Spectral Procession", "Little Girl", "Plains", "Æther Vial", "Emrakul", "Westvale Abbey"),
        "c<w" to setOf("Plains", "Æther Vial", "Emrakul", "Westvale Abbey"),
        "c>g" to setOf("Kitchen Finks", "Thrasios"),
        "c>=gw" to setOf("Kitchen Finks"),
        "c:selesnya" to setOf("Kitchen Finks"),
        "c:c" to setOf("Plains", "Æther Vial", "Emrakul", "Westvale Abbey"),
        "c:colorless include:extras" to setOf("Plains", "Æther Vial", "Emrakul", "Westvale Abbey", "Tazeem"),
        "c:m" to setOf("Fire", "Kitchen Finks", "Thrasios", "Nicol Bolas"),
        "-c:m c:r" to setOf("Lightning Bolt", "Bonecrusher Giant", "Comet Storm"),
        "c=3" to setOf("Nicol Bolas"),
        "c>=2" to setOf("Fire", "Kitchen Finks", "Thrasios", "Nicol Bolas"),
        "c!=u c:u" to setOf("Fire", "Thrasios", "Nicol Bolas"),
        "c:grixis" to setOf("Nicol Bolas"),
        "color:blue" to setOf("Counterspell", "Fire", "Delver of Secrets", "Jace", "Thrasios", "Nicol Bolas"),
        "c:b" to setOf("Westvale Abbey", "Nicol Bolas"),
    )

    @Test fun colourIdentityDefaultsToSubsetForCommanderSearches() = check(
        "id:simic" to setOf("Counterspell", "Birthing Pod", "Tarmogoyf", "Thrasios", "Æther Vial", "Emrakul", "Delver of Secrets", "Jace"),
        "id=gu" to setOf("Thrasios"),
        "id>=gu" to setOf("Thrasios"),
        "id:c" to setOf("Æther Vial", "Emrakul"),
        "id:m" to setOf("Fire", "Kitchen Finks", "Thrasios", "Nicol Bolas"),
        "id>1" to setOf("Fire", "Kitchen Finks", "Thrasios", "Nicol Bolas"),
        "id:b" to setOf("Westvale Abbey", "Æther Vial", "Emrakul"),
    )

    @Test fun manaValueComesFromTheCostWithHybridPhyrexianAndX() = check(
        "mv=1" to setOf("Lightning Bolt", "Delver of Secrets", "Æther Vial"),
        "cmc=3" to setOf("Kitchen Finks", "Bonecrusher Giant"),
        "mv=4" to setOf("Fire", "Birthing Pod", "Jace", "Nicol Bolas"),
        "mv=6" to setOf("Spectral Procession"),
        "mv:2" to setOf("Counterspell", "Tarmogoyf", "Comet Storm", "Thrasios"),
        "mv=0.5" to setOf("Little Girl"),
        "mv>=15" to setOf("Emrakul"),
        "mv=0 -t:land" to emptySet(),
        "mv=0 -t:land include:extras" to setOf("Tazeem"),
        "mv<1" to setOf("Westvale Abbey", "Plains", "Little Girl"),
        "mv!=0 mv<=1" to setOf("Lightning Bolt", "Delver of Secrets", "Æther Vial", "Little Girl"),
    )

    @Test fun powerToughnessAndLoyaltyCompareNumbersOrEachOther() = check(
        "pow>=4" to setOf("Westvale Abbey", "Bonecrusher Giant", "Emrakul", "Nicol Bolas"),
        "pow=3" to setOf("Delver of Secrets", "Kitchen Finks"),
        "pow=0" to setOf("Tarmogoyf"),
        "tou=1" to setOf("Delver of Secrets", "Tarmogoyf"),
        "pow>tou" to setOf("Delver of Secrets", "Westvale Abbey", "Kitchen Finks", "Bonecrusher Giant"),
        "pow<tou" to setOf("Tarmogoyf", "Thrasios"),
        "power=0.5" to setOf("Little Girl"),
        "loy>=3" to setOf("Jace", "Nicol Bolas"),
        "loy=7" to setOf("Nicol Bolas"),
        "-pow>0 t:creature" to setOf("Tarmogoyf"),
    )

    @Test fun rarityAndSetMatchTheSamePrinting() = check(
        "r:common" to setOf("Lightning Bolt", "Plains", "Delver of Secrets", "Westvale Abbey", "Bonecrusher Giant",
            "Æther Vial", "Emrakul", "Comet Storm", "Little Girl", "Thrasios", "Nicol Bolas"),
        "r:m" to setOf("Tarmogoyf", "Jace"),
        "rarity:mythic t:creature" to setOf("Tarmogoyf"),
        "r>=rare" to setOf("Birthing Pod", "Tarmogoyf", "Jace", "Lightning Bolt"),
        "r>rare" to setOf("Tarmogoyf", "Jace", "Lightning Bolt"),
        "s:lea" to setOf("Lightning Bolt", "Counterspell", "Plains"),
        "set:LEA r:uncommon" to setOf("Counterspell"),
        "s:2xm r:common" to emptySet(),
        "e:2xm or s:mh2" to setOf("Lightning Bolt", "Counterspell", "Fire"),
        "-s:lea s:2xm" to setOf("Lightning Bolt"),
        "s:lea -r:common" to setOf("Counterspell"),
    )

    @Test fun formatsLayoutsAndExtras() = check(
        "f:modern" to setOf("Lightning Bolt", "Plains"),
        "f:pauper t:instant" to setOf("Lightning Bolt", "Counterspell"),
        "legal:edh is:split" to setOf("Fire"),
        "-f:commander" to setOf("Birthing Pod", "Little Girl"),
        "is:dfc" to setOf("Delver of Secrets", "Westvale Abbey", "Nicol Bolas"),
        "is:transform" to setOf("Delver of Secrets", "Westvale Abbey", "Nicol Bolas"),
        "is:adventure" to setOf("Bonecrusher Giant"),
        "t:plane" to setOf("Jace", "Nicol Bolas"),
        "t:plane include:extras" to setOf("Jace", "Nicol Bolas", "Tazeem"),
        "is:planar" to setOf("Tazeem"),
        "zendikar" to emptySet(),
    )

    @Test fun booleanStructureOrParenthesesNegationAndExplicitAnd() = check(
        "c:r or c:g t:creature" to setOf("Lightning Bolt", "Fire", "Bonecrusher Giant", "Comet Storm", "Nicol Bolas", "Kitchen Finks", "Tarmogoyf", "Thrasios"),
        "(c:r or c:g) t:creature" to setOf("Bonecrusher Giant", "Nicol Bolas", "Kitchen Finks", "Tarmogoyf", "Thrasios"),
        "(c:r OR c:g) and t:creature -t:legendary" to setOf("Bonecrusher Giant", "Kitchen Finks", "Tarmogoyf"),
        "-(t:creature or t:land) c:u" to setOf("Counterspell", "Fire", "Jace"),
        "--bolt" to setOf("Lightning Bolt"),
        "-bolt -bolas t:instant" to setOf("Counterspell", "Fire", "Comet Storm", "Bonecrusher Giant"),
        "(t:instant" to setOf("Lightning Bolt", "Counterspell", "Fire", "Comet Storm", "Bonecrusher Giant"),
        "o:\"draw" to setOf("Fire", "Nicol Bolas"),
        "((c:u))" to setOf("Counterspell", "Fire", "Delver of Secrets", "Jace", "Thrasios", "Nicol Bolas"),
        "Kiki-Jiki" to emptySet(),
    )

    @Test fun emptyQueryMatchesEveryCardExceptExtras() {
        assertEquals(cards.size - 1, search("").size)
        assertEquals(cards.size - 1, search("   ").size)
    }

    @Test fun printingMatchPointsAtTheFirstMatchingPrinting() {
        val bolt = cards.first()
        fun at(query: String) = (CardSearchQuery.parse(query) as CardSearchParse.Valid).query.match(bolt)
        assertEquals(0, at("bolt"))
        assertEquals(1, at("s:lea"))
        assertEquals(2, at("r:special"))
        assertEquals(1, at("-s:2xm"))
        assertNull(at("s:mh2"))
        assertFalse((CardSearchQuery.parse("t:instant") as CardSearchParse.Valid).query.printingSpecific)
        assertTrue((CardSearchQuery.parse("-(r:c)") as CardSearchParse.Valid).query.printingSpecific)
    }

    @Test fun parsedTreeIsNormalized() {
        fun tree(query: String) = (CardSearchQuery.parse(query) as CardSearchParse.Valid).query.root
        assertEquals(CardSearchNode.And(listOf(CardSearchNode.Name("lightning"), CardSearchNode.Not(CardSearchNode.TypeLine("land")))),
            tree("Lightning -t:Land"))
        assertEquals(CardSearchNode.Colors(identity = false, op = CardSearchOp.GE, mask = u or r), tree("c:UR"))
        assertEquals(CardSearchNode.Colors(identity = true, op = CardSearchOp.LE, mask = w or u or b), tree("id:esper"))
        assertEquals(CardSearchNode.Colors(identity = false, op = CardSearchOp.EQ, mask = 0), tree("c:c"))
        assertEquals(CardSearchNode.Colors(identity = false, op = CardSearchOp.GE, count = 2), tree("c:m"))
        assertEquals(CardSearchNode.Stat(CardSearchStat.MANA_VALUE, CardSearchOp.LE, 3.0), tree("cmc<=3"))
        assertEquals(CardSearchNode.Not(CardSearchNode.SetCode("neo")), tree("s!=NEO"))
        assertEquals(CardSearchNode.Or(listOf(CardSearchNode.Format("commander"), CardSearchNode.Rarity(CardSearchOp.EQ, 4))), tree("f:EDH or r:m"))
        assertNull(tree(""))
    }

    @Test fun malformedQueriesReturnErrorsWithPositionsInsteadOfThrowing() {
        val rows = listOf(
            "t:" to "Write a value after t:",
            "foo:bar" to "Unknown filter \"foo:\"",
            "c:x" to "Unknown colour",
            "c>m" to "Use c:m",
            "c=6" to "0 to 5 colours",
            "mv>three" to "Write a number",
            "mv>pow" to "Compare mana value with a number",
            "r:legendary" to "Unknown rarity",
            "f:tinyleaders" to "Unknown format",
            "is:commander" to "Unknown is: value",
            "s:<x>" to "set code",
            "t>creature" to "compares only with : or =",
            "include:tokens" to "Use include:extras",
            "or bolt" to "before or",
            "bolt or" to "after or",
            "bolt or or ice" to "after or",
            "and bolt" to "both sides of and",
            "bolt )" to "Unmatched )",
            ")" to "Unmatched )",
            "()" to "Empty parentheses",
            "-" to "Nothing to exclude",
            "bolt -" to "Nothing to exclude",
            "- bolt" to "Nothing to exclude",
            "-or" to "Nothing to exclude",
            "!" to "Write a card name after !",
            "\"\"" to "Write a card name",
            "(".repeat(20) + "bolt" to "Too many nested parentheses",
            "x".repeat(1001) to "Search is too long.",
            List(101) { "a" }.joinToString(" ") to "Too many search terms",
        )
        for ((query, message) in rows) {
            val result = CardSearchQuery.parse(query)
            assertIs<CardSearchParse.Invalid>(result, query)
            assertTrue(message in result.error.message, "$query: ${result.error}")
            assertTrue(result.error.start in 0..query.length && result.error.end in result.error.start..query.length, "$query: ${result.error}")
        }
        assertEquals(CardSearchError("Unknown filter \"foo:\"", 5, 12), (CardSearchQuery.parse("bolt foo:bar") as CardSearchParse.Invalid).error)
    }

    @Test fun randomInputNeverThrows() {
        val alphabet = "abc tcmvoidrsf:=<>!-()\"'~ORand0123/{}"
        val random = kotlin.random.Random(7)
        repeat(5000) {
            val query = CharArray(random.nextInt(0, 30)) { alphabet[random.nextInt(alphabet.length)] }.concatToString()
            when (val parsed = CardSearchQuery.parse(query)) {
                is CardSearchParse.Valid -> cards.forEach { parsed.query.match(it) }
                is CardSearchParse.Invalid -> assertTrue(parsed.error.message.isNotBlank())
            }
        }
    }

    @Test fun rulesHelpers() {
        assertEquals(listOf(u, b), CardSearchRules.colorUnits("transform", listOf(face("a", "", colors = u), face("b", "", colors = b))))
        assertEquals(listOf(u or r), CardSearchRules.colorUnits("split", listOf(face("a", "", colors = u), face("b", "", colors = r))))
        assertEquals(4.0, CardSearchRules.manaValue("split", listOf(face("a", "", "{1}{R}"), face("b", "", "{1}{U}"))))
        assertEquals(2.0, CardSearchRules.manaValue("adventure", listOf(face("a", "", "{1}{R}"), face("b", "", "{5}{U}"))))
        assertEquals(0.0, CardSearchRules.statValue("*"))
        assertEquals(1.0, CardSearchRules.statValue("1+*"))
        assertEquals(-1.0, CardSearchRules.statValue("-1"))
        assertEquals(Double.POSITIVE_INFINITY, CardSearchRules.statValue("∞"))
        assertNull(CardSearchRules.statValue(""))
        assertEquals("lim-dul's vault", CardSearchRules.foldName("Lim-Dûl’s Vault"))
        assertEquals(w or g, CardColors.mask(listOf("W", "G", "x")))
        assertEquals(3, CardColors.count(u or b or r))
    }
}
