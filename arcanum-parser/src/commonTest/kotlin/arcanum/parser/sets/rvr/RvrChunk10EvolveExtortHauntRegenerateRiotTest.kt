package arcanum.parser.sets.rvr

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Ravnica chunk 10: evolve, extort, haunt, regenerate, jump-start, proliferate, riot, fear and transmute. */
class RvrChunk10EvolveExtortHauntRegenerateRiotTest {
    private val hauntedDies = EventPattern.ZoneChange(ObjectFilter.Is(Objects.LinkedTo(Keyword.Haunt)), from = Zone.Battlefield, to = Zone.Graveyard)
    private val entersOrHauntedDies = Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), hauntedDies))
    private val regenerateSelf = doing(Effect.Action(KeywordAction.Regenerate(Objects.Self)))

    // CR 702.100a (evolve)
    @Test
    fun cloudfinRaptorLookalike() = card(
        name = "Cloudwing Raptor", cost = "{U}", type = "Creature — Bird Mutant", pt = "0/1",
        text = """
            Flying
            Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
        """,
    ) parsesTo abilities(Keyword.Flying, Keyword.Evolve)

    // CR 702.100a
    @Test
    fun crocanuraLookalike() = card(
        name = "Crocoranura", cost = "{2}{G}", type = "Creature — Crocodile Frog", pt = "1/3",
        text = """
            Reach (This creature can block creatures with flying.)
            Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
        """,
    ) parsesTo abilities(Keyword.Reach, Keyword.Evolve)

    // CR 702.100a, 701.19a (regenerate), 601.2f (removing counters as a cost)
    @Test
    fun experimentOneLookalike() = card(
        name = "Experiment Uno", cost = "{G}", type = "Creature — Human Ooze", pt = "1/1",
        text = """
            Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
            Remove two +1/+1 counters from this creature: Regenerate it. (The next time this creature would be destroyed this turn, instead tap it, remove it from combat, and heal all damage on it.)
        """,
    ) parsesTo abilities(
        Keyword.Evolve,
        activated(cost(CostPart.RemoveCounters(p1p1, n(2), CostObjects.Self)), regenerateSelf),
    )

    // CR 702.101a (extort)
    @Test
    fun basilicaGuardsLookalike() = card(
        name = "Basilica Wardens", cost = "{2}{W}", type = "Creature — Human Soldier", pt = "1/4",
        text = """
            Defender
            Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
        """,
    ) parsesTo abilities(Keyword.Defender, Keyword.Extort)

    // CR 702.101a, 614.1c, 614.12 (enters tapped)
    @Test
    fun blindObedienceLookalike() = card(
        name = "Willing Obedience", cost = "{1}{W}", type = "Enchantment",
        text = """
            Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
            Artifacts and creatures your opponents control enter tapped.
        """,
    ) parsesTo abilities(
        Keyword.Extort,
        static(Mechanic.Replacement(
            EventPattern.ZoneChange(and(or(artifact, creature), opponents), to = Zone.Battlefield),
            modify = listOf(Modification.EntersTapped),
        )),
    )

    // CR 702.101a, 106.12a (a triggered mana ability: "add an additional")
    @Test
    fun cryptGhastLookalike() = card(
        name = "Crypt Wraith", cost = "{3}{B}", type = "Creature — Spirit", pt = "2/2",
        text = """
            Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
            Whenever you tap a Swamp for mana, add an additional {B}.
        """,
    ) parsesTo abilities(
        Keyword.Extort,
        triggered(on(EventPattern.Tap(and(sub("Swamp"), you), forMana = true)),
            doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{B}")!!)), Players.You))),
    )

    // CR 702.55b, c (haunt), 119.3 and 119.1 (life loss and gain)
    @Test
    fun blindHunterLookalike() = card(
        name = "Blind Stalker", cost = "{2}{W}{B}", type = "Creature — Bat", pt = "2/2",
        text = """
            Flying
            Haunt (When this creature dies, exile it haunting target creature.)
            When this creature enters or the creature it haunts dies, target player loses 2 life and you gain 2 life.
        """,
    ) parsesTo abilities(
        Keyword.Flying, Keyword.Haunt,
        triggered(entersOrHauntedDies, loseLife(2, Players.Target("player")), gainLife(2),
            targets = listOf(targetPlayer.copy(name = "player")), functionsIn = listOf(Zone.Battlefield, Zone.Exile)),
    )

    // CR 702.55b, c, 120.3 and 700.4 ("was dealt damage this turn" checked as the target is chosen and again on resolution)
    @Test
    fun orzhovEuthanistLookalike() = card(
        name = "Orzhov Executioner", cost = "{2}{B}", type = "Creature — Human Assassin", pt = "2/2",
        text = """
            Haunt (When this creature dies, exile it haunting target creature.)
            When this creature enters or the creature it haunts dies, destroy target creature that was dealt damage this turn.
        """,
    ) parsesTo abilities(
        Keyword.Haunt,
        triggered(entersOrHauntedDies, destroy(target("creature")),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ThisTurn(EventPattern.Damage(source = ObjectFilter.Any, to = RecipientFilter()), EventRole.Object)))),
            functionsIn = listOf(Zone.Battlefield, Zone.Exile)),
    )

    // CR 207.2c (hellbent is an ability word), 613.1f, 701.19a; the granted ability is quoted
    @Test
    fun gobhobblerRatsLookalike() = card(
        name = "Gobhobbler Mice", cost = "{B}{R}", type = "Creature — Rat", pt = "2/2",
        text = """
            Hellbent — As long as you have no cards in hand, this creature gets +1/+0 and has "{B}: Regenerate this creature." (The next time it would be destroyed this turn, instead tap it, remove it from combat, and heal all damage on it.)
        """,
    ) parsesTo abilities(
        static(pt(1, 0), has(activated(cost(mana("{B}")), regenerateSelf)), condition = hellbent).copy(abilityWord = "Hellbent"),
    )

    // CR 702.133a (jump-start), 707.10 (copy of a creature)
    @Test
    fun quasiduplicateLookalike() = card(
        name = "Quasimultiply", cost = "{1}{U}{U}", type = "Sorcery",
        text = """
            Create a token that's a copy of target creature you control.
            Jump-start (You may cast this card from your graveyard by discarding a card in addition to paying its other costs. Then exile this card.)
        """,
    ) parsesTo abilities(
        spell(Step.Do(Effect.CreateToken(TokenSpec.CopyOf(target("creature")), n(1), Players.You)),
            targets = listOf(slot("creature", and(creature, you)))),
        Keyword.JumpStart,
    )

    // CR 702.133a
    @Test
    fun radicalIdeaLookalike() = card(
        name = "Radical Notion", cost = "{1}{U}", type = "Instant",
        text = """
            Draw a card.
            Jump-start (You may cast this card from your graveyard by discarding a card in addition to paying its other costs. Then exile this card.)
        """,
    ) parsesTo abilities(spell(draw(1)), Keyword.JumpStart)

    // CR 701.34a (proliferate), 605.1a ({C} mana ability)
    @Test
    fun karnsBastionLookalike() = card(
        name = "Karn's Fortress", cost = null, type = "Land",
        text = """
            {T}: Add {C}.
            {4}, {T}: Proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        activated(cost(mana("{4}"), tap), doing(Effect.Action(KeywordAction.Proliferate))),
    )

    // CR 701.34a
    @Test
    fun kiorasDambreakerLookalike() = card(
        name = "Kiora's Wavebreaker", cost = "{5}{U}", type = "Creature — Leviathan", pt = "5/6",
        text = """
            When this creature enters, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
        """,
    ) parsesTo abilities(triggered(entersSelf, doing(Effect.Action(KeywordAction.Proliferate))))

    // CR 702.36a (fear), 701.19a (sacrificing a creature as the cost), 702.53a (transmute)
    @Test
    fun dimirHouseGuardLookalike() = card(
        name = "Dimir Gatekeeper", cost = "{3}{B}", type = "Creature — Skeleton", pt = "2/3",
        text = """
            Fear (This creature can't be blocked except by artifact creatures and/or black creatures.)
            Sacrifice a creature: Regenerate this creature.
            Transmute {1}{B}{B} ({1}{B}{B}, Discard this card: Search your library for a card with the same mana value as this card, reveal it, put it into your hand, then shuffle. Transmute only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Fear,
        activated(cost(sacrificeA(creature)), regenerateSelf),
        Keyword.Transmute(manaCost("{1}{B}{B}")),
    )

    // CR 702.136a (riot), 508.1 (attack trigger), 701.8a (discarding: "you may discard ... if you do" is optional)
    @Test
    fun burningTreeVandalLookalike() = card(
        name = "Burning-Bush Vandal", cost = "{2}{R}", type = "Creature — Human Rogue", pt = "2/1",
        text = """
            Riot (This creature enters with your choice of a +1/+1 counter or haste.)
            Whenever this creature attacks, you may discard a card. If you do, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Riot,
        triggered(attacksSelf, Step.Pay(cost(discardACard), payer = Players.You, bind = "paid"), Step.If(Condition.Did("paid"), then = listOf(draw(1)))),
    )

    // CR 702.136a
    @Test
    fun rampagingRendhornLookalike() = card(
        name = "Rampaging Rendbeast", cost = "{4}{G}", type = "Creature — Beast", pt = "4/4",
        text = "Riot (This creature enters with your choice of a +1/+1 counter or haste.)",
    ) parsesTo abilities(Keyword.Riot)
}
