package arcanum.parser.sets.dsk

import arcanum.api.Ability
import arcanum.api.AbilityKind
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.sets.dsk.Dsk.create
import arcanum.parser.sets.dsk.Dsk.creature
import arcanum.parser.sets.dsk.Dsk.creatureYouControl
import arcanum.parser.sets.dsk.Dsk.door
import arcanum.parser.sets.dsk.Dsk.manifestDread
import arcanum.parser.sets.dsk.Dsk.room
import arcanum.parser.sets.dsk.Dsk.roomsYouControl
import arcanum.parser.sets.dsk.Dsk.token
import arcanum.parser.sets.dsk.Dsk.whenYouUnlockThisDoor
import arcanum.parser.sets.dsk.Dsk.youControl
import kotlin.test.Test

/**
 * Duskmourn chunk 02: black, red and green Rooms (CR 709.5) and the multicolour ones. Door triggers that return,
 * exile and play, deal damage as "this Room", mill and pick from among the milled cards, search for basic lands and
 * manifest dread; static doors (a permission to play lands from the graveyard, granted mana abilities, no maximum hand
 * size, a once-each-turn alternative cost for spells cast from exile); first-main-phase mana for Rooms and unlocking
 * doors; an X/X token counting unlocked doors.
 */
class DskChunk02BlackRedGreenRoomsTest {
    private val plusOne = CounterKind.PowerToughness(1, 1)
    private val yourGraveyard = listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
    private fun slot(name: String, filter: ObjectFilter, min: Int = 1, max: Int = 1) =
        TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(min), max = Value.Fixed(max))
    private val creatureAnOpponentControls = ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Opponent)))
    private val atYourEndStep = Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.You))
    private val whenYouAttack = Trigger.On(EventPattern.Attack(youControl), oneOrMore = true)
    private fun mayPlay(objects: Objects, duration: Duration) = Step.Do(Effect.ApplyMechanics(
        mechanics = listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
        affects = Affected.Objects(objects),
        duration = duration,
    ))

    /** "One or more cards leave your graveyard" looks back in time (CR 603.10a); once each turn (CR 603.2h). */
    @Test
    fun desecratedVaultEmbalmingLab() = room(
        door(
            name = "Desecrated Vault",
            cost = "{3}{B}",
            text = "Whenever one or more cards leave your graveyard, create a 2/2 black Horror enchantment creature token. This ability triggers only once each turn.",
        ) to abilities(
            Ability.Triggered(
                trigger = Trigger.On(
                    EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Card), ObjectFilter.OwnedBy(PlayerFilter.You))), from = Zone.Graveyard),
                    oneOrMore = true,
                ),
                instructions = Instructions(steps = listOf(
                    create(token(listOf("Horror"), listOf(Color.Black), 2, 2, listOf(CardType.Enchantment, CardType.Creature))),
                )),
                perTurn = 1,
            ),
        ),
        door(
            name = "Embalming Lab",
            cost = "{B}",
            text = "When you unlock this door, return target creature card from your graveyard to your hand.",
        ) to abilities(
            whenYouUnlockThisDoor(
                Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Hand))),
                targets = listOf(slot("card", ObjectFilter.And(listOf(creature) + yourGraveyard))),
            ),
        ),
    )

    /** "Attacks alone" (CR 506.5); "it" is the attacking creature. */
    @Test
    fun dustyGarretLonelyBalcony() = room(
        door(
            name = "Dusty Garret",
            cost = "{2}{B}",
            text = "When you unlock this door, you draw two cards and you lose 2 life.",
        ) to abilities(
            whenYouUnlockThisDoor(
                Step.Do(Effect.Draw(Players.You, Value.Fixed(2))),
                Step.Do(Effect.LoseLife(Players.You, Value.Fixed(2))),
            ),
        ),
        door(
            name = "Lonely Balcony",
            cost = "{3}{B}",
            text = "Whenever a creature you control attacks alone, it gets +1/+0 and gains deathtouch until end of turn.",
        ) to abilities(
            Ability.Triggered(
                trigger = Trigger.On(EventPattern.Attack(creatureYouControl, alone = true)),
                instructions = Instructions(steps = listOf(Step.Do(Effect.ApplyMechanics(
                    mechanics = listOf(
                        Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(0)),
                        Mechanic.GainAbilities(listOf(Keyword.Deathtouch)),
                    ),
                    affects = Affected.Objects(Objects.EventObject(arcanum.api.EventRole.Object)),
                    duration = Duration.EndOfTurn,
                )))),
            ),
        ),
    )

    @Test
    fun mourningChapelRestlessHall() = room(
        door(
            name = "Mourning Chapel",
            cost = "{2}{B}",
            text = "Whenever a creature you control dies, each opponent loses 1 life and you gain 1 life.",
        ) to abilities(
            Ability.Triggered(
                trigger = Trigger.On(EventPattern.ZoneChange(creatureYouControl, from = Zone.Battlefield, to = Zone.Graveyard)),
                instructions = Instructions(steps = listOf(
                    Step.Do(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), Value.Fixed(1))),
                    Step.Do(Effect.GainLife(Players.You, Value.Fixed(1))),
                )),
            ),
        ),
        door(
            name = "Restless Hall",
            cost = "{6}{B}{B}",
            text = "When you unlock this door, return all creature cards from your graveyard to the battlefield.",
        ) to abilities(
            whenYouUnlockThisDoor(
                Step.Do(Effect.Move(Objects.Each(ObjectFilter.And(listOf(creature) + yourGraveyard)), Destination(Zone.Battlefield))),
            ),
        ),
    )

    @Test
    fun profaneAnnexSummoningChamber() = room(
        door(
            name = "Profane Annex",
            cost = "{2}{B}",
            text = "At the beginning of your end step, draw a card. If you control a Demon, each opponent loses 2 life and you gain 2 life. Otherwise, you lose 2 life.",
        ) to abilities(
            Ability.Triggered(
                trigger = atYourEndStep,
                instructions = Instructions(steps = listOf(
                    Step.Do(Effect.Draw(Players.You, Value.Fixed(1))),
                    Step.If(
                        condition = Condition.Exists(ObjectFilter.And(listOf(ObjectFilter.Sub(Subtype("Demon")), youControl)), atLeast = 1),
                        then = listOf(
                            Step.Do(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), Value.Fixed(2))),
                            Step.Do(Effect.GainLife(Players.You, Value.Fixed(2))),
                        ),
                        otherwise = listOf(Step.Do(Effect.LoseLife(Players.You, Value.Fixed(2)))),
                    ),
                )),
            ),
        ),
        door(
            name = "Summoning Chamber",
            cost = "{3}{B}{B}",
            text = "When you unlock this door, create a 6/6 black Demon creature token with flying.",
        ) to abilities(
            whenYouUnlockThisDoor(create(token(listOf("Demon"), listOf(Color.Black), 6, 6, abilities = listOf(Keyword.Flying)))),
        ),
    )

    /** An alternative cost the Room gives spells its controller casts from exile, once each turn (CR 118.9). */
    @Test
    fun scorchedLobbyTwistedGallery() = room(
        door(
            name = "Scorched Lobby",
            cost = "{3}{R}",
            text = "At the beginning of your upkeep, exile the top card of your library. You may play it this turn.",
        ) to abilities(
            Ability.Triggered(
                trigger = Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You)),
                instructions = Instructions(steps = listOf(
                    Step.Do(Effect.Move(Objects.LibraryCards(Players.You, Value.Fixed(1), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
                    mayPlay(Objects.Bound("exiled"), Duration.EndOfTurn),
                )),
            ),
        ),
        door(
            name = "Twisted Gallery",
            cost = "{4}{R}{R}",
            text = "Once each turn, you may pay {0} rather than pay the mana cost for a spell you cast from exile.",
        ) to abilities(
            Ability.Static(
                mechanics = listOf(Mechanic.AlternativeCost(cost = Cost.mana(ManaCost.parse("{0}")!!), perTurn = 1)),
                // The spell cast from exile (CR 601.2a)
                affects = Affected.Objects(Objects.Each(ObjectFilter.WasCast(Zone.Exile))),
            ),
        ),
    )

    /** "This Room" is the Room itself (its subtype as a self name). */
    @Test
    fun shatteredConservatorySplinteredCourtyard() = room(
        door(
            name = "Shattered Conservatory",
            cost = "{2}{R}",
            text = "When you unlock this door, this Room deals 4 damage to target creature an opponent controls.",
        ) to abilities(
            whenYouUnlockThisDoor(
                Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("creature")), Value.Fixed(4), source = Objects.Self)),
                targets = listOf(slot("creature", creatureAnOpponentControls)),
            ),
        ),
        door(
            name = "Splintered Courtyard",
            cost = "{4}{R}",
            text = "At the beginning of your end step, this Room deals 1 damage to each opponent.",
        ) to abilities(
            Ability.Triggered(
                trigger = atYourEndStep,
                instructions = Instructions(steps = listOf(
                    Step.Do(Effect.DealDamage(Recipients(players = Players.Each(PlayerFilter.Opponent)), Value.Fixed(1), source = Objects.Self)),
                )),
            ),
        ),
    )

    @Test
    fun artistsLoftVandalizedHall() = room(
        door(
            name = "Artist's Loft",
            cost = "{2}{R}",
            text = "When you unlock this door, exile the top two cards of your library. You may play them until the end of your next turn.",
        ) to abilities(
            whenYouUnlockThisDoor(
                Step.Do(Effect.Move(Objects.LibraryCards(Players.You, Value.Fixed(2), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
                mayPlay(Objects.Bound("exiled"), Duration.UntilEndOfYourNextTurn),
            ),
        ),
        door(
            name = "Vandalized Hall",
            cost = "{1}{R}",
            text = "Whenever you attack, attacking creatures you control get +1/+0 until end of turn.",
        ) to abilities(
            Ability.Triggered(
                trigger = whenYouAttack,
                instructions = Instructions(steps = listOf(Step.Do(Effect.ApplyMechanics(
                    mechanics = listOf(Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(0))),
                    affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.HasStatus(Status.Attacking), creature, youControl)))),
                    duration = Duration.EndOfTurn,
                )))),
            ),
        ),
    )

    @Test
    fun admissionKioskHatredTunnel() = room(
        door(
            name = "Admission Kiosk",
            cost = "{2}{R}",
            text = "When you unlock this door, manifest dread.",
        ) to abilities(
            whenYouUnlockThisDoor(manifestDread(bind = "manifested")),
        ),
        door(
            name = "Hatred Tunnel",
            cost = "{4}{R}{R}",
            text = "Whenever you attack, target attacking creature gains double strike until end of turn.",
        ) to abilities(
            Ability.Triggered(
                trigger = whenYouAttack,
                instructions = Instructions(
                    targets = listOf(slot("creature", ObjectFilter.And(listOf(ObjectFilter.HasStatus(Status.Attacking), creature)))),
                    steps = listOf(Step.Do(Effect.ApplyMechanics(
                        mechanics = listOf(Mechanic.GainAbilities(listOf(Keyword.DoubleStrike))),
                        affects = Affected.Objects(Objects.Target("creature")),
                        duration = Duration.EndOfTurn,
                    ))),
                ),
            ),
        ),
    )

    /** A granted mana ability (CR 113.10, 605.1a); "return up to two permanent cards from among them" chooses among the
     * milled cards (CR 110.4a). */
    @Test
    fun overgrownSolariumCreakingPergola() = room(
        door(
            name = "Overgrown Solarium",
            cost = "{2}{G}",
            text = "Lands you control have \"{T}: Add one mana of any color.\"",
        ) to abilities(
            Ability.Static(
                mechanics = listOf(Mechanic.GainAbilities(listOf(Ability.Activated(
                    cost = Cost(listOf(CostPart.TapSelf)),
                    instructions = Instructions(steps = listOf(Step.Do(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You)))),
                    rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
                )))),
                affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Land), youControl)))),
            ),
        ),
        door(
            name = "Creaking Pergola",
            cost = "{3}{G}",
            text = "When you unlock this door, mill four cards, then return up to two permanent cards from among them to your hand.",
        ) to abilities(
            whenYouUnlockThisDoor(
                Step.Do(Effect.Mill(Players.You, Value.Fixed(4), postBind = "milled")),
                Step.Choose(
                    choice = Choice.Objects(
                        filter = ObjectFilter.Or(listOf(CardType.Artifact, CardType.Battle, CardType.Creature, CardType.Enchantment, CardType.Land,
                            CardType.Planeswalker).map { ObjectFilter.Type(it) }),
                        min = Value.Fixed(0),
                        max = Value.Fixed(2),
                        from = Objects.Bound("milled"),
                    ),
                    bind = "chosen",
                    chooser = Players.You,
                ),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))),
            ),
        ),
    )

    @Test
    fun mildewedGymnasiumIronRoom() = room(
        door(
            name = "Mildewed Gymnasium",
            cost = "{2}{G}",
            text = "When you unlock this door, search your library for a basic land card, put it onto the battlefield tapped, then shuffle.",
        ) to abilities(
            whenYouUnlockThisDoor(
                Step.Do(Effect.Search(Players.You, ObjectFilter.And(listOf(ObjectFilter.Super(Supertype.Basic), ObjectFilter.Type(CardType.Land))),
                    max = Value.Fixed(1), zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield, tapped = true))),
                Step.Do(Effect.Shuffle(Players.You)),
            ),
        ),
        door(
            name = "Iron Room",
            cost = "{5}{G}",
            text = "When you unlock this door, manifest dread, then put three +1/+1 counters on that creature.",
        ) to abilities(
            whenYouUnlockThisDoor(
                manifestDread(bind = "manifested"),
                Step.Do(Effect.PutCounters(Objects.Bound("manifested"), plusOne, Value.Fixed(3))),
            ),
        ),
    )

    /** Permissions to play lands (CR 305.1) and cast spells from the graveyard for the turn, and a replacement for the
     * turn (CR 614.1a), both effects of the door's ability (CR 611.2c: they also cover cards put there later). */
    @Test
    fun wardrobeNookForsakenBasement() = room(
        door(
            name = "Wardrobe Nook",
            cost = "{2}{G}",
            text = "You may play lands from your graveyard.",
        ) to abilities(
            Ability.Static(
                mechanics = listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Play, holder = PlayerFilter.You)),
                affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Land)) + yourGraveyard))),
            ),
        ),
        door(
            name = "Forsaken Basement",
            cost = "{3}{G}{G}",
            text = "When you unlock this door, you may cast spells from your graveyard this turn, and if a card would be put into your graveyard from anywhere this turn, exile it instead.",
        ) to abilities(
            whenYouUnlockThisDoor(
                Step.Do(Effect.ApplyMechanics(
                    mechanics = listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, holder = PlayerFilter.You)),
                    affects = Affected.Objects(Objects.Each(ObjectFilter.And(yourGraveyard))),
                    duration = Duration.EndOfTurn,
                )),
                Step.Do(Effect.ApplyMechanics(
                    mechanics = listOf(Mechanic.Replacement(
                        EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Card), ObjectFilter.OwnedBy(PlayerFilter.You))), to = Zone.Graveyard),
                        modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))),
                    )),
                    affects = null,
                    duration = Duration.EndOfTurn,
                )),
            ),
        ),
    )

    @Test
    fun blazingHearthVaporBath() = room(
        door(
            name = "Blazing Hearth",
            cost = "{1}{R}",
            text = "When you unlock this door, this Room deals damage equal to the number of cards in your hand to target creature an opponent controls.",
        ) to abilities(
            whenYouUnlockThisDoor(
                Step.Do(Effect.DealDamage(
                    to = Recipients(objects = Objects.Target("creature")),
                    amount = Value.Count(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))))),
                    source = Objects.Self,
                )),
                targets = listOf(slot("creature", creatureAnOpponentControls)),
            ),
        ),
        door(
            name = "Vapor Bath",
            cost = "{3}{U}{U}",
            text = """
                You have no maximum hand size.
                At the beginning of your end step, draw a card.
            """,
        ) to abilities(
            Ability.Static(listOf(Mechanic.NoMaximumHandSize), affects = Affected.Players(Players.You)),
            Ability.Triggered(atYourEndStep, Instructions(steps = listOf(Step.Do(Effect.Draw(Players.You, Value.Fixed(1)))))),
        ),
    )

    /** "Your first main phase" is the precombat main phase (CR 505.1a); mana restricted to Room spells and the unlock
     * special action (CR 106.6, 709.5e). Unlocked doors among Rooms counted for X (CR 709.5c). */
    @Test
    fun hazyLoungeFoggySalon() = room(
        door(
            name = "Hazy Lounge",
            cost = "{2}{R}",
            text = "At the beginning of your first main phase, add {R}{R}. Spend this mana only to cast Room spells and unlock doors.",
        ) to abilities(
            Ability.Triggered(
                trigger = Trigger.On(EventPattern.StepBegins(TurnStep.PrecombatMain, PlayerFilter.You)),
                instructions = Instructions(steps = listOf(Step.Do(Effect.AddMana(
                    ManaProduction(
                        kind = ManaKind.Fixed(ManaCost.parse("{R}{R}")!!),
                        restriction = ObjectFilter.Or(listOf(
                            ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Spell), ObjectFilter.Sub(Subtype("Room")))),
                            ObjectFilter.AbilityOf(kind = AbilityKind.Unlock),
                        )),
                    ),
                    Players.You,
                )))),
            ),
        ),
        door(
            name = "Foggy Salon",
            cost = "{3}{U}",
            text = "When you unlock this door, create an X/X blue Spirit creature token with flying, where X is the number of unlocked doors among Rooms you control.",
        ) to abilities(
            whenYouUnlockThisDoor(
                Step.Do(Effect.CreateToken(
                    TokenSpec.Defined(
                        FaceDefinition(
                            name = "Spirit Token",
                            colorIndicator = listOf(Color.Blue),
                            types = listOf(CardType.Creature),
                            subtypes = listOf(Subtype("Spirit")),
                            power = PrintedNumber.X,
                            toughness = PrintedNumber.X,
                            abilities = listOf(Keyword.Flying),
                        ),
                        power = Value.Of(NumericProperty.UnlockedDoors, Objects.Each(roomsYouControl), Combine.Total),
                        toughness = Value.Of(NumericProperty.UnlockedDoors, Objects.Each(roomsYouControl), Combine.Total),
                    ),
                    count = Value.Fixed(1),
                    controller = Players.You,
                )),
            ),
        ),
    )
}
