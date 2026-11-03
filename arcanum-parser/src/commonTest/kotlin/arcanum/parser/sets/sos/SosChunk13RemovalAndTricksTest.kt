package arcanum.parser.sets.sos

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Secrets of Strixhaven chunk 13: removal and combat tricks of one colour — costs reduced by what the spell targets (CR 601.2f, after
 * targets are chosen), the destroyed permanent's controller searching (CR 701.23), the owner's choice of top or bottom, several "up to one
 * target" paragraphs, X from cards named like the spell in the graveyard, "damage can't be prevented this turn" (CR 615.12), fighting
 * after a boost (CR 701.14), modes, and creatures gaining a quoted dies trigger until end of turn.
 */
class SosChunk13RemovalAndTricksTest {
    private val controller = Players.ControllerOf(target("permanent"))
    private val namedInGraveyard = Value.Sum(listOf(n(1), Value.Count(Objects.Each(inYourGraveyard(ObjectFilter.Named("Forebears' Wrath"))))))

    // CR 601.2f: targets are chosen before the cost is determined
    @Test
    fun mentorsReply() = card(
        name = "Mentor's Reply",
        cost = "{4}{W}",
        type = "Instant",
        text = """
            This spell costs {3} less to cast if it targets a tapped creature.
            Destroy target creature.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{3}")!!, n(1))),
            condition = Condition.ObjectMatches(target("creature"), tapped)),
        spell(destroy(target("creature")), targets = listOf(slot("creature", creature))),
    )

    // CR 701.23: the destroyed permanent's controller searches their own library
    @Test
    fun wearAwayToSand() = card(
        name = "Wear Away to Sand",
        cost = "{W}",
        type = "Instant",
        text = "Destroy target creature or planeswalker. Its controller may search their library for a basic land card, put it onto the battlefield tapped, then shuffle.",
    ) parsesTo abilities(
        spell(
            destroy(target("permanent")),
            Step.May(listOf(
                doing(Effect.Search(controller, basicLand(), max = n(1), zones = listOf(Zone.Library), bind = "found", searcher = controller)),
                doing(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield, tapped = true, controller = controller))),
                doing(Effect.Shuffle(controller))), controller),
            targets = listOf(slot("permanent", or(creature, ObjectFilter.Type(arcanum.api.CardType.Planeswalker)))),
        ),
    )

    @Test
    fun sharpInterruption() = card(
        name = "Sharp Interruption",
        cost = "{W}",
        type = "Instant",
        text = "Target creature gets +2/+2 and gains first strike until end of turn.",
    ) parsesTo abilities(
        spell(pump(target("creature"), 2, 2, has(Keyword.FirstStrike)), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun holdYourGround() = card(
        name = "Hold Your Ground",
        cost = "{2}{W}",
        type = "Instant",
        text = "Destroy target creature with power 3 or greater.",
    ) parsesTo abilities(
        spell(destroy(target("creature")), targets = listOf(slot("creature", and(creature, power(Comparison.AtLeast, 3))))),
    )

    @Test
    fun splinteredNovice() = card(
        name = "Splintered Novice",
        cost = "{1}{W}",
        type = "Creature — Dwarf Warlock",
        pt = "2/2",
        text = """
            Lifelink
            {1}, Sacrifice this creature: Destroy target artifact or enchantment.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        activated(cost(mana("{1}"), sacrificeSelf), destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, enchantment)))),
    )

    @Test
    fun exilingTreachery() = card(
        name = "Exiling Treachery",
        cost = "{1}{U}",
        type = "Instant",
        text = "Return target nonland permanent to its owner's hand. Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)",
    ) parsesTo abilities(
        spell(move(target("permanent"), Zone.Hand), doing(Effect.Surveil(Players.You, n(1))), targets = listOf(slot("permanent", and(nonland, permanentFilter)))),
    )

    @Test
    fun waveAside() = card(
        name = "Wave Aside",
        cost = "{2}{U}{U}",
        type = "Instant",
        text = """
            This spell costs {1}{U} less to cast if it targets an instant or sorcery spell.
            Counter target spell.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}{U}")!!, n(1))),
            condition = Condition.ObjectMatches(target("spell"), instantOrSorcery)),
        spell(doing(Effect.Counter(target("spell"))), targets = listOf(slot("spell", spellFilter))),
    )

    @Test
    fun pursueTheMuse() = card(
        name = "Pursue the Muse",
        cost = "{U}",
        type = "Instant",
        text = "Target creature you control gets +0/+3 and gains hexproof until end of turn. (It can't be the target of spells or abilities your opponents control.)",
    ) parsesTo abilities(
        spell(pump(target("creature"), 0, 3, has(Keyword.Hexproof)), targets = listOf(slot("creature", creatureYouControl))),
    )

    @Test
    fun spiritDispersal() = card(
        name = "Spirit Dispersal",
        cost = "{1}{U}",
        type = "Instant",
        text = "Counter target creature spell.",
    ) parsesTo abilities(
        spell(doing(Effect.Counter(target("spell"))), targets = listOf(slot("spell", and(creature, spellFilter)))),
    )

    // CR 608.2d: the owner chooses as it resolves
    @Test
    fun fallBehindSchedule() = card(
        name = "Fall Behind Schedule",
        cost = "{3}{U}",
        type = "Instant",
        text = """
            This spell costs {1} less to cast if it targets an attacking creature.
            Target creature's owner puts it on their choice of the top or bottom of their library.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, n(1))),
            condition = Condition.ObjectMatches(target("creature"), attacking)),
        spell(
            Step.Choose(Choice.Option(listOf("top", "bottom")), bind = "position", chooser = Players.OwnerOf(target("creature"))),
            ifThen(Condition.Chose("position", "top"), move(target("creature"), Zone.Library, position = 0),
                otherwise = listOf(move(target("creature"), Zone.Library, position = -1))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    @Test
    fun priceOfGenius() = card(
        name = "Price of Genius",
        cost = "{2}{B}",
        type = "Sorcery",
        text = "Target player draws two cards and loses 2 life. Put a +1/+1 counter on up to one target creature.",
    ) parsesTo abilities(
        spell(draw(2, Players.Target("player")), loseLife(2, Players.Target("player")), counters(target("creature")),
            targets = listOf(targetPlayer, slot("creature", creature, min = 0))),
    )

    // CR 115.3: each "up to one target creature" is its own target
    @Test
    fun anatomyDrill() = card(
        name = "Anatomy Drill",
        cost = "{B}",
        type = "Instant",
        text = """
            Target opponent loses 1 life and you gain 1 life.
            Up to one target creature gets +1/+1 until end of turn.
            Up to one target creature gets -1/-1 until end of turn.
        """,
    ) parsesTo abilities(
        spell(
            loseLife(1, Players.Target("opponent")),
            gainLife(1),
            pump(target("creature"), 1, 1),
            pump(target("second creature"), -1, -1),
            targets = listOf(targetOpponent, slot("creature", creature, min = 0), slot("second creature", creature, min = 0)),
        ),
    )

    @Test
    fun finalBreathDrawn() = card(
        name = "Final Breath Drawn",
        cost = "{1}{B}",
        type = "Instant",
        text = "Target creature gets -3/-3 until end of turn.",
    ) parsesTo abilities(
        spell(pump(target("creature"), -3, -3), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun virtuosoFlourish() = card(
        name = "Virtuoso Flourish",
        cost = "{B}",
        type = "Instant",
        text = "Target creature you control gets +1/+0 and gains indestructible until end of turn. (Damage and effects that say \"destroy\" don't destroy it.)",
    ) parsesTo abilities(
        spell(pump(target("creature"), 1, 0, has(Keyword.Indestructible)), targets = listOf(slot("creature", creatureYouControl))),
    )

    @Test
    fun strayAway() = card(
        name = "Stray Away",
        cost = "{3}{B}",
        type = "Instant",
        text = "Exile target creature.",
    ) parsesTo abilities(
        spell(move(target("creature"), Zone.Exile), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun burrogDoomcrafter() = card(
        name = "Burrog Doomcrafter",
        cost = "{B}",
        type = "Creature — Frog Warlock",
        pt = "1/1",
        text = """
            Deathtouch
            {1}{B}: This creature gets +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch, activated(manaCost("{1}{B}"), pump(Objects.Self, 1, 1)),
    )

    // CR 107.3c: X counted as it resolves (CR 201.2: cards with the same name)
    @Test
    fun forebearsWrath() = card(
        name = "Forebears' Wrath",
        cost = "{R}",
        type = "Sorcery",
        text = """
            Target creature gains trample and gets +X/+0 until end of turn, where X is 1 plus the number of cards named Forebears' Wrath in your graveyard.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), has(Keyword.Trample), Mechanic.ModifyPowerToughness(namedInGraveyard, n(0))),
            draw(1),
            targets = listOf(slot("creature", creature)),
        ),
    )

    @Test
    fun creativeMethod() = card(
        name = "Creative Method",
        cost = "{3}{R}{R}",
        type = "Sorcery",
        text = """
            Choose one —
            • Creative Method deals 6 damage to target creature.
            • Creative Method deals 2 damage to each creature you don't control.
            • Create a 3/3 blue and red Elemental creature token with flying. It gains haste until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("creature", creature)), steps = listOf(damage(6, toObjects(target("creature")))))),
            Mode(Instructions(steps = listOf(damage(2, toObjects(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))))))))),
            Mode(Instructions(steps = listOf(create(elemental, bind = "token"), gains(Objects.Bound("token"), Keyword.Haste)))),
        ), min = 1, max = 1, chooser = Players.You))),
    )

    // CR 615.12: damage that can't be prevented
    @Test
    fun illTimedJest() = card(
        name = "Ill-Timed Jest",
        cost = "{R}",
        type = "Sorcery",
        text = "Damage can't be prevented this turn. Ill-Timed Jest deals 3 damage to up to one target creature or planeswalker.",
    ) parsesTo abilities(
        spell(
            doing(Effect.ApplyMechanics(listOf(Mechanic.DamageCantBePrevented(EventPattern.Damage(ObjectFilter.Any, RecipientFilter()))), null, Duration.EndOfTurn)),
            damage(3, toObjects(target("permanent"))),
            targets = listOf(slot("permanent", or(creature, ObjectFilter.Type(arcanum.api.CardType.Planeswalker)), min = 0)),
        ),
    )

    @Test
    fun blatantRidicule() = card(
        name = "Blatant Ridicule",
        cost = "{2}{R}",
        type = "Instant",
        text = "Blatant Ridicule deals 4 damage to target creature. Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)",
    ) parsesTo abilities(
        spell(damage(4, toObjects(target("creature"))), doing(Effect.Surveil(Players.You, n(1))), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun buckingCindermare() = card(
        name = "Bucking Cindermare",
        cost = "{4}{R}",
        type = "Creature — Horse Beast",
        pt = "4/5",
        text = "Reach, haste",
    ) parsesTo abilities(
        Keyword.Reach, Keyword.Haste,
    )

    // CR 608.2h: "another" excludes this spell; the damage uses its power as it resolves
    @Test
    fun burrogBombardment() = card(
        name = "Burrog Bombardment",
        cost = "{1}{G}",
        type = "Instant",
        text = "Target creature you control gets +1/+0 until end of turn if you've cast another instant or sorcery spell this turn. Then it deals damage equal to its power to up to one target creature an opponent controls.",
    ) parsesTo abilities(
        spell(
            ifThen(Condition.HappenedThisTurn(EventPattern.Cast(and(instantOrSorcery, spellFilter, another), PlayerFilter.You), atLeast = 1, measure = Measure.Events),
                pump(target("creature"), 1, 0)),
            doing(Effect.DealDamage(toObjects(target("second creature")), power(target("creature")), target("creature"))),
            targets = listOf(slot("creature", creatureYouControl), slot("second creature", creatureOpponentControls, min = 0)),
        ),
    )

    // CR 701.14: fight
    @Test
    fun tortoiseTakedown() = card(
        name = "Tortoise Takedown",
        cost = "{2}{G}",
        type = "Sorcery",
        text = "Target creature you control gets +0/+10 until end of turn. Then it fights up to one target creature an opponent controls. (Each deals damage equal to its power to the other.)",
    ) parsesTo abilities(
        spell(
            pump(target("creature"), 0, 10),
            doing(Effect.Fight(target("creature"), target("second creature"))),
            targets = listOf(slot("creature", creatureYouControl), slot("second creature", creatureOpponentControls, min = 0)),
        ),
    )

    @Test
    fun splendidRot() = card(
        name = "Splendid Rot",
        cost = "{1}{G}",
        type = "Instant",
        text = """
            Choose one —
            • Destroy target artifact.
            • Splendid Rot deals 4 damage to target creature with flying.
            • Exile target card from a graveyard. Draw a card.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("artifact", artifact)), steps = listOf(destroy(target("artifact"))))),
            Mode(Instructions(targets = listOf(slot("creature", and(creature, kw(Keyword.Flying)))), steps = listOf(damage(4, toObjects(target("creature")))))),
            Mode(Instructions(targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard)))), steps = listOf(move(target("card"), Zone.Exile, postBind = "exiled"), draw(1)))),
        ), min = 1, max = 1, chooser = Players.You))),
    )

    @Test
    fun toxicEft() = card(
        name = "Toxic Eft",
        cost = "{1}{G}",
        type = "Creature — Salamander",
        pt = "1/2",
        text = """
            Deathtouch
            {T}: Add {G}.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch, activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!)), Players.You))),
    )

    @Test
    fun seersRenewal() = card(
        name = "Seer's Renewal",
        cost = "{G}",
        type = "Sorcery",
        text = "Target creature you control gets +1/+1 until end of turn. You draw a card and gain 1 life.",
    ) parsesTo abilities(
        spell(pump(target("creature"), 1, 1), draw(1), gainLife(1), targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 611.2c: the creatures are fixed as it resolves; each gains its own dies trigger
    @Test
    fun frenziedAssault() = card(
        name = "Frenzied Assault",
        cost = "{1}{B}",
        type = "Instant",
        text = "Until end of turn, any number of target creatures you control each get +1/+0 and gain \"When this creature dies, draw a card.\"",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), pt(1, 0), has(triggered(diesSelf, draw(1)))),
            targets = listOf(slot("creature", creatureYouControl, min = 0, max = null)),
        ),
    )

    @Test
    fun rootbindingCraft() = card(
        name = "Rootbinding Craft",
        cost = "{3}{B}{G}",
        type = "Sorcery",
        text = "Until end of turn, creatures you control get +2/+2 and gain menace and \"Whenever this creature attacks, you gain 1 life.\" (A creature with menace can't be blocked except by two or more creatures.)",
    ) parsesTo abilities(
        spell(doing(Effect.ApplyMechanics(listOf(pt(2, 2), has(Keyword.Menace, triggered(attacksSelf, gainLife(1)))), Affected.Objects(Objects.Each(creatureYouControl)),
            Duration.EndOfTurn))),
    )
}
