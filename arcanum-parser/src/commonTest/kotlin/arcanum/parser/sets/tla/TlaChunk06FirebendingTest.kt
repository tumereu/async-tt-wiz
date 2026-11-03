package arcanum.parser.sets.tla

import arcanum.api.AbilityLabel
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Avatar: The Last Airbender chunk 06: firebending (CR 702.189a: whenever this creature attacks, add N {R} kept until end of combat) as a
 * keyword, with X, granted (while a Lesson is in the graveyard, until end of turn, to tokens); copying a creature's attack-caused trigger
 * (CR 603.2, 707.10), copying the next Lesson spell, exiled cards played until a later turn, a token copy that isn't legendary.
 */
class TlaChunk06FirebendingTest {
    private fun addR(): Step = doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}")!!)), Players.You))
    private val entersTappedUnlessBasic = static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.EntersTapped)), condition = Condition.Not(Condition.Exists(and(basic, land, you), 1)))

    // CR 702.189a
    @Test
    fun vexaOnTheHunt() = card(
        name = "Vexa, On the Hunt",
        cost = "{3}{B}",
        type = "Legendary Creature — Human Noble",
        text = """
            Firebending 2 (Whenever this creature attacks, add {R}{R}. This mana lasts until end of combat.)
            Whenever Vexa attacks, you lose 1 life and create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
        pt = "4/3",
    ) parsesTo abilities(
        firebending(2),
        triggered(attacksSelf, loseLife(1), createClue),
    )

    // CR 607.2a: cards exiled with it, cast when it attacks
    @Test
    fun scaldingPitRioter() = card(
        name = "Scalding Pit Rioter",
        cost = "{2}{B}",
        type = "Creature — Human Rogue Ally",
        text = """
            Firebending 1 (Whenever this creature attacks, add {R}. This mana lasts until end of combat.)
            Tap an untapped Ally you control: Exile target card from a graveyard.
            Whenever this creature attacks, you may cast an Ally spell from among cards you own exiled with this creature.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        firebending(1),
        activated(cost(CostPart.TapObjects(CostObjects.Choose(ally, count = n(1)))),
            move(target("card"), Zone.Exile, postBind = "exiled"), doing(Effect.Remember("exiled", Objects.Bound("exiled"))),
            targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard))))),
        triggered(attacksSelf,
            Step.Choose(Choice.Objects(and(ally, ObjectFilter.OwnedBy(PlayerFilter.You), inZone(Zone.Exile)), n(0), n(1), from = Objects.Remembered("exiled")),
                bind = "card", chooser = Players.You),
            doing(Effect.Cast(Objects.Bound("card"), PlayKind.Cast, caster = Players.You))),
    )

    // CR 702.189a, 702.122a
    @Test
    fun permafrostTank() = card(
        name = "Permafrost Tank",
        cost = "{2}{B}",
        type = "Artifact — Vehicle",
        text = """
            Firebending 1 (Whenever this creature attacks, add {R}. This mana lasts until end of combat.)
            When this Vehicle enters, target creature you control gains indestructible until end of turn.
            Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "4/4",
    ) parsesTo abilities(
        firebending(1),
        triggered(entersSelf, gains(target("creature"), Keyword.Indestructible), targets = listOf(slot("creature", creatureYouControl))),
        Keyword.Crew(1),
    )

    // CR 111.4: tokens with firebending
    @Test
    fun theEmberLegionStrikes() = card(
        name = "The Ember Legion Strikes",
        cost = "{4}{R}",
        type = "Instant",
        text = """
            Create two 2/2 red Soldier creature tokens with firebending 1. (Whenever a creature with firebending 1 attacks, add {R}. This mana lasts until end of combat.)
            Flashback {8}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(create(soldierFirebending, n(2))),
        Keyword.Flashback(manaCost("{8}{R}")),
    )

    // CR 613.1f: firebending while a Lesson is in the graveyard
    @Test
    fun emberLegionCadets() = card(
        name = "Ember Legion Cadets",
        cost = "{R}",
        type = "Creature — Human Soldier",
        text = """
            This creature has firebending 2 as long as there's a Lesson card in your graveyard. (Whenever this creature attacks, add {R}{R}. This mana lasts until end of combat.)
            {2}: This creature gets +1/+0 until end of turn.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        static(has(firebending(2)), condition = lessonInGraveyard),
        activated(cost(mana("{2}")), pump(Objects.Self, 1, 0)),
    )

    @Test
    fun flameElders() = card(
        name = "Flame Elders",
        cost = "{1}{R}",
        type = "Creature — Human Cleric",
        text = """
            Firebending 1 (Whenever this creature attacks, add {R}. This mana lasts until end of combat.)
            {1}{R}{R}: Put a +1/+1 counter on this creature.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        firebending(1),
        activated(cost(mana("{1}{R}{R}")), counters(Objects.Self)),
    )

    // CR 603.2 (a trigger caused by the creature attacking), 707.10 (copying a triggered ability)
    @Test
    fun flamecallerAscension() = card(
        name = "Flamecaller Ascension",
        cost = "{1}{R}",
        type = "Enchantment",
        text = """
            When this enchantment enters, create a 2/2 red Soldier creature token with firebending 1.
            Whenever a creature you control attacking causes a triggered ability of that creature to trigger, put a quest counter on this enchantment. Then if it has four or more quest counters on it, you may copy that ability. You may choose new targets for the copy.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(soldierFirebending)),
        triggered(on(EventPattern.AbilityTriggers(creatureYouControl, cause = EventPattern.Attack(ObjectFilter.Is(Objects.EventObject(EventRole.Source))))),
            counters(Objects.Self, 1, quest),
            Step.If(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(quest, atLeast = 4)), then = listOf(
                Step.May(listOf(doing(Effect.CopySpell(Objects.EventObject(EventRole.Object), Players.You, newTargets = true))), player = Players.You)))),
    )

    // CR 702.189a with X read as it resolves
    @Test
    fun flamecallingStudent() = card(
        name = "Flamecalling Student",
        cost = "{1}{R}",
        type = "Creature — Human Monk",
        text = """
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            Firebending X, where X is this creature's power. (Whenever this creature attacks, add X {R}. This mana lasts until end of combat.)
        """,
        pt = "1/2",
    ) parsesTo abilities(
        Keyword.Prowess,
        Keyword.FirebendingX(powerOf(Objects.Self)),
    )

    // CR 603.7: "when you next cast" (a delayed trigger, once), 707.10
    @Test
    fun corvaneTheDeserter() = card(
        name = "Corvane, the Deserter",
        cost = "{2}{R}",
        type = "Legendary Creature — Human Rebel Ally",
        text = """
            Firebending 1 (Whenever this creature attacks, add {R}. This mana lasts until end of combat.)
            Exhaust — {3}: Put a +1/+1 counter on Corvane. When you next cast a Lesson spell this turn, copy it and you may choose new targets for the copy. (Activate each exhaust ability only once.)
        """,
        pt = "2/3",
    ) parsesTo abilities(
        firebending(1),
        activated(cost(mana("{3}")), counters(Objects.Self),
            doing(Effect.CreateDelayedTrigger(youCast(lesson), Instructions(steps = listOf(
                doing(Effect.CopySpell(Objects.EventObject(EventRole.Object), Players.You, newTargets = true)))), duration = Duration.EndOfTurn)),
            label = AbilityLabel.Exhaust),
    )

    // CR 714.2b, 106.1 (one mana of any colour), 111.4
    @Test
    fun theTaleOfArdan() = cardFaces(Layout.Transform,
        card(
            name = "The Tale of Ardan",
            cost = "{2}{R}{R}",
            type = "Enchantment — Saga",
            text = """
                (As this Saga enters and after your draw step, add a lore counter.)
                I — Exile the top three cards of your library. Until the end of your next turn, you may play those cards.
                II — Add one mana of any color.
                III — Exile this Saga, then return it to the battlefield transformed under your control.
            """,
        ) to abilities(
            chapter(1, move(Objects.LibraryCards(Players.You, n(3), fromTop = true), Zone.Exile, postBind = "exiled"),
                doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")),
                    Duration.UntilEndOfYourNextTurn))),
            chapter(2, doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
            exileAndReturnTransformed,
        ),
        card(
            name = "Herald Ardan",
            cost = null,
            type = "Legendary Creature — Avatar",
            text = """
                Firebending 4 (Whenever this creature attacks, add {R}{R}{R}{R}. This mana lasts until end of combat.)
                {8}: Create a 4/4 red Dragon creature token with flying and firebending 4.
            """,
            pt = "4/4",
        ) to abilities(
            firebending(4),
            activated(cost(mana("{8}")), create(creatureToken("Dragon", listOf(Color.Red), 4, 4, abilities = listOf(Keyword.Flying, firebending(4))))),
        ),
    )

    // CR 603.4, 601.2 ("if you cast them"), 707.9b
    @Test
    fun velAndSorr() = card(
        name = "Vel and Sorr",
        cost = "{3}{R}{R}",
        type = "Legendary Creature — Dragon",
        text = """
            Flying, firebending 2
            When Vel and Sorr enter, if you cast them and there are three or more Dragon and/or Lesson cards in your graveyard, create a token that's a copy of Vel and Sorr, except it's not legendary.
            {3}{R}: Dragons you control get +2/+0 until end of turn.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Flying,
        firebending(2),
        triggered(entersSelf,
            doing(Effect.CreateToken(TokenSpec.CopyOf(Objects.Self, except = listOf(CopyException.Types(removeSupertypes = listOf(Supertype.Legendary)))), n(1), Players.You)),
            interveningIf = Condition.And(listOf(Condition.CastFrom(null, Objects.Self),
                Condition.Exists(inYourGraveyard(or(sub("Dragon"), lesson)), atLeast = 3)))),
        activated(cost(mana("{3}{R}")), pump(Objects.Each(and(sub("Dragon"), you)), 2, 0)),
    )

    @Test
    fun ruggedRhinoLancers() = card(
        name = "Rugged Rhino Lancers",
        cost = "{4}{R}",
        type = "Creature — Human Mercenary",
        text = """
            Firebending 2 (Whenever this creature attacks, add {R}{R}. This mana lasts until end of combat.)
            Exhaust — {8}: Put two +1/+1 counters on this creature. It gains trample until end of turn. (Activate each exhaust ability only once.)
        """,
        pt = "5/5",
    ) parsesTo abilities(
        firebending(2),
        activated(cost(mana("{8}")), counters(Objects.Self, 2), gains(Objects.Self, Keyword.Trample), label = AbilityLabel.Exhaust),
    )

    // CR 613.1f (firebending granted until end of turn), 702.143a (foretell)
    @Test
    fun vardansComet() = card(
        name = "Vardan's Comet",
        cost = "{3}{R}{R}",
        type = "Sorcery",
        text = """
            Each creature you control gains firebending 5 until end of turn. (Whenever it attacks, add {R}{R}{R}{R}{R}. This mana lasts until end of combat.)
            Foretell {2}{R} (During your turn, you may pay {2} and exile this card from your hand face down. Cast it on a later turn for its foretell cost.)
        """,
    ) parsesTo abilities(
        spell(gains(Objects.Each(creatureYouControl), firebending(5))),
        Keyword.Foretell(manaCost("{2}{R}")),
    )

    @Test
    fun darrowExiledPrince() = card(
        name = "Darrow, Exiled Prince",
        cost = "{3}{R}",
        type = "Legendary Creature — Human Noble",
        text = """
            Firebending 3 (Whenever this creature attacks, add {R}{R}{R}. This mana lasts until end of combat.)
            {3}: Exile the top card of your library. You may play that card this turn.
        """,
        pt = "4/3",
    ) parsesTo abilities(
        firebending(3),
        activated(cost(mana("{3}")), move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")),
                Duration.EndOfTurn))),
    )

    @Test
    fun emberLegionPalace() = card(
        name = "Ember Legion Palace",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control a basic land.
            {T}: Add {R}.
            {1}{R}, {T}: Target creature you control gains firebending 4 until end of turn. (Whenever it attacks, add {R}{R}{R}{R}. This mana lasts until end of combat.)
        """,
    ) parsesTo abilities(
        entersTappedUnlessBasic,
        activated(cost(tap), addR()),
        activated(cost(mana("{1}{R}"), tap), gains(target("creature"), firebending(4)), targets = listOf(slot("creature", creatureYouControl))),
    )
}
