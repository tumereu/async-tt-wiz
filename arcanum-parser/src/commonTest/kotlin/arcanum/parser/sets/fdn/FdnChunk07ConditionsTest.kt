package arcanum.parser.sets.fdn

import arcanum.api.DamageOccurrence
import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** The FDN cards whose first unread clause was a condition (chunk 07). The invented names carry the oracle text of the real cards. */
class FdnChunk07ConditionsTest {
    private fun life(comparison: Comparison, n: Int) = Condition.Compare(Value.LifeOf(Players.You), comparison, n(n))

    // CR 614.1a (both replace the event), CR 111.1 (tokens are created by effects), CR 118.3, 606.4 (a cost isn't an effect)
    @Test
    fun twofoldSpringtide() = card(
        name = "Twofold Springtide",
        cost = "{4}{G}",
        type = "Enchantment",
        text = """
            If an effect would create one or more tokens under your control, it creates twice that many of those tokens instead.
            If an effect would put one or more counters on a permanent you control, it puts twice that many of those counters on that permanent instead.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.CreateToken(you), modify = listOf(Modification.MultiplyAmount(2)))),
            affects = Affected.Objects(Objects.Self)),
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.CountersPut(and(permanentFilter, you), byEffect = true),
            modify = listOf(Modification.MultiplyAmount(2)))), affects = Affected.Objects(Objects.Self)),
    )

    // CR 118.12a: the player who cast the spell may discard a card; if they don't, they lose the life
    @Test
    fun bitterDilemma() = card(
        name = "Bitter Dilemma",
        cost = "{3}{B}{B}",
        type = "Enchantment",
        text = "Whenever an opponent casts a spell, that player loses 5 life unless they discard a card.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Opponent)),
            Step.Pay(Cost(listOf(discardACard)), payer = Players.EventPlayer, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(loseLife(5, Players.EventPlayer))),
        ),
    )

    // CR 110.4 (the permanent types), CR 305.2 (a land played this way uses a land play), CR 601.3 (cast with permission)
    @Test
    fun vashka() = card(
        name = "Vashka, the Barrowtide",
        cost = "{3}{B}{G}{U}",
        type = "Legendary Creature — Elemental Avatar",
        pt = "6/6",
        text = "During each of your turns, you may play a land and cast a permanent spell of each permanent type from your graveyard.",
    ) parsesTo abilities(
        fromGraveyard(PlayKind.Play, CardType.Land),
        fromGraveyard(PlayKind.Cast, CardType.Artifact),
        fromGraveyard(PlayKind.Cast, CardType.Battle),
        fromGraveyard(PlayKind.Cast, CardType.Creature),
        fromGraveyard(PlayKind.Cast, CardType.Enchantment),
        fromGraveyard(PlayKind.Cast, CardType.Planeswalker),
    )

    private fun fromGraveyard(how: PlayKind, type: CardType) = Ability.Static(
        listOf(Mechanic.MayPlay(Zone.Graveyard, how = how, perTurn = 1, holder = PlayerFilter.You)),
        affects = Affected.Objects(Objects.Each(and(ObjectFilter.Type(type), inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))),
        condition = Condition.YourTurn,
    )

    // CR 601.2c (the player is targeted as it is cast), CR 608.2b (checked as it resolves)
    @Test
    fun ormosTenfoldRite() = card(
        name = "Ormo's Tenfold Rite",
        cost = "{3}{R}",
        type = "Instant",
        text = "If target player has exactly 10 life, Ormo's Tenfold Rite deals 10 damage to that player.",
    ) parsesTo abilities(
        spell(
            Step.If(Condition.Compare(Value.LifeOf(Players.Target("player")), Comparison.Equal, n(10)), then = listOf(
                damage(10, Recipients(players = Players.Target("player"))))),
            targets = listOf(targetPlayer),
        ),
    )

    // CR 608.2c: "it" is the returned card
    @Test
    fun boneyardMuster() = card(
        name = "Boneyard Muster",
        cost = "{1}{B}",
        type = "Sorcery",
        text = "Return target creature card from your graveyard to your hand. If it's a Zombie card, draw a card.",
    ) parsesTo abilities(
        spell(
            move(target("card"), Zone.Hand),
            Step.If(Condition.ObjectMatches(target("card"), sub("Zombie")), then = listOf(draw(1))),
            targets = listOf(slot("card", inYourGraveyard(creature))),
        ),
    )

    // CR 205.1b (the new creature types replace the old), CR 613.4b (it becomes 3/2), CR 611.2a (for as long as it stays: no duration)
    @Test
    fun tamsin() = card(
        name = "Tamsin, Planar Wayfarer",
        cost = "{R}",
        type = "Legendary Creature — Human Faerie Scout",
        pt = "2/1",
        text = """
            {1}{R}: If Tamsin is a Scout, it becomes a Human Faerie Detective and gains "Whenever Tamsin deals combat damage to a player, exile the top card of your library. You may play that card this turn."
            {2}{R}: If Tamsin is a Detective, it becomes a 3/2 Human Faerie Rogue and gains double strike.
        """,
    ) parsesTo abilities(
        activated(manaCost("{1}{R}"), becomes("Scout", listOf("Human", "Faerie", "Detective"),
            Mechanic.GainAbilities(listOf(triggered(combatDamageToAPlayer,
                Step.Do(Effect.Move(Objects.LibraryCards(Players.You, n(1), fromTop = true), arcanum.api.Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                    Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn))))))),
        activated(manaCost("{2}{R}"), becomes("Detective", listOf("Human", "Faerie", "Rogue"),
            Mechanic.SetPowerToughness(n(3), n(2)), has(Keyword.DoubleStrike))),
    )

    private fun becomes(was: String, subtypes: List<String>, vararg also: Mechanic) =
        Step.If(Condition.ObjectMatches(Objects.Self, sub(was)), then = listOf(Step.Do(Effect.ApplyMechanics(
            listOf(Mechanic.SetTypes(addSubtypes = subtypes.map { SubtypeRef.Literal(Subtype(it)) }, replaceSubtypes = true)) + also,
            Affected.Objects(Objects.Self), Duration.Indefinitely))))

    // CR 608.2c: counted among the cards the exile moved
    @Test
    fun spiritFetteredGhoul() = card(
        name = "Spirit-Fettered Ghoul",
        cost = "{3}{B}",
        type = "Creature — Zombie",
        pt = "4/2",
        text = "When this creature enters, exile up to two target cards from a single graveyard. If at least one creature card was exiled this way, each opponent loses 2 life and you gain 2 life.",
    ) parsesTo abilities(
        triggered(
            entersSelf,
            move(target("card"), Zone.Exile, postBind = "exiled"),
            Step.If(Condition.Compare(Value.Count(Objects.Where(Objects.Bound("exiled"), creature)), Comparison.AtLeast, n(1)), then = listOf(
                loseLife(2, Players.Each(PlayerFilter.Opponent)), gainLife(2))),
            targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard)), min = 0, max = 2).copy(sameOwner = true)),
        ),
    )

    // CR 614.1a (the life gain is replaced), CR 611.3a (+2/+2 for as long as the condition holds)
    @Test
    fun seraphOfVigor() = card(
        name = "Seraph of Vigor",
        cost = "{2}{W}",
        type = "Creature — Angel",
        pt = "2/2",
        text = """
            Flying
            If you would gain life, you gain that much life plus 1 instead.
            This creature gets +2/+2 as long as you have 25 or more life.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.GainLife(PlayerFilter.You), modify = listOf(Modification.AddAmount(n(1))))),
            affects = Affected.Objects(Objects.Self)),
        static(pt(2, 2), condition = life(Comparison.AtLeast, 25)),
    )

    // CR 611.3a, 702.4b (double strike for as long as you have 25 or more life)
    @Test
    fun dualswordTemplar() = card(
        name = "Dualsword Templar",
        cost = "{3}{W}",
        type = "Creature — Human Knight",
        pt = "3/3",
        text = """
            Whenever you gain life, put a +1/+1 counter on this creature.
            As long as you have 25 or more life, this creature has double strike.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.GainLife(PlayerFilter.You)), counters(Objects.Self)),
        static(has(Keyword.DoubleStrike), condition = life(Comparison.AtLeast, 25)),
    )

    private val shuffledInstead = Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Graveyard),
        instead = Instructions(steps = listOf(doing(Effect.Reveal(Objects.Self)), move(Objects.Self, Zone.Library),
            doing(Effect.Shuffle(Players.OwnerOf(Objects.Self))))))), affects = Affected.Objects(Objects.Self),
        functionsIn = listOf(Zone.Battlefield, Zone.Hand, Zone.Library, Zone.Exile, Zone.Stack, Zone.Command))

    // CR 614.1a, 113.6b ("from anywhere": it functions in every zone it could leave for a graveyard), CR 701.24 (shuffled into its owner's library)
    @Test
    fun ironheartJuggernaut() = card(
        name = "Ironheart Juggernaut",
        cost = "{11}",
        type = "Artifact Creature — Golem",
        pt = "11/11",
        text = """
            Trample
            Indestructible
            If Ironheart Juggernaut would be put into a graveyard from anywhere, reveal Ironheart Juggernaut and shuffle it into its owner's library instead.
        """,
    ) parsesTo abilities(Keyword.Trample, Keyword.Indestructible, shuffledInstead)

    // CR 702.16j (protection from everything), CR 614.1a
    @Test
    fun primogenitorHydra() = card(
        name = "Primogenitor Hydra",
        cost = "{W}{W}{U}{U}{B}{B}{R}{R}{G}{G}",
        type = "Legendary Creature — Hydra Avatar",
        pt = "10/10",
        text = """
            Protection from everything
            If Primogenitor Hydra would be put into a graveyard from anywhere, reveal Primogenitor Hydra and shuffle it into its owner's library instead.
        """,
    ) parsesTo abilities(Keyword.Protection(ObjectFilter.Any), shuffledInstead)

    // CR 101.4 (chosen in turn order, sacrificed at once), CR 608.2c ("another": not the sacrificed card)
    @Test
    fun lethalTincture() = card(
        name = "Lethal Tincture",
        cost = "{B}{G}",
        type = "Sorcery",
        text = "Each player sacrifices a creature or planeswalker of their choice. If you sacrificed a permanent this way, you may return another permanent card from your graveyard to your hand.",
    ) parsesTo abilities(
        spell(Step.Choose(Choice.Objects(and(or(creature, ObjectFilter.Type(CardType.Planeswalker)), you), n(1), n(1)), bind = "yours", chooser = Players.You),
            doing(Effect.Sacrifice(Objects.Bound("yours"), postBind = "your card")),
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "player", listOf(
                Step.Choose(Choice.Objects(and(or(creature, ObjectFilter.Type(CardType.Planeswalker)), ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))), n(1), n(1)),
                    bind = "theirs", chooser = Players.Bound("player")),
                doing(Effect.Sacrifice(Objects.Bound("theirs")))), together = true),
            Step.If(Condition.NonEmpty("yours"), then = listOf(
                Step.Choose(Choice.Objects(and(permanentCard, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You), not(ObjectFilter.Is(Objects.Bound("your card")))), n(0), n(1)),
                    bind = "returned", chooser = Players.You),
                move(Objects.Bound("returned"), Zone.Hand)))),
    )

    // CR 613.4c (other Goblins +1/+1), CR 611.2a, 603.2 (the granted trigger, until end of turn), CR 115.1 ("another target": not this creature)
    @Test
    fun rocketKickGoblin() = card(
        name = "Rocket-Kick Goblin",
        cost = "{2}{R}",
        type = "Creature — Goblin Warrior",
        pt = "2/3",
        text = """
            Other Goblins you control get +1/+1.
            {R}: Until end of turn, another target Goblin you control gains flying and "When this creature deals combat damage, sacrifice it."
        """,
    ) parsesTo abilities(
        static(pt(1, 1), affects = Objects.Each(and(another, sub("Goblin"), you))),
        activated(manaCost("{R}"),
            untilEndOfTurn(target("Goblin"), Mechanic.GainAbilities(listOf(Keyword.Flying,
                triggered(on(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(), combat = true, per = DamageOccurrence.Source)), sacrificeIt)))),
            targets = listOf(slot("Goblin", and(another, sub("Goblin"), you)))),
    )

    // CR 603.10a (a creature that died is seen as it last existed: attacking or not), CR 608.2c ("Otherwise")
    @Test
    fun varna() = card(
        name = "Varna, Bloodhand of Sel",
        cost = "{1}{B}{R}{R}",
        type = "Legendary Creature — Human Berserker",
        pt = "4/3",
        text = "Whenever another creature you control dies, draw a card if it was attacking. Otherwise, Varna deals 1 damage to each opponent.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(and(another, creature, you), from = Zone.Battlefield, to = Zone.Graveyard)),
            Step.If(Condition.ObjectMatches(Objects.EventObject(EventRole.Before), attacking), then = listOf(draw(1)),
                otherwise = listOf(damage(1, eachOpponent))),
        ),
    )
}
