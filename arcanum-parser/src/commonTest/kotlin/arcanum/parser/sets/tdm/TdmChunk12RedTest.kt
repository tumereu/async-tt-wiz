package arcanum.parser.sets.tdm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Tarkir: Dragonstorm chunk 12: red cards: an exile instead of dying, random exile from the graveyard to play, damage divided between one or two targets, a creature that was dealt damage this turn.
 */
class TdmChunk12RedTest {
    @Test
    fun flameEdgeForm() = card(
        name = "Flame-Edge Form",
        cost = "{1}{R}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature
            When this Aura enters, enchanted creature gains first strike until end of turn.
            Enchanted creature gets +2/+0.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Enchant(what = TargetKind.Objects(filter = creature)),
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.GainAbilities(abilities = listOf(Keyword.FirstStrike))),
                            affects = Affected.Objects(objects = Objects.AttachedTo(of = Objects.Self)),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
        ),
        Ability.Static(
            mechanics = listOf(Mechanic.ModifyPowerToughness(power = n(2), toughness = n(0))),
            affects = Affected.Objects(objects = Objects.AttachedTo(of = Objects.Self)),
        ),
    )

    @Test
    fun passingEffigy() = card(
        name = "Passing Effigy",
        cost = "{R}",
        type = "Creature — Elemental",
        pt = "2/2",
        text = """
            Haste
            At the beginning of your end step, return this creature to its owner's hand. (Return it only if it's on the battlefield.)
            {2}{R}: This creature gets +2/+0 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You))),
            instructions = Instructions(steps = listOf(doing(Effect.Move(objects = Objects.Self, to = Destination(zone = Zone.Hand))))),
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{2}{R}"))),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.ModifyPowerToughness(power = n(2), toughness = n(0))),
                            affects = Affected.Objects(objects = Objects.Self),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
            rules = anyTime,
        ),
    )

    @Test
    fun prismaticTiger() = card(
        name = "Prismatic Tiger",
        cost = "{4}{R}",
        type = "Creature — Cat",
        pt = "3/4",
        text = "When this creature enters, if you cast it, add {W}{U}{B}{R}{G}.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.AddMana(
                            mana = ManaProduction(kind = ManaKind.Fixed(mana = ManaCost.parse("{W}{U}{B}{R}{G}")!!)),
                            players = Players.You,
                        ),
                    ),
                ),
            ),
            interveningIf = Condition.CastFrom(zone = null, of = Objects.Self),
        ),
    )

    @Test
    fun painstakingArtisan() = card(
        name = "Painstaking Artisan",
        cost = "{3}{R}",
        type = "Creature — Djinn Artificer",
        pt = "3/3",
        text = """
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            When this creature enters, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
    ) parsesTo abilities(
        Keyword.Prowess,
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(doing(Effect.CreateToken(token = TokenSpec.Predefined(name = "Treasure"), count = n(1), controller = Players.You))),
            ),
        ),
    )

    @Test
    fun liriensReproof() = card(
        name = "Lirien's Reproof",
        cost = "{4}{R}",
        type = "Instant",
        text = "Lirien's Reproof deals 5 damage to target creature. Add {U}{R}{W}. If that creature would die this turn, exile it instead.",
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                steps = listOf(
                    doing(Effect.DealDamage(to = Recipients(objects = target("creature")), amount = n(5), source = Objects.Self)),
                    doing(
                        Effect.AddMana(
                            mana = ManaProduction(kind = ManaKind.Fixed(mana = ManaCost.parse("{U}{R}{W}")!!)),
                            players = Players.You,
                        ),
                    ),
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(
                                Mechanic.Replacement(
                                    event = EventPattern.ZoneChange(
                                        objects = ObjectFilter.Is(objects = target("creature")),
                                        from = Zone.Battlefield,
                                        to = Zone.Graveyard,
                                    ),
                                    modify = listOf(Modification.ChangeDestination(to = Destination(zone = Zone.Exile))),
                                ),
                            ),
                            affects = null,
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun crushingSurge() = card(
        name = "Crushing Surge",
        cost = "{2}{R}",
        type = "Instant",
        text = """
            Choose one or both —
            • Crushing Surge deals 3 damage to target creature.
            • Destroy target noncreature artifact.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                modes = Modes(
                    options = listOf(
                        Mode(
                            instructions = Instructions(
                                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                                steps = listOf(
                                    doing(
                                        Effect.DealDamage(
                                            to = Recipients(objects = target("creature")),
                                            amount = n(3),
                                            source = Objects.Self,
                                        ),
                                    ),
                                ),
                            ),
                            pawprints = 0,
                        ),
                        Mode(
                            instructions = Instructions(
                                targets = listOf(
                                    TargetSlot(
                                        name = "artifact",
                                        what = TargetKind.Objects(filter = and(not(creature), artifact)),
                                        min = n(1),
                                        max = n(1),
                                    ),
                                ),
                                steps = listOf(doing(Effect.Destroy(objects = target("artifact")))),
                            ),
                            pawprints = 0,
                        ),
                    ),
                    min = 1,
                    max = 2,
                    chooser = Players.You,
                ),
            ),
        ),
    )

    @Test
    fun rescuingLeopard() = card(
        name = "Rescuing Leopard",
        cost = "{2}{R}",
        type = "Creature — Cat",
        pt = "4/2",
        text = "Whenever this creature becomes tapped, you may discard a card. If you do, draw a card.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.Tap(objects = isSelf))),
            instructions = Instructions(
                steps = listOf(
                    Step.Pay(
                        cost = Cost(parts = listOf(CostPart.Discard(what = CostObjects.Choose(filter = cardFilter, count = n(1))))),
                        payer = Players.You,
                        bind = "paid",
                    ),
                    Step.If(
                        condition = Condition.Did(name = "paid"),
                        then = listOf(doing(Effect.Draw(players = Players.You, count = n(1)))),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun graspTheMoment() = card(
        name = "Grasp the Moment",
        cost = "{2}{R}",
        type = "Instant",
        text = """
            Choose one —
            • Exile the top two cards of your library. Until the end of your next turn, you may play those cards.
            • Up to two target creatures each get +2/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                modes = Modes(
                    options = listOf(
                        Mode(
                            instructions = Instructions(
                                steps = listOf(
                                    doing(
                                        Effect.Move(
                                            objects = Objects.LibraryCards(of = Players.You, count = n(2), fromTop = true),
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
                            pawprints = 0,
                        ),
                        Mode(
                            instructions = Instructions(
                                targets = listOf(TargetSlot(name = "creatures", what = TargetKind.Objects(filter = creature), min = n(0), max = n(2))),
                                steps = listOf(
                                    doing(
                                        Effect.ApplyMechanics(
                                            mechanics = listOf(Mechanic.ModifyPowerToughness(power = n(2), toughness = n(1))),
                                            affects = Affected.Objects(objects = target("creatures")),
                                            duration = Duration.EndOfTurn,
                                        ),
                                    ),
                                ),
                            ),
                            pawprints = 0,
                        ),
                    ),
                    min = 1,
                    max = 1,
                    chooser = Players.You,
                ),
            ),
        ),
    )

    @Test
    fun startlingSharpshooter() = card(
        name = "Startling Sharpshooter",
        cost = "{1}{R}",
        type = "Creature — Human Archer",
        pt = "1/3",
        text = """
            Reach
            Whenever another creature you control enters, this creature deals 1 damage to target opponent.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.ZoneChange(objects = and(not(isSelf), creature, you), to = Zone.Battlefield))),
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "opponent", what = TargetKind.Players(filter = PlayerFilter.Opponent), min = n(1), max = n(1))),
                steps = listOf(
                    doing(
                        Effect.DealDamage(
                            to = Recipients(players = Players.Target(slot = "opponent")),
                            amount = n(1),
                            source = Objects.Self,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun peakIntimidator() = card(
        name = "Peak Intimidator",
        cost = "{3}{R}",
        type = "Creature — Yeti",
        pt = "4/3",
        text = """
            Reach
            When this creature enters, target creature can't block this turn.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.CantBlock(attackers = ObjectFilter.Any)),
                            affects = Affected.Objects(objects = target("creature")),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun dusklightStrikemaster() = card(
        name = "Dusklight Strikemaster",
        cost = "{1}{R}",
        type = "Creature — Human Monk",
        pt = "3/1",
        text = """
            {T}: Add {R}.
            {2}{R}, {T}, Sacrifice this creature: It deals 6 damage to target creature with flying.
        """,
    ) parsesTo abilities(
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
            cost = Cost(parts = listOf(mana("{2}{R}"), tap, CostPart.Sacrifice(what = CostObjects.Self))),
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "creature",
                        what = TargetKind.Objects(filter = and(creature, ObjectFilter.HasKeyword(keyword = Keyword.Flying))),
                        min = n(1),
                        max = n(1),
                    ),
                ),
                steps = listOf(doing(Effect.DealDamage(to = Recipients(objects = target("creature")), amount = n(6), source = Objects.Self))),
            ),
            rules = anyTime,
        ),
    )

    @Test
    fun ovraLightbreaker() = card(
        name = "Ovra Lightbreaker",
        cost = "{2}{R}",
        type = "Legendary Creature — Orc Wizard",
        pt = "3/3",
        text = """
            Haste
            When Ovra Lightbreaker enters, discard up to two cards, then draw that many cards.
            Whenever Ovra Lightbreaker attacks, if there are seven or more cards in your graveyard, exile a card at random from your graveyard. You may play that card this turn.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    Step.Choose(
                        choice = Choice.Objects(filter = and(inZone(Zone.Hand), ownedByYou), min = n(0), max = n(2)),
                        bind = "chosen",
                        chooser = Players.You,
                    ),
                    doing(Effect.Discard(players = Players.You, count = n(2), filter = ObjectFilter.Any, objects = bound("chosen"))),
                    doing(Effect.Draw(players = Players.You, count = Value.Count(objects = bound("chosen")))),
                ),
            ),
        ),
        Ability.Triggered(
            trigger = attacksSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.Move(
                            objects = Objects.Random(of = Objects.Each(filter = and(cardFilter, inZone(Zone.Graveyard), ownedByYou)), count = n(1)),
                            to = Destination(zone = Zone.Exile),
                            postBind = "exiled",
                        ),
                    ),
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Play, holder = PlayerFilter.You)),
                            affects = Affected.Objects(objects = bound("exiled")),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
            interveningIf = Condition.Exists(filter = and(inZone(Zone.Graveyard), ownedByYou), atLeast = 7),
        ),
    )

    @Test
    fun pairedBolt() = card(
        name = "Paired Bolt",
        cost = "{1}{R}",
        type = "Instant",
        text = "Paired Bolt deals 2 damage divided as you choose among one or two targets.",
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "any",
                        what = TargetKind.Either(
                            objects = or(creature, planeswalker, ObjectFilter.Type(cardType = CardTypeRef.Literal(cardType = CardType.Battle))),
                            players = PlayerFilter.Any,
                        ),
                        min = n(1),
                        max = n(2),
                        divided = n(2),
                    ),
                ),
                steps = listOf(doing(Effect.DealDamage(to = Recipients(target = "any"), amount = Value.DividedShare, source = Objects.Self))),
            ),
        ),
    )

    @Test
    fun underheelScrappers() = card(
        name = "Underheel Scrappers",
        cost = "{2}{R}",
        type = "Creature — Goblin Warrior",
        pt = "1/2",
        text = """
            When this creature enters, create a 1/1 red Goblin creature token.
            {1}, {T}: Target creature you control with power 2 or less can't be blocked this turn.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.CreateToken(
                            token = TokenSpec.Defined(
                                face = FaceDefinition(
                                    name = "Goblin Token",
                                    colorIndicator = listOf(Color.Red),
                                    types = listOf(CardType.Creature),
                                    subtypes = listOf(Subtype(name = "Goblin")),
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
        ),
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{1}"), tap)),
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "creature",
                        what = TargetKind.Objects(filter = and(creature, you, ObjectFilter.Power(comparison = Comparison.AtMost, value = n(2)))),
                        min = n(1),
                        max = n(1),
                    ),
                ),
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.CantBeBlocked),
                            affects = Affected.Objects(objects = target("creature")),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
            rules = anyTime,
        ),
    )

    @Test
    fun mercilessBoltcaster() = card(
        name = "Merciless Boltcaster",
        cost = "{2}{R}",
        type = "Creature — Ogre Wizard",
        pt = "3/3",
        text = "When this creature enters, it deals 5 damage to target creature an opponent controls that was dealt damage this turn.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "creature",
                        what = TargetKind.Objects(
                            filter = and(
                                creature,
                                opponents,
                                ObjectFilter.ThisTurn(event = EventPattern.Damage(source = ObjectFilter.Any, to = RecipientFilter()), role = arcanum.api.EventRole.Object),
                            ),
                        ),
                        min = n(1),
                        max = n(1),
                    ),
                ),
                steps = listOf(doing(Effect.DealDamage(to = Recipients(objects = target("creature")), amount = n(5), source = Objects.Self))),
            ),
        ),
    )
}
