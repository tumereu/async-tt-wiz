package arcanum.parser.sets.woe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Wilds of Eldraine chunk 16: put counters, reflexive triggers, tap and untap, set P/T, surveil and spend-mana-as-any-color wordings. */
class WoeChunk16CountersTapUntapTest {
    private val manaValue5 = and(spellFilter, manaValue(Comparison.AtLeast, 5))
    private val tapAndStun = listOf(tapIt(target("creature")), counters(target("creature"), 1, CounterKind.Stun))

    // CR 603.2c (one or more), 603.4 (once each turn), 614.? no: 122.1 (counters)
    @Test
    fun thornlitArchivist() = card(
        name = "Thornlit Archivist",
        cost = "{1}{G}",
        type = "Creature — Elf Artificer",
        text = """
            Whenever one or more artifacts you control enter, put two +1/+1 counters on this creature. This ability triggers only once each turn.
            Whenever one or more enchantments you control enter, draw a card. This ability triggers only once each turn.
        """,
        pt = "0/1",
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.ZoneChange(and(artifact, you), to = Zone.Battlefield), oneOrMore = true), counters(Objects.Self, 2), perTurn = 1),
        triggered(Trigger.On(EventPattern.ZoneChange(and(enchantment, you), to = Zone.Battlefield), oneOrMore = true), draw(1), perTurn = 1),
    )

    // CR 510.3a (combat damage trigger), 603.12 (reflexive trigger), 406.1 (exile top cards), 122.1
    @Test
    fun skewerfireElemental() = card(
        name = "Skewerfire Elemental",
        cost = "{R}",
        type = "Creature — Elemental",
        text = """
            Menace
            Whenever this creature deals combat damage to a player, put a skewer counter on this creature. Then you may sacrifice it. If you do, exile the top X cards of your library, where X is the number of skewer counters on this creature. You may play those cards this turn.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(combatDamageToAPlayer,
            counters(Objects.Self, 1, CounterKind.Named("skewer")),
            Step.Pay(null, steps = listOf(doing(Effect.Sacrifice(Objects.Self))), payer = Players.You, mandatory = false, bind = "paid"),
            ifThen(Condition.Did("paid"),
                move(Objects.LibraryCards(Players.You, Value.Of(NumericProperty.Counters(CounterKind.Named("skewer")), Objects.Self, Combine.Single), fromTop = true),
                    Zone.Exile, postBind = "exiled")),
            untilEndOfTurn(Objects.Bound("exiled"), Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
        ),
    )

    // CR 700.4 (put into a graveyard from the battlefield), 122.1
    @Test
    fun hearthwardSavior() = card(
        name = "Hearthward Savior",
        cost = "{2}{W}",
        type = "Creature — Human Knight",
        text = """
            Vigilance
            Whenever an enchantment you control is put into a graveyard from the battlefield, put a +1/+1 counter on this creature.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(on(EventPattern.ZoneChange(and(enchantment, you), from = Zone.Battlefield, to = Zone.Graveyard)), counters(Objects.Self)),
    )

    // CR 601.2 (cast trigger), 202.3 (mana value), 122.1
    @Test
    fun galeProwler() = card(
        name = "Gale Prowler",
        cost = "{1}{U}",
        type = "Creature — Human Rogue",
        text = "Whenever you cast a spell with mana value 5 or greater, put two +1/+1 counters on this creature.",
        pt = "2/1",
    ) parsesTo abilities(triggered(youCast(manaValue5), counters(Objects.Self, 2)))

    // CR 715.2 (Adventure faces), 601.2, 202.3, 701.9 (discard)
    @Test
    fun gusthornHart() = cardFaces(Layout.Adventure,
        card(
            name = "Gusthorn Hart",
            cost = "{3}{G}",
            type = "Creature — Elemental Elk",
            text = """
                Trample
                Whenever you cast a spell with mana value 5 or greater, put a +1/+1 counter on this creature.
            """,
            pt = "3/4",
        ) to abilities(Keyword.Trample, triggered(youCast(manaValue5), counters(Objects.Self))),
        card(
            name = "Sift the Gust",
            cost = "{1}{U}",
            type = "Instant — Adventure",
            text = "Draw two cards, then discard two cards. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(spell(draw(2), discard(2))),
    )

    // CR 715.2, 511.1 (end step), 122.1, 701.26 (untap)
    @Test
    fun virtueOfPledges() = cardFaces(Layout.Adventure,
        card(
            name = "Virtue of Pledges",
            cost = "{3}{W}{W}",
            type = "Enchantment",
            text = "At the beginning of your end step, put a +1/+1 counter on each creature you control. Untap those creatures.",
        ) to abilities(
            triggered(stepBegins(TurnStep.End, PlayerFilter.You),
                counters(Objects.Each(creatureYouControl)), untapIt(Objects.Each(creatureYouControl))),
        ),
        card(
            name = "Pledge Ardent",
            cost = "{1}{W}",
            type = "Instant — Adventure",
            text = "Create a 2/2 white Knight creature token with vigilance. (Then exile this card. You may cast the enchantment later from exile.)",
        ) to abilities(spell(create(creatureToken("Knight", listOf(Color.White), 2, 2, listOf(Keyword.Vigilance))))),
    )

    // CR 603.2 (token enters), 122.1, 508.1 (attack), 611.2 (X pump)
    @Test
    fun briarwoodMentor() = card(
        name = "Briarwood Mentor",
        cost = "{2}{G}",
        type = "Creature — Treefolk",
        text = """
            Whenever a token you control enters, put a +1/+1 counter on this creature.
            Whenever this creature attacks, another target attacking creature gets +X/+X until end of turn, where X is this creature's power.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        triggered(enters(and(token, you)), counters(Objects.Self)),
        triggered(attacksSelf,
            untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(power(Objects.Self), power(Objects.Self))),
            targets = listOf(slot("creature", and(another, attacking, creature)))),
    )

    // CR 603.12 (reflexive trigger), 118.12 (may pay), 701.26 (tap), 122.1 (stun)
    @Test
    fun netcasterSprite() = card(
        name = "Netcaster Sprite",
        cost = "{U}",
        type = "Creature — Faerie Wizard",
        text = """
            Flying
            When this creature enters, you may pay {2}. When you do, tap target creature an opponent controls and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf,
            Step.Pay(manaCost("{2}"), bind = "paid", payer = Players.You),
            ifThen(Condition.Did("paid"), reflexive(*tapAndStun.toTypedArray(), targets = listOf(slot("creature", creatureOpponentControls))))),
    )

    // CR 702.118 (skulk), 107.3 (X), 122.1, 614.1c (enters with counters), 603.4
    @Test
    fun cleverSavant() = card(
        name = "Clever Savant",
        cost = "{X}{U}",
        type = "Creature — Human Wizard",
        text = """
            Skulk (This creature can't be blocked by creatures with greater power.)
            This creature enters with X +1/+1 counters on it.
            At the beginning of your upkeep, if this creature has one or more +1/+1 counters on it, you may remove a +1/+1 counter from it. If you do, draw a card.
        """,
        pt = "0/1",
    ) parsesTo abilities(
        Keyword.Skulk,
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, Value.CastX(Objects.Self))))),
        triggered(yourUpkeep,
            Step.Pay(null, steps = listOf(doing(Effect.RemoveCounters(Objects.Self, p1p1, n(1)))), payer = Players.You, mandatory = false, bind = "paid"),
            ifThen(Condition.Did("paid"), draw(1)),
            interveningIf = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(p1p1, atLeast = 1))),
    )

    // CR 305.7? no: 614.1d (enters tapped), 205.1b (still a land), 508.1 (attack), 122.1
    @Test
    fun wanderingCamp() = card(
        name = "Wandering Camp",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {R} or {W}.
            {1}{R}{W}: This land becomes a 2/2 red and white Ox creature until end of turn. It's still a land.
            Whenever this land attacks, put a +1/+1 counter on target creature you control.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Literal(Color.Red), ColorRef.Literal(Color.White)))), Players.You))),
        activated(cost(mana("{1}{R}{W}")), untilEndOfTurn(Objects.Self,
            Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Ox")))),  // CR 205.1b: "still a [type]" keeps all prior subtypes
            Mechanic.SetColors(listOf(ColorRef.Literal(Color.Red), ColorRef.Literal(Color.White))), Mechanic.SetPowerToughness(n(2), n(2)))),
        triggered(attacksSelf, counters(target("creature")), targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 614.1d, 205.1b, 508.1, 119.3 (lose life), 119.1 (gain life)
    @Test
    fun wanderingKeep() = card(
        name = "Wandering Keep",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {W} or {B}.
            {2}{W}{B}: This land becomes a 1/4 white and black Nightmare creature until end of turn. It's still a land.
            Whenever this land attacks, defending player loses 2 life and you gain 2 life.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Literal(Color.White), ColorRef.Literal(Color.Black)))), Players.You))),
        activated(cost(mana("{2}{W}{B}")), untilEndOfTurn(Objects.Self,
            Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Nightmare")))),  // CR 205.1b: "still a [type]" keeps all prior subtypes
            Mechanic.SetColors(listOf(ColorRef.Literal(Color.White), ColorRef.Literal(Color.Black))), Mechanic.SetPowerToughness(n(1), n(4)))),
        triggered(attacksSelf,
            loseLife(2, Players.DefendingPlayerOf(Objects.EventObject(EventRole.Object))), gainLife(2)),
    )

    // CR 604.3 (characteristic-defining ability), 208.2a
    @Test
    fun gentleHareicorn() = card(
        name = "Gentle Hareicorn",
        cost = "{1}{W}",
        type = "Creature — Rabbit Unicorn",
        text = "Gentle Hareicorn's power and toughness are each equal to the number of nonland permanents you control.",
        pt = "*/*",
    ) parsesTo abilities(
        static(Mechanic.SetPowerToughness(count(and(nonland, permanentFilter, you)), count(and(nonland, permanentFilter, you))), characteristicDefining = true),
    )

    // CR 609.4b (spend mana as though any color), 702.? no: 113.? gain abilities, 406.6 (linked exile), 122.1
    @Test
    fun marrowsSoulKettle() = card(
        name = "Marrow's Soul Kettle",
        cost = "{2}",
        type = "Legendary Artifact",
        text = """
            You may spend mana as though it were mana of any color to activate abilities of creatures you control.
            Creatures you control with +1/+1 counters on them have all activated abilities of all creature cards exiled with Marrow's Soul Kettle.
            {T}: Exile target card from a graveyard. When a creature card is exiled this way, put a +1/+1 counter on target creature you control.
        """,
    ) parsesTo abilities(
        playerStatic(Mechanic.SpendManaAsAnyColor(onlyFor = ObjectFilter.AbilityOf(source = creatureYouControl))),
        static(Mechanic.GainAbilitiesOf(Objects.Where(Objects.Remembered("exiled"), creature)),
            affects = Objects.Each(and(creatureYouControl, ObjectFilter.HasCounter(p1p1, atLeast = 1)))),
        activated(cost(tap),
            move(target("card"), Zone.Exile, postBind = "exiled"),
            doing(Effect.Remember("exiled", Objects.Bound("exiled"))),
            ifThen(Condition.ObjectMatches(Objects.Bound("exiled"), creature),
                reflexive(counters(target("creature")), targets = listOf(slot("creature", creatureYouControl)))),
            targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard))))),
    )

    // CR 701.25 (surveil), 602.2 (activate from graveyard), 119.3, 121.1
    @Test
    fun duskDreamer() = card(
        name = "Dusk Dreamer",
        cost = "{B}",
        type = "Creature — Faerie Warlock",
        text = """
            Flying
            When this creature enters, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
            {2}{B}, Exile this card from your graveyard: You draw a card and you lose 1 life.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, doing(Effect.Surveil(Players.You, n(1)))),
        activated(cost(mana("{2}{B}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)), draw(1), loseLife(1)),
    )

    // CR 715.2, 601.2, 202.3, 701.26, 122.1 (stun)
    @Test
    fun thunderColossus() = cardFaces(Layout.Adventure,
        card(
            name = "Thunder Colossus",
            cost = "{3}{U}",
            type = "Creature — Giant Wizard",
            text = "Whenever you cast a spell with mana value 5 or greater, tap target creature an opponent controls and put a stun counter on it.",
            pt = "3/3",
        ) to abilities(triggered(youCast(manaValue5), *tapAndStun.toTypedArray(), targets = listOf(slot("creature", creatureOpponentControls)))),
        card(
            name = "Gale Reading",
            cost = "{5}{U}{U}",
            type = "Instant — Adventure",
            text = "Draw four cards, then discard two cards. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(spell(draw(4), discard(2))),
    )

    // CR 603.2 (enters trigger), 701.26 (tap)
    @Test
    fun frostmaneCaribou() = card(
        name = "Frostmane Caribou",
        cost = "{3}{W}",
        type = "Creature — Elk",
        text = "Whenever an enchantment you control enters, tap target creature an opponent controls.",
        pt = "3/4",
    ) parsesTo abilities(triggered(enters(and(enchantment, you)), tapIt(target("creature")), targets = listOf(slot("creature", creatureOpponentControls))))

    // CR 115.1d (one or two targets), 701.26, 122.1 (stun)
    @Test
    fun yieldToTheFrost() = card(
        name = "Yield to the Frost",
        cost = "{2}{U}",
        type = "Instant",
        text = "Tap one or two target creatures an opponent controls. Put a stun counter on each of them. (If a permanent with a stun counter would become untapped, remove one from it instead.)",
    ) parsesTo abilities(spell(tapIt(target("creatures")), counters(target("creatures"), 1, CounterKind.Stun),
        targets = listOf(slot("creatures", creatureOpponentControls, min = 1, max = 2))))

    // CR 700.2 (modes), 701.26 (untap), 702.12 (indestructible), 701.8 (destroy)
    @Test
    fun hourOfCourage() = card(
        name = "Hour of Courage",
        cost = "{2}{W}",
        type = "Instant",
        text = """
            Choose one —
            • Untap target creature. It gets +1/+0 and gains indestructible until end of turn.
            • Destroy target creature with power 4 or greater.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(untapIt(target("creature")), pump(target("creature"), 1, 0, has(Keyword.Indestructible)), targets = listOf(slot("creature", creature))),
            mode(destroy(target("creature")), targets = listOf(slot("creature", and(creature, power(Comparison.AtLeast, 4))))),
        ))),
    )
}
