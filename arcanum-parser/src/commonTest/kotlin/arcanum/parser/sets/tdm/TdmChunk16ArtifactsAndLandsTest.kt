package arcanum.parser.sets.tdm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Tarkir: Dragonstorm chunk 16: colorless and land cards: exile on casting a colorless spell, a search for colorless cards to cast free, hexproof from monocolored, equip cheaper for each color, the lands that enter tapped unless you control a basic type.
 */
class TdmChunk16ArtifactsAndLandsTest {
    @Test
    fun bloodrockGrotto() = card(
        name = "Bloodrock Grotto",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            When this land enters, you gain 1 life.
            {T}: Add {B} or {R}.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.Replacement(
                    event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                    modify = listOf(Modification.EntersTapped),
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
        ),
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(steps = listOf(doing(Effect.GainLife(players = Players.You, amount = n(1))))),
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(tap)),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.AddMana(
                            mana = ManaProduction(
                                kind = ManaKind.Choose(
                                    amount = n(1),
                                    colors = listOf(ColorRef.Literal(color = Color.Black), ColorRef.Literal(color = Color.Red)),
                                ),
                            ),
                            players = Players.You,
                        ),
                    ),
                ),
            ),
            rules = anyTime,
        ),
    )

    @Test
    fun summitMonastery() = card(
        name = "Summit Monastery",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control a Plains or an Island.
            {T}: Add {R}.
            {3}{R}, {T}: Exile the top card of your library. Until the end of your next turn, you may play that card.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.Replacement(
                    event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                    modify = listOf(Modification.EntersTapped),
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
            condition = Condition.Not(condition = Condition.Exists(filter = and(or(sub("Plains"), sub("Island")), you), atLeast = 1)),
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(tap)),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.AddMana(mana = ManaProduction(kind = ManaKind.Fixed(mana = ManaCost.parse("{R}")!!)), players = Players.You),
                    ),
                ),
            ),
            rules = anyTime,
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{3}{R}"), tap)),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.Move(
                            objects = Objects.LibraryCards(of = Players.You, count = n(1), fromTop = true),
                            to = Destination(zone = Zone.Exile),
                            postBind = "exiled",
                        ),
                    ),
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Play, holder = PlayerFilter.You)),
                            affects = Affected.Objects(objects = bound("exiled")),
                            duration = Duration.UntilEndOfYourNextTurn,
                        ),
                    ),
                ),
            ),
            rules = anyTime,
        ),
    )

    @Test
    fun wyrmfireBlade() = card(
        name = "Wyrmfire Blade",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +2/+2 and has hexproof from monocolored.
            Equip {4}. This ability costs {1} less to activate for each color of the creature it targets.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.ModifyPowerToughness(power = n(2), toughness = n(2)),
                Mechanic.GainAbilities(abilities = listOf(Keyword.HexproofFrom(from = ObjectFilter.Monocolored))),
            ),
            affects = Affected.Objects(objects = Objects.AttachedTo(of = Objects.Self)),
        ),
        Keyword.Equip(
            cost = Cost(parts = listOf(mana("{4}"))),
            costChange = CostChange.Less(
                mana = ManaCost.parse("{1}")!!,
                times = Value.CountDistinct(objects = target("creature"), quality = SharedQuality.Color),
            ),
        ),
    )

    @Test
    fun frontierEncampmentGrounds() = card(
        name = "Frontier Encampment Grounds",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {G}, {U}, or {R}.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.Replacement(
                    event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                    modify = listOf(Modification.EntersTapped),
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(tap)),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.AddMana(
                            mana = ManaProduction(
                                kind = ManaKind.Choose(
                                    amount = n(1),
                                    colors = listOf(
                                        ColorRef.Literal(color = Color.Green),
                                        ColorRef.Literal(color = Color.Blue),
                                        ColorRef.Literal(color = Color.Red),
                                    ),
                                ),
                            ),
                            players = Players.You,
                        ),
                    ),
                ),
            ),
            rules = anyTime,
        ),
    )

    @Test
    fun greatDawnwallCity() = card(
        name = "Great Dawnwall City",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control a Forest or a Plains.
            {T}: Add {B}.
            {1}{B}, {T}, Exile a creature card from your graveyard: Create a 1/1 white Spirit creature token.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.Replacement(
                    event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                    modify = listOf(Modification.EntersTapped),
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
            condition = Condition.Not(condition = Condition.Exists(filter = and(or(sub("Forest"), sub("Plains")), you), atLeast = 1)),
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(tap)),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.AddMana(mana = ManaProduction(kind = ManaKind.Fixed(mana = ManaCost.parse("{B}")!!)), players = Players.You),
                    ),
                ),
            ),
            rules = anyTime,
        ),
        Ability.Activated(
            cost = Cost(
                parts = listOf(
                    mana("{1}{B}"),
                    tap,
                    CostPart.Exile(what = CostObjects.Choose(filter = creature, count = n(1)), from = Zone.Graveyard),
                ),
            ),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.CreateToken(
                            token = TokenSpec.Defined(
                                face = FaceDefinition(
                                    name = "Spirit Token",
                                    colorIndicator = listOf(Color.White),
                                    types = listOf(CardType.Creature),
                                    subtypes = listOf(Subtype(name = "Spirit")),
                                    power = PrintedNumber.Fixed(value = 1),
                                    toughness = PrintedNumber.Fixed(value = 1),
                                ),
                            ),
                            count = n(1),
                            controller = Players.You,
                        ),
                    ),
                ),
            ),
            rules = anyTime,
        ),
    )

    @Test
    fun jadeForgedSentinel() = card(
        name = "Jade-Forged Sentinel",
        cost = "{4}",
        type = "Artifact Creature — Ape Snake",
        pt = "1/5",
        text = """
            Reach
            {2}, {T}: Put target card from a graveyard on the bottom of its owner's library.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{2}"), tap)),
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "card",
                        what = TargetKind.Objects(filter = and(cardFilter, inZone(Zone.Graveyard))),
                        min = n(1),
                        max = n(1),
                    ),
                ),
                steps = listOf(doing(Effect.Move(objects = target("card"), to = Destination(zone = Zone.Library, libraryPosition = -1)))),
            ),
            rules = anyTime,
        ),
    )

    @Test
    fun riverbankVillage() = card(
        name = "Riverbank Village",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control an Island or a Swamp.
            {T}: Add {G}.
            {3}{G}, {T}: Surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.Replacement(
                    event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                    modify = listOf(Modification.EntersTapped),
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
            condition = Condition.Not(condition = Condition.Exists(filter = and(or(sub("Island"), sub("Swamp")), you), atLeast = 1)),
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(tap)),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.AddMana(mana = ManaProduction(kind = ManaKind.Fixed(mana = ManaCost.parse("{G}")!!)), players = Players.You),
                    ),
                ),
            ),
            rules = anyTime,
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{3}{G}"), tap)),
            instructions = Instructions(steps = listOf(doing(Effect.Surveil(players = Players.You, count = n(2))))),
            rules = anyTime,
        ),
    )

    @Test
    fun fogriseVillage() = card(
        name = "Fogrise Village",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control a Mountain or a Forest.
            {T}: Add {U}.
            {U}, {T}: The next spell you cast this turn can't be countered.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(
                Mechanic.Replacement(
                    event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                    modify = listOf(Modification.EntersTapped),
                ),
            ),
            affects = Affected.Objects(objects = Objects.Self),
            condition = Condition.Not(condition = Condition.Exists(filter = and(or(sub("Mountain"), sub("Forest")), you), atLeast = 1)),
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(tap)),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.AddMana(mana = ManaProduction(kind = ManaKind.Fixed(mana = ManaCost.parse("{U}")!!)), players = Players.You),
                    ),
                ),
            ),
            rules = anyTime,
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{U}"), tap)),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.CantBeCountered),
                            affects = Affected.NextSpells(spells = you, count = 1),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
            rules = anyTime,
        ),
    )

    @Test
    fun orvaxEyeOfTheTempest() = card(
        name = "Orvax, Eye of the Tempest",
        cost = "{7}",
        type = "Legendary Planeswalker — Ugin",
        loyalty = "7",
        text = """
            When you cast this spell, exile up to one target permanent that's one or more colors.
            Whenever you cast a colorless spell, exile up to one target permanent that's one or more colors.
            [+2]: You gain 3 life and draw a card.
            [0]: Add {C}{C}{C}.
            [−11]: Search your library for any number of colorless nonland cards, exile them, then shuffle. Until end of turn, you may cast those cards without paying their mana costs.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.Cast(spell = isSelf, by = PlayerFilter.You))),
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "permanent",
                        what = TargetKind.Objects(filter = and(permanentFilter, not(ObjectFilter.Colorless))),
                        min = n(0),
                        max = n(1),
                    ),
                ),
                steps = listOf(doing(Effect.Move(objects = target("permanent"), to = Destination(zone = Zone.Exile), postBind = "exiled"))),
            ),
        ),
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.Cast(spell = and(ObjectFilter.Colorless, spellFilter), by = PlayerFilter.You))),
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "permanent",
                        what = TargetKind.Objects(filter = and(permanentFilter, not(ObjectFilter.Colorless))),
                        min = n(0),
                        max = n(1),
                    ),
                ),
                steps = listOf(doing(Effect.Move(objects = target("permanent"), to = Destination(zone = Zone.Exile), postBind = "exiled"))),
            ),
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(CostPart.Loyalty(amount = n(2)))),
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.GainLife(players = Players.You, amount = n(3))),
                    doing(Effect.Draw(players = Players.You, count = n(1))),
                ),
            ),
            rules = anyTime,
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(CostPart.Loyalty(amount = n(0)))),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.AddMana(
                            mana = ManaProduction(kind = ManaKind.Fixed(mana = ManaCost.parse("{C}{C}{C}")!!)),
                            players = Players.You,
                        ),
                    ),
                ),
            ),
            rules = anyTime,
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(CostPart.Loyalty(amount = n(-11)))),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.Search(
                            library = Players.You,
                            filter = and(ObjectFilter.Colorless, not(land)),
                            max = Value.Count(
                                objects = Objects.Each(filter = and(ObjectFilter.Colorless, not(land), inZone(Zone.Library), ownedByYou)),
                            ),
                            zones = listOf(Zone.Library),
                            bind = "found",
                            searcher = Players.You,
                        ),
                    ),
                    doing(Effect.Move(objects = bound("found"), to = Destination(zone = Zone.Exile), postBind = "exiled")),
                    doing(Effect.Shuffle(library = Players.You)),
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(
                                Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Cast, withoutPaying = true, holder = PlayerFilter.You),
                            ),
                            affects = Affected.Objects(objects = bound("exiled")),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
            rules = anyTime,
        ),
    )

    @Test
    fun watcherOfTheRoadside() = card(
        name = "Watcher of the Roadside",
        cost = "{3}",
        type = "Artifact Creature — Golem",
        pt = "3/2",
        text = "When this creature enters, target player mills two cards. You gain 2 life. (To mill two cards, a player puts the top two cards of their library into their graveyard.)",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "player", what = TargetKind.Players(filter = PlayerFilter.Any), min = n(1), max = n(1))),
                steps = listOf(
                    doing(Effect.Mill(players = Players.Target(slot = "player"), count = n(2))),
                    doing(Effect.GainLife(players = Players.You, amount = n(2))),
                ),
            ),
        ),
    )
}
