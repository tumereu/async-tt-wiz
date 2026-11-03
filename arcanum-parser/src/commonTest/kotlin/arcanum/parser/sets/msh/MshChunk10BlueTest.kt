package arcanum.parser.sets.msh

import arcanum.api.Ability
import arcanum.api.AbilityKind
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostPart
import arcanum.api.CostObjects
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.TokenSpec
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel Super Heroes chunk 10: blue cards. The second card drawn each turn (CR 121.2); one trigger per player attacked by a batch
 * of Merfolk; a spell costing less by its target (CR 601.2f); a copy of an activated or triggered ability (CR 707.10); a named
 * legendary token with a quoted attack trigger (CR 111.4); an Aura that removes all abilities and stops untapping (layer 6); mana
 * that can't be spent on nonartifact spells (CR 106.6); an artifact or creature becoming a 4/4 artifact creature (layers 4, 7b);
 * spells given improvise (CR 702.126); nonland permanents returned to their owners' hands.
 */
class MshChunk10BlueTest {
    @Test
    fun tidalLancers() = card(
        name = "Tidal Lancers",
        cost = "{2}{U}",
        type = "Creature — Merfolk Soldier",
        text = """
            Vigilance
            Whenever you draw your second card each turn, put a +1/+1 counter on this creature.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(secondCardEachTurn, counters(Objects.Self)),
    )

    // one trigger per player attacked (CR 508.3d by wording)
    @Test
    fun krakosTidalWarlord() = card(
        name = "Krakos, Tidal Warlord",
        cost = "{2}{U}{U}",
        type = "Legendary Creature — Merfolk Warrior Villain",
        text = """
            Other Merfolk you control get +1/+1.
            Whenever one or more Merfolk you control attack a player, draw a card.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        static(pt(1, 1), affects = Objects.Each(and(another, sub("Merfolk"), you))),
        triggered(arcanum.api.Trigger.On(listOf(EventPattern.Attack(and(sub("Merfolk"), you), target = RecipientFilter(players = PlayerFilter.Any))),
            oneOrMore = true, batchPerPlayer = true), draw(1)),
    )

    // CR 601.2f: the cost is worked out after the target is chosen
    @Test
    fun sapTheSurge() = card(
        name = "Sap the Surge",
        cost = "{2}{U}",
        type = "Instant",
        text = """
            This spell costs {2} less to cast if it targets an attacking creature.
            Target creature gets -4/-0 until end of turn.
            Draw a card.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{2}")!!, n(1)))),
            affects = Affected.Objects(Objects.Self), condition = Condition.ObjectMatches(target("creature"), attacking)),
        spell(pump(target("creature"), -4, 0), draw(1), targets = listOf(slot("creature", creature))),
    )

    // CR 707.10: a copy of an ability from a creature source (mana abilities can't be targeted, CR 605.3b)
    @Test
    fun mirrorPerceptiveProdigy() = card(
        name = "Mirror, Perceptive Prodigy",
        cost = "{2}{U}",
        type = "Legendary Creature — Human Hero",
        text = """
            Vigilance
            {1}, {T}: Copy target activated or triggered ability you control from a creature source. You may choose new targets for the copy. (Mana abilities can't be targeted.)
        """,
        pt = "1/4",
    ) parsesTo abilities(
        Keyword.Vigilance,
        activated(cost(mana("{1}"), tap), Step.Do(Effect.CopySpell(target("ability"), Players.You, newTargets = true)),
            targets = listOf(slot("ability", and(or(ObjectFilter.AbilityOf(kind = AbilityKind.Activated, source = creature),
                ObjectFilter.AbilityOf(kind = AbilityKind.Triggered, source = creature)), you)))),
    )

    // CR 111.4: the token's own name in its quoted ability; a flavor word (CR 207.2d)
    @Test
    fun kestrelWingedWonder() = card(
        name = "Kestrel, Winged Wonder",
        cost = "{4}{U}",
        type = "Legendary Creature — Human Hero",
        text = """
            Flying
            Avian Telepathy — When Kestrel enters, create Scarlet Wing, a legendary 1/1 blue Bird Scout creature token with flying and "Whenever Scarlet Wing attacks, surveil 1." (Look at the top card of your library. You may put it into your graveyard.)
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, create(TokenSpec.Defined(FaceDefinition("Scarlet Wing", colorIndicator = listOf(Color.Blue), supertypes = listOf(Supertype.Legendary),
            types = listOf(CardType.Creature), subtypes = listOf(Subtype("Bird"), Subtype("Scout")), power = PrintedNumber.Fixed(1), toughness = PrintedNumber.Fixed(1),
            abilities = listOf(Keyword.Flying, triggered(attacksSelf, Step.Do(Effect.Surveil(Players.You, n(1)))))))),
            abilityWord = "Avian Telepathy"),
    )

    @Test
    fun kestrelsWingHarness() = card(
        name = "Kestrel's Wing Harness",
        cost = "{1}{U}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, attach it to target creature you control.
            Equipped creature gets +1/+1 and has flying and ward {1}. (Whenever equipped creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {1}.)
            Equip {2}{U} ({2}{U}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, Step.Do(Effect.Attach(Objects.Self, Recipients(objects = target("creature")))), targets = listOf(slot("creature", creatureYouControl))),
        static(pt(1, 1), has(Keyword.Flying, Keyword.Ward(manaCost("{1}"))), affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(manaCost("{2}{U}")),
    )

    // layer 6: no abilities; it can't become untapped, by its untap step or an effect
    @Test
    fun encasedInFrost() = card(
        name = "Encased in Frost",
        cost = "{2}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, tap enchanted creature.
            Enchanted creature loses all abilities and can't become untapped.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(arcanum.api.TargetKind.Objects(creature)),
        triggered(entersSelf, tapIt(Objects.AttachedTo(Objects.Self))),
        static(Mechanic.LoseAbilities(), Mechanic.CantBecomeUntapped, affects = Objects.AttachedTo(Objects.Self)),
    )

    @Test
    fun futuristFoundry() = card(
        name = "Futurist Foundry",
        cost = "{1}{U}",
        type = "Artifact",
        text = """
            When this artifact enters, draw a card.
            {3}{U}, Sacrifice this artifact: Draw two cards.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, draw(1)),
        activated(cost(mana("{3}{U}"), sacrificeSelf), draw(2)),
    )

    @Test
    fun giantSizedFlyingBeetle() = card(
        name = "Giant-Sized Flying Beetle",
        cost = "{3}{U}",
        type = "Creature — Insect",
        text = """
            Flash
            Flying
            When this creature enters, choose one —
            • Tap target nonland permanent.
            • Untap target nonland permanent.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(tapIt(target("permanent")), targets = listOf(slot("permanent", and(nonland, permanentFilter)))),
            mode(untapIt(target("permanent")), targets = listOf(slot("permanent", and(nonland, permanentFilter))))))),
    )

    // CR 106.6: the mana can be spent on anything but nonartifact spells
    @Test
    fun hydraulicAssistant() = card(
        name = "Hydraulic Assistant",
        cost = "{1}{U}",
        type = "Artifact Creature — Robot",
        text = """
            Defender
            {T}: Add {U}. This mana can't be spent to cast a nonartifact spell.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Defender,
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{U}")!!),
            restriction = not(and(spellFilter, not(artifact)))), Players.You))),
    )

    // layers 4 and 7b: an artifact creature with base power and toughness 4/4
    @Test
    fun iAmTheArmor() = card(
        name = "I Am the Armor",
        cost = "{2}{U}",
        type = "Instant",
        text = """
            Until end of turn, target artifact or creature becomes an artifact creature with base power and toughness 4/4 and gains flying.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(untilEndOfTurn(target("permanent"), Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature)),
            Mechanic.SetPowerToughness(n(4), n(4)), has(Keyword.Flying)), draw(1),
            targets = listOf(slot("permanent", or(artifact, creature)))),
    )

    @Test
    fun steelLadDivergingDestiny() = card(
        name = "Steel Lad, Diverging Destiny",
        cost = "{2}{U}",
        type = "Legendary Artifact Creature — Human Hero",
        text = """
            Flying, vigilance
            You may look at the top card of your library any time.
            {T}: Reveal the top card of your library. If it's an artifact card, draw a card.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        playerStatic(Mechanic.MayLookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true))),
        activated(cost(tap), Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, n(1), fromTop = true), bind = "revealed")),
            Step.If(Condition.ObjectMatches(Objects.Bound("revealed"), artifact), then = listOf(draw(1)))),
    )

    // CR 702.126: improvise, and given to your noncreature spells
    @Test
    fun forgeheartCleverChampion() = card(
        name = "Forgeheart, Clever Champion",
        cost = "{4}{U}",
        type = "Legendary Artifact Creature — Human Hero",
        text = """
            Improvise (Your artifacts can help cast this spell. Each artifact you tap after you're done activating mana abilities pays for {1}.)
            Flying
            Noncreature spells you cast have improvise.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Keyword.Improvise,
        Keyword.Flying,
        static(has(Keyword.Improvise), affects = Objects.Each(and(not(creature), spellFilter, you))),
    )

    @Test
    fun verdictTelekineticKnight() = card(
        name = "Verdict, Telekinetic Knight",
        cost = "{2}{U}",
        type = "Legendary Creature — Mutant Hero",
        text = """
            Flying
            When Verdict enters, return up to one target nonland, nontoken permanent to its owner's hand.
            Whenever another nonland permanent you control is returned to its owner's hand, put a +1/+1 counter on Verdict.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, move(target("permanent"), Zone.Hand), targets = listOf(slot("permanent", and(nonland, not(token), permanentFilter), min = 0))),
        triggered(on(EventPattern.ZoneChange(and(another, nonland, permanentFilter, you), from = Zone.Battlefield, to = Zone.Hand)), counters(Objects.Self)),
    )
}
