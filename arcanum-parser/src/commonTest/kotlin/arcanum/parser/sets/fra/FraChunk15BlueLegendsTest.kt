package arcanum.parser.sets.fra

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.gtc.creature
import arcanum.parser.sets.gtc.token
import arcanum.parser.sets.gtc.you
import kotlin.test.Test

/**
 * Reality Fracture chunk 15: blue legendary creatures. Untapping itself when another nontoken creature enters; a trigger on
 * opponents being dealt combat damage during your turn that can win the game with an empty library (CR 104.2b) and shuffles
 * the card away; cost reductions; tap-and-stun one creature of each opponent (CR 601.2c, 122.1d); drawing three cards in a turn;
 * a delayed trigger for the turn; "you may pay {2}" after scrying or surveilling; two instances of prowess (CR 702.108b);
 * granting split second to spells (CR 702.61); unblockable small creatures; a cost reduction after a noncreature spell; -X/-0
 * by the number of cards in your graveyard.
 */
class FraChunk15BlueLegendsTest {
    @Test
    fun orrinModestCopyist() = card(
        name = "Orrin, Modest Copyist",
        cost = "{2}{U}",
        type = "Legendary Creature — Human Wizard",
        pt = "3/2",
        text = """
            Whenever another nontoken creature you control enters, untap Orrin.
            {T}: Draw a card, then discard a card.
        """,
    ) parsesTo abilities(
            triggered(on(EventPattern.ZoneChange(and(anotherObject, not(token), creature, you), to = Zone.Battlefield)), Step.Do(Effect.Untap(Objects.Self))),
            activated(cost(tap), draw(1), Step.Do(Effect.Discard(Players.You, n(1), ObjectFilter.Any))),
        )

    // CR 104.2b, 701.24a
    @Test
    fun plimboHopelesslyAstray() = card(
        name = "Plimbo, Hopelessly Astray",
        cost = "{1}{U}",
        type = "Legendary Creature — Homunculus",
        pt = "1/1",
        text = "When one or more of your opponents are dealt combat damage during your turn, draw two cards. If your library has no cards in it, you win the game. Plimbo's owner shuffles him into their library. (If you draw from an empty library this way, you still win the game.)",
    ) parsesTo abilities(Ability.Triggered(Trigger.On(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(players = PlayerFilter.Opponent), combat = true), oneOrMore = true),
            Instructions(steps = listOf(
                draw(2),
                Step.If(Condition.Not(Condition.Exists(and(ObjectFilter.InZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)), 1)),
                    then = listOf(Step.Do(Effect.WinGame(Players.You)))),
                move(Objects.Self, Zone.Library),
                Step.Do(Effect.Shuffle(Players.OwnerOf(Objects.Self))))),
            triggersOnlyIf = Condition.YourTurn))

    // CR 601.2f
    @Test
    fun wraithOfBlessedMirela() = card(
        name = "Wraith of Blessed Mirela",
        cost = "{1}{U}",
        type = "Legendary Creature — Spirit Cleric",
        pt = "1/2",
        text = """
            Flying
            Noncreature spells you cast cost {1} less to cast.
        """,
    ) parsesTo abilities(Keyword.Flying, static(Mechanic.CostChange(and(not(creature), spellFilter, you), CostChange.Less(ManaCost.parse("{1}")!!, n(1)))))

    // CR 601.2c, 122.1d
    @Test
    fun nefrahTheDuneChill() = card(
        name = "Nefrah, the Dune Chill",
        cost = "{3}{U}",
        type = "Legendary Creature — Human Wizard",
        pt = "4/3",
        text = """
            When Nefrah enters, for each opponent, tap up to one target creature that player controls. Put a stun counter on each of those creatures. (If a permanent with a stun counter would become untapped, remove one from it instead.)
            {2}{U}: Untap target creature.
        """,
    ) parsesTo abilities(
            triggered(entersSelf, Step.Do(Effect.Tap(target("creature"))), counters(target("creature"), kind = CounterKind.Stun),
                targets = listOf(TargetSlot("creature", TargetKind.Objects(and(creature, controlledBy(PlayerFilter.Is(Players.SlotPlayer)))), min = n(0), max = n(1),
                    perPlayer = PlayerFilter.Opponent))),
            activated(cost(mana("{2}{U}")), Step.Do(Effect.Untap(target("creature"))), targets = listOf(slot("creature", creature))),
        )

    // CR 603.7b
    @Test
    fun aurielleAcademySeraph() = card(
        name = "Aurielle, Academy Seraph",
        cost = "{1}{U}{U}",
        type = "Legendary Creature — Angel Wizard",
        pt = "3/3",
        text = """
            Flying
            At the beginning of each end step, if you've drawn three or more cards this turn, create a 3/3 blue Angel creature token with flying.
            {3}{U}{U}: Until end of turn, whenever Aurielle deals combat damage to a player, draw two cards.
        """,
    ) parsesTo abilities(
            Keyword.Flying,
            triggered(stepBegins(TurnStep.End, PlayerFilter.Any), Step.Do(Effect.CreateToken(creatureToken("Angel", listOf(Color.Blue), 3, 3, abilities = listOf(Keyword.Flying)),
                n(1), Players.You)), interveningIf = thisTurn(EventPattern.Draw(PlayerFilter.You), 3)),
            activated(cost(mana("{3}{U}{U}")), Step.Do(Effect.CreateDelayedTrigger(on(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(players = PlayerFilter.Any),
                combat = true)), Instructions(steps = listOf(draw(2))), duration = Duration.EndOfTurn, repeats = true))),
        )

    // CR 118.12
    @Test
    fun quillInvestigatingSleuth() = card(
        name = "Quill, Investigating Sleuth",
        cost = "{1}{U}",
        type = "Legendary Creature — Human Detective",
        pt = "2/2",
        text = "Whenever you scry or surveil, you may pay {2}. If you do, put a +1/+1 counter on Quill and draw a card. (Draw after you scry or surveil.)",
    ) parsesTo abilities(triggered(youScryOrSurveil, Step.Pay(cost(mana("{2}")), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(counters(Objects.Self), draw(1)))))

    // CR 702.108b
    @Test
    fun gorrumVaxBiomancer() = card(
        name = "Gorrum Vax, Biomancer",
        cost = "{4}{U}{U}",
        type = "Legendary Creature — Ogre Crab Wizard",
        pt = "4/6",
        text = """
            Flying
            Prowess, prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn twice.)
            Whenever Gorrum Vax becomes the target of a spell or ability an opponent controls, draw a card.
        """,
    ) parsesTo abilities(
            Keyword.Flying,
            Keyword.Prowess,
            Keyword.Prowess,
            triggered(on(EventPattern.BecomesTarget(ObjectFilter.Is(Objects.Self), controlledBy(PlayerFilter.Opponent))), draw(1)),
        )

    // CR 702.61, 613.1f
    @Test
    fun hekatTyrantOfTheSands() = card(
        name = "Hekat, Tyrant of the Sands",
        cost = "{1}{U}",
        type = "Legendary Creature — Human Wizard",
        pt = "2/1",
        text = "Instant and sorcery spells you control have split second. (As long as a spell with split second is on the stack, players can't cast spells or activate abilities that aren't mana abilities.)",
    ) parsesTo abilities(static(Mechanic.GainAbilities(listOf(Keyword.SplitSecond)),
            affects = Objects.Each(and(or(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery)), spellFilter, you))))

    @Test
    fun rinkoTakaharaRunaway() = card(
        name = "Rinko Takahara, Runaway",
        cost = "{1}{U}",
        type = "Legendary Creature — Human Rogue",
        pt = "1/3",
        text = "Creatures you control with power or toughness 1 or less can't be blocked.",
    ) parsesTo abilities(static(Mechanic.CantBeBlocked, affects = Objects.Each(and(creature, you,
            or(ObjectFilter.Power(Comparison.AtMost, n(1)), ObjectFilter.Toughness(Comparison.AtMost, n(1)))))))

    // CR 601.2f
    @Test
    fun vortanScholasticWarden() = card(
        name = "Vortan, Scholastic Warden",
        cost = "{3}{U}",
        type = "Legendary Artifact Creature — Dragon Construct",
        pt = "1/5",
        text = """
            This spell costs {2} less to cast if you've cast a noncreature spell this turn.
            Flying, vigilance
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
        """,
    ) parsesTo abilities(
            static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{2}")!!, n(1))),
                condition = thisTurn(EventPattern.Cast(not(creature), PlayerFilter.You))),
            Keyword.Flying,
            Keyword.Vigilance,
            Keyword.Prowess,
        )

    @Test
    fun akemiLightFromTheShadows() = card(
        name = "Akemi, Light from the Shadows",
        cost = "{U}",
        type = "Legendary Creature — Human Ninja",
        pt = "1/1",
        text = """
            Flash
            When Akemi enters, choose one —
            • Target creature gets -X/-0 until end of turn, where X is the number of cards in your graveyard.
            • Surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        """,
    ) parsesTo abilities(Keyword.Flash, Ability.Triggered(entersSelf, Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("creature", creature)),
                steps = listOf(untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(Value.Times(cardsInYourGraveyard, -1), n(0)))))),
            Mode(Instructions(steps = listOf(surveil(2)))),
        ), min = 1, max = 1, chooser = Players.You))))
}
