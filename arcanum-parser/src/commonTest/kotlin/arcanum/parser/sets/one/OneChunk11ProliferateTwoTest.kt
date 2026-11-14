package arcanum.parser.sets.one

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.FaceDefinition
import arcanum.api.PrintedNumber
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.TokenSpec
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordActionKind
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/**
 * Phyrexia: All Will Be One chunk 11: proliferate (CR 701.34a) as the last or only step of a spell, after an optional tap or payment,
 * as a cost's effect and in modes; triggers on proliferating (CR 701.34e); toxic (CR 702.164a).
 */
class OneChunk11ProliferateTwoTest {
    private fun bound(name: String): Objects = Objects.Bound(name)
    private val proliferating = on(EventPattern.KeywordActionDone(KeywordActionKind.Proliferate, PlayerFilter.You))

    // CR 701.34a: proliferate after the look-at-top-three-and-keep-one sequence
    @Test
    fun crypticAugury() = card(
        name = "Cryptic Augury", cost = "{1}{U}", type = "Instant",
        text = "Look at the top three cards of your library. Put one of them into your hand and the rest on the bottom of your library in any order. Proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)",
    ) parsesTo abilities(
        spell(
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(3), fromTop = true), bind = "seen", players = Players.You)),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = bound("seen")), bind = "kept", chooser = Players.You),
            doing(Effect.Move(bound("kept"), Destination(Zone.Hand))),
            doing(Effect.Move(Objects.Except(bound("seen"), bound("kept")), Destination(Zone.Library, libraryPosition = -1))),
            proliferate,
        ),
    )

    // CR 701.34a, 118.12: tapping the creature is an optional action; proliferate only if it was done
    @Test
    fun gildedAnatomist() = card(
        name = "Gilded Anatomist", cost = "{3}{U}", type = "Creature — Phyrexian Wizard", pt = "2/5",
        text = "When this creature enters, you may tap it. If you do, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)",
    ) parsesTo abilities(
        whenThisEnters(
            Step.Pay(cost = Cost(listOf(CostPart.TapSelf)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(proliferate)),
        ),
    )

    // CR 603.2: one trigger for entering and dying
    @Test
    fun gulpingScrapfiend() = card(
        name = "Gulping Scrapfiend", cost = "{4}{B}", type = "Creature — Phyrexian Horror", pt = "4/4",
        text = "When this creature enters or dies, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)",
    ) parsesTo abilities(
        triggered(
            arcanum.api.Trigger.On(listOf(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard),
            )),
            proliferate,
        ),
    )

    // CR 701.34e: a trigger on proliferating
    @Test
    fun schemingAdept() = card(
        name = "Scheming Adept", cost = "{1}{B}", type = "Creature — Phyrexian Advisor", pt = "1/3",
        text = "Whenever you proliferate, each opponent loses 2 life and you gain 2 life.",
    ) parsesTo abilities(
        triggered(proliferating, loseLife(2, Players.Each(PlayerFilter.Opponent)), gainLife(2)),
    )

    // CR 701.34a, 118.4: five abilities; life payments as costs; a mana ability producing one mana of any color (CR 106.1b)
    @Test
    fun staffOfFulfillment() = card(
        name = "Staff of Fulfillment", cost = "{3}", type = "Artifact",
        text = """
            {T}, Pay 1 life: Destroy target permanent you own.
            {T}, Pay 2 life: Add one mana of any color.
            {T}, Pay 3 life: Proliferate.
            {T}, Pay 4 life: Draw a card.
            {5}: Untap this artifact.
        """,
    ) parsesTo abilities(
        activated(cost(tap, CostPart.PayLife(n(1))), destroy(target("own")),
            targets = listOf(slot("own", and(permanentFilter, ObjectFilter.OwnedBy(PlayerFilter.You))))),
        activated(cost(tap, CostPart.PayLife(n(2))), doing(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You))),
        activated(cost(tap, CostPart.PayLife(n(3))), proliferate),
        activated(cost(tap, CostPart.PayLife(n(4))), draw(1)),
        activated(cost(mana("{5}")), untapIt(Objects.Self)),
    )

    // CR 702.164a: toxic; CR 603.2: "another creature you control enters", pay {2}, then proliferate
    @Test
    fun taintedWatcher() = card(
        name = "Tainted Watcher", cost = "{1}{G}{U}", type = "Creature — Phyrexian Bird", pt = "2/3",
        text = """
            Flying
            Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
            Whenever another creature you control enters, you may pay {2}. If you do, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Toxic(1),
        triggered(
            enters(and(another, creature, you)),
            Step.Pay(cost = cost(mana("{2}")), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(proliferate)),
        ),
    )

    // CR 700.2: one mode searches for a basic land card (CR 701.23), the other proliferates
    @Test
    fun thirstingMoss() = card(
        name = "Thirsting Moss", cost = "{G}", type = "Sorcery",
        text = """
            Choose one —
            • Search your library for a basic land card, reveal it, put it into your hand, then shuffle.
            • Proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(search(basicLand(), reveal = true), move(bound("found"), Zone.Hand), shuffle),
            mode(proliferate),
        ))),
    )

    // CR 510.2, 701.34a: proliferate when combat damage is dealt to a player
    @Test
    fun thrummingSparrow() = card(
        name = "Thrumming Sparrow", cost = "{1}{U}", type = "Creature — Phyrexian Bird Horror", pt = "1/1",
        text = """
            Flying
            Whenever this creature deals combat damage to a player, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(combatDamageToAPlayer, proliferate),
    )

    @Test
    fun unnaturalRevival() = card(
        name = "Unnatural Revival", cost = "{1}{G}", type = "Sorcery",
        text = "Return target permanent card from your graveyard to your hand. Proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)",
    ) parsesTo abilities(
        spell(
            move(target("card"), Zone.Hand), proliferate,
            targets = listOf(slot("card", and(permanentCard, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))),
        ),
    )

    // CR 702.164a: toxic 3; CR 118.12: pay {1}{G} when it enters, then proliferate
    @Test
    fun venomousBruiser() = card(
        name = "Venomous Bruiser", cost = "{2}{G}{G}", type = "Creature — Phyrexian Knight", pt = "4/4",
        text = """
            Toxic 3 (Players dealt combat damage by this creature also get three poison counters.)
            When this creature enters, you may pay {1}{G}. If you do, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
        """,
    ) parsesTo abilities(
        Keyword.Toxic(3),
        whenThisEnters(
            Step.Pay(cost = manaCost("{1}{G}"), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(proliferate)),
        ),
    )

    // CR 701.34e, 700.2: a mode that creates a token only if you control no creature of that name, and a mode that grants flying and lifelink
    @Test
    fun vesnerCorpseHandler() = card(
        name = "Vesner, Corpse Handler", cost = "{U}{B}", type = "Legendary Creature — Phyrexian Zombie Wizard", pt = "1/3",
        text = """
            Lifelink, toxic 1
            Whenever you proliferate, choose one —
            • If you don't control a creature named The Hollow Warden, create The Hollow Warden, a legendary 3/3 colorless Phyrexian Golem artifact creature token.
            • Target artifact creature you control gains flying and lifelink until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        Keyword.Toxic(1),
        Ability.Triggered(proliferating, Instructions(modes = modes(
            mode(Step.If(
                Condition.Not(Condition.Exists(and(creature, ObjectFilter.Named("The Hollow Warden"), you), atLeast = 1)),
                then = listOf(create(TokenSpec.Defined(FaceDefinition(
                    name = "The Hollow Warden", colorIndicator = emptyList(), supertypes = listOf(Supertype.Legendary), types = listOf(CardType.Artifact, CardType.Creature),
                    subtypes = listOf(Subtype("Phyrexian"), Subtype("Golem")), power = PrintedNumber.Fixed(3), toughness = PrintedNumber.Fixed(3),
                )))),
            )),
            mode(gains(target("creature"), Keyword.Flying, Keyword.Lifelink), targets = listOf(slot("creature", and(artifact, creature, you)))),
        ))),
    )

    @Test
    fun vivisurgeonsLesson() = card(
        name = "Vivisurgeon's Lesson", cost = "{3}{U}{U}", type = "Sorcery",
        text = "Draw three cards. Proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)",
    ) parsesTo abilities(
        spell(draw(3), proliferate),
    )

    // CR 701.34e: "when you proliferate" works from the graveyard (CR 603.6)
    @Test
    fun voidwingHatchling() = card(
        name = "Voidwing Hatchling", cost = "{U}{B}", type = "Creature — Phyrexian Bat", pt = "2/1",
        text = """
            Flying
            Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
            When you proliferate, return this card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Toxic(1),
        triggered(proliferating, move(Objects.Self, Zone.Hand), functionsIn = listOf(Zone.Graveyard)),
    )

    @Test
    fun voltSurge() = card(
        name = "Volt Surge", cost = "{2}{R}", type = "Instant",
        text = "Volt Surge deals 3 damage to any target. Proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)",
    ) parsesTo abilities(
        spell(damage(3, toAny), proliferate, targets = listOf(anyTarget)),
    )

    @Test
    fun whisperOfTheMurk() = card(
        name = "Whisper of the Murk", cost = "{B}", type = "Instant",
        text = "Target creature gets -1/-1 until end of turn. Proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)",
    ) parsesTo abilities(
        spell(pump(target("creature"), -1, -1), proliferate, targets = listOf(slot("creature", creature))),
    )
}
