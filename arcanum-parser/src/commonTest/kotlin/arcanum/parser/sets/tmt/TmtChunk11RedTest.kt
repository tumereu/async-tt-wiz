package arcanum.parser.sets.tmt

import arcanum.api.DamageOccurrence
import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Teenage Mutant Ninja Turtles chunk 11: red cards. Damage with an exile-instead replacement for the turn (CR 614.1a), stealing all
 * opponents' artifacts until end of turn (a locked set, CR 611.2c), revealing an artifact among the top cards, a delayed random discard
 * (CR 603.7), a sacrifice with a reflexive trigger, putting a card from hand on the bottom to draw (CR 118.12), modes, enters-or-leaves
 * triggers, first strike during your turn, a token that gains haste and is destroyed at the next end step ("it" is the token), mana
 * spendable on artifact spells or abilities (CR 106.6), "must be blocked if able", enrage adding mana that isn't lost (CR 106.4), cast
 * triggers and "can't block this turn".
 */
class TmtChunk11RedTest {
    private fun robots(count: Int = 1) = create(robotToken, n(count))

    // CR 614.1a: if it would die this turn, it is exiled instead
    @Test
    fun robotSmashingHour() = card(
        name = "Robot Smashing Hour",
        cost = "{3}{R}",
        type = "Sorcery",
        text = "Robot Smashing Hour deals 6 damage to target creature. If that creature would die this turn, exile it instead.",
    ) parsesTo abilities(
        spell(damage(6, toObjects(target("creature"))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(target("creature")), from = Zone.Battlefield, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), affects = null, duration = Duration.EndOfTurn)),
            targets = listOf(slot("creature", creature))),
    )

    // CR 611.2c: the artifacts are fixed as the effect begins
    @Test
    fun airwaveHijacking() = card(
        name = "Airwave Hijacking",
        cost = "{2}{R}{R}{R}",
        type = "Sorcery",
        text = "Gain control of all artifacts your opponents control until end of turn. Untap them. They gain haste until end of turn.",
    ) parsesTo abilities(
        spell(Step.Bind(Objects.Each(and(artifact, opponents)), "them"),
            untilEndOfTurn(Objects.Bound("them"), Mechanic.ControlledBy(Players.You)),
            untapIt(Objects.Bound("them")),
            gains(Objects.Bound("them"), Keyword.Haste)),
    )

    @Test
    fun mackHardyJuryRigJusticiar() = card(
        name = "Mack Hardy, Jury-Rig Justiciar",
        cost = "{1}{R}",
        type = "Legendary Creature — Human Berserker",
        text = """
            Haste
            When Mack Hardy enters, look at the top four cards of your library. You may reveal an artifact card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(entersSelf,
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(4), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(artifact, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
    )

    // CR 603.7: a delayed trigger at your next upkeep
    @Test
    fun mackHardyStreetVigilante() = card(
        name = "Mack Hardy, Street Vigilante",
        cost = "{1}{R}{R}",
        type = "Legendary Creature — Human Berserker",
        text = "When Mack Hardy enters, draw three cards. At the beginning of your next upkeep, discard three cards at random.",
        pt = "4/3",
    ) parsesTo abilities(
        triggered(entersSelf, draw(3), delayed(stepBegins(arcanum.api.TurnStep.Upkeep, PlayerFilter.You),
            doing(Effect.Discard(Players.You, n(3), ObjectFilter.Any, random = true)))),
    )

    // CR 603.12: the reflexive trigger's target is chosen after the sacrifice
    @Test
    fun generalGraniteHeartOfStone() = card(
        name = "General Granite, Heart of Stone",
        cost = "{3}{R}{R}",
        type = "Legendary Artifact Creature — Elemental Soldier",
        text = """
            Trample
            When General Granite enters, you may sacrifice another artifact. When you do, General Granite deals 4 damage to target creature.
        """,
        pt = "4/3",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(entersSelf,
            Step.Pay(cost(sacrificeA(and(another, artifact))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(damage(4, toObjects(target("creature"))), targets = listOf(slot("creature", creature)))))),
    )

    @Test
    fun manholeCoverToss() = card(
        name = "Manhole Cover Toss",
        cost = "{1}{R}",
        type = "Instant",
        text = "Manhole Cover Toss deals 3 damage to target creature. You may put a card from your hand on the bottom of your library. If you do, draw a card.",
    ) parsesTo abilities(
        spell(damage(3, toObjects(target("creature"))),
            Step.Pay(steps = listOf(
                Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))),
                    bind = "put", chooser = Players.You),
                move(Objects.Bound("put"), Zone.Library, position = -1)), payer = Players.You, bind = "bottom"),
            Step.If(Condition.Did("bottom"), then = listOf(draw(1))),
            targets = listOf(slot("creature", creature))),
    )

    @Test
    fun roboRodentAttack() = card(
        name = "Robo-Rodent Attack!",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Choose one —
            • Create a 1/1 colorless Robot artifact creature token.
            • Target creature gets +3/+0 and gains first strike until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(robots()),
            mode(pump(target("creature"), 3, 0, has(Keyword.FirstStrike)), targets = listOf(slot("creature", creature)))))),
    )

    @Test
    fun roboRodentFoundry() = card(
        name = "Robo-Rodent Foundry",
        cost = "{1}{R}",
        type = "Artifact",
        text = """
            When this artifact enters or leaves the battlefield, create a 1/1 colorless Robot artifact creature token.
            {4}{R}, Sacrifice this artifact: It deals 3 damage to target creature.
        """,
    ) parsesTo abilities(
        triggered(arcanum.api.Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield))), robots()),
        activated(cost(mana("{4}{R}"), sacrificeSelf), damage(3, toObjects(target("creature"))), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun blackOpsBioAssets() = card(
        name = "Black Ops Bio-Assets",
        cost = "{2}{R}",
        type = "Creature — Mutant Mercenary",
        text = """
            During your turn, this creature has first strike.
            Whenever this creature attacks, you may discard a card. If you do, draw a card.
        """,
        pt = "3/1",
    ) parsesTo abilities(
        static(has(Keyword.FirstStrike), condition = Condition.YourTurn),
        triggered(attacksSelf, Step.Pay(cost(discardACard), payer = Players.You, bind = "paid"), Step.If(Condition.Did("paid"), then = listOf(draw(1)))),
    )

    // CR 111.1: "it" is the token just created; CR 603.7: destroyed at the next end step
    @Test
    fun scrapperTomAlleycatBlues() = card(
        name = "Scrapper Tom, Alleycat Blues",
        cost = "{4}{R}",
        type = "Legendary Creature — Cat Mutant Rebel",
        text = """
            At the beginning of combat on your turn, create a 2/2 red Mutant creature token. It gains haste until end of turn. Destroy it at the beginning of the next end step.
            {1}{W}: Target attacking creature token gains indestructible until end of turn.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn,
            create(mutantToken, bind = "token"),
            gains(Objects.Bound("token"), Keyword.Haste),
            delayed(nextEndStep, destroy(Objects.Bound("token")))),
        activated(manaCost("{1}{W}"), gains(target("token"), Keyword.Indestructible), targets = listOf(slot("token", and(attacking, creature, token)))),
    )

    // CR 106.6: a restriction on how the mana is spent
    @Test
    fun violetSerpentPunks() = card(
        name = "Violet Serpent Punks",
        cost = "{1}{R}",
        type = "Creature — Human Rogue",
        text = "{T}: Add {R}. Spend this mana only to cast an artifact spell or to activate an ability.",
        pt = "2/2",
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}")!!),
            restriction = or(and(spellFilter, artifact), ObjectFilter.AbilityOf(kind = arcanum.api.AbilityKind.Activated))), Players.You))),
    )

    // CR 120.3: as much mana as the damage dealt; CR 106.4: kept until end of turn
    @Test
    fun hotshellNinjaDestroyer() = card(
        name = "Hotshell, Ninja Destroyer",
        cost = "{2}{R}{R}",
        type = "Legendary Creature — Mutant Ninja Turtle",
        text = """
            Hotshell must be blocked if able.
            Enrage — Whenever Hotshell is dealt damage, add that much {R}. Until end of turn, you don't lose this mana as steps and phases end.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        static(Mechanic.MustBeBlocked),
        triggered(on(EventPattern.Damage(ObjectFilter.Any, arcanum.api.RecipientFilter(objects = ObjectFilter.Is(Objects.Self)), per = DamageOccurrence.Recipient)),
            doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}")!!, times = Value.EventAmount), keptUntil = Duration.EndOfTurn), Players.You)),
            abilityWord = "Enrage"),
    )

    @Test
    fun voraciousRobots() = card(
        name = "Voracious Robots",
        cost = "{1}{R}",
        type = "Artifact Creature — Robot",
        text = """
            Whenever you cast an artifact spell, create a 1/1 colorless Robot artifact creature token.
            {R}, {T}: Creature tokens you control gain haste until end of turn.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        triggered(youCast(artifact), robots()),
        activated(cost(mana("{R}"), tap), gains(Objects.Each(and(creature, token, you)), Keyword.Haste)),
    )

    @Test
    fun boulderSoldiers() = card(
        name = "Boulder Soldiers",
        cost = "{3}{R}",
        type = "Artifact Creature — Elemental Soldier",
        text = "When this creature enters, destroy up to one target noncreature artifact.",
        pt = "4/3",
    ) parsesTo abilities(
        triggered(entersSelf, destroy(target("artifact")), targets = listOf(slot("artifact", and(not(creature), artifact), min = 0))),
    )

    @Test
    fun grogHornedCastaway() = card(
        name = "Grog, Horned Castaway",
        cost = "{4}{R}",
        type = "Legendary Creature — Dinosaur Soldier",
        text = """
            Reach, trample
            When Grog enters, target creature can't block this turn.
            Mountaincycling {2} ({2}, Discard this card: Search your library for a Mountain card, reveal it, put it into your hand, then shuffle.)
        """,
        pt = "5/4",
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Trample,
        triggered(entersSelf, untilEndOfTurn(target("creature"), Mechanic.CantBlock(ObjectFilter.Any)), targets = listOf(slot("creature", creature))),
        landcycling("Mountain"),
    )
}
