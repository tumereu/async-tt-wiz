package arcanum.parser.sets.mh3

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * MH3 chunk 15: alternative costs that sacrifice a creature (CR 118.9), spells that cost less (CR 601.2f), counterspells
 * (CR 701.6), exploit (CR 702.110), modular (CR 702.43) and a modal double-faced card (CR 712).
 */
class Mh3Chunk15AlternativeCostsCostChangeCounterTest {
    private val instantOrSorcery = or(instant, sorcery)
    private val targetSpell = Objects.Target("spell")
    private fun spellSlot(filter: ObjectFilter) = TargetSlot("spell", TargetKind.Objects(filter), min = n(1), max = n(1))
    private val counterIt = Step.Do(Effect.Counter(targetSpell))

    /** "You may sacrifice a nontoken [color] creature rather than pay this spell's mana cost." (CR 118.9: the sacrifice is a cost, so only a creature you control can pay it) */
    private fun sacrificeAlternative(color: Color): Ability = static(Mechanic.AlternativeCost(cost = cost(sacrificeA(and(not(token), ObjectFilter.OfColor(color), creature)))))

    // CR 118.9, 701.6a
    @Test
    fun flareOfDenial() = card(
        name = "Flare of Dismissal",
        cost = "{1}{U}{U}",
        type = "Instant",
        text = """
            You may sacrifice a nontoken blue creature rather than pay this spell's mana cost.
            Counter target spell.
        """,
    ) parsesTo abilities(
        sacrificeAlternative(Color.Blue),
        spell(counterIt, targets = listOf(spellSlot(spellFilter))),
    )

    // CR 118.9, 707.10, 707.10c: copy target instant or sorcery spell, new targets may be chosen
    @Test
    fun flareOfDuplication() = card(
        name = "Flare of Repetition",
        cost = "{1}{R}{R}",
        type = "Instant",
        text = """
            You may sacrifice a nontoken red creature rather than pay this spell's mana cost.
            Copy target instant or sorcery spell. You may choose new targets for the copy.
        """,
    ) parsesTo abilities(
        sacrificeAlternative(Color.Red),
        spell(
            Step.Do(Effect.CopySpell(targetSpell, controller = Players.You, newTargets = true)),
            targets = listOf(spellSlot(and(instantOrSorcery, spellFilter))),
        ),
    )

    // CR 118.9, 701.21a, 608.2d: each opponent chooses among their own creatures and planeswalkers of the greatest mana value.
    // Audit (unsure) on the sacrifice cost lacking controlled-by(you): a false alarm; the same model as every other
    // "sacrifice a creature" cost, which the cost's payer can only take from their own permanents (CR 118.3)
    @Test
    fun flareOfMalice() {
        val creatureOrPlaneswalker = or(creature, ObjectFilter.Type(CardType.Planeswalker))
        val theirs = ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent")))
        card(
            name = "Flare of Decay",
            cost = "{2}{B}{B}",
            type = "Instant",
            text = """
                You may sacrifice a nontoken black creature rather than pay this spell's mana cost.
                Each opponent sacrifices a creature or planeswalker with the greatest mana value among creatures and planeswalkers they control.
            """,
        ) parsesTo abilities(
            sacrificeAlternative(Color.Black),
            spell(Step.ForEach(
                each = Each.Players(Players.Each(PlayerFilter.Opponent)),
                bind = "opponent",
                together = true,
                steps = listOf(
                    Step.Choose(
                        choice = Choice.Objects(
                            filter = and(creatureOrPlaneswalker, theirs, ObjectFilter.ManaValue(
                                Comparison.Equal,
                                Value.Of(NumericProperty.ManaValue, Objects.Each(and(creatureOrPlaneswalker, theirs)), Combine.Greatest),
                            )),
                            min = n(1),
                            max = n(1),
                        ),
                        bind = "sacrificed",
                        chooser = Players.Bound("opponent"),
                    ),
                    Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))),
                ),
            )),
        )
    }

    // CR 702.43a (modular), 603.2: another artifact entering under your control
    @Test
    fun arcboundCondor() = card(
        name = "Arcbound Hawk",
        cost = "{2}{B}{B}",
        type = "Artifact Creature — Bird",
        pt = "0/0",
        text = """
            Flying
            Modular 3 (This creature enters with three +1/+1 counters on it. When it dies, you may put its +1/+1 counters on target artifact creature.)
            Whenever another artifact you control enters, target creature an opponent controls gets -1/-1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Modular(3),
        triggered(enters(and(another, artifact, you)),
            pump(target("creature"), -1, -1),
            targets = listOf(slot("creature", creatureOpponentControls))),
    )

    // CR 712.2 (modal double-faced), 701.14a (fight), 614.1c: pay 3 life as it enters or it enters tapped.
    // Audit (unsure) on the optional life payment: a false alarm, `Step.Pay` is optional by default (mandatory = false)
    // and "If you don't" reads as Not(Did)
    @Test
    fun bridgeworksBattle() = cardFaces(Layout.ModalDoubleFaced,
        card(
            name = "Bridgeyard Brawl",
            cost = "{2}{G}",
            type = "Sorcery",
            text = "Target creature you control gets +2/+2 until end of turn. It fights up to one target creature you don't control. (Each deals damage equal to its power to the other.)",
        ) to abilities(
            spell(
                pump(target("creature"), 2, 2),
                doing(Effect.Fight(target("creature"), target("second creature"))),
                targets = listOf(slot("creature", creatureYouControl), slot("second creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))), min = 0, max = 1)),
            ),
        ),
        card(
            name = "Tanglespan Crossing",
            cost = null,
            type = "Land",
            text = """
                As this land enters, you may pay 3 life. If you don't, it enters tapped.
                {T}: Add {G}.
            """,
        ) to abilities(
            static(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.AsEnters(listOf(
                    Step.Pay(cost(CostPart.PayLife(n(3))), payer = Players.You, mandatory = false, bind = "paid"),
                    Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Modify(Modification.EntersTapped))),
                ))),
            )),
            addOne("{G}"),
        ),
    )

    // CR 702.110a (exploit), 701.17c, 613.1b, 702.10a: "exploits a creature" triggers only when a creature was sacrificed
    @Test
    fun infernalCaptor() = card(
        name = "Infernal Taker",
        cost = "{3}{R}",
        type = "Creature — Devil Rogue",
        pt = "3/3",
        text = """
            Exploit (When this creature enters, you may sacrifice a creature.)
            When this creature exploits a creature, gain control of target artifact or creature until end of turn. Untap that permanent. It gains haste until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Exploit,
        triggered(on(EventPattern.Exploits(ObjectFilter.Is(Objects.Self))),
            untilEndOfTurn(target("permanent"), Mechanic.ControlledBy(Players.You)),
            untapIt(target("permanent")),
            gains(target("permanent"), Keyword.Haste),
            targets = listOf(slot("permanent", or(artifact, creature)))),
    )

    // CR 602.5d (sorcery timing), 118.12: a graveyard ability that exiles the card as a cost
    @Test
    fun witherAndBloom() = card(
        name = "Wither and Thrive",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            Target creature gets -3/-3 until end of turn.
            {1}{B}, Exile this card from your graveyard: Put a +1/+1 counter on target creature you control. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        spell(pump(target("creature"), -3, -3), targets = listOf(slot("creature", creature))),
        activated(cost(mana("{1}{B}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)),
            counters(target("creature")),
            targets = listOf(slot("creature", creatureYouControl)),
            rules = asSorcery),
    )

    private fun medallion(name: String, color: Color, word: String) = card(
        name = name,
        cost = "{2}",
        type = "Artifact",
        text = "$word spells you cast cost {1} less to cast.",
    ) parsesTo abilities(
        static(Mechanic.CostChange(and(ObjectFilter.OfColor(ColorRef.Literal(color)), spellFilter, you), CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
    )

    // CR 601.2f, 118.7: "{1} less" reduces generic mana only, for spells of that color the controller casts
    @Test fun emeraldMedallion() = medallion("Verdant Medallion", Color.Green, "Green")
    @Test fun jetMedallion() = medallion("Onyx Medallion", Color.Black, "Black")
    @Test fun pearlMedallion() = medallion("Ivory Medallion", Color.White, "White")
    @Test fun rubyMedallion() = medallion("Garnet Medallion", Color.Red, "Red")
    @Test fun sapphireMedallion() = medallion("Azure Medallion", Color.Blue, "Blue")

    // CR 700.2 (modal), 105.4 (multicolored), 701.6a, 701.8a
    @Test
    fun nullElementalBlast() = card(
        name = "Null Blast Charm",
        cost = "{C}",
        type = "Instant",
        text = """
            Choose one —
            • Counter target multicolored spell.
            • Destroy target multicolored permanent.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(counterIt, targets = listOf(spellSlot(and(ObjectFilter.Multicolored, spellFilter)))),
            mode(destroy(target("permanent")), targets = listOf(slot("permanent", and(ObjectFilter.Multicolored, permanentFilter)))),
        ))),
    )

    // CR 701.6a, 111.2: the countered spell's controller creates the token
    @Test
    fun strixSerenade() = card(
        name = "Strix Lullaby",
        cost = "{U}",
        type = "Instant",
        text = "Counter target artifact, creature, or planeswalker spell. Its controller creates a 2/2 blue Bird creature token with flying.",
    ) parsesTo abilities(
        spell(
            counterIt,
            Step.Do(Effect.CreateToken(
                creatureToken("Bird", listOf(Color.Blue), 2, 2, abilities = listOf(Keyword.Flying)), n(1),
                Players.ControllerOf(targetSpell),
            )),
            targets = listOf(spellSlot(and(or(artifact, creature, ObjectFilter.Type(CardType.Planeswalker)), spellFilter))),
        ),
    )

    // CR 603.4 (intervening if), 601.2h, 701.6a: "no mana was spent" is checked as it triggers and as it resolves
    @Test
    fun vexingBauble() = card(
        name = "Vexing Trinket",
        cost = "{1}",
        type = "Artifact",
        text = """
            Whenever a player casts a spell, if no mana was spent to cast it, counter that spell.
            {1}, {T}, Sacrifice this artifact: Draw a card.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Any)),
            doing(Effect.Counter(Objects.EventObject(EventRole.Object))),
            interveningIf = Condition.Compare(Value.ManaSpent(of = Objects.EventObject(EventRole.Object)), Comparison.Equal, n(0))),
        activated(cost(mana("{1}"), tap, sacrificeSelf), draw(1)),
    )
}
