package arcanum.parser.sets.vow

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * VOW chunk 11: effects that create tokens: Blood tokens (CR 111.10g) from triggers of many kinds, Zombie, Spirit, Wolf and
 * Dragon Illusion tokens, a token copy, and the costs and conditions around them.
 */
class VowChunk11CreateTokenTest {
    private val vampire = sub("Vampire")
    private val bloodToken = and(sub("Blood"), token)
    private val zombie = creatureToken("Zombie", listOf(Color.Black), 2, 2)
    private val spirit = creatureToken("Spirit", listOf(Color.White), 1, 1, listOf(Keyword.Flying))
    private val nextEndStep = stepBegins(TurnStep.End, PlayerFilter.Any)
    private val bloodYouControl = and(bloodToken, ObjectFilter.ControlledBy(PlayerFilter.You))

    // CR 603.2 and 603.2c (and/or one or more: one trigger for a batch), 603.4 (once each turn), 111.10g; CR 602.2 (sacrifice as a cost)
    @Test
    fun anjeMaidOfDishonor() = card(
        name = "Verra, Maid of Disgrace",
        cost = "{2}{B}{R}",
        type = "Legendary Creature — Vampire",
        pt = "4/5",
        text = """
            Whenever Verra and/or one or more other Vampires you control enter, create a Blood token. This ability triggers only once each turn. (It's an artifact with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")
            {2}, Sacrifice another creature or a Blood token: Each opponent loses 2 life and you gain 2 life.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(
                EventPattern.ZoneChange(or(ObjectFilter.Is(Objects.Self), and(vampire, you)), to = Zone.Battlefield),
                oneOrMore = true,
            ),
            instructions = Instructions(steps = listOf(create(blood))),
            perTurn = 1,
        ),
        activated(cost(mana("{2}"), sacrificeA(and(not(ObjectFilter.Is(Objects.Self)), or(creature, bloodToken)))),
            loseLife(2, Players.Each(PlayerFilter.Opponent)), gainLife(2)),
    )

    // CR 510.2 and 603.2 (combat damage to a player), 111.10g
    @Test
    fun belligerentGuest() = card(
        name = "Truculent Visitor",
        cost = "{2}{R}",
        type = "Creature — Vampire",
        pt = "3/2",
        text = """
            Trample (This creature can deal excess combat damage to the player or planeswalker it's attacking.)
            Whenever this creature deals combat damage to a player, create a Blood token. (It's an artifact with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(Keyword.Trample, triggered(combatDamageToAPlayer, create(blood)))

    // CR 111.10g; CR 602.2 (cost: tap and sacrifice this artifact), 115.1 ("up to two target" creature cards in your graveyard)
    @Test
    fun bloodFountain() = card(
        name = "Crimson Cistern",
        cost = "{B}",
        type = "Artifact",
        text = """
            When this artifact enters, create a Blood token. (It's an artifact with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")
            {3}{B}, {T}, Sacrifice this artifact: Return up to two target creature cards from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(blood)),
        activated(cost(mana("{3}{B}"), tap, sacrificeSelf),
            move(target("cards"), Zone.Hand),
            targets = listOf(slot("cards", inYourGraveyard(creature), min = 0, max = 2))),
    )

    // CR 608.2c and 117.12 ("you may sacrifice ... if you do": an optional payment), 111.10g
    @Test
    fun bloodcrazedSocialite() = card(
        name = "Gorecrazed Courtier",
        cost = "{3}{B}",
        type = "Creature — Vampire",
        pt = "3/3",
        text = """
            Menace
            When this creature enters, create a Blood token. (It's an artifact with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")
            Whenever this creature attacks, you may sacrifice a Blood token. If you do, it gets +2/+2 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(entersSelf, create(blood)),
        triggered(attacksSelf,
            Step.Pay(cost(sacrificeA(bloodToken)), emptyList(), Players.You, mandatory = false, bind = "paid"),
            ifThen(Condition.Did("paid"), pump(Objects.Self, 2, 2))),
    )

    // CR 602.5d (only as a sorcery), 107.3 (X is twice the number of Blood tokens you control, counted on resolution), 613.4c
    @Test
    fun bloodtitheHarvester() = card(
        name = "Gorelevy Reaper",
        cost = "{B}{R}",
        type = "Creature — Vampire",
        pt = "3/2",
        text = """
            When this creature enters, create a Blood token. (It's an artifact with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")
            {T}, Sacrifice this creature: Target creature gets -X/-X until end of turn, where X is twice the number of Blood tokens you control. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(blood)),
        activated(cost(tap, sacrificeSelf),
            untilEndOfTurn(target("creature"), pt(0, 0).let {
                val x = Value.Times(Value.Times(count(bloodYouControl), 2), -1)
                Mechanic.ModifyPowerToughness(x, x)
            }),
            targets = listOf(slot("creature", creature)), rules = asSorcery),
    )

    // CR 603.2 (an opponent casts a spell), 111.2 ("that player creates": the token's owner and controller), 508.1 (attacks), 506.2 (defending player)
    @Test
    fun bloodvialPurveyor() = card(
        name = "Veinflask Dealer",
        cost = "{2}{B}{B}",
        type = "Creature — Vampire",
        pt = "5/6",
        text = """
            Flying, trample
            Whenever an opponent casts a spell, that player creates a Blood token. (It's an artifact with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")
            Whenever this creature attacks, it gets +1/+0 until end of turn for each Blood token defending player controls.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Trample,
        triggered(on(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Opponent)),
            Step.Do(Effect.CreateToken(blood, n(1), Players.EventPlayer))),
        triggered(attacksSelf,
            untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(
                count(and(bloodToken, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.DefendingPlayerOf(Objects.Self))))), n(0)))),
    )

    // CR 700.4 (dies), 111.1
    @Test
    fun doomedDissenter() = card(
        name = "Fated Objector",
        cost = "{1}{B}",
        type = "Creature — Human",
        pt = "1/1",
        text = "When this creature dies, create a 2/2 black Zombie creature token.",
    ) parsesTo abilities(triggered(diesSelf, create(zombie)))

    // CR 603.2c (one or more cards discarded: one trigger), 603.4 (once each turn), 111.5 (a token that enters tapped)
    @Test
    fun dyingToServe() = card(
        name = "Dying to Obey",
        cost = "{2}{B}",
        type = "Enchantment",
        text = "Whenever you discard one or more cards, create a tapped 2/2 black Zombie creature token. This ability triggers only once each turn.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Discard(ObjectFilter.Any, PlayerFilter.You), oneOrMore = true),
            instructions = Instructions(steps = listOf(Step.Do(Effect.CreateToken(zombie, n(1), Players.You, tapped = true)))),
            perTurn = 1,
        ),
    )

    // CR 111.10g: two Blood tokens
    @Test
    fun falkenrathCelebrants() = card(
        name = "Ravenmoor Revelers",
        cost = "{4}{R}",
        type = "Creature — Vampire",
        pt = "4/4",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            When this creature enters, create two Blood tokens. (They're artifacts with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(Keyword.Menace, triggered(entersSelf, create(blood, n(2))))

    // CR 602.5d (sorcery), 602.5b (activate only if you control an enchantment), 118.8 (exiling itself is a cost)
    @Test
    fun heronBlessedGeist() = card(
        name = "Egret-Touched Wraith",
        cost = "{4}{W}",
        type = "Creature — Spirit",
        pt = "3/3",
        text = """
            Flying
            {3}{W}, Exile this card from your graveyard: Create two 1/1 white Spirit creature tokens with flying. Activate only if you control an enchantment and only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        activated(cost(mana("{3}{W}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)),
            create(spirit, n(2)),
            rules = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You, condition = Condition.Exists(and(enchantment, you), 1))),
    )

    // CR 118.8 and 602.2 (costs: tap, exile three cards from your own graveyard), 111.1
    @Test
    fun kessigWolfrider() = card(
        name = "Moorfen Houndrider",
        cost = "{R}",
        type = "Creature — Human Knight",
        pt = "1/2",
        text = """
            Menace
            {2}{R}, {T}, Exile three cards from your graveyard: Create a 3/2 red Wolf creature token.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        activated(cost(mana("{2}{R}"), tap, CostPart.Exile(CostObjects.Choose(cardFilter, count = n(3)), from = Zone.Graveyard)),
            create(creatureToken("Wolf", listOf(Color.Red), 3, 2))),
    )

    // CR 107.3 and 202.1 (X: the mana spent to cast that spell), 603.7 (the delayed trigger exiles that token at the next end step)
    @Test
    fun manaformHellkite() = card(
        name = "Spellshaped Drake",
        cost = "{2}{R}{R}",
        type = "Creature — Dragon",
        pt = "4/4",
        text = """
            Flying
            Whenever you cast a noncreature spell, create an X/X red Dragon Illusion creature token with flying and haste, where X is the amount of mana spent to cast that spell. Exile that token at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(youCast(noncreatureSpell),
            Step.Do(Effect.CreateToken(
                TokenSpec.Defined(
                    FaceDefinition("Dragon Illusion Token", colorIndicator = listOf(Color.Red), types = listOf(CardType.Creature),
                        subtypes = listOf(Subtype("Dragon"), Subtype("Illusion")), power = PrintedNumber.X, toughness = PrintedNumber.X,
                        abilities = listOf(Keyword.Flying, Keyword.Haste)),
                    power = Value.ManaSpent(of = Objects.EventObject(EventRole.Object)),
                    toughness = Value.ManaSpent(of = Objects.EventObject(EventRole.Object))),
                n(1), Players.You, bind = "tokens")),
            Step.Do(Effect.CreateDelayedTrigger(nextEndStep, Instructions(steps = listOf(move(Objects.Bound("tokens"), Zone.Exile)))))),
    )

    // CR 707.2 and 111.10 (a token that's a copy), 603.2 (a nontoken Zombie you control enters)
    @Test
    fun necroduality() = card(
        name = "Corpse Duplicity",
        cost = "{3}{U}",
        type = "Enchantment",
        text = "Whenever a nontoken Zombie you control enters, create a token that's a copy of that creature.",
    ) parsesTo abilities(
        triggered(enters(and(not(token), sub("Zombie"), you)), create(TokenSpec.CopyOf(Objects.EventObject(EventRole.After)))),
    )

    // CR 120.3 and 603.2 (deals damage), 603.10 (that many: the damage dealt), 111.10g
    @Test
    fun oliviasAttendants() = card(
        name = "Odalys's Retinue",
        cost = "{4}{R}{R}",
        type = "Creature — Vampire",
        pt = "6/6",
        text = """
            Menace
            Whenever this creature deals damage, create that many Blood tokens. (They're artifacts with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")
            {2}{R}: This creature deals 1 damage to any target.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(on(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(), per = DamageOccurrence.Source)),
            create(blood, Value.EventAmount)),
        activated(manaCost("{2}{R}"), damage(1, toAny), targets = listOf(anyTarget)),
    )

    // CR 701.21 (sacrifice), 603.2, 205.1b and 613.1d (becomes a 3/3 Vampire artifact creature with haste), 608.2d (may)
    @Test
    fun sanguineStatuette() = card(
        name = "Scarlet Figurine",
        cost = "{1}{R}",
        type = "Artifact",
        text = """
            When this artifact enters, create a Blood token. (It's an artifact with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")
            Whenever you sacrifice a Blood token, you may have this artifact become a 3/3 Vampire artifact creature with haste until end of turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(blood)),
        triggered(on(EventPattern.Sacrifice(and(bloodToken, ObjectFilter.ControlledBy(PlayerFilter.You)))),
            Step.May(listOf(doing(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Vampire"))), replaceSubtypes = true),
                Mechanic.SetPowerToughness(n(3), n(3)),
                Mechanic.GainAbilities(listOf(Keyword.Haste))), Affected.Objects(Objects.Self), Duration.EndOfTurn))), Players.You)),
    )

    // CR 603.2 (cast a noncreature spell), 603.4 (once each turn), 111.1
    @Test
    fun whisperingWizard() = card(
        name = "Murmuring Mage",
        cost = "{3}{U}",
        type = "Creature — Human Wizard",
        pt = "3/2",
        text = "Whenever you cast a noncreature spell, create a 1/1 white Spirit creature token with flying. This ability triggers only once each turn.",
    ) parsesTo abilities(triggered(youCast(noncreatureSpell), create(spirit), perTurn = 1))
}
