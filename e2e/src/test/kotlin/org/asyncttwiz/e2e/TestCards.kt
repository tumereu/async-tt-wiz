package org.asyncttwiz.e2e

import org.asyncttwiz.catalog.CardCatalog
import org.asyncttwiz.catalog.CardCatalogFiles
import org.asyncttwiz.catalog.CatalogCards
import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.DeckJson
import java.io.File
import java.util.UUID

/** One printed face of a [TestCard], as its record in the test cards' catalog holds it. */
data class TestFace(val name: String, val typeLine: String, val manaCost: String = "", val rulesText: String = "",
    val power: String = "", val toughness: String = "", val loyalty: String = "") {
    /** Its printing id: made from its name, so it stays the same however the cards are ordered. */
    val id: String get() = UUID.nameUUIDFromBytes("e2e/$name".encodeToByteArray()).toString()
}

/**
 * An invented card (AGENTS.md: our own names and costs, wording from real oracle templates) of the test cards' catalog
 * (tests/fixtures/card-catalog), in set [set]: the catalog's records of these cards are generated from them
 * ([TestCardsFixture], kept the same by TestCardsFixtureTest), so the server and the phones read them from their catalog
 * like any card. [name] the card's name ("A // B" for a card of two faces), [faces] its printed faces in order, [layout]
 * MTGJSON's, [scryfallId] a picture id of its own (invented) or none, [token] a token record (as dungeons are, CR 309).
 */
class TestCard(val name: String, val faces: List<TestFace>, val layout: String = "normal", val set: String = TestCards.SET,
    val scryfallId: String? = null, val token: Boolean = false) {
    /** The printing a deck or cube names it by (its first face's). */
    val printingId: String get() = faces.first().id
    /** Its card data, exactly as the server builds it from the catalog (CatalogCards.definition). */
    val definition: CardDefinition get() = TestCatalog.definition(printingId)
    val digest: String get() = DeckJson.digest(definition)
    override fun toString() = name
}

/** The test cards' catalog the e2e server serves (:catalog:testCardsCatalog), read once. */
object TestCatalog {
    private val catalog: CardCatalog by lazy {
        val directory = File(requireNotNull(System.getProperty("atw.e2e.catalogDir")) { "Run the e2e tests with ./scripts/gradle.sh :e2e:test" })
        CardCatalogFiles.read(File(directory, File(directory, "current").readText().trim() + CardCatalogFiles.SUFFIX))
    }
    private val definitions = java.util.concurrent.ConcurrentHashMap<String, CardDefinition>()

    /** The test cards' catalog's build, as a phone's installed card records name it. */
    val metadata: org.asyncttwiz.storage.CatalogMetadata by lazy {
        org.asyncttwiz.storage.CatalogMetadata(catalog.header.mtgjsonDate, catalog.header.mtgjsonVersion)
    }

    /** The faces of the card printing [printingId] is a face of, front first, as a phone's card records hold them (the
     * records the app indexes the catalog into); null for a printing the catalog doesn't have. */
    fun faces(printingId: String): List<org.asyncttwiz.storage.CatalogPrinting>? = catalog.printing[printingId]?.let { printing ->
        CatalogCards.linked(catalog, printing).map { side -> val face = catalog.faces[side.face]
            org.asyncttwiz.storage.CatalogPrinting(side.id, face.name, side.set, side.number, "English", face.side, side.scryfall, side.token,
                side.others, CatalogCards.sourceRecord(side, face).toString()) }
    }

    /** The card data of the physical card printing [printingId] is a face of. */
    fun definition(printingId: String): CardDefinition = definitions.getOrPut(printingId) {
        // As a deck or cube holds it (the server's ServerCatalog.card)
        org.asyncttwiz.protocol.PhysicalCardFaces.normalize(
            CatalogCards.definition(catalog, requireNotNull(catalog.printing[printingId]) { "No printing $printingId in the test cards' catalog" }))
    }
}

/**
 * Invented cards for the e2e games (AGENTS.md: our own names and costs, wording from real oracle templates), in the test
 * cards' catalog: the card data the server and both devices parse. The interim app tests' cards (EngineGameFixture) have
 * the same names and texts. Every card has a name of its own (the catalog knows a card by its name).
 */
object TestCards {
    /** The set of the e2e cards in the test cards' catalog. */
    const val SET = "E2E"
    /** The set of the basic lands the server supplies with pod decks (M21, as in the real catalog; LOB-052). */
    const val BASICS = "M21"

    private fun card(name: String, typeLine: String, manaCost: String = "", rulesText: String = "", power: String = "",
        toughness: String = "", loyalty: String = "", scryfallId: String? = null, set: String = SET) =
        TestCard(name, listOf(TestFace(name, typeLine, manaCost, rulesText, power, toughness, loyalty)), set = set, scryfallId = scryfallId)

    private const val LEARN = "Learn. (You may reveal a Lesson card you own from outside the game and put it into your hand, or discard a card to draw a card.)"

    // The basic lands (M21): pod decks get them from the server (LOB-052), decks use the Mountain
    val plains = card("Plains", "Basic Land — Plains", set = BASICS)
    val island = card("Island", "Basic Land — Island", set = BASICS)
    val swamp = card("Swamp", "Basic Land — Swamp", set = BASICS)
    val mountain = card("Mountain", "Basic Land — Mountain", set = BASICS)
    val forest = card("Forest", "Basic Land — Forest", set = BASICS)
    val spark = card("Storm Spark", "Instant", "{R}", "Storm Spark deals 3 damage to any target.")
    val cub = card("Ember Cub", "Creature — Elemental Cat", "{R}", power = "2", toughness = "1")
    val herald = card("Ember Herald", "Creature — Elemental", "{R}", "When this creature enters, create a 1/1 red Elemental creature token.",
        power = "1", toughness = "1")
    val hound = card("Cinder Hound", "Creature — Elemental Dog", "{R}", "{R}: This creature gets +1/+0 until end of turn.", "2", "1")
    val stride = card("Ember Stride", "Sorcery", "{R}", "Take an extra turn after this one.")
    /** A free instant: casting it gives the other player a pause (their answer may be assumed) without using mana. */
    val glimmer = card("Idle Glimmer", "Instant", "{0}", "Draw a card.")
    /** Another free instant, telling itself apart from the Glimmer by what it does. */
    val glint = card("Ember Glint", "Instant", "{0}", "You gain 2 life.")
    /** A free single-target counterspell. */
    val quench = card("Quench Spark", "Instant", "{0}", "Counter target spell.")
    /** Report KX8GYS: a spell that costs far less on a tapped creature, payable only with that target. */
    val banishing = card("Ember Banishing", "Sorcery", "{3}{R}", "This spell costs {3} less to cast if it targets a tapped creature.\nExile target creature.")
    /** A free creature that enters tapped. */
    val drowsy = card("Drowsy Pup", "Creature — Dog", "{0}", "This creature enters tapped.", "1", "1")
    /** A loop of mandatory actions on its own (CR 104.4b): it enters and is sacrificed, dies and comes back, again and again. */
    val wisp = card("Restless Wisp", "Creature — Spirit", "{0}",
        "When this creature enters, sacrifice a creature.\nWhen this creature dies, return it to the battlefield under its owner's control.",
        "1", "1")
    /** Makes a token copy of itself as it enters (when cast), drawn with a picture of its own (an invented art id). */
    val twin = card("Ember Twin", "Creature — Elemental", "{R}", "When this creature enters, if you cast it, create a token that's a copy of it.",
        "2", "1", scryfallId = UUID.nameUUIDFromBytes("e2e/art/Ember Twin".encodeToByteArray()).toString())
    /** Two different enters triggers (STK-030: their order is always asked). */
    val twinherald = card("Ember Twinherald", "Creature — Elemental", "{R}",
        "When this creature enters, you gain 1 life.\nWhen this creature enters, draw a card.", "1", "1")
    /** An X spell (STK-023): X damage to any target, X paid by its caster's mana. */
    val burst = card("Ember Burst", "Sorcery", "{X}", "Ember Burst deals X damage to any target.")
    /** A spell whose text is taller than half the table in a target bar (STK-047; the bar shows the text without reminder text,
     * so it is long by its own sentences). */
    val cascade = card("Ember Cascade", "Instant", "{R}", "Ember Cascade deals 3 damage to any target." + " You gain 1 life.".repeat(160))
    /** A bestow creature whose bestow needs collect evidence (owner playtest 2026-11-05 evening: its Bestow chip was missing
     * while the evidence couldn't be collected). */
    val lark = card("Cinder Lark", "Enchantment Creature — Elemental Bird", "{R}",
        "Bestow—{R}, Collect evidence 6. (To pay this bestow cost, pay {R} and exile cards with total mana value 6 or greater from your graveyard.)\n" +
            "Flying, haste\nEnchanted creature gets +1/+1 and has flying and haste.", "1", "1")
    /** Empties its caster's hand for free. */
    val thoughts = card("Empty Thoughts", "Sorcery", "{0}", "Discard your hand.")
    // Castability (castability spec §6): free permanents, so a board is out on the first turn
    /** Costs {1}{R} on a tapped creature, {3}{R} on another. */
    val rebuke = card("Ember Rebuke", "Sorcery", "{3}{R}", "This spell costs {2} less to cast if it targets a tapped creature.\nExile target creature.")
    /** A free rock making two mana. */
    val geode = card("Ember Geode", "Artifact", "{0}", "{T}: Add {R}{R}.")
    /** The owner's example: {R}{R} for each creature type among its targets. */
    val typeToll = card("Ember Toll", "Sorcery", "{0}", "This spell costs {R}{R} more to cast for each creature type among target creatures.\nTap up to five target creatures.")
    val quadBeast = card("Quad Beast", "Creature — Elf Bear Ox Human", "{0}", power = "1", toughness = "1")
    val twoTypes = card("Elf Bear", "Creature — Elf Bear", "{0}", power = "1", toughness = "1")
    val otherTwoTypes = card("Ox Human", "Creature — Ox Human", "{0}", power = "1", toughness = "1")
    val sameTwoTypes = card("Bear Elf", "Creature — Elf Bear", "{0}", power = "1", toughness = "1")
    val oneType = card("Lone Goat", "Creature — Goat", "{0}", power = "1", toughness = "1")
    /** {5}, {1} less for each target. */
    val hush = card("Ember Hush", "Sorcery", "{5}", "This spell costs {1} less to cast for each target creature.\nTap up to five target creatures.")
    /** A rising cost per target, strive's template. */
    val striving = card("Ember Striving", "Sorcery", "{R}", "This spell costs {1}{R} more to cast for each target creature beyond the first.\nTap up to five target creatures.")
    /** Spells that target it cost more, on the other side. */
    val wardedPup = card("Warded Pup", "Creature — Dog", "{0}", "Spells your opponents cast that target this creature cost {2} more to cast.", "1", "1")
    /** X and a reduction read from its target. */
    val cleave = card("Ember Cleave", "Sorcery", "{X}{3}{R}", "This spell costs {3} less to cast if it targets a tapped creature.\nThis spell deals X damage to target creature.")
    /** A hasty mana creature, tappable at once. */
    val sprite = card("Ember Sprite", "Creature — Elemental", "{0}", "Haste\n{T}: Add {R}.", "1", "1")
    /** A second hasty mana creature, told apart from the Sprite by its name. */
    val whisp = card("Ember Whisp", "Creature — Elemental", "{0}", "Haste\n{T}: Add {R}.", "1", "1")
    /** Pays {1} again and again: every payment for an ability is a payment. */
    val engineRock = card("Ember Engine", "Artifact", "{0}", "{1}: You gain 1 life.")
    val painStone = card("Pain Stone", "Artifact", "{0}", "{T}: Add {C}.\n{T}: Add {R}. This artifact deals 1 damage to you.")
    val tomb = card("Tomb Stone", "Artifact", "{0}", "{T}: Add {C}{C}. This artifact deals 2 damage to you.")
    val doom = card("Doom Stone", "Artifact", "{0}", "{T}: Add {R}. This artifact deals 40 damage to you.")
    val chargeStone = card("Charge Stone", "Artifact", "{0}", "This artifact enters with a charge counter on it.\n{T}, Remove a charge counter from this artifact: Add {R}.")
    val bloodAltar = card("Blood Altar", "Artifact", "{0}", "Sacrifice a creature: Add {R}.")
    val signet = card("Ember Signet", "Artifact", "{0}", "{1}, {T}: Add {R}{U}.")
    val filter = card("Filter Stone", "Artifact", "{0}", "{R/U}, {T}: Add two mana in any combination of {R} and/or {U}.")
    val prism = card("Ember Prism", "Artifact", "{0}", "{T}: Add one mana of any color.")
    val boost = card("Mountain Hymn", "Enchantment", "{0}", "Whenever you tap a Mountain for mana, add an additional {R}.")
    /** A way Autotap can't find (an announced amount): {U} through it. */
    val xStone = card("Measure Stone", "Artifact", "{0}", "{X}, {T}: Add X mana of any one color.")
    val blueNote = card("Blue Note", "Sorcery", "{U}", "Draw a card.")
    val twoPence = card("Two Pence", "Sorcery", "{2}", "Draw a card.")
    /** Its additional cost is a land: tapped for its mana first, then sacrificed (CR 601.2g before 601.2h). */
    val landEater = card("Land Eater", "Sorcery", "{R}", "As an additional cost to cast this spell, sacrifice a land.\nDraw a card.")
    val tapPact = card("Tap Pact", "Sorcery", "{R}", "As an additional cost to cast this spell, tap an untapped creature you control.\nDraw a card.")
    val bloodPact = card("Blood Pact", "Sorcery", "{R}", "As an additional cost to cast this spell, pay 2 life.\nDraw a card.")
    val offering = card("Altar Offering", "Sorcery", "{R}", "As an additional cost to cast this spell, sacrifice a creature.\nDraw a card.")
    /** An instant castable only through a pain source, for Quick mode's windows. */
    val redFlash = card("Red Flash", "Instant", "{R}", "Draw a card.")
    /** An instant castable only on a tapped creature with this mana. */
    val tappedFlash = card("Tapped Flash", "Instant", "{1}{R}", "This spell costs {1} less to cast if it targets a tapped creature.\nTap target creature.")
    /** Counters unless paid, for free. */
    val freeTax = card("Free Tax", "Instant", "{0}", "Counter target spell unless its controller pays {1}.")
    val emberStone = card("Ember Stone", "Artifact", "{0}", "{T}: Add {C}.")
    /** A free source with a second ability it can't use early on (TBL-124): its chips open even with only the mana usable. */
    /** A spell with convoke and something to do (STK-024: its panel always opens). */
    val rally = card("Ember Rally", "Sorcery", "{2}{R}", "Convoke\nDraw a card.")
    val cairn = card("Ember Cairn", "Artifact", "{0}", "{T}: Add {R}.\n{5}: You gain 1 life.")
    val study = card("Ember Reading", "Sorcery", "{0}", "Draw a card.")
    /** A triggered mana ability of a colour chosen as it resolves (CR 605.1b, 605.4a). */
    val bounty = card("Ember Bounty", "Enchantment — Aura", "{0}", "Enchant land\nWhenever enchanted land is tapped for mana, its controller adds an additional one mana of any color.")
    val redBlue = card("Red Blue", "Sorcery", "{R}{U}", "Draw a card.")
    val oneTap = card("One Tap", "Sorcery", "{1}", "As an additional cost to cast this spell, tap an untapped creature you control.\nDraw a card.")
    /** A cost a panel can change: kicker adds to it. */
    val clan = card("Ember Clan", "Creature — Elemental", "{1}", "Kicker {1}", "1", "1")
    /** Two ways, offspring dearer: for a panel paying several sources. */
    val brood = card("Ember Brood", "Creature — Elemental", "{0}", "Offspring {2}", "1", "1")
    /** A free mana rock: identical ones lie in one stack (TBL-053). */
    val shard = card("Ember Shard", "Artifact", "{0}", "{T}: Add {R}.")
    /** A free creature with offspring {R} (ZON-080: its "Cast" and "Cast with offspring {R}" chips). */
    val kit = card("Ember Kit", "Creature — Elemental Fox", "{0}",
        "Offspring {R} (You may pay an additional {R} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)",
        "1", "1")
    /** A free creature with a pump ability. */
    val ashHound = card("Ash Hound", "Creature — Elemental Dog", "{0}", "{R}: This creature gets +1/+0 until end of turn.", "1", "1")
    /** A free creature with nothing to do. */
    val pup = card("Ash Pup", "Creature — Elemental Dog", "{0}", power = "1", toughness = "1")
    /** A free creature with trample (CMB-033: its damage divides among its blockers and the defender). */
    val brute = card("Cinder Brute", "Creature — Elemental Ox", "{0}", "Trample", "2", "2")
    /** Free Equipment with a free equip. */
    val blade = card("Ember Blade", "Artifact — Equipment", "{0}", "Equipped creature gets +1/+0.\nEquip {0}")
    /** A sorcery too dear for one Mountain that cycles for {R} (ZON-080: its Cycling chip). */
    val musing = card("Ember Musing", "Sorcery", "{3}{R}", "Draw two cards.\nCycling {R} ({R}, Discard this card: Draw a card.)")
    /** An instant milling fourteen that cycles for {R} with a "when you cycle" trigger (owner playtest 2026-11-07: worded
     * as a real cycling card with a cycle trigger, STK-057). */
    val reason = card("Shattered Reason", "Instant", "{1}{U}{U}",
        "Each opponent mills fourteen cards.\nCycling {R} ({R}, Discard this card: Draw a card.)\nWhen you cycle this card, each opponent mills four cards.")
    /** A free instant dealing 1 damage to any target (a targeted spell that needs no mana). */
    val flick = card("Ember Flick", "Instant", "{0}", "Ember Flick deals 1 damage to any target.")
    /** A fetch-style land (owner playtest 2026-11-05 evening): its ability searches its controller's library for a basic land. */
    val cinderTrail = card("Cinder Trail", "Land",
        rulesText = "{T}, Pay 1 life, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield, then shuffle.")

    /** A free sorcery that gives its caster an emblem (TRN-053: the emblem's round chip by the life square). */
    val sigil = card("Ember Sigil", "Sorcery", "{0}", "You get an emblem with \"Creatures you control get +1/+1.\"")
    /** A free sorcery: the Ring tempts its caster (TBL-133, TRN-072: a Ring-bearer's chip and the Ring's chip by the life square). */
    val beckoning = card("Ember Beckoning", "Sorcery", "{0}", "The Ring tempts you.")
    /** A free creature whose enters trigger asks to pay {1} or not (STK-049: Pay or Don't pay). */
    val tollkeeper = card("Ember Tollkeeper", "Creature — Elemental", "{0}", "When this creature enters, you may pay {1}. If you do, draw a card.", "1", "1")
    /** A free legendary creature: a second one makes its controller keep one (CR 704.5j). */
    val sovereign = card("Ember Sovereign", "Legendary Creature — Elemental Noble", "{0}", power = "1", toughness = "1")
    /** A free creature that names a nonland card as it enters (STK-041, CR 201.4). */
    val muzzle = card("Ember Muzzle", "Artifact Creature — Construct", "{0}",
        "As this creature enters, choose a nonland card name.\nActivated abilities of sources with the chosen name can't be activated.", "1", "1")
    /** A free enchantment doubling its controller's damage (a replacement effect, CR 614.1a). */
    val amplifier = card("Ember Amplifier", "Enchantment", "{0}",
        "If a source you control would deal damage to a permanent or player, it deals double that damage instead.")
    /** A free enchantment preventing its controller's damage to an opponent, who mills that many cards instead (a prevention
     * effect with an additional effect, CR 615.5). */
    val mindtrap = card("Ember Mindtrap", "Enchantment", "{0}",
        "If a source you control would deal damage to an opponent, prevent that damage and each opponent mills that many cards.")
    /** A free sorcery whose resolving shows its caster the target player's hidden hand (TRN-067). */
    val glance = card("Ember Glance", "Sorcery", "{0}", "Look at target player's hand.")
    /** A free sorcery revealing the target player's hand (LOB-135: held back unless "Allow assumes that reveal hands"). */
    val exposure = card("Ember Exposure", "Sorcery", "{0}", "Target player reveals their hand.")
    /** A free sorcery revealing the target player's top two library cards (LOB-135: let through by default). */
    val augury = card("Ember Augury", "Sorcery", "{0}", "Target player reveals the top two cards of their library.")
    /** A free enchantment whose trigger its controller gets on the other player's cheap spell (their spell puts it on top). */
    val warden = card("Ember Warden", "Enchantment", "{0}", "Whenever an opponent casts a spell with mana value 2 or less, you gain 2 life.")
    /** A free sorcery that reveals its caster's top card and puts it into their hand (HIS-110: the other player's reveal
     * panel). */
    val insight = card("Ember Insight", "Sorcery", "{0}", "Reveal the top card of your library and put that card into your hand.")
    /** A free sorcery with a reveal until a land (HIS-110: House Cartographer's wording). */
    val scouting = card("Ember Scouting", "Sorcery", "{0}", "Reveal cards from the top of your library until you reveal a land card. Put that card into your hand and the rest on the bottom of your library in a random order.")
    /** A free sorcery whose resolution stops on the top two library cards: which one is manifested (report DH3QX6). */
    val dread = card("Ember Dread", "Sorcery", "{0}", "Manifest dread.")
    /** A free creature with haste: it attacks the turn it is cast. */
    val dasher = card("Ember Dasher", "Creature — Elemental", "{0}", "Haste", "1", "1")
    /** A free creature whose own trigger goes on the Stack when it dies (no target: nothing to choose). */
    val martyr = card("Ember Martyr", "Creature — Elemental", "{0}", "When this creature dies, you gain 2 life.", "1", "1")
    /** A free creature with a trigger of its own when another of its controller's creatures dies (with an Ember Martyr
     * dying: two different triggers, one of a source that has left the battlefield; problem report 3UE2NW). */
    val mourner = card("Ember Mourner", "Creature — Elemental", "{0}", "Whenever another creature you control dies, you gain 1 life.", "1", "1")
    /** A free artifact whose ability's cost sacrifices a creature (an Ember Martyr's trigger then goes on the Stack above
     * the ability: two of its controller's own items there). */
    val altar = card("Ember Altar", "Artifact", "{0}", "{T}, Sacrifice another creature: Each opponent loses 2 life and you gain 2 life.")
    /** A free creature whose death trigger targets a player when it or another creature dies (owner playtest 2026-11-11: the
     * trigger's target bar showed no sentence when the creature was sacrificed). */
    val vigil = card("Ember Vigil", "Creature — Vampire", "{0}", "Whenever this creature or another creature dies, target player loses 1 life and you gain 1 life.", "0", "1")
    /** Free Equipment whose equip cost sacrifices a creature (owner, 2026-11-11: which pick is which, the target or the sacrifice). */
    val hauberk = card("Ember Hauberk", "Artifact — Equipment", "{0}", "Equipped creature gets +4/+2.\nEquip—Sacrifice a creature.")
    /** A free enchantment whose trigger begins its controller's upkeep (no input before it in the step). */
    val chime = card("Ember Chime", "Enchantment", "{0}", "At the beginning of your upkeep, you gain 1 life.")
    /** CMB-044: a free enchantment whose cost to attack its controller has an X the board defines (their enchantments). */
    val bulwark = card("Ember Bulwark", "Enchantment", "{0}",
        "Creatures can't attack you unless their controller pays {X} for each creature they control that's attacking you, where X is the number of enchantments you control.")
    /** A free Aura (D1: an enchanted creature beside an identical one, told apart on the table). */
    val mantle = card("Ember Mantle", "Enchantment — Aura", "{0}", "Enchant creature\nEnchanted creature gets +1/+1.")
    /** A free edict (D1: the other player picks their creature to sacrifice on the table). */
    val edict = card("Ember Edict", "Sorcery", "{0}", "Each opponent sacrifices a creature of their choice.")
    /** A free instant with two targets (TBL-130: targets picked from one battlefield group). */
    /** A free sorcery that ends a game by the rules (TRN-065: the other player's life reaches 0). */
    val ruin = card("Ember Ruin", "Sorcery", "{0}", "Each opponent loses 20 life.")
    val snare = card("Frost Snare", "Instant", "{0}", "Tap up to two target creatures.")
    /** A free wish (LOB-141): its caster may put a card they own from outside the game (their Side and Maybe) into their hand. */
    val wish = card("Ember Wish", "Instant", "{0}", "You may put a card you own from outside the game into your hand.")

    /** Learn (CR 701.48a, LOB-141), for free. */
    val learner = card("Ember Study", "Sorcery", "{0}", LEARN)
    /** Lessons, what learn takes from outside the game. */
    val primer = card("Ember Primer", "Sorcery — Lesson", "{R}", "Draw a card.")
    val treatise = card("Ember Treatise", "Sorcery — Lesson", "{1}{R}", "Draw two cards.")
    /** A free Lesson that learns: a pod of only this card has a Lesson in every hand and outside the game (LOB-141). */
    val lecture = card("Ember Lecture", "Sorcery — Lesson", "{0}", LEARN)

    /** Collects evidence as its trigger resolves (CR 701.59a). */
    val witness = card("Ember Witness", "Creature — Human Detective", "{0}", "When this creature enters, you may collect evidence 2. If you do, draw a card.", "1", "1")
    /** A free permanent, so what it mills is all that is in the graveyard. */
    val sifter = card("Ember Sifter", "Artifact", "{0}", "When this artifact enters, mill two cards.")

    /** A free source of two colours (STK-024: its ways to pay). */
    val fork = card("Ember Fork", "Artifact", "{0}", "{T}: Add {R} or {U}.")
    /** A free Vehicle with crew 2 (CR 702.122a). */
    val cart = card("Ember Cart", "Artifact — Vehicle", "{0}", "Crew 2", "3", "3")
    /** CMB-050: a free creature with lifelink and haste: it attacks the turn it is cast, its controller gaining the damage. */
    val leech = card("Ember Leech", "Creature — Elemental Leech", "{0}", "Lifelink\nHaste", "2", "2")
    /** TBL-021, TBL-135: a free planeswalker, its loyalty the number in its corner. */
    val sage = card("Ember Sage", "Legendary Planeswalker — Chandra", "{0}", "[+1]: You gain 1 life.", loyalty = "3")

    /** HIS-114: a free permanent that puts itself into its graveyard, and free spells moving a card out of it: to the hand
     * (a hidden zone) and to the battlefield. */
    val trinket = card("Ember Trinket", "Artifact", "{0}", "Sacrifice this artifact: Draw a card.")
    val recall = card("Ember Recall", "Sorcery", "{0}", "Return target card from your graveyard to your hand.")
    val revival = card("Ember Revival", "Sorcery", "{0}", "Return target artifact card from your graveyard to the battlefield.")

    /** A modal double-faced card, an instant with a land for a back (STK-041). */
    val pathCrag = TestCard("Ember Path // Ember Crag", listOf(
        TestFace("Ember Path", "Instant", "{1}{R}", "Draw a card."),
        TestFace("Ember Crag", "Land", rulesText = "{T}: Add {R}.")), layout = "modal_dfc")

    /** A split card ({2}{G} // {1}{R}{R}): the catalog keeps it as one face holding both halves (PhysicalCardFaces). */
    val trailBlaze = TestCard("Trail // Blaze", listOf(
        TestFace("Trail", "Instant", "{2}{G}", "Draw a card."),
        TestFace("Blaze", "Instant", "{1}{R}{R}", "Blaze deals 3 damage to any target.")), layout = "split")

    /** Owner gallery review, 2026-11-06: rules text Automatum can't do (UnsupportedPodSourceTest). */
    val relic = card("Odd Relic", "Artifact", "{1}", "Whenever the moon is full, this artifact hums a slow tune.")

    /** LOB-117: a free sorcery that ventures into the dungeon, and the dungeons the server brings with its player's Ready
     * (CR 309.2): a plain one and an Undercity (CR 701.49d), token records as the catalog keeps dungeons. */
    val torchMap = card("Dungeon Torch Map", "Sorcery", "{0}", "Venture into the dungeon.")
    val crypt = TestCard("Dungeon Test Crypt", listOf(TestFace("Dungeon Test Crypt", "Dungeon", rulesText = """
        Crypt Door — You gain 1 life. (Leads to: Bone Hall, Dark Well)
        Bone Hall — Each opponent loses 1 life. (Leads to: Final Vault)
        Dark Well — You gain 2 life. (Leads to: Final Vault)
        Final Vault — You gain 3 life.
    """.trimIndent())), layout = "token", token = true)
    val sunkenCity = TestCard("Dungeon Test Sunken City", listOf(TestFace("Dungeon Test Sunken City", "Dungeon — Undercity", rulesText = """
        You can't enter this dungeon unless you "venture into Undercity."
        Flooded Stair — You gain 4 life. (Leads to: Abyss Throne)
        Abyss Throne — You gain 5 life.
    """.trimIndent())), layout = "token", token = true)

    /** DRF-051: the card the server gives each player of a pod with Librarians (it looks it up by this name), a stand-in of
     * our own: its printed values only, no draft text. */
    val librarian = card("Cogwork Librarian", "Artifact Creature — Construct", "{4}", power = "3", toughness = "3")

    /** Every test card, by name (the catalog's collector numbers follow this order). */
    val all: List<TestCard> by lazy {
        TestCards::class.java.declaredFields.filter { TestCard::class.java.isAssignableFrom(it.type) }
            .onEach { it.isAccessible = true }.map { it.get(this) as TestCard }.sortedBy { it.name }
    }
}

/** A deck: [entries] of cards, in this order (the library's order before the opening shuffle); [side] and [maybe] its Side
 * and Maybe, the cards outside the game (LOB-141). */
data class TestDeck(val name: String, val entries: List<Pair<TestCard, Int>>,
    val side: List<Pair<TestCard, Int>> = emptyList(), val maybe: List<Pair<TestCard, Int>> = emptyList()) {
    val size get() = entries.sumOf { it.second }

    companion object {
        /** 40 cards, a third each of Mountains, [creature]s and Storm Sparks (as the interim fixture's decks). */
        fun thirds(creature: TestCard = TestCards.cub) =
            TestDeck("Thirds", listOf(TestCards.mountain to 14, creature to 13, TestCards.spark to 13))

        /** 40 cards, Mountains and Ember Wishes, with a Cinder Hound in Side and an Ember Herald in Maybe (LOB-141). */
        fun wishes() = TestDeck("Wishes", listOf(TestCards.mountain to 20, TestCards.wish to 20),
            side = listOf(TestCards.hound to 1), maybe = listOf(TestCards.herald to 1))

        /** 40 cards, Mountains and Ember Studies, with an Ember Primer and a Cinder Hound in Side and an Ember Treatise in Maybe (LOB-141). */
        fun studies() = TestDeck("Studies", listOf(TestCards.mountain to 20, TestCards.learner to 20),
            side = listOf(TestCards.primer to 1, TestCards.hound to 1), maybe = listOf(TestCards.treatise to 1))

        /** 40 cards: Ember Witnesses and Ember Sifters, and Storm Sparks ({R}) to mill. */
        fun witnesses() = TestDeck("Witnesses", listOf(TestCards.witness to 8, TestCards.sifter to 8, TestCards.spark to 24))

        /** 40 cards: Mountains and Idle Glimmers (a free instant: its holder can always respond). */
        fun glimmers() = TestDeck("Glimmers", listOf(TestCards.mountain to 20, TestCards.glimmer to 20))

        /** 40 cards: Mountains and Ember Bursts (an X spell). */
        fun bursts() = TestDeck("Bursts", listOf(TestCards.mountain to 20, TestCards.burst to 20))

        /** 40 cards: Mountains and Cinder Larks (bestow with collect evidence). */
        fun larks() = TestDeck("Larks", listOf(TestCards.mountain to 20, TestCards.lark to 20))

        /** 40 cards: Mountains and Ember Sigils (a free emblem). */
        fun sigils() = TestDeck("Sigils", listOf(TestCards.mountain to 20, TestCards.sigil to 20))

        /** 40 cards: Mountains and Storm Sparks. */
        fun sparks() = TestDeck("Sparks", listOf(TestCards.mountain to 20, TestCards.spark to 20))

        /** 40 cards: Mountains and Ember Insights (a free reveal). */
        fun insights() = TestDeck("Insights", listOf(TestCards.mountain to 20, TestCards.insight to 20))

        /** 40 cards: Mountains and Ember Dreads (a free manifest dread). */
        fun dreads() = TestDeck("Dreads", listOf(TestCards.mountain to 20, TestCards.dread to 20))

        /** 40 cards: Mountains and Ember Musings (cycling). */
        fun musings() = TestDeck("Musings", listOf(TestCards.mountain to 20, TestCards.musing to 20))

        /** 40 cards: Mountains, Ember Shards (a free mana rock) and Ember Kits (offspring {R}). */
        fun kits() = TestDeck("Kits", listOf(TestCards.mountain to 14, TestCards.shard to 13, TestCards.kit to 13))

        /** 40 cards: Mountains, Cinder Trails (a fetch-style land) and Storm Sparks. */
        fun trails() = TestDeck("Trails", listOf(TestCards.mountain to 14, TestCards.cinderTrail to 13, TestCards.spark to 13))

        /** Two of each of the [TestCards] (21 different cards): a deck with as many different cards as a real one has. */
        fun everyCard() = TestDeck("Every card", with(TestCards) {
            listOf(mountain, spark, cub, herald, hound, stride, glimmer, glint, quench, wisp, twin, burst, lark, thoughts, shard,
                ashHound, pup, brute, blade, musing, cinderTrail).map { it to 2 }
        })
    }
}
