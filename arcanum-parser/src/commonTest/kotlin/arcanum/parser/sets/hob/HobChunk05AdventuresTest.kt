package arcanum.parser.sets.hob

import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.SubtypeRef
import arcanum.api.Supertype
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import kotlin.test.Test

/**
 * The Hobbit chunk 05: adventurer cards (CR 715), each face parsed on its own. A creature type chosen as it enters (CR 607.2d);
 * X tokens; one or two targets; two targets sharing a card type, the second chosen after the first (CR 701.12, 601.2c); a blink
 * (CR 400.7); milling and choosing from among the milled cards; cards exiled face down that may be played while you control a
 * Wizard (CR 406.3); an additional land play (CR 305.2); a cost reduction by the equipped creature's power (CR 601.2f).
 */
class HobChunk05AdventuresTest {
    // CR 715.3
    @Test
    fun bolfinSteadyWarden() = cardFaces(Layout.Adventure,
        card(
            name = "Bolfin, Steady Warden",
            cost = "{W}",
            type = "Legendary Creature — Dwarf Scout",
            text = "Lifelink",
            pt = "1/1",
        ) to abilities(
            Keyword.Lifelink,
        ),
        card(
            name = "United Care",
            cost = "{1}{W}",
            type = "Instant — Adventure",
            text = "Target artifact or creature you control gains hexproof and indestructible until end of turn. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(gains(target("permanent"), Keyword.Hexproof, Keyword.Indestructible), targets = listOf(slot("permanent", and(or(artifact, creature), you)))),
        ),
    )

    // CR 607.2d: the type chosen as it entered; X tokens of an Adventure (CR 107.3)
    @Test
    fun aSurpriseFeast() = cardFaces(Layout.Adventure,
        card(
            name = "A Surprise Feast",
            cost = "{2}{W}{W}",
            type = "Enchantment",
            text = """
                As this enchantment enters, choose a creature type.
                Creatures you control of the chosen type get +2/+2.
            """,
        ) to abilities(
            static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.AsEnters(listOf(Step.Choose(Choice.CreatureType, bind = "type", chooser = Players.You, remember = true)))))),
            static(pt(2, 2), affects = Objects.Each(and(creature, you, ObjectFilter.Sub(SubtypeRef.Remembered("type"))))),
        ),
        card(
            name = "Knock Knock",
            cost = "{X}{2}{W}",
            type = "Sorcery — Adventure",
            text = "Create X 2/2 red Dwarf creature tokens. (Then exile this card. You may cast the enchantment later from exile.)",
        ) to abilities(
            spell(create(dwarf, Value.Announced("X")), announced = listOf("X")),
        ),
    )

    // CR 115.1d: one or two targets
    @Test
    fun silkwingMoths() = cardFaces(Layout.Adventure,
        card(
            name = "Silkwing Moths",
            cost = "{2}{W}",
            type = "Creature — Insect",
            text = "Flying",
            pt = "2/2",
        ) to abilities(
            Keyword.Flying,
        ),
        card(
            name = "Stare in Awe",
            cost = "{1}{W}",
            type = "Instant — Adventure",
            text = "Tap one or two target creatures. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(tapIt(target("creatures")), targets = listOf(slot("creatures", creature, min = 1, max = 2))),
        ),
    )

    // CR 701.12a, 601.2c: the second target shares a card type with the first
    @Test
    fun tobboLuckholder() = cardFaces(Layout.Adventure,
        card(
            name = "Tobbo, Luckholder",
            cost = "{1}{U}",
            type = "Legendary Creature — Halfling Rogue",
            text = """
                Tobbo can't be blocked.
                Whenever Tobbo deals combat damage to a player, draw a card, then discard a card.
            """,
            pt = "1/1",
        ) to abilities(
            static(Mechanic.CantBeBlocked),
            triggered(combatDamageToAPlayer, draw(1), discard(1)),
        ),
        card(
            name = "Pilferer's Scheme",
            cost = "{4}{U}",
            type = "Sorcery — Adventure",
            text = "Exchange control of two target nonland permanents that share a card type. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(Step.Do(Effect.ExchangeControl(target("permanent"), target("other permanent"))), targets = listOf(
                slot("permanent", and(nonland, permanentFilter)),
                slot("other permanent", and(nonland, permanentFilter, ObjectFilter.Shares(SharedQuality.CardType, target("permanent")))).copy(distinctFrom = listOf("permanent")))),
        ),
    )

    @Test
    fun tobboUnderhillSneak() = cardFaces(Layout.Adventure,
        card(
            name = "Tobbo Underhill, Sneak",
            cost = "{2}{U}",
            type = "Legendary Creature — Halfling Rogue",
            text = "When Tobbo Underhill enters, draw a card.",
            pt = "2/1",
        ) to abilities(
            triggered(entersSelf, draw(1)),
        ),
        card(
            name = "Peek Ahead",
            cost = "{U}",
            type = "Sorcery — Adventure",
            text = "Scry 2. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(scry(2)),
        ),
    )

    // CR 400.7: the exiled cards return as new objects
    @Test
    fun mereTownBoatmen() = cardFaces(Layout.Adventure,
        card(
            name = "Mere-town Boatmen",
            cost = "{4}{U}{U}",
            type = "Creature — Human Citizen",
            text = """
                Vigilance
                Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
            """,
            pt = "6/5",
        ) to abilities(
            Keyword.Vigilance,
            Keyword.Ward(manaCost("{2}")),
        ),
        card(
            name = "Off Angling",
            cost = "{3}{U}",
            type = "Instant — Adventure",
            text = "Exile two target creatures and/or lands you control, then return them to the battlefield under their owner's control.",
        ) to abilities(
            spell(move(target("permanents"), Zone.Exile, postBind = "exiled"),
                move(Objects.Bound("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))),
                targets = listOf(slot("permanents", and(or(creature, land), you), min = 2, max = 2))),
        ),
    )

    // CR 207.2c (threshold), 400.7: the milled cards are new objects in the graveyard
    @Test
    fun mostAncientRaven() = cardFaces(Layout.Adventure,
        card(
            name = "Most Ancient Raven",
            cost = "{U}",
            type = "Creature — Bird",
            text = """
                Flying
                Threshold — This creature gets +1/+1 as long as there are seven or more cards in your graveyard.
            """,
            pt = "1/1",
        ) to abilities(
            Keyword.Flying,
            static(pt(1, 1), condition = Condition.Exists(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)), atLeast = 7)).copy(abilityWord = "Threshold"),
        ),
        card(
            name = "Whisper Riddles",
            cost = "{1}{U}",
            type = "Sorcery — Adventure",
            text = "Mill four cards, then put an instant or sorcery card from among them into your hand.",
        ) to abilities(
            spell(mill(4, postBind = "milled"),
                Step.Choose(Choice.Objects(or(instant, sorcery), n(1), n(1), from = Objects.Where(Objects.Bound("milled"), and(or(instant, sorcery), inZone(Zone.Graveyard)))),
                    bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Hand)),
        ),
    )

    @Test
    fun smeggleQuietCreeper() = cardFaces(Layout.Adventure,
        card(
            name = "Smeggle, Quiet Creeper",
            cost = "{3}{B}",
            type = "Legendary Creature — Halfling Horror",
            text = "Menace (This creature can't be blocked except by two or more creatures.)",
            pt = "4/3",
        ) to abilities(
            Keyword.Menace,
        ),
        card(
            name = "Scanty Supper",
            cost = "{B}",
            type = "Sorcery — Adventure",
            text = "Put a +1/+1 counter on up to one target creature. Target player gains 2 life. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(counters(target("creature")), gainLife(2, Players.Target("player")), targets = listOf(slot("creature", creature, min = 0), targetPlayer)),
        ),
    )

    // CR 702.111
    @Test
    fun bigWartyGoblin() = cardFaces(Layout.Adventure,
        card(
            name = "Big Warty Goblin",
            cost = "{5}{B}",
            type = "Creature — Goblin Soldier",
            text = "Each creature you control with a +1/+1 counter on it has menace. (It can't be blocked except by two or more creatures.)",
            pt = "4/4",
        ) to abilities(
            static(has(Keyword.Menace), affects = Objects.Each(and(creature, you, ObjectFilter.HasCounter(p1p1, atLeast = 1)))),
        ),
        card(
            name = "Smash! Crash!",
            cost = "{1}{B}",
            type = "Sorcery — Adventure",
            text = "Amass Goblins 2. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(amassGoblins(2)),
        ),
    )

    // CR 406.3: you may look at and play the face-down cards while you control a Wizard
    @Test
    fun grimbaldOrcsBane() = cardFaces(Layout.Adventure,
        card(
            name = "Grimbald, Orcs' Bane",
            cost = "{2}{R}",
            type = "Legendary Creature — Avatar Wizard",
            text = "Whenever you cast a noncreature spell, Grimbald gets +1/+1 until end of turn and deals 1 damage to each opponent.",
            pt = "2/3",
        ) to abilities(
            triggered(youCast(noncreatureSpell), pump(Objects.Self, 1, 1), damage(1, eachOpponent)),
        ),
        card(
            name = "Fireform",
            cost = "{1}{R}",
            type = "Sorcery — Adventure",
            text = "Look at the top two cards of your library and exile them face down. For as long as they remain exiled, you may play them if you control a Wizard.",
        ) to abilities(
            spell(Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(2), fromTop = true), Players.You, bind = "looked")),
                Step.Do(Effect.Move(Objects.Bound("looked"), Destination(Zone.Exile, faceDown = true), postBind = "exiled")),
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayLookAt(Objects.Bound("exiled"))), Affected.Players(Players.You), Duration.Indefinitely)),
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You,
                    condition = Condition.Exists(and(sub("Wizard"), you), atLeast = 1))), Affected.Objects(Objects.Bound("exiled")), Duration.Indefinitely))),
        ),
    )

    // CR 505.1a: the first main phase is the precombat main phase
    @Test
    fun gruniTheStrong() = cardFaces(Layout.Adventure,
        card(
            name = "Gruni the Strong",
            cost = "{3}{R}",
            type = "Legendary Creature — Dwarf Warrior",
            text = "At the beginning of your first main phase, add {R}{R}.",
            pt = "4/3",
        ) to abilities(
            triggered(stepBegins(TurnStep.PrecombatMain, PlayerFilter.You), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}{R}")!!)), Players.You))),
        ),
        card(
            name = "Simple Spoils",
            cost = "{2}{R}",
            type = "Sorcery — Adventure",
            text = "Simple Spoils deals 1 damage to each creature your opponents control. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(damage(1, toObjects(Objects.Each(creatureOpponentControls)))),
        ),
    )

    @Test
    fun skargTheGreatRuin() = cardFaces(Layout.Adventure,
        card(
            name = "Skarg, the Great Ruin",
            cost = "{5}{R}{R}",
            type = "Legendary Creature — Dragon",
            text = "Flying",
            pt = "5/5",
        ) to abilities(
            Keyword.Flying,
        ),
        card(
            name = "Belch Fire",
            cost = "{4}{R}",
            type = "Sorcery — Adventure",
            text = "Belch Fire deals 5 damage to target creature. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(damage(5, toObjects(target("creature"))), targets = listOf(slot("creature", creature))),
        ),
    )

    // CR 305.2
    @Test
    fun barnulfGrudgingHost() = cardFaces(Layout.Adventure,
        card(
            name = "Barnulf, Grudging Host",
            cost = "{4}{G}",
            type = "Legendary Creature — Human Bear Shapeshifter",
            text = "Trample",
            pt = "5/5",
        ) to abilities(
            Keyword.Trample,
        ),
        card(
            name = "Plow and Sow",
            cost = "{1}{G}",
            type = "Sorcery — Adventure",
            text = "You may play an additional land this turn. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ExtraLandPlays(1)), Affected.Players(Players.You), Duration.EndOfTurn))),
        ),
    )

    // CR 207.2c
    @Test
    fun elrianWoodlandLiege() = cardFaces(Layout.Adventure,
        card(
            name = "Elrian, Woodland Liege",
            cost = "{2}{G/U}{G/U}",
            type = "Legendary Creature — Elf Noble",
            text = """
                Other Elves you control get +1/+1.
                Landfall — Whenever a land you control enters, create a 1/1 green Elf creature token.
            """,
            pt = "2/3",
        ) to abilities(
            static(pt(1, 1), affects = Objects.Each(and(another, sub("Elf"), you))),
            triggered(landfall, create(elf), abilityWord = "Landfall"),
        ),
        card(
            name = "Sylvan Muster",
            cost = "{1}{G/U}{G/U}",
            type = "Sorcery — Adventure",
            text = "Mill four cards, then put up to two land cards from among them into your hand. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(mill(4, postBind = "milled"),
                Step.Choose(Choice.Objects(land, n(0), n(2), from = Objects.Where(Objects.Bound("milled"), and(land, inZone(Zone.Graveyard)))), bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Hand)),
        ),
    )

    // CR 701.23
    @Test
    fun theHeartgem() = cardFaces(Layout.Adventure,
        card(
            name = "The Heartgem",
            cost = "{5}",
            type = "Legendary Artifact",
            text = """
                Creatures you control get +1/+1.
                At the beginning of your end step, draw a card.
            """,
        ) to abilities(
            static(pt(1, 1), affects = Objects.Each(creatureYouControl)),
            triggered(stepBegins(TurnStep.End, PlayerFilter.You), draw(1)),
        ),
        card(
            name = "Seek the Core",
            cost = "{2}{W}",
            type = "Sorcery — Adventure",
            text = "Search your library for a legendary creature card, reveal it, put it into your hand, then shuffle. (Then exile this card. You may cast the artifact later from exile.)",
        ) to abilities(
            spell(search(and(legendary, creature), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
        ),
    )

    // CR 601.2f: {1} less for each point of the equipped creature's power
    @Test
    fun skarnbladeFoeCleaver() = cardFaces(Layout.Adventure,
        card(
            name = "Skarnblade, Foe-cleaver",
            cost = "{2}",
            type = "Legendary Artifact — Equipment",
            text = """
                Instant and sorcery spells you cast cost {X} less to cast, where X is equipped creature's power.
                Equip {2}
            """,
        ) to abilities(
            static(Mechanic.CostChange(and(or(instant, sorcery), spellFilter, you), CostChange.Less(ManaCost.parse("{1}")!!, power(Objects.AttachedTo(Objects.Self))))),
            Keyword.Equip(manaCost("{2}")),
        ),
        card(
            name = "Glint of Doom",
            cost = "{3}{U}",
            type = "Sorcery — Adventure",
            text = "Mill six cards, then put all instant and sorcery cards from among them into your hand. (Then exile this card. You may cast the artifact later from exile.)",
        ) to abilities(
            spell(mill(6, postBind = "milled"), move(Objects.Where(Objects.Bound("milled"), and(or(instant, sorcery), inZone(Zone.Graveyard))), Zone.Hand)),
        ),
    )

    // CR 702.6a: an equip cost that isn't only mana; CR 601.2f: an additional cost
    @Test
    fun myTreasure() = cardFaces(Layout.Adventure,
        card(
            name = "My Treasure",
            cost = "{3}",
            type = "Legendary Artifact — Equipment",
            text = """
                Equipped creature has hexproof and can't be blocked.
                Equip—{2}, Pay 2 life.
            """,
        ) to abilities(
            static(has(Keyword.Hexproof), Mechanic.CantBeBlocked, affects = Objects.AttachedTo(Objects.Self)),
            Keyword.Equip(cost(mana("{2}"), CostPart.PayLife(n(2)))),
        ),
        card(
            name = "Lure of Might",
            cost = "{1}{B}",
            type = "Instant — Adventure",
            text = """
                As an additional cost to cast this spell, sacrifice a creature.
                Draw two cards.
            """,
        ) to abilities(
            static(Mechanic.AdditionalCost(cost = cost(sacrificeA(creature)))),
            spell(draw(2)),
        ),
    )
}
