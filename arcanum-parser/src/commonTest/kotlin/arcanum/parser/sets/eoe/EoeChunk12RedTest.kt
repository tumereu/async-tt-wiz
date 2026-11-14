package arcanum.parser.sets.eoe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Edge of Eternities chunk 12: red cards: damage to itself, X copies with haste, "if you control a Spacecraft", attacking tokens sacrificed at end of combat, exile and play by the sacrificed mana value, damage equal to an enchanted creature's power, stealing with charge counters, granted leave and end-step triggers, exile until nonland.
 */
class EoeChunk12RedTest {

    @Test
    fun barrageShot() = card(
        name = "Barrage Shot",
        cost = "{2}{R}",
        type = "Instant",
        text = "Barrage Shot deals 4 damage to target creature.",
    ) parsesTo abilities(
        spell(
            doing(Effect.DealDamage(to = Recipients(objects = target("creature")), amount = n(4), source = Objects.Self)),
            targets = listOf(slot("creature", creature)),
        ),
    )

    @Test
    fun severThrusters() = card(
        name = "Sever Thrusters",
        cost = "{2}{R}",
        type = "Instant",
        text = "Target creature deals damage to itself equal to its power. If that creature has flying, it deals twice that much damage to itself instead.",
    ) parsesTo abilities(
        spell(
            Step.If(
                condition = Condition.ObjectMatches(
                    objects = target("creature"),
                    filter = ObjectFilter.HasKeyword(keyword = Keyword.Flying),
                ),
                then = listOf(
                    doing(
                        Effect.DealDamage(
                            to = Recipients(objects = target("creature")),
                            amount = Value.Times(
                                value = Value.Of(property = NumericProperty.Power, objects = target("creature"), how = Combine.Single),
                                factor = 2,
                            ),
                            source = target("creature"),
                        ),
                    ),
                ),
                otherwise = listOf(
                    doing(
                        Effect.DealDamage(
                            to = Recipients(objects = target("creature")),
                            amount = Value.Of(property = NumericProperty.Power, objects = target("creature"), how = Combine.Single),
                            source = target("creature"),
                        ),
                    ),
                ),
            ),
            targets = listOf(slot("creature", creature)),
        ),
    )

    @Test
    fun ruinousOffensive() = card(
        name = "Ruinous Offensive",
        cost = "{X}{X}{R}",
        type = "Sorcery",
        text = "Create X tokens that are copies of target artifact or creature you control. Those tokens gain haste until end of turn. Sacrifice them at the beginning of the next end step.",
    ) parsesTo abilities(
        spell(
            doing(
                Effect.CreateToken(
                    token = TokenSpec.CopyOf(objects = target("permanent")),
                    count = Value.Announced(name = "X"),
                    controller = Players.You,
                    bind = "tokens",
                ),
            ),
            untilEndOfTurn(bound("tokens"), has(Keyword.Haste)),
            doing(
                Effect.CreateDelayedTrigger(
                    trigger = on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.Any)),
                    instructions = Instructions(steps = listOf(doing(Effect.Sacrifice(objects = bound("tokens"))))),
                ),
            ),
            targets = listOf(slot("permanent", and(or(artifact, creature), you))),
            announced = listOf("X"),
        ),
    )

    @Test
    fun borePastTheCore() = card(
        name = "Bore Past the Core",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Choose one —
            • Put five charge counters on target Spacecraft or Planet you control.
            • Destroy target artifact.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                modes = Modes(
                    options = listOf(
                        Mode(
                            instructions = Instructions(
                                targets = listOf(slot("permanent", and(or(sub("Spacecraft"), sub("Planet")), you))),
                                steps = listOf(doing(Effect.PutCounters(objects = target("permanent"), kind = charge, amount = n(5)))),
                            ),
                        ),
                        Mode(
                            instructions = Instructions(
                                targets = listOf(slot("artifact", artifact)),
                                steps = listOf(doing(Effect.Destroy(objects = target("artifact")))),
                            ),
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
    fun vanguardBattleBrute() = card(
        name = "Vanguard Battle-Brute",
        cost = "{2}{R}",
        type = "Creature — Kavu Soldier",
        pt = "2/3",
        text = "At the beginning of your end step, if you control two or more tapped creatures, put a +1/+1 counter on this creature.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You)),
            doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1))),
            interveningIf = Condition.Exists(filter = and(tapped, creature, you), atLeast = 2),
        ),
    )

    @Test
    fun incursionTactics() = card(
        name = "Incursion Tactics",
        cost = "{1}{R}",
        type = "Instant",
        text = "Incursion Tactics deals 3 damage to target creature. It deals 5 damage instead if you control a Spacecraft.",
    ) parsesTo abilities(
        spell(
            Step.If(
                condition = Condition.Exists(filter = and(sub("Spacecraft"), you), atLeast = 1),
                then = listOf(doing(Effect.DealDamage(to = Recipients(objects = target("creature")), amount = n(5), source = Objects.Self))),
                otherwise = listOf(
                    doing(Effect.DealDamage(to = Recipients(objects = target("creature")), amount = n(3), source = Objects.Self)),
                ),
            ),
            targets = listOf(slot("creature", creature)),
        ),
    )

    @Test
    fun pyrrhosRaider() = card(
        name = "Pyrrhos Raider",
        cost = "{R}",
        type = "Artifact Creature — Robot Soldier",
        pt = "2/1",
        text = "Whenever this creature attacks, you may pay {2}. If you do, create a 2/2 colorless Robot artifact creature token that's tapped and attacking. Sacrifice that token at end of combat.",
    ) parsesTo abilities(
        triggered(
            attacksSelf,
            Step.Pay(cost = cost(mana("{2}")), payer = Players.You, bind = "paid"),
            Step.If(
                condition = Condition.Did(name = "paid"),
                then = listOf(
                    doing(
                        Effect.CreateToken(
                            token = robot,
                            count = n(1),
                            controller = Players.You,
                            tapped = true,
                            attacking = AttackTarget.Chosen,
                            bind = "token",
                        ),
                    ),
                    doing(
                        Effect.CreateDelayedTrigger(
                            trigger = on(EventPattern.StepBegins(step = TurnStep.EndOfCombat, whose = PlayerFilter.Any)),
                            instructions = Instructions(steps = listOf(doing(Effect.Sacrifice(objects = bound("token"))))),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun pyrrhosBoostdrone() = card(
        name = "Pyrrhos Boostdrone",
        cost = "{2}{R}",
        type = "Artifact Creature — Robot Scout",
        pt = "2/3",
        text = "{T}: Target creature you control gets +1/+1 and gains haste until end of turn. Activate only as a sorcery.",
    ) parsesTo abilities(
        activated(
            cost(tap),
            untilEndOfTurn(target("creature"), pt(1, 1), has(Keyword.Haste)),
            targets = listOf(slot("creature", and(creature, you))),
            rules = asSorcery,
        ),
    )

    @Test
    fun fusedMoxstone() = card(
        name = "Fused Moxstone",
        cost = "{1}{R}",
        type = "Artifact",
        text = """
            When this artifact enters, you may discard a card. If you do, draw two cards.
            {3}, Sacrifice this artifact: Create a tapped 2/2 colorless Robot artifact creature token.
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            Step.Pay(
                cost = cost(CostPart.Discard(what = CostObjects.Choose(filter = cardFilter, count = n(1)))),
                payer = Players.You,
                bind = "paid",
            ),
            Step.If(condition = Condition.Did(name = "paid"), then = listOf(doing(Effect.Draw(players = Players.You, count = n(2))))),
        ),
        activated(
            cost(mana("{3}"), CostPart.Sacrifice(what = CostObjects.Self)),
            doing(Effect.CreateToken(token = robot, count = n(1), controller = Players.You, tapped = true)),
        ),
    )

    @Test
    fun remembranceStrongroom() = card(
        name = "Remembrance Strongroom",
        cost = "{3}{R}",
        type = "Artifact",
        text = "{T}, Sacrifice another artifact: Exile the top X cards of your library, where X is one plus the mana value of the sacrificed artifact. You may play those cards this turn.",
    ) parsesTo abilities(
        activated(
            cost(
                tap,
                CostPart.Sacrifice(what = CostObjects.Choose(filter = and(not(isSelf), artifact), count = n(1)), preBind = "sacrificed"),
            ),
            doing(
                Effect.Move(
                    objects = Objects.LibraryCards(
                        of = Players.You,
                        count = Value.Sum(
                            parts = listOf(
                                n(1),
                                Value.Of(property = NumericProperty.ManaValue, objects = bound("sacrificed"), how = Combine.Single),
                            ),
                        ),
                        fromTop = true,
                    ),
                    to = Destination(zone = Zone.Exile),
                    postBind = "exiled",
                ),
            ),
            untilEndOfTurn(bound("exiled"), Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Play, holder = PlayerFilter.You)),
        ),
    )

    @Test
    fun atomicTinkerer() = card(
        name = "Atomic Tinkerer",
        cost = "{2}{R}",
        type = "Creature — Kavu Artificer",
        pt = "2/2",
        text = "At the beginning of combat on your turn, target creature you control gets +1/+0 and gains first strike until end of turn.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.BeginningOfCombat, whose = PlayerFilter.You)),
            untilEndOfTurn(target("creature"), pt(1, 0), has(Keyword.FirstStrike)),
            targets = listOf(slot("creature", and(creature, you))),
        ),
    )

    @Test
    fun stardustWyrm() = card(
        name = "Stardust Wyrm",
        cost = "{6}{R}",
        type = "Creature — Dragon",
        pt = "4/4",
        text = """
            Flying
            When this creature enters, it deals 3 damage to any target.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, doing(Effect.DealDamage(to = toAny, amount = n(3), source = Objects.Self)), targets = listOf(anyTarget)),
    )

    @Test
    fun orehideArmadillo() = card(
        name = "Orehide Armadillo",
        cost = "{1}{R}",
        type = "Artifact Creature — Robot Pangolin",
        pt = "2/2",
        text = "Whenever another artifact you control enters, you may pay {1}. If you do, put a +1/+1 counter on this creature.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = and(not(isSelf), artifact, you), to = Zone.Battlefield)),
            Step.Pay(cost = cost(mana("{1}")), payer = Players.You, bind = "paid"),
            Step.If(
                condition = Condition.Did(name = "paid"),
                then = listOf(doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1)))),
            ),
        ),
    )

    @Test
    fun sufferingShared() = card(
        name = "Suffering Shared",
        cost = "{2}{R}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature you control
            When this Aura enters, enchanted creature deals damage equal to its power to any other target.
            Whenever enchanted creature is dealt damage, it deals that much damage to each opponent.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(what = TargetKind.Objects(filter = and(creature, you))),
        triggered(
            entersSelf,
            doing(
                Effect.DealDamage(
                    to = toAny,
                    amount = Value.Of(
                        property = NumericProperty.Power,
                        objects = Objects.AttachedTo(of = Objects.Self),
                        how = Combine.Single,
                    ),
                    source = Objects.AttachedTo(of = Objects.Self),
                ),
            ),
            targets = listOf(
                TargetSlot(
                    name = "any",
                    what = TargetKind.Either(
                        objects = and(
                            or(creature, ObjectFilter.Type(CardType.Planeswalker), ObjectFilter.Type(CardType.Battle)),
                            not(ObjectFilter.Is(objects = Objects.AttachedTo(of = Objects.Self))),
                        ),
                        players = PlayerFilter.Any,
                    ),
                    min = n(1),
                    max = n(1),
                ),
            ),
        ),
        triggered(
            on(
                EventPattern.Damage(
                    source = ObjectFilter.Any,
                    to = RecipientFilter(objects = ObjectFilter.Is(objects = Objects.AttachedTo(of = Objects.Self))),
                    per = DamageOccurrence.Recipient,
                ),
            ),
            doing(
                Effect.DealDamage(
                    to = Recipients(players = Players.Each(filter = PlayerFilter.Opponent)),
                    amount = Value.EventAmount,
                    source = Objects.EventObject(role = EventRole.Object),
                ),
            ),
        ),
    )

    @Test
    fun residueElemental() = card(
        name = "Residue Elemental",
        cost = "{1}{R}",
        type = "Creature — Elemental",
        pt = "0/4",
        text = """
            Reach
            Landfall — Whenever a land you control enters, this creature gets +2/+0 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(
            on(EventPattern.ZoneChange(objects = and(land, you), to = Zone.Battlefield)),
            untilEndOfTurn(Objects.Self, pt(2, 0)),
            abilityWord = "Landfall",
        ),
    )

    @Test
    fun outfitForBattle() = card(
        name = "Outfit for Battle",
        cost = "{1}{R}",
        type = "Instant",
        text = "Target creature gets +3/+0 and gains reach and first strike until end of turn.",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), pt(3, 0), has(Keyword.Reach, Keyword.FirstStrike)),
            targets = listOf(slot("creature", creature)),
        ),
    )

    @Test
    fun calamitousStampede() = card(
        name = "Calamitous Stampede",
        cost = "{1}{R}{R}",
        type = "Sorcery",
        text = """
            Choose one —
            • Calamitous Stampede deals 3 damage to each opponent.
            • Exile all artifacts with mana value 3 or less.
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
                                        Effect.DealDamage(
                                            to = Recipients(players = Players.Each(filter = PlayerFilter.Opponent)),
                                            amount = n(3),
                                            source = Objects.Self,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        Mode(
                            instructions = Instructions(
                                steps = listOf(
                                    doing(
                                        Effect.Move(
                                            objects = Objects.Each(
                                                filter = and(artifact, ObjectFilter.ManaValue(comparison = Comparison.AtMost, value = n(3))),
                                            ),
                                            to = Destination(zone = Zone.Exile),
                                        ),
                                    ),
                                ),
                            ),
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
    fun corrosionReaper() = card(
        name = "Corrosion Reaper",
        cost = "{R}",
        type = "Artifact Creature — Robot",
        pt = "1/1",
        text = """
            Menace
            {2}, {T}, Exile an artifact card from your graveyard: Put a +1/+1 counter on this creature, then it deals damage equal to its power to any target.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        activated(
            cost(mana("{2}"), tap, CostPart.Exile(what = CostObjects.Choose(filter = artifact, count = n(1)), from = Zone.Graveyard)),
            doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1))),
            doing(
                Effect.DealDamage(
                    to = toAny,
                    amount = Value.Of(property = NumericProperty.Power, objects = Objects.Self, how = Combine.Single),
                    source = Objects.Self,
                ),
            ),
            targets = listOf(anyTarget),
        ),
    )

    @Test
    fun cinderboreSalvager() = card(
        name = "Cinderbore Salvager",
        cost = "{R}",
        type = "Artifact Creature — Robot Scout",
        pt = "1/2",
        text = "{2}, {T}, Sacrifice another artifact or land: Draw a card.",
    ) parsesTo abilities(
        activated(
            cost(
                mana("{2}"),
                tap,
                CostPart.Sacrifice(what = CostObjects.Choose(filter = and(not(isSelf), or(artifact, land)), count = n(1))),
            ),
            doing(Effect.Draw(players = Players.You, count = n(1))),
        ),
    )

    @Test
    fun controlHijack() = card(
        name = "Control Hijack",
        cost = "{2}{R}",
        type = "Sorcery",
        text = "Gain control of target artifact or creature until end of turn. Untap that permanent. It gains haste until end of turn. If it's a Spacecraft, put ten charge counters on it. If you do, remove ten charge counters from it at the beginning of the next end step.",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("permanent"), Mechanic.ControlledBy(players = Players.You)),
            doing(Effect.Untap(objects = target("permanent"))),
            untilEndOfTurn(target("permanent"), has(Keyword.Haste)),
            Step.If(
                condition = Condition.ObjectMatches(objects = target("permanent"), filter = sub("Spacecraft")),
                then = listOf(
                    doing(Effect.PutCounters(objects = target("permanent"), kind = charge, amount = n(10))),
                    doing(
                        Effect.CreateDelayedTrigger(
                            trigger = on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.Any)),
                            instructions = Instructions(
                                steps = listOf(doing(Effect.RemoveCounters(objects = target("permanent"), kind = charge, amount = n(10)))),
                            ),
                        ),
                    ),
                ),
            ),
            targets = listOf(slot("permanent", or(artifact, creature))),
        ),
    )

    @Test
    fun finalPlummet() = card(
        name = "Final Plummet",
        cost = "{4}{R}{R}",
        type = "Sorcery",
        text = "You may put an artifact or creature card from your hand onto the battlefield. That permanent gains haste, \"When this permanent leaves the battlefield, it deals damage equal to its mana value to each creature,\" and \"At the beginning of your end step, sacrifice this permanent.\"",
    ) parsesTo abilities(
        spell(
            Step.Choose(
                choice = Choice.Objects(filter = and(or(artifact, creature), inZone(Zone.Hand), ownedByYou), min = n(0), max = n(1)),
                bind = "chosen",
                chooser = Players.You,
            ),
            doing(Effect.Move(objects = bound("chosen"), to = Destination(zone = Zone.Battlefield), postBind = "put")),
            doing(
                Effect.ApplyMechanics(
                    mechanics = listOf(
                        has(
                            Keyword.Haste,
                            triggered(
                                on(EventPattern.ZoneChange(objects = isSelf, from = Zone.Battlefield)),
                                doing(
                                    Effect.DealDamage(
                                        to = Recipients(objects = Objects.Each(filter = creature)),
                                        amount = Value.Of(
                                            property = NumericProperty.ManaValue,
                                            objects = Objects.Self,
                                            how = Combine.Single,
                                        ),
                                        source = Objects.Self,
                                    ),
                                ),
                            ),
                            triggered(
                                on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You)),
                                doing(Effect.Sacrifice(objects = Objects.Self)),
                            ),
                        ),
                    ),
                    affects = Affected.Objects(objects = bound("put")),
                    duration = Duration.Indefinitely,
                ),
            ),
        ),
    )

    @Test
    fun turfGuardingHornback() = card(
        name = "Turf-Guarding Hornback",
        cost = "{4}{R}{R}",
        type = "Creature — Beast",
        pt = "6/6",
        text = """
            Reach
            Landfall — Whenever a land you control enters, exile cards from the top of your library until you exile a nonland card. You may cast that card this turn.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(
            on(EventPattern.ZoneChange(objects = and(land, you), to = Zone.Battlefield)),
            doing(
                Effect.RevealUntil(
                    filter = not(land),
                    library = Players.You,
                    count = n(1),
                    reveal = false,
                    into = Zone.Exile,
                    foundBind = "found",
                ),
            ),
            untilEndOfTurn(bound("found"), Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Cast, holder = PlayerFilter.You)),
            abilityWord = "Landfall",
        ),
    )

    @Test
    fun strongroomSentinel() = card(
        name = "Strongroom Sentinel",
        cost = "{4}{R}",
        type = "Creature — Kavu Soldier",
        pt = "5/5",
        text = "At the beginning of your end step, if you control two or more tapped creatures, you may discard your hand. If you do, draw two cards.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You)),
            Step.Pay(
                cost = cost(CostPart.Discard(what = CostObjects.These(objects = Objects.Each(filter = and(inZone(Zone.Hand), ownedByYou))))),
                payer = Players.You,
                bind = "paid",
            ),
            Step.If(condition = Condition.Did(name = "paid"), then = listOf(doing(Effect.Draw(players = Players.You, count = n(2))))),
            interveningIf = Condition.Exists(filter = and(tapped, creature, you), atLeast = 2),
        ),
    )

    @Test
    fun armamentAssembly() = card(
        name = "Armament Assembly",
        cost = "{1}{R}",
        type = "Enchantment",
        text = "Whenever a nontoken artifact you control enters, create a colorless artifact token named Munitions with \"When this token leaves the battlefield, it deals 2 damage to any target.\"",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = and(not(token), artifact, you), to = Zone.Battlefield)),
            doing(
                Effect.CreateToken(
                    token = TokenSpec.Defined(
                        face = FaceDefinition(
                            name = "Munitions",
                            colorIndicator = listOf(),
                            types = listOf(CardType.Artifact),
                            abilities = listOf(
                                triggered(
                                    on(EventPattern.ZoneChange(objects = isSelf, from = Zone.Battlefield)),
                                    doing(Effect.DealDamage(to = toAny, amount = n(2), source = Objects.Self)),
                                    targets = listOf(anyTarget),
                                ),
                            ),
                        ),
                    ),
                    count = n(1),
                    controller = Players.You,
                ),
            ),
        ),
    )

    @Test
    fun menagerieAutomaton() = card(
        name = "Menagerie Automaton",
        cost = "{1}{R}",
        type = "Artifact Creature — Robot",
        pt = "1/3",
        text = """
            {T}: Add {R}.
            {6}{R}: Target creature you control gets +4/+0 until end of turn. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        activated(
            cost(tap),
            doing(Effect.AddMana(mana = ManaProduction(kind = ManaKind.Fixed(mana = ManaCost.parse("{R}")!!)), players = Players.You)),
        ),
        activated(
            cost(mana("{6}{R}")),
            untilEndOfTurn(target("creature"), pt(4, 0)),
            targets = listOf(slot("creature", and(creature, you))),
            rules = asSorcery,
        ),
    )
}
