package arcanum.parser.sets.dmr

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/** DMR chunk 11: adding mana, additional and alternative costs, attack costs, "can't attack alone", counters and tokens. */
class DmrChunk11ManaCostsCountersTokensTest {
    private fun addManaStep(symbols: String): Step =
        Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse(symbols)!!)), Players.You))

    private val inZoneYouOwn = ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))
    private val counterTargetSpell = Effect.Counter(Objects.Target("spell"))

    // CR 603.4, 106.1: the intervening "if" asks how it was cast
    @Test
    fun ashStoker() = card(
        name = "Ash Stoker", cost = "{3}{R}", type = "Creature — Elemental", pt = "3/3",
        text = "When this creature enters, if you cast it from your hand, add {R}{R}{R}.",
    ) parsesTo abilities(
        triggered(entersSelf, addManaStep("{R}{R}{R}"), interveningIf = Condition.CastFrom(Zone.Hand, Objects.Self)),
    )

    // CR 602.2, 106.3: a mana ability that works from the hand; exiling this card is its cost
    @Test
    fun elvishSoulGuide() = card(
        name = "Elvish Soul Guide", cost = "{2}{G}", type = "Creature — Elf Spirit", pt = "2/2",
        text = "Exile this card from your hand: Add {G}.",
    ) parsesTo abilities(
        activated(cost(CostPart.Exile(CostObjects.Self, from = Zone.Hand)), addManaStep("{G}")),
    )

    // CR 605.1a, 602.2: the mana ability and the sacrifice ability are separate abilities, both needing {T} or this artifact
    @Test
    fun thoughtStone() = card(
        name = "Thought Stone", cost = "{2}", type = "Artifact",
        text = """
            {T}: Add {C}.
            {1}, {T}, Sacrifice this artifact: Draw a card.
        """,
    ) parsesTo abilities(
        addMana("{C}"),
        activated(cost(mana("{1}"), tap, sacrificeSelf), draw(1)),
    )

    // CR 205.3m, 613.1d: becomes an artifact creature, still a land (CR 205.1b: its creature types replaced); the pump is for Assembly-Workers only
    @Test
    fun tolvansFoundry() = card(
        name = "Tolvan's Foundry", cost = null, type = "Land",
        text = """
            {T}: Add {C}.
            {1}: This land becomes a 2/2 Assembly-Worker artifact creature until end of turn. It's still a land.
            {T}: Target Assembly-Worker creature gets +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        addMana("{C}"),
        activated(cost(mana("{1}")), untilEndOfTurn(Objects.Self, Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature),
            addSubtypes = listOf(SubtypeRef.Literal(Subtype("Assembly-Worker"))), replaceSubtypes = true), Mechanic.SetPowerToughness(n(2), n(2)))),
        activated(cost(tap), pump(target("creature"), 1, 1), targets = listOf(slot("creature", and(sub("Assembly-Worker"), creature)))),
    )

    // CR 508.1, 603.2: an optional mana effect when attacking
    @Test
    fun radhiHeirToKelt() = card(
        name = "Radhi, Heir to Kelt", cost = "{R}{G}", type = "Legendary Creature — Elf Warrior", pt = "2/2",
        text = """
            Whenever Radhi attacks, you may add {R}{R}.
            {T}: Add {G}.
        """,
    ) parsesTo abilities(
        triggered(attacksSelf, Step.May(listOf(addManaStep("{R}{R}")), Players.You)),
        addMana("{G}"),
    )

    // CR 118.3: the sacrificed Goblin may be this creature itself
    @Test
    fun skirtProspector() = card(
        name = "Skirt Prospector", cost = "{R}", type = "Creature — Goblin", pt = "1/1",
        text = "Sacrifice a Goblin: Add {R}.",
    ) parsesTo abilities(
        activated(cost(sacrificeA(sub("Goblin"))), addManaStep("{R}")),
    )

    // CR 701.23a, 602.2: sacrificing the land is part of the cost
    @Test
    fun finalMoraine() = card(
        name = "Final Moraine", cost = null, type = "Land",
        text = """
            {T}: Add {C}.
            {2}, {T}, Sacrifice this land: Search your library for a basic land card, put that card onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(
        addMana("{C}"),
        activated(cost(mana("{2}"), tap, sacrificeSelf), search(basicLand()),
            move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
    )

    // CR 118.8, 701.23a: an additional cost of sacrificing a land, then an untapped-free tutor
    @Test
    fun fieldRotation() = card(
        name = "Field Rotation", cost = "{G}", type = "Instant",
        text = """
            As an additional cost to cast this spell, sacrifice a land.
            Search your library for a land card, put that card onto the battlefield, then shuffle.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.AdditionalCost(cost = cost(sacrificeA(land)))), affects = Affected.Objects(Objects.Self)),
        spell(search(land), move(Objects.Bound("found"), Zone.Battlefield), shuffle),
    )

    // CR 601.2b, 118.8: the optional {2} more also lets it be cast at instant speed
    @Test
    fun saplingSymbiosis() = card(
        name = "Sapling Symbiosis", cost = "{3}{G}", type = "Sorcery",
        text = """
            You may cast this spell as though it had flash if you pay {2} more to cast it. (You may cast it any time you could cast an instant.)
            Create a 1/1 green Saproling creature token for each creature you control.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(mana("{2}")), optional = true, flash = true)),
        spell(create(creatureToken("Saproling", listOf(Color.Green), 1, 1), count(creatureYouControl))),
    )

    // CR 118.9: sacrificing two Mountains in place of the mana cost (the audit's span complaint concerns only the cost span, the model is complete)
    @Test
    fun firegust() = card(
        name = "Firegust", cost = "{4}{R}{R}", type = "Instant",
        text = """
            You may sacrifice two Mountains rather than pay this spell's mana cost.
            Firegust deals 4 damage to any target.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.AlternativeCost(cost = cost(CostPart.Sacrifice(CostObjects.Choose(sub("Mountain"), count = n(2)))))),
            affects = Affected.Objects(Objects.Self)),
        spell(damage(4, toAny), targets = listOf(anyTarget)),
    )

    // CR 118.9, 118.3: pay life and exile a blue card from hand instead of the mana cost
    @Test
    fun forceOfWit() = card(
        name = "Force of Wit", cost = "{3}{U}{U}", type = "Instant",
        text = """
            You may pay 1 life and exile a blue card from your hand rather than pay this spell's mana cost.
            Counter target spell.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.AlternativeCost(cost = cost(CostPart.PayLife(n(1)),
            CostPart.Exile(CostObjects.Choose(ObjectFilter.OfColor(Color.Blue), count = n(1)), from = Zone.Hand)))),
            affects = Affected.Objects(Objects.Self)),
        spell(doing(counterTargetSpell), targets = listOf(slot("spell", spellFilter))),
    )

    // CR 508.1d: a cost to attack you, paid per attacking creature
    @Test
    fun windborneMuse() = card(
        name = "Windborne Muse", cost = "{3}{W}", type = "Creature — Spirit", pt = "2/3",
        text = """
            Flying
            Creatures can't attack you unless their controller pays {2} for each creature they control that's attacking you.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Static(listOf(Mechanic.AttackCost(manaCost("{2}"), Recipients(players = Players.You))), Affected.Objects(Objects.Each(creature))),
    )

    // CR 508.1, 509.1: it must be joined by another creature
    @Test
    fun emberedBeast() = card(
        name = "Embered Beast", cost = "{2}{R}", type = "Creature — Beast", pt = "3/4",
        text = "This creature can't attack or block alone.",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantAttackAlone, Mechanic.CantBlockAlone), affects = Affected.Objects(Objects.Self)),
    )

    // CR 709.3: each half of a split card has its own abilities; 701.19c (the destroyed creature can't be regenerated)
    @Test
    fun spiteMalice() = cardFaces(
        Layout.Split,
        card(name = "Spiteful", cost = "{3}{U}", type = "Instant", text = "Counter target noncreature spell.") to abilities(
            spell(doing(counterTargetSpell), targets = listOf(slot("spell", and(noncreatureSpell, spellFilter)))),
        ),
        card(name = "Maliciously", cost = "{3}{B}", type = "Instant", text = "Destroy target nonblack creature. It can't be regenerated.") to abilities(
            spell(doing(Effect.Destroy(target("creature"), noRegeneration = true)),
                targets = listOf(slot("creature", and(not(ObjectFilter.OfColor(Color.Black)), creature)))),
        ),
    )

    // CR 603.2, 121.2: the second card drawn in a turn
    @Test
    fun jolraelMwonvuliRecluse() = card(
        name = "Joral, Mwonvuli Recluse", cost = "{1}{G}", type = "Legendary Creature — Human Druid", pt = "1/2",
        text = """
            Whenever you draw your second card each turn, create a 2/2 green Cat creature token.
            {4}{G}{G}: Until end of turn, creatures you control have base power and toughness X/X, where X is the number of cards in your hand.
        """,
    ) parsesTo abilities(
        triggered(drawSecondCard, create(creatureToken("Cat", listOf(Color.Green), 2, 2))),
        activated(cost(mana("{4}{G}{G}")), untilEndOfTurn(Objects.Each(creatureYouControl),
            Mechanic.SetPowerToughness(count(inZoneYouOwn), count(inZoneYouOwn)))),
    )

    // CR 700.4: dies
    @Test
    fun penumbralBobcat() = card(
        name = "Penumbral Bobcat", cost = "{2}{G}", type = "Creature — Cat", pt = "2/1",
        text = "When this creature dies, create a 2/1 black Cat creature token.",
    ) parsesTo abilities(
        triggered(diesSelf, create(creatureToken("Cat", listOf(Color.Black), 2, 1))),
    )

    // CR 111.2: three tokens, and a sacrifice cost for damage
    @Test
    fun siegeGangCaptain() = card(
        name = "Siege-Gang Captain", cost = "{3}{R}{R}", type = "Creature — Goblin", pt = "2/2",
        text = """
            When this creature enters, create three 1/1 red Goblin creature tokens.
            {1}{R}, Sacrifice a Goblin: This creature deals 2 damage to any target.
        """,
    ) parsesTo abilities(
        whenThisEnters(create(creatureToken("Goblin", listOf(Color.Red), 1, 1), n(3))),
        activated(cost(mana("{1}{R}"), sacrificeA(sub("Goblin"))), damage(2, toAny), targets = listOf(anyTarget)),
    )

    // CR 700.4: four tokens when it dies
    @Test
    fun symbioticBeastie() = card(
        name = "Symbiotic Beastie", cost = "{4}{G}{G}", type = "Creature — Insect Beast", pt = "4/4",
        text = "When this creature dies, create four 1/1 green Insect creature tokens.",
    ) parsesTo abilities(
        triggered(diesSelf, create(creatureToken("Insect", listOf(Color.Green), 1, 1), n(4))),
    )

    // CR 111.10, 118.3: a token whose own ability counts artifacts; a tap cost naming an untapped artifact
    @Test
    fun urzaLordHighCraftsman() = card(
        name = "Urza, Lord High Craftsman", cost = "{2}{U}{U}", type = "Legendary Creature — Human Artificer", pt = "1/4",
        text = """
            When Urza enters, create a 0/0 colorless Construct artifact creature token with "This token gets +1/+1 for each artifact you control."
            Tap an untapped artifact you control: Add {U}.
            {5}: Shuffle your library, then exile the top card. Until end of turn, you may play that card without paying its mana cost.
        """,
    ) parsesTo abilities(
        whenThisEnters(create(creatureToken("Construct", emptyList(), 0, 0,
            abilities = listOf(static(Mechanic.ModifyPowerToughness(count(and(artifact, you)), count(and(artifact, you))))),
            types = listOf(CardType.Artifact, CardType.Creature)))),
        activated(cost(CostPart.TapObjects(CostObjects.Choose(artifact, count = n(1)))), addManaStep("{U}")),
        activated(cost(mana("{5}")),
            Step.Do(Effect.Shuffle(Players.You)),
            Step.Do(Effect.Move(Objects.LibraryCards(Players.You, n(1), fromTop = true), to = Destination(Zone.Exile), postBind = "exiled")),
            untilEndOfTurn(Objects.Bound("exiled"), Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Play, withoutPaying = true, holder = PlayerFilter.You)),
        ),
    )
}
