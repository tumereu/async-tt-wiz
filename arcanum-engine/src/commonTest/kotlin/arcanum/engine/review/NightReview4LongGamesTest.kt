package arcanum.engine.review

import arcanum.engine.cards.Cards
import arcanum.engine.state.PlayerId
import arcanum.parser.OracleCard
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Night review 2026-11-04: whole seeded games ([LongGame], [FakeHost]) of two decks built from the lane 2–4 mechanics (enlist, exert,
 * banding, mutate, assist, epic, the Ring, soulbond, haunt, venture and the initiative, Sieges, costs to attack and block), with every
 * invariant checked after every input (D56, D58, D59, D60).
 */
class NightReview4LongGamesTest {
    private object Lane {
        val sergeant = Cards.invent("NR4L Drafting Sergeant", "{1}{W}", "Creature — Human Soldier",
            "Enlist (As this creature attacks, you may tap a nonattacking creature you control without summoning sickness. When you do, add its power to this creature's until end of turn.)", "2/2")
        val duelist = Cards.invent("NR4L Sunscorched Duelist", "{1}{W}", "Creature — Human Warrior",
            "You may exert this creature as it attacks. When you do, it gets +1/+3 and gains lifelink until end of turn. " +
                "(An exerted creature won't untap during your next untap step.)", "2/2")
        val knight = Cards.invent("NR4L Band Shield Knight", "{1}{W}", "Creature — Human Knight", "Banding", "2/2")
        val hawk = Cards.invent("NR4L Band Sky Hawk", "{W}", "Creature — Bird", "Flying\nBanding", "1/1")
        val brute = Cards.invent("NR4L Mutate Brute", "{3}{G}{G}", "Creature — Beast", """
            Mutate {2}{G}
            Trample
            Whenever this creature mutates, draw a card.
        """.trimIndent(), "6/6")
        val beetle = Cards.invent("NR4L Mutate Beetle", "{3}{G}", "Creature — Insect", "Mutate {G}\nReach", "1/5")
        val troll = Cards.invent("NR4L Assist Troll", "{4}{G}", "Creature — Troll", "Assist (Another player can pay up to {4} of this spell's cost.)", "4/4")
        val skystalker = Cards.invent("NR4L Bond Skystalker", "{2}{W}", "Creature — Human Soldier", """
            Soulbond
            As long as this creature is paired with another creature, both creatures have flying.
        """.trimIndent(), "2/2")
        val grovebrute = Cards.invent("NR4L Bond Grovebrute", "{1}{G}", "Creature — Beast", """
            Soulbond
            As long as this creature is paired with another creature, each of those creatures gets +2/+2.
        """.trimIndent(), "3/3")
        val map = Cards.invent("NR4L Map", "{W}", "Sorcery", "Venture into the dungeon.\nDraw a card.")
        val vanguard = Cards.invent("NR4L Vanguard", "{1}{W}", "Creature — Human Soldier", "When this creature enters, you take the initiative.", "2/2")
        val siege = Cards.inventFaces("Transform",
            OracleCard("NR4L Siege of the Pass", "{1}{W}", "Battle — Siege", "When this Siege enters, you gain 2 life.", defense = "3"),
            OracleCard("NR4L Pass Warden", null, "Creature — Spirit", "Vigilance", power = "3", toughness = "3", colorIndicator = "W"),
        )
        val lurker = Cards.invent("NR4L Barrow Lurker", "{1}{B}", "Creature — Wraith", "When this creature enters, the Ring tempts you.", "2/1")
        val beckon = Cards.invent("NR4L Dark Beckoning", "{B}", "Sorcery", "The Ring tempts you.\nDraw a card.")
        val specter = Cards.invent("NR4L Haunt Gloom Specter", "{1}{B}", "Creature — Spirit", """
            Haunt (When this creature dies, exile it haunting target creature.)
            When this creature enters or the creature it haunts dies, target player loses 2 life and you gain 2 life.
        """.trimIndent(), "1/1")
        val grim = Cards.invent("NR4L Haunt Grim Visit", "{B}", "Sorcery", """
            Target player loses 1 life.
            Haunt (When this spell card is put into a graveyard after resolving, exile it haunting target creature.)
            When the creature this card haunts dies, target player loses 3 life.
        """.trimIndent())
        val drain = Cards.invent("NR4L Epic Endless Drain", "{3}{B}{B}", "Sorcery", """
            Each opponent loses 2 life.
            Epic (For the rest of the game, you can't cast spells. At the beginning of each of your upkeeps, copy this spell except for its epic ability.)
        """.trimIndent())
        val bastion = Cards.invent("NR4L Wailing Bastion", "{2}{W}", "Enchantment",
            "Creatures can't attack you unless their controller pays {1} for each creature they control that's attacking you.")
        val toll = Cards.invent("NR4L Toll of Thorns", "{1}{B}", "Enchantment",
            "Creatures can't block unless their controller pays 2 life for each blocking creature they control.")
        val blight = Cards.invent("NR4L Blight Crawler", "{1}{B}", "Creature — Phyrexian Horror", "Infect", "2/2")
        val raider = Cards.invent("NR4L Exert Raider", "{2}{B}", "Creature — Zombie Warrior",
            "You may exert this creature as it attacks. When you do, target creature can't block this turn. " +
                "(An exerted creature won't untap during your next untap step.)", "3/2")

        /** White-green: enlist, exert, banding, mutate, assist, soulbond, venture, the initiative, Sieges. */
        val whiteGreen: List<String> = List(8) { "Plains" } + List(8) { "Forest" } + listOf(
            sergeant, sergeant, duelist, duelist, knight, knight, hawk, hawk, brute, brute, beetle, beetle, troll, skystalker,
            grovebrute, grovebrute, map, map, vanguard, siege, siege, bastion, "Elvish Seer", "Canopy Visionary",
        )

        /** White-black: the Ring, haunt, epic, costs to block, infect, exert with a target, Sieges, the initiative. */
        val whiteBlack: List<String> = List(8) { "Plains" } + List(8) { "Swamp" } + listOf(
            lurker, lurker, beckon, beckon, specter, specter, grim, grim, drain, toll, blight, blight, raider, raider, sergeant,
            duelist, knight, hawk, vanguard, siege, map, "Storm Spark", "Denial", "Click Back",
        )

        fun match(): Map<PlayerId, List<String>> = mapOf(PlayerId("alice") to whiteGreen, PlayerId("bob") to whiteBlack)
        fun swapped(): Map<PlayerId, List<String>> = mapOf(PlayerId("alice") to whiteBlack, PlayerId("bob") to whiteGreen)
    }

    // CR 103, 104, D56, D58, D59: lane decks play whole games on full information
    @Test
    fun laneDecksPlayWholeGames() {
        listOf(4101L, 4102L, 4103L).forEach { seed -> LongGame(seed, jsonEvery = 20, decks = Lane.match()).play() }
        listOf(4104L, 4105L).forEach { seed -> LongGame(seed, jsonEvery = 20, decks = Lane.swapped()).play() }
    }

    // D60: on each player's view, public cards known and hidden ones not
    @Test
    fun laneDecksOnAView() {
        LongGame(4111L, jsonEvery = 20, viewer = PlayerId("bob"), decks = Lane.match()).play()
        LongGame(4112L, jsonEvery = 20, viewer = PlayerId("alice"), decks = Lane.match()).play()
    }

    // D60, D35: a token's id (here the Ring's emblem, CR 701.54c) is the same in every view, so a host can keep the views in step: bob's
    // "The Ring tempts you" spell is known in alice's view under another card id (new ids after a shuffle), and the emblem's id is
    // derived from the resolving spell's id, so the views' emblems (and the Ring's triggers on the Stack) differ from the truth's
    @Ignore("nightreview #11: token and emblem ids made by a resolving spell differ between views")
    @Test
    fun theRingsEmblemHasTheSameIdInEveryView() {
        val host = FakeHost(4300L, Lane.match(), maxInputs = 600)
        host.play()
        assertTrue(host.views.all { it.diverged == null }, host.report())
    }

    // D58, D60, D31: a host keeps both views in step with the truth through the lane mechanics
    @Test
    fun aHostKeepsBothViewsInStep() {
        val host = FakeHost(4121L, Lane.match(), optionsEvery = 30, maxInputs = 800)
        host.play()
        assertTrue(host.views.all { it.diverged == null }, host.report())
        assertTrue(host.findings.keys.none { it.startsWith("public card") || it.startsWith("another player's hand") ||
            it.startsWith("an option only") || it.startsWith("a card put into another") }, host.report())
    }
}
