package org.asyncttwiz.e2e

import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.DeckJson
import org.asyncttwiz.protocol.DefinitionFace
import org.asyncttwiz.protocol.SubmittedDefinition
import java.util.UUID

/**
 * Invented cards for the e2e games (AGENTS.md: our own names and costs, wording from real oracle templates), as a
 * device submits them in a deck: the card data the server and both devices parse. The interim app tests' cards
 * (EngineGameFixture) have the same names and texts.
 */
object TestCards {
    private fun card(name: String, typeLine: String, manaCost: String = "", rulesText: String = "", power: String = "",
        toughness: String = "", loyalty: String = "", scryfallId: String? = null): SubmittedDefinition {
        val face = DefinitionFace(UUID.nameUUIDFromBytes("e2e/$name".encodeToByteArray()).toString(), name, typeLine = typeLine,
            manaCost = manaCost, rulesText = rulesText, power = power, toughness = toughness, loyalty = loyalty, scryfallId = scryfallId)
        val definition = CardDefinition(name, listOf(face))
        return SubmittedDefinition(DeckJson.digest(definition), definition)
    }

    val mountain = card("Mountain", "Basic Land — Mountain")
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
    /** A bestow creature whose bestow needs collect evidence (owner playtest 2026-11-05 evening: its Bestow chip was missing
     * while the evidence couldn't be collected). */
    val lark = card("Cinder Lark", "Enchantment Creature — Elemental Bird", "{R}",
        "Bestow—{R}, Collect evidence 6. (To pay this bestow cost, pay {R} and exile cards with total mana value 6 or greater from your graveyard.)\n" +
            "Flying, haste\nEnchanted creature gets +1/+1 and has flying and haste.", "1", "1")
    /** Empties its caster's hand for free. */
    val thoughts = card("Empty Thoughts", "Sorcery", "{0}", "Discard your hand.")
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
    /** A free creature with haste: it attacks the turn it is cast. */
    val dasher = card("Ember Dasher", "Creature — Elemental", "{0}", "Haste", "1", "1")
    /** A free creature whose own trigger goes on the Stack when it dies (no target: nothing to choose). */
    val martyr = card("Ember Martyr", "Creature — Elemental", "{0}", "When this creature dies, you gain 2 life.", "1", "1")
    /** A free artifact whose ability's cost sacrifices a creature (an Ember Martyr's trigger then goes on the Stack above
     * the ability: two of its controller's own items there). */
    val altar = card("Ember Altar", "Artifact", "{0}", "{T}, Sacrifice another creature: Each opponent loses 2 life and you gain 2 life.")
    /** A free enchantment whose trigger begins its controller's upkeep (no input before it in the step). */
    val chime = card("Ember Chime", "Enchantment", "{0}", "At the beginning of your upkeep, you gain 1 life.")
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

    /** A split card ({2}{G} // {1}{R}{R}) as a catalog import keeps it (PhysicalCardFaces): one face holding both halves,
     * the front half's catalog record (its cost only) as the face's source data. */
    val trailBlaze: SubmittedDefinition = run {
        val halves = listOf(
            DefinitionFace(UUID.nameUUIDFromBytes("e2e/Trail".encodeToByteArray()).toString(), "Trail", typeLine = "Instant", manaCost = "{2}{G}", rulesText = "Draw a card.",
                sourceData = """{"manaCost":"{2}{G}"}"""),
            DefinitionFace(UUID.nameUUIDFromBytes("e2e/Blaze".encodeToByteArray()).toString(), "Blaze", typeLine = "Instant", manaCost = "{1}{R}{R}", rulesText = "Blaze deals 3 damage to any target."))
        val definition = org.asyncttwiz.protocol.PhysicalCardFaces.normalize(
            CardDefinition("Trail // Blaze", halves, provider = "mtgjson", layout = "split"))
        SubmittedDefinition(DeckJson.digest(definition), definition)
    }
}

/** A deck: [entries] of cards, in this order (the library's order before the opening shuffle); [side] and [maybe] its Side
 * and Maybe, the cards outside the game (LOB-141). */
data class TestDeck(val name: String, val entries: List<Pair<SubmittedDefinition, Int>>,
    val side: List<Pair<SubmittedDefinition, Int>> = emptyList(), val maybe: List<Pair<SubmittedDefinition, Int>> = emptyList()) {
    val size get() = entries.sumOf { it.second }

    companion object {
        /** 40 cards, a third each of Mountains, [creature]s and Storm Sparks (as the interim fixture's decks). */
        fun thirds(creature: SubmittedDefinition = TestCards.cub) =
            TestDeck("Thirds", listOf(TestCards.mountain to 14, creature to 13, TestCards.spark to 13))

        /** 40 cards, Mountains and Ember Wishes, with a Cinder Hound in Side and an Ember Herald in Maybe (LOB-141). */
        fun wishes() = TestDeck("Wishes", listOf(TestCards.mountain to 20, TestCards.wish to 20),
            side = listOf(TestCards.hound to 1), maybe = listOf(TestCards.herald to 1))

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
