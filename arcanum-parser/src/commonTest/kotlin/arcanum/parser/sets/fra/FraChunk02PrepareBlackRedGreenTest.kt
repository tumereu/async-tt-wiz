package arcanum.parser.sets.fra

import arcanum.api.Condition
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.gtc.creature
import arcanum.parser.sets.gtc.creatureYouControl
import arcanum.parser.sets.gtc.land
import arcanum.parser.sets.gtc.targetOpponent
import arcanum.parser.sets.gtc.targetPlayer
import arcanum.parser.sets.gtc.you
import kotlin.test.Test

/**
 * Reality Fracture chunk 02: preparation cards (CR 722) in black, red and green, and the cards that prepare others. Becoming
 * prepared at the beginning of each end step after three creatures died, or each upkeep when it isn't; a mana ability whose
 * mana can't be spent on spells cast from the hand (CR 106.6); "whenever you cast a prepared spell, copy it" (CR 722.3d,
 * 707.10); a land and an artifact that make creatures prepared (only those with prepare spells can be, 722.3a); an Aura that
 * taps its creature and unprepares it (722.3b). Heartwood tokens are predefined (CR 111.10x).
 */
class FraChunk02PrepareBlackRedGreenTest {
    // CR 603.4, 700.4
    @Test
    fun lineageRememberer() = cardFaces(
        Layout.Prepare,
        card(
            name = "Lineage Rememberer",
            cost = "{1}{B}",
            type = "Creature — Vampire Warlock",
            pt = "2/2",
            text = "At the beginning of each end step, if three or more creatures died this turn, this creature becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)",
        ) to abilities(triggered(stepBegins(TurnStep.End, PlayerFilter.Any), becomesPrepared(Objects.Self),
            interveningIf = thisTurn(EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard), 3, Measure.Objects))),
        card(
            name = "Ancestor's Hunger",
            cost = "{B}",
            type = "Instant",
            text = "Target player draws three cards and loses 3 life.",
        ) to abilities(spell(draw(3, Players.Target("player")), loseLife(3, Players.Target("player")), targets = listOf(targetPlayer))),
    )

    @Test
    fun corridorJeerer() = cardFaces(
        Layout.Prepare,
        card(
            name = "Corridor Jeerer",
            cost = "{2}{R}",
            type = "Creature — Elemental Sorcerer",
            pt = "2/3",
            text = """
                This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
                {T}, Discard a card: Draw a card.
            """,
        ) to abilities(entersPrepared, activated(cost(tap, discardACard), draw(1))),
        card(
            name = "Spiteful Stanza",
            cost = "{B/R}",
            type = "Sorcery",
            text = "Spiteful Stanza deals 1 damage to target opponent.",
        ) to abilities(spell(damage(1, Recipients(players = Players.Target("opponent"))), targets = listOf(targetOpponent))),
    )

    // CR 118.12
    @Test
    fun vaingloriousSpellslinger() = cardFaces(
        Layout.Prepare,
        card(
            name = "Vainglorious Spellslinger",
            cost = "{R}",
            type = "Creature — Goblin Sorcerer",
            pt = "1/1",
            text = """
                Prowess
                This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            """,
        ) to abilities(Keyword.Prowess, entersPrepared),
        card(
            name = "Unscripted Scene",
            cost = "{R}",
            type = "Sorcery",
            text = "You may discard a card. If you do, draw a card.",
        ) to abilities(spell(Step.Pay(cost(discardACard), payer = Players.You, bind = "paid"), Step.If(Condition.Did("paid"), then = listOf(draw(1))))),
    )

    // CR 603.7b, 605.1b: a triggered mana ability each time
    @Test
    fun cinderRhymester() = cardFaces(
        Layout.Prepare,
        card(
            name = "Cinder Rhymester",
            cost = "{1}{R}{R}",
            type = "Creature — Elemental Sorcerer",
            pt = "3/3",
            text = """
                Prowess
                This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            """,
        ) to abilities(Keyword.Prowess, entersPrepared),
        card(
            name = "Lava Swell",
            cost = "{R}",
            type = "Instant",
            text = "Until end of turn, whenever you tap a Mountain for mana, add an additional {R}.",
        ) to abilities(spell(Step.Do(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.Tap(and(sub("Mountain"), you), forMana = true)),
            Instructions(steps = listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}")!!)), Players.You)))),
            duration = Duration.EndOfTurn, repeats = true)))),
    )

    @Test
    fun maneaterGardener() = cardFaces(
        Layout.Prepare,
        card(
            name = "Maneater Gardener",
            cost = "{1}{G}",
            type = "Creature — Elf Warlock",
            pt = "2/3",
            text = """
                Deathtouch
                This creature enters prepared.
                Whenever this creature deals combat damage to a player, return target land card from your graveyard to your hand.
            """,
        ) to abilities(
            Keyword.Deathtouch,
            entersPrepared,
            triggered(on(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(players = PlayerFilter.Any), combat = true)),
                move(target("card"), Zone.Hand), targets = listOf(slot("card", and(land, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))))),
        ),
        card(
            name = "Take Root",
            cost = "{G}",
            type = "Sorcery",
            text = "Search your library for a land card, put it into your graveyard, then shuffle.",
        ) to abilities(spell(
            Step.Do(Effect.Search(Players.You, land, n(1), zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
            move(Objects.Bound("found"), Zone.Graveyard),
            Step.Do(Effect.Shuffle(Players.You)),
        )),
    )

    // CR 106.6
    @Test
    fun heartgrainWhittler() = cardFaces(
        Layout.Prepare,
        card(
            name = "Heartgrain Whittler",
            cost = "{G}",
            type = "Creature — Elf Artificer",
            pt = "1/1",
            text = """
                This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
                {T}: Add {C}. This mana can't be spent to cast spells from your hand.
            """,
        ) to abilities(entersPrepared, activated(cost(tap),
            Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!), restriction = not(ObjectFilter.WasCast(Zone.Hand))), Players.You)))),
        card(
            name = "Spirit Binding",
            cost = "{2}{R/G}",
            type = "Sorcery",
            text = "Create a Heartwood token. (It's a red and green artifact with \"{T}: Add {R} or {G}.\")",
        ) to abilities(spell(createHeartwood)),
    )

    @Test
    fun workshopImproviser() = cardFaces(
        Layout.Prepare,
        card(
            name = "Workshop Improviser",
            cost = "{1}{R/G}",
            type = "Creature — Human Artificer",
            pt = "2/2",
            text = "This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)",
        ) to abilities(entersPrepared),
        card(
            name = "Spirit Binding",
            cost = "{2}{R/G}",
            type = "Sorcery",
            text = "Create a Heartwood token. (It's a red and green artifact with \"{T}: Add {R} or {G}.\")",
        ) to abilities(spell(createHeartwood)),
    )

    @Test
    fun barbquillChanter() = cardFaces(
        Layout.Prepare,
        card(
            name = "Barbquill Chanter",
            cost = "{B/R}",
            type = "Creature — Goblin Sorcerer",
            pt = "1/2",
            text = "At the beginning of your upkeep, if this creature isn't prepared, it becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)",
        ) to abilities(preparesEachUpkeep),
        card(
            name = "Spiteful Stanza",
            cost = "{B/R}",
            type = "Sorcery",
            text = "Spiteful Stanza deals 1 damage to target opponent.",
        ) to abilities(spell(damage(1, Recipients(players = Players.Target("opponent"))), targets = listOf(targetOpponent))),
    )

    @Test
    fun lashingLyricist() = cardFaces(
        Layout.Prepare,
        card(
            name = "Lashing Lyricist",
            cost = "{3}{B/R}",
            type = "Creature — Vampire Sorcerer",
            pt = "3/3",
            text = """
                This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
                As long as an opponent was dealt noncombat damage this turn, this creature has flying and haste.
            """,
        ) to abilities(entersPrepared, static(Mechanic.GainAbilities(listOf(Keyword.Flying, Keyword.Haste)), condition = thisTurn(opponentDealtNoncombatDamage))),
        card(
            name = "Spiteful Stanza",
            cost = "{B/R}",
            type = "Sorcery",
            text = "Spiteful Stanza deals 1 damage to target opponent.",
        ) to abilities(spell(damage(1, Recipients(players = Players.Target("opponent"))), targets = listOf(targetOpponent))),
    )

    @Test
    fun joineryProdigy() = cardFaces(
        Layout.Prepare,
        card(
            name = "Joinery Prodigy",
            cost = "{2}{R/G}",
            type = "Creature — Cat Druid",
            pt = "3/3",
            text = "At the beginning of your upkeep, if this creature isn't prepared, it becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)",
        ) to abilities(preparesEachUpkeep),
        card(
            name = "Spirit Binding",
            cost = "{2}{R/G}",
            type = "Sorcery",
            text = "Create a Heartwood token. (It's a red and green artifact with \"{T}: Add {R} or {G}.\")",
        ) to abilities(spell(createHeartwood)),
    )

    // CR 722.3d, 707.10
    @Test
    fun pageyHungeringTome() = card(
        name = "Pagey, Hungering Tome",
        cost = "{3}",
        type = "Legendary Artifact Creature — Book Construct",
        pt = "1/4",
        text = """
            Whenever you cast a prepared spell, copy it. You may choose new targets for the copy.
            {W}{U}{B}{R}{G}, {T}: Each creature you control becomes prepared. (Only creatures with prepare spells can become prepared.)
        """,
    ) parsesTo abilities(
            triggered(youCast(ObjectFilter.PrepareSpell), Step.Do(Effect.CopySpell(Objects.EventObject(EventRole.Object), Players.You, newTargets = true))),
            activated(cost(mana("{W}{U}{B}{R}{G}"), tap), becomesPrepared(Objects.Each(creatureYouControl))),
        )

    @Test
    fun spellclashProvingGrounds() = card(
        name = "Spellclash Proving Grounds",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {2}, {T}: Target creature that attacked this turn becomes prepared. Activate only as a sorcery. (Only creatures with prepare spells can become prepared.)
            {4}, {T}: Target creature becomes prepared.
        """,
    ) parsesTo abilities(
            activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
            activated(cost(mana("{2}"), tap), becomesPrepared(target("creature")),
                targets = listOf(slot("creature", and(creature, ObjectFilter.ThisTurn(EventPattern.Attack(ObjectFilter.Any))))), rules = asSorcery),
            activated(cost(mana("{4}"), tap), becomesPrepared(target("creature")), targets = listOf(slot("creature", creature))),
        )

    // CR 722.3b, 613.1f
    @Test
    fun endlessSyllabus() = card(
        name = "Endless Syllabus",
        cost = "{2}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, tap enchanted creature. It becomes unprepared.
            Enchanted creature loses all abilities and doesn't untap during its controller's untap step.
        """,
    ) parsesTo abilities(
            Keyword.Enchant(TargetKind.Objects(creature)),
            triggered(entersSelf, Step.Do(Effect.Tap(Objects.AttachedTo(Objects.Self))), becomesUnprepared(Objects.AttachedTo(Objects.Self))),
            static(Mechanic.LoseAbilities(), Mechanic.DoesntUntap, affects = Objects.AttachedTo(Objects.Self)),
        )
}
