package arcanum.parser.sets.rtr

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.rtr.Rtr.activated
import arcanum.parser.sets.rtr.Rtr.all
import arcanum.parser.sets.rtr.Rtr.anyTarget
import arcanum.parser.sets.rtr.Rtr.cost
import arcanum.parser.sets.rtr.Rtr.counters
import arcanum.parser.sets.rtr.Rtr.creature
import arcanum.parser.sets.rtr.Rtr.damage
import arcanum.parser.sets.rtr.Rtr.draw
import arcanum.parser.sets.rtr.Rtr.instant
import arcanum.parser.sets.rtr.Rtr.manaPart
import arcanum.parser.sets.rtr.Rtr.n
import arcanum.parser.sets.rtr.Rtr.nonland
import arcanum.parser.sets.rtr.Rtr.permanent
import arcanum.parser.sets.rtr.Rtr.pump
import arcanum.parser.sets.rtr.Rtr.slot
import arcanum.parser.sets.rtr.Rtr.sorcery
import arcanum.parser.sets.rtr.Rtr.static
import arcanum.parser.sets.rtr.Rtr.target
import arcanum.parser.sets.rtr.Rtr.triggered
import arcanum.parser.sets.rtr.Rtr.you
import arcanum.parser.sets.rtr.Rtr.youCast
import kotlin.test.Test

/**
 * Return to Ravnica chunk 11: the Izzet creatures and a Golgari planeswalker. "Untap it" after a pump of this creature,
 * cost reductions for instants and sorceries, sorceries as though they had flash, damage to a target and every other
 * creature with its name, damage by a discarded card's mana value (CR 608.2h), copying a spell (CR 707.10), exiling a spell
 * from the Stack as a cost (CR 405.1), a delayed trigger on combat damage to the planeswalker itself, tokens that make a
 * player lose the game.
 */
class RtrChunk11IzzetAndGuildCreaturesTest {
    private val instantOrSorcery = ObjectFilter.Or(listOf(instant, sorcery))

    /** "Untap it": this creature, the object of the sentence before (not the spell cast). */
    @Test
    fun sparkcoilOddity() = card(
        name = "Sparkcoil Oddity",
        cost = "{U/R}",
        type = "Creature — Weird",
        pt = "1/1",
        text = "Whenever you cast an instant or sorcery spell, this creature gets +1/+1 until end of turn. Untap it.",
    ) parsesTo abilities(
        triggered(youCast(instantOrSorcery), pump(Objects.Self, 1, 1), Step.Do(Effect.Untap(Objects.Self))),
    )

    @Test
    fun rimeflareOddity() = card(
        name = "Rimeflare Oddity",
        cost = "{U/R}{U/R}",
        type = "Creature — Weird",
        pt = "1/4",
        text = "{U/R}: This creature gets +1/-1 until end of turn.",
    ) parsesTo abilities(
        activated(cost(manaPart("{U/R}")), pump(Objects.Self, 1, -1)),
    )

    /** CR 601.2f: generic mana only (CR 118.7a). */
    @Test
    fun goblinVoltsmith() = card(
        name = "Goblin Voltsmith",
        cost = "{U}{R}",
        type = "Creature — Goblin Wizard",
        pt = "2/2",
        text = "Instant and sorcery spells you cast cost {1} less to cast.",
    ) parsesTo abilities(
        static(Objects.Self, Mechanic.CostChange(all(instantOrSorcery, Rtr.spell, you), CostChange.Less(Rtr.mana("{1}"), n(1)))),
    )

    /** A permission of the player (CR 702.8a: as though they had flash). */
    @Test
    fun thunderclapDragon() = card(
        name = "Thunderclap Dragon",
        cost = "{3}{U}{R}",
        type = "Creature — Dragon",
        pt = "4/4",
        text = """
            Flying, haste
            You may cast sorcery spells as though they had flash. (You may cast them any time you could cast an instant.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Haste,
        Ability.Static(listOf(Mechanic.CastAsThoughFlash(all(sorcery, Rtr.spell))), affects = Affected.Players(Players.You)),
    )

    /** One damage event to the target and each other creature sharing its name (CR 201.2, 120.3). */
    @Test
    fun staticSparkMage() = card(
        name = "Static Spark-Mage",
        cost = "{1}{U}{R}",
        type = "Creature — Human Wizard",
        pt = "0/3",
        text = """
            Flash (You may cast this spell any time you could cast an instant.)
            Haste
            {T}: This creature deals 1 damage to target creature and each other creature with the same name as that creature.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Haste,
        activated(cost(CostPart.TapSelf), damage(Recipients(objects = Objects.Union(listOf(target("creature"), Objects.Each(all(
            ObjectFilter.Not(ObjectFilter.Is(target("creature"))), creature, ObjectFilter.SameNameAs(target("creature"))))))), 1),
            targets = listOf(slot("creature", creature))),
    )

    /** "The discarded card's mana value": the card as it was discarded (CR 608.2h). */
    @Test
    fun volatileAlchemist() = card(
        name = "Volatile Alchemist",
        cost = "{3}{U}{R}",
        type = "Creature — Human Wizard",
        pt = "3/3",
        text = """
            {U}, {T}: Draw two cards.
            {R}, {T}, Discard a card: This creature deals damage to target creature equal to the discarded card's mana value.
        """,
    ) parsesTo abilities(
        activated(cost(manaPart("{U}"), CostPart.TapSelf), draw(2)),
        activated(cost(manaPart("{R}"), CostPart.TapSelf, CostPart.Discard(CostObjects.Choose(Rtr.card, count = n(1)), preBind = "discarded")),
            damage(Recipients(objects = target("creature")), Value.Of(NumericProperty.ManaValue, Objects.Bound("discarded"), Combine.Single)),
            targets = listOf(slot("creature", creature))),
    )

    /** Damage of any kind to a player (CR 120): its own name refers to itself (CR 201.5). */
    @Test
    fun vazrekDragonSavant() = card(
        name = "Vazrek, Dragon Savant",
        cost = "{2}{U}{U}{R}{R}",
        type = "Legendary Creature — Dragon Wizard",
        pt = "5/5",
        text = """
            Flying
            Whenever Vazrek deals damage to a player, you may draw a card.
            {U}{R}: Vazrek deals 1 damage to any target.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(Trigger.On(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(players = PlayerFilter.Any))),
            Step.May(listOf(draw(1)), player = Players.You)),
        activated(cost(manaPart("{U}{R}")), damage(Recipients(target = "any"), 1), targets = listOf(anyTarget)),
    )

    /** A copy of a spell (CR 707.10) with new targets if you like (CR 707.10c). */
    @Test
    fun laboratoryGuildmage() = card(
        name = "Laboratory Guildmage",
        cost = "{U}{R}",
        type = "Creature — Human Wizard",
        pt = "2/2",
        text = """
            {1}{U}{R}: Draw a card, then discard a card.
            {2}{U}{R}: Copy target instant or sorcery spell you control. You may choose new targets for the copy.
        """,
    ) parsesTo abilities(
        activated(cost(manaPart("{1}{U}{R}")), draw(1), Step.Do(Effect.Discard(Players.You, n(1), ObjectFilter.Any))),
        activated(cost(manaPart("{2}{U}{R}")), Step.Do(Effect.CopySpell(target("spell"), controller = Players.You, newTargets = true)),
            targets = listOf(slot("spell", all(instantOrSorcery, Rtr.spell, you)))),
    )

    /** A spell of yours on the Stack exiled as the cost: it won't resolve (CR 405.1, 118.3). */
    @Test
    fun spellgorgerElemental() = card(
        name = "Spellgorger Elemental",
        cost = "{U/R}",
        type = "Creature — Elemental",
        pt = "1/2",
        text = "Exile an instant or sorcery spell you control: Put two +1/+1 counters on this creature. (That spell won't resolve.)",
    ) parsesTo abilities(
        activated(cost(CostPart.Exile(CostObjects.Choose(all(instantOrSorcery, Rtr.spell, you), count = n(1)), from = Zone.Stack)), counters(Objects.Self, 2)),
    )

    /**
     * +1: until your next turn, each creature that deals combat damage to it is destroyed (a repeating delayed trigger, CR
     * 603.7b; "that creature" is the source); −7: tokens with a triggered ability (CR 111.4).
     */
    @Test
    fun ilsevraTheVeiled() = card(
        name = "Ilsevra the Veiled",
        cost = "{3}{B}{G}",
        type = "Legendary Planeswalker — Vraska",
        loyalty = "5",
        text = """
            [+1]: Until your next turn, whenever a creature deals combat damage to Ilsevra, destroy that creature.
            [−3]: Destroy target nonland permanent.
            [−7]: Create three 1/1 black Assassin creature tokens with "Whenever this token deals combat damage to a player, that player loses the game."
        """,
    ) parsesTo abilities(
        activated(cost(CostPart.Loyalty(n(1))), Step.Do(Effect.CreateDelayedTrigger(
            Trigger.On(EventPattern.Damage(creature, RecipientFilter(objects = ObjectFilter.Is(Objects.Self)), combat = true)),
            Instructions(steps = listOf(Rtr.destroy(Objects.EventObject(EventRole.Source)))),
            duration = Duration.UntilYourNextTurn, repeats = true))),
        activated(cost(CostPart.Loyalty(n(-3))), Rtr.destroy(target("permanent")), targets = listOf(slot("permanent", all(nonland, permanent)))),
        activated(cost(CostPart.Loyalty(n(-7))), Step.Do(Effect.CreateToken(TokenSpec.Defined(FaceDefinition(
            name = "Assassin Token", colorIndicator = listOf(Color.Black), types = listOf(arcanum.api.CardType.Creature), subtypes = listOf(Subtype("Assassin")),
            power = PrintedNumber.Fixed(1), toughness = PrintedNumber.Fixed(1),
            abilities = listOf(triggered(Rtr.combatDamageToAPlayer(), Step.Do(Effect.LoseGame(Players.EventPlayer)))),
        )), count = n(3), controller = Players.You))),
    )
}
