package arcanum.parser.sets.sos

import arcanum.api.Affected
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Secrets of Strixhaven chunk 01: converge (an ability word, CR 207.2c) — the number of colours of mana spent to cast the spell
 * (CR 601.2h), kept by the permanent it becomes (CR 400.7d) — and the Archaics: entering with that many counters (CR 614.1c), exile by
 * mana value, "A, B, and C" with one X, a draw that may be zero, excess damage (CR 120.4a), "that creature enters with X additional
 * counters" for a creature spell cast (CR 400.7d), and a cost reduction for each instant and sorcery card in the graveyard.
 */
class SosChunk01ConvergeTest {
    private val converge = "Converge"
    private val creatureSpell = creature

    // CR 601.2f: the reduction counts each card that is either type; CR 608.2b: "if that spell would be put into your graveyard"
    @Test
    fun theRousingAntiquity() = card(
        name = "The Rousing Antiquity",
        cost = "{10}",
        type = "Legendary Creature — Avatar",
        pt = "7/7",
        text = """
            This spell costs {1} less to cast for each instant and sorcery card in your graveyard.
            Reach
            Whenever The Rousing Antiquity attacks, you may cast target instant or sorcery card from your graveyard without paying its mana cost. If that spell would be put into your graveyard, exile it instead.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, count(inYourGraveyard(instantOrSorcery))))),
        Keyword.Reach,
        triggered(
            attacksSelf,
            Step.May(listOf(doing(Effect.Cast(target("card"), PlayKind.Cast, withoutPaying = true, caster = Players.You, bind = "spell"))), Players.You),
            exiledInsteadOfGraveyard(Objects.Bound("spell")),
            targets = listOf(slot("card", inYourGraveyard(instantOrSorcery))),
        ),
    )

    // CR 614.1c: it enters with the counters; CR 400.7d: the permanent knows the mana spent on its spell
    @Test
    fun spitefulAntiquity() = card(
        name = "Spiteful Antiquity",
        cost = "{5}",
        type = "Creature — Avatar",
        pt = "2/2",
        text = """
            Reach, trample
            Converge — This creature enters with a +1/+1 counter on it for each color of mana spent to cast it.
        """,
    ) parsesTo abilities(Keyword.Reach, Keyword.Trample, entersWith(p1p1, colorsSpent(), abilityWord = converge))

    // CR 115.1: the target's mana value is compared as it is targeted and as the ability resolves (CR 608.2b)
    @Test
    fun cleavingAntiquity() = card(
        name = "Cleaving Antiquity",
        cost = "{6}",
        type = "Creature — Avatar",
        pt = "3/3",
        text = """
            Converge — When this creature enters, exile target nonland permanent an opponent controls with mana value less than or equal to the number of colors of mana spent to cast this creature.
            {2}: Put target card from a graveyard on the bottom of its owner's library.
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            move(target("permanent"), Zone.Exile),
            targets = listOf(slot("permanent", and(nonland, permanentFilter, opponents, ObjectFilter.ManaValue(Comparison.AtMost, colorsSpent())))),
            abilityWord = converge,
        ),
        activated(
            manaCost("{2}"),
            move(target("card"), Zone.Library, position = -1),
            targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard)))),
        ),
    )

    // CR 107.3c: one X defined for the whole ability
    @Test
    fun allHandsConvened() = card(
        name = "All Hands Convened",
        cost = "{6}",
        type = "Sorcery",
        text = "Converge — Target player draws X cards, All Hands Convened deals X damage to any target, and you gain X life, where X is the number of colors of mana spent to cast this spell.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Draw(Players.Target("player"), colorsSpent())),
            damage(colorsSpent(), toAny),
            doing(Effect.GainLife(Players.You, colorsSpent())),
            targets = listOf(targetPlayer, anyTarget),
            abilityWord = converge,
        ),
    )

    // "If you draw one or more cards this way" reads the cards actually drawn (X may be 0)
    @Test
    fun ascendantAntiquity() = card(
        name = "Ascendant Antiquity",
        cost = "{7}",
        type = "Creature — Avatar",
        pt = "6/6",
        text = """
            Vigilance
            Converge — When this creature enters, you may draw X cards, where X is the number of colors of mana spent to cast this spell. If you draw one or more cards this way, discard two cards.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(
            entersSelf,
            Step.May(listOf(doing(Effect.Draw(Players.You, colorsSpent(), postBind = "drawn"))), Players.You),
            ifThen(Condition.NonEmpty("drawn"), discard(2)),
            abilityWord = converge,
        ),
    )

    @Test
    fun arcanePortentsRetold() = card(
        name = "Arcane Portents Retold",
        cost = "{4}{B}",
        type = "Sorcery",
        text = "Converge — Target player discards X cards, where X is the number of colors of mana spent to cast this spell.",
    ) parsesTo abilities(
        spell(doing(Effect.Discard(Players.Target("player"), colorsSpent(), ObjectFilter.Any)), targets = listOf(targetPlayer), abilityWord = converge),
    )

    // CR 120.4a: excess damage is the damage beyond lethal; the exiled cards may be played (CR 305.1) until the end of your next turn
    @Test
    fun antiquitysTorment() = card(
        name = "Antiquity's Torment",
        cost = "{4}{R}",
        type = "Sorcery",
        text = "Converge — Antiquity's Torment deals X damage to target creature, where X is the number of colors of mana spent to cast this spell. Exile cards from the top of your library equal to the excess damage dealt to that creature this way. You may play those cards until the end of your next turn.",
    ) parsesTo abilities(
        spell(
            doing(Effect.DealDamage(toObjects(target("creature")), colorsSpent(), Objects.Self, bind = "damage")),
            doing(Effect.Move(Objects.LibraryCards(Players.You, Value.Excess("damage"), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")),
                Duration.UntilEndOfYourNextTurn)),
            targets = listOf(slot("creature", creature)),
            abilityWord = converge,
        ),
    )

    // CR 611.2c: the bonus is fixed as the trigger resolves, from the colours spent on the spell that triggered it
    @Test
    fun cinderbloodAntiquity() = card(
        name = "Cinderblood Antiquity",
        cost = "{2/R}{2/R}{2/R}",
        type = "Creature — Avatar",
        pt = "2/2",
        text = """
            Reach, trample
            Converge — This creature enters with a +1/+1 counter on it for each color of mana spent to cast it.
            Whenever you cast an instant or sorcery spell, creatures you control get +1/+0 until end of turn for each color of mana spent to cast that spell.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Trample,
        entersWith(p1p1, colorsSpent(), abilityWord = converge),
        triggered(
            youCastInstantOrSorcery,
            doing(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(colorsSpent(thatSpell), n(0))), Affected.Objects(Objects.Each(creatureYouControl)),
                Duration.EndOfTurn)),
        ),
    )

    @Test
    fun brambleChorus() = card(
        name = "Bramble Chorus",
        cost = "{5}{G}",
        type = "Sorcery",
        text = "Converge — Create two 0/0 green and blue Fractal creature tokens. Put X +1/+1 counters on each of them and you gain X life, where X is the number of colors of mana spent to cast this spell.",
    ) parsesTo abilities(
        spell(
            create(fractal, n(2), bind = "tokens"),
            counters(Objects.Bound("tokens"), colorsSpent()),
            doing(Effect.GainLife(Players.You, colorsSpent())),
            abilityWord = converge,
        ),
    )

    // CR 400.7d: what the spell gets goes with the permanent it becomes; the X is the colours spent on that creature spell
    @Test
    fun thicketbornAntiquity() = card(
        name = "Thicketborn Antiquity",
        cost = "{2/G}{2/G}",
        type = "Creature — Avatar",
        pt = "0/0",
        text = """
            Reach, trample
            Converge — This creature enters with a +1/+1 counter on it for each color of mana spent to cast it.
            Whenever you cast a creature spell, that creature enters with X additional +1/+1 counters on it, where X is the number of colors of mana spent to cast it.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Trample,
        entersWith(p1p1, colorsSpent(), abilityWord = converge),
        triggered(
            youCast(creatureSpell),
            doing(Effect.SpellGets(thatSpell, listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Affected), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(p1p1, colorsSpent(thatSpell))))))),
        ),
    )
}
