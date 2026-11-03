package arcanum.parser.sets.msh

import arcanum.api.Ability
import arcanum.api.AbilityKind
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel Super Heroes chunk 16: more green cards. Doubling power and toughness (+X/+X, CR 701.10b) and a fight (CR 701.14) as modes;
 * a permanent card put into your hand from among the milled ones; becoming a Dinosaur Hero with base power and toughness (its creature
 * types replaced, CR 205.1b; layers 4, 6, 7b) by flavor-word abilities; a player gaining life and searching their own library; abilities
 * activated as though the creatures had haste (CR 302.6) and mana only for abilities of creature sources (CR 106.6); one trigger per
 * player dealt damage by a batch of Heroes; "enters or attacks"; all creature types (CR 205.3d); more counters on another Hero.
 */
class MshChunk16GreenMoreTest {
    // CR 701.10b: doubling is +X/+X; CR 701.14: a fight
    @Test
    fun legendarySlugfest() = card(
        name = "Legendary Slugfest",
        cost = "{2}{G}",
        type = "Sorcery",
        text = """
            Choose one or both —
            • Double target creature's power and toughness until end of turn.
            • Target creature you control fights target creature an opponent controls.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(power(target("creature")), toughness(target("creature")))),
                targets = listOf(slot("creature", creature))),
            mode(Step.Do(Effect.Fight(target("creature"), target("second creature"))),
                targets = listOf(slot("creature", creatureYouControl), slot("second creature", creatureOpponentControls))),
            max = 2))),
    )

    @Test
    fun swiftSalvage() = card(
        name = "Swift Salvage",
        cost = "{G}",
        type = "Instant",
        text = "Mill two cards. You may put a permanent card from among the milled cards into your hand. You gain 2 life. (To mill two cards, put the top two cards of your library into your graveyard.)",
    ) parsesTo abilities(
        spell(mill(2, postBind = "milled"),
            Step.Choose(Choice.Objects(permanentCard, n(0), n(1), from = Objects.Bound("milled")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand), gainLife(2)),
    )

    // CR 205.1b: "becomes a Dinosaur Hero" replaces its creature types; flavor words (CR 207.2d)
    @Test
    fun saurianDinomorpher() = card(
        name = "Saurian, Dinomorpher",
        cost = "{G}",
        type = "Legendary Creature — Human Hero",
        text = """
            Brontosaurus — {3}: Until end of turn, Saurian becomes a Dinosaur Hero with base power and toughness 3/5 and gains reach and vigilance.
            Tyrannosaurus Rex — {6}: Until end of turn, Saurian becomes a Dinosaur Hero with base power and toughness 6/6 and gains trample.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        Ability.Activated(manaCost("{3}"), Instructions(steps = listOf(becomes(3, 5, Keyword.Reach, Keyword.Vigilance))), anyTime, abilityWord = "Brontosaurus"),
        Ability.Activated(manaCost("{6}"), Instructions(steps = listOf(becomes(6, 6, Keyword.Trample))), anyTime, abilityWord = "Tyrannosaurus Rex"),
    )

    // CR 701.23: the target player searches their own library
    @Test
    fun mendingTechnique() = card(
        name = "Mending Technique",
        cost = "{2}{G}",
        type = "Sorcery",
        text = "Target player gains 2 life, then searches their library for a basic land card, puts it onto the battlefield tapped, then shuffles. Put a +1/+1 counter on up to one target creature.",
    ) parsesTo abilities(
        spell(
            Step.ForEach(arcanum.api.Each.Players(Players.Target("player")), "player", listOf(
                gainLife(2, Players.Bound("player")),
                Step.Do(Effect.Search(Players.Bound("player"), basicLand(), max = n(1), zones = listOf(Zone.Library), bind = "found", searcher = Players.Bound("player"))),
                move(Objects.Bound("found"), Zone.Battlefield, tapped = true),
                Step.Do(Effect.Shuffle(Players.Bound("player")))), together = true),
            counters(target("creature")),
            targets = listOf(targetPlayer, slot("creature", creature, min = 0))),
    )

    @Test
    fun roryJenningsDestinedSidekick() = card(
        name = "Rory Jennings, Destined Sidekick",
        cost = "{G}",
        type = "Legendary Creature — Human Advisor",
        text = "{3}, {T}: Mill four cards. You may put a Hero or enchantment card from among those cards into your hand. (To mill four cards, put the top four cards of your library into your graveyard.)",
        pt = "0/3",
    ) parsesTo abilities(
        activated(cost(mana("{3}"), tap), mill(4, postBind = "milled"),
            Step.Choose(Choice.Objects(or(hero, enchantment), n(0), n(1), from = Objects.Bound("milled")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand)),
    )

    // CR 302.6: activated as though they had haste; CR 106.6: mana only for abilities of creature sources
    @Test
    fun masterLinKungFuProdigy() = card(
        name = "Master Lin, Kung Fu Prodigy",
        cost = "{1}{G}",
        type = "Legendary Creature — Human Warrior Hero",
        text = """
            You may activate abilities of creatures you control as though those creatures had haste.
            {T}: Add two mana of any one color. Spend this mana only to activate abilities of creature sources.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        static(Mechanic.AbilitiesIgnoreSummoningSickness, affects = Objects.Each(creatureYouControl)),
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(2)), restriction = ObjectFilter.AbilityOf(kind = AbilityKind.Activated, source = creature)), Players.You))),
    )

    @Test
    fun mightyStrength() = card(
        name = "Mighty Strength",
        cost = "{4}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +4/+4 and has trample and ward {1}. (Whenever enchanted creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {1}.)
        """,
    ) parsesTo abilities(
        Keyword.Enchant(arcanum.api.TargetKind.Objects(creature)),
        static(pt(4, 4), has(Keyword.Trample, Keyword.Ward(manaCost("{1}"))), affects = Objects.AttachedTo(Objects.Self)),
    )

    // one trigger per player dealt damage by the batch
    @Test
    fun theBoulderBennettStone() = card(
        name = "The Boulder, Bennett Stone",
        cost = "{5}{G}",
        type = "Legendary Creature — Human Hero",
        text = """
            Trample
            Whenever one or more Heroes you control deal damage to a player, put two +1/+1 counters on The Boulder.
        """,
        pt = "7/7",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(arcanum.api.Trigger.On(listOf(EventPattern.Damage(and(hero, you), RecipientFilter(players = PlayerFilter.Any))), oneOrMore = true, batchPerPlayer = true),
            counters(Objects.Self, 2)),
    )

    @Test
    fun pantheressFelineFury() = card(
        name = "Pantheress, Feline Fury",
        cost = "{1}{G}",
        type = "Legendary Creature — Cat Human Hero",
        text = """
            Flash
            Trample
            Whenever you gain life, put a +1/+1 counter on Pantheress.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Trample,
        triggered(on(EventPattern.GainLife(PlayerFilter.You)), counters(Objects.Self)),
    )

    @Test
    fun trainingRoutine() = card(
        name = "Training Routine",
        cost = "{3}{G}",
        type = "Enchantment",
        text = """
            Creatures you control with +1/+1 counters on them have trample.
            At the beginning of combat on your turn, put a +1/+1 counter on target creature you control.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Trample), affects = Objects.Each(and(creatureYouControl, ObjectFilter.HasCounter(p1p1, atLeast = 1)))),
        triggered(beginningOfCombatOnYourTurn, counters(target("creature")), targets = listOf(slot("creature", creatureYouControl))),
    )

    // "enters or attacks": one ability with two events; X is the number of Squirrels as it resolves
    @Test
    fun theUnstoppableChipmunkGirl() = card(
        name = "The Unstoppable Chipmunk Girl",
        cost = "{1}{G}{G}{G}",
        type = "Legendary Creature — Squirrel Human Hero",
        text = """
            Do You Like Squirrels? — Whenever The Unstoppable Chipmunk Girl enters or attacks, create a 1/1 green Squirrel creature token.
            I LOVE Squirrels! — {1}{G}{G}{G}: Create X 1/1 green Squirrel creature tokens, where X is the number of Squirrels you control.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        triggered(entersOrAttacksSelf, create(squirrel), abilityWord = "Do You Like Squirrels?"),
        Ability.Activated(manaCost("{1}{G}{G}{G}"), Instructions(steps = listOf(create(squirrel, count(and(sub("Squirrel"), you))))), anyTime,
            abilityWord = "I LOVE Squirrels!"),
    )

    // CR 205.3d: all creature types while the condition holds
    @Test
    fun undercoverShapeshifter() = card(
        name = "Undercover Shapeshifter",
        cost = "{1}{G}",
        type = "Creature — Skrull Shapeshifter Villain",
        text = """
            As long as there are two or more creature cards in your graveyard, this creature gets +2/+2 and is all creature types.
            {T}: Add one mana of any color.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        static(pt(2, 2), Mechanic.AllCreatureTypes, condition = Condition.Exists(inYourGraveyard(creature), atLeast = 2)),
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
    )

    // two counters instead if the target is another Hero as it resolves
    @Test
    fun kingdomRoyalGuard() = card(
        name = "Kingdom Royal Guard",
        cost = "{4}{G}",
        type = "Creature — Human Soldier Hero",
        text = """
            Vigilance
            When this creature enters, put a +1/+1 counter on target creature. If that creature is another Hero, put two +1/+1 counters on it instead.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(entersSelf, Step.If(Condition.ObjectMatches(target("creature"), and(another, hero)), then = listOf(counters(target("creature"), 2)),
            otherwise = listOf(counters(target("creature")))), targets = listOf(slot("creature", creature))),
    )

    private val squirrel = creatureToken("Squirrel", listOf(Color.Green), 1, 1)
    private fun becomes(p: Int, t: Int, vararg keywords: Ability) = untilEndOfTurn(Objects.Self,
        Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Dinosaur")), SubtypeRef.Literal(Subtype("Hero"))), replaceSubtypes = true),
        Mechanic.SetPowerToughness(n(p), n(t)), has(*keywords))
}
