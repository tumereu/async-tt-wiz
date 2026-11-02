package arcanum.parser.sets.fra

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.gtc.creature
import arcanum.parser.sets.gtc.creatureOpponentControls
import arcanum.parser.sets.gtc.creatureYouControl
import arcanum.parser.sets.gtc.land
import arcanum.parser.sets.gtc.power
import arcanum.parser.sets.gtc.token
import arcanum.parser.sets.gtc.toughness
import arcanum.parser.sets.gtc.you
import kotlin.test.Test

/**
 * Reality Fracture chunk 17: green, multicoloured and artifact legendary creatures. An end step trigger when you cast no spell;
 * domain power (CR 604.3) and searching for X basic lands with different names as it was cast (CR 107.3m); a named legendary
 * token; untapping all your tokens; mana by the number of different powers (CR 106.1); hexproof until it deals combat damage;
 * a trigger on discarding the card itself; fighting up to one creature; sacrifices paying for counters and menace; X -1/-1
 * counters for each opponent's creature; a card for each colour among other artifacts; Thopters with haste; artifact creature
 * Sculpture Treasures; a token for the controller of the destroyed permanent; entering artifacts and creatures causing no
 * triggers (CR 603.2); a creature that untaps only when you cast an artifact or creature spell.
 */
class FraChunk17GreenMulticoloredLegendsTest {
    @Test
    fun corvinMoonlitMonarch() = card(
        name = "Corvin, Moonlit Monarch",
        cost = "{3}{G}{G}",
        type = "Legendary Creature — Werewolf Noble",
        pt = "4/4",
        text = """
            Flash
            At the beginning of your end step, if you didn't cast a spell this turn, put two +1/+1 counters on Corvin.
            {4}{G}: Put a +1/+1 counter on each creature you control with a +1/+1 counter on it.
        """,
    ) parsesTo abilities(
            Keyword.Flash,
            triggered(stepBegins(TurnStep.End, PlayerFilter.You), counters(Objects.Self, 2),
                interveningIf = Condition.Not(thisTurn(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You)))),
            activated(cost(mana("{4}{G}")), counters(Objects.Each(and(creature, you, ObjectFilter.HasCounter(p1p1, 1))))),
        )

    // CR 604.3, 107.3m
    @Test
    fun plimboFindsThePath() = card(
        name = "Plimbo, Finds the Path",
        cost = "{X}{G}{G}",
        type = "Legendary Creature — Homunculus Scout",
        pt = "*/2",
        text = """
            Domain — Plimbo's power is equal to the number of basic land types among lands you control.
            When Plimbo enters, search your library for up to X basic land cards with different names, reveal them, put them into your hand, then shuffle.
        """,
    ) parsesTo abilities(
            Ability.Static(listOf(Mechanic.SetPowerToughness(Value.CountDistinct(Objects.Each(and(land, you)), SharedQuality.BasicLandType), null)),
                affects = Affected.Objects(Objects.Self), characteristicDefining = true, abilityWord = "Domain"),
            triggered(entersSelf,
                Step.Do(Effect.Search(Players.You, and(ObjectFilter.Super(Supertype.Basic), land), Value.CastX(Objects.Self), reveal = true, zones = listOf(Zone.Library),
                    differentNames = true, bind = "found", searcher = Players.You)),
                move(Objects.Bound("found"), Zone.Hand),
                Step.Do(Effect.Shuffle(Players.You))),
        )

    // CR 601.2f
    @Test
    fun brontorTheUnhaltable() = card(
        name = "Brontor the Unhaltable",
        cost = "{8}{G}",
        type = "Legendary Creature — Elder Dinosaur",
        pt = "8/8",
        text = """
            This spell costs {X} less to cast, where X is the greatest power among creatures you control.
            Trample
            Other creatures you control have trample.
        """,
    ) parsesTo abilities(
            static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!,
                Value.Of(NumericProperty.Power, Objects.Each(creatureYouControl), Combine.Greatest)))),
            Keyword.Trample,
            static(Mechanic.GainAbilities(listOf(Keyword.Trample)), affects = Objects.Each(and(anotherObject, creature, you))),
        )

    // CR 111.4
    @Test
    fun weiLanfengNeverLonely() = card(
        name = "Wei Lanfeng, Never Lonely",
        cost = "{3}{G}",
        type = "Legendary Creature — Human Druid",
        pt = "2/2",
        text = """
            When Wei Lanfeng enters, create Bao, a legendary 3/3 green Dog creature token.
            At the beginning of your end step, untap all tokens you control.
        """,
    ) parsesTo abilities(
            triggered(entersSelf, Step.Do(Effect.CreateToken(TokenSpec.Defined(FaceDefinition("Bao", colorIndicator = listOf(Color.Green), supertypes = listOf(Supertype.Legendary),
                types = listOf(CardType.Creature), subtypes = listOf(Subtype("Dog")), power = PrintedNumber.Fixed(3), toughness = PrintedNumber.Fixed(3))), n(1), Players.You))),
            triggered(stepBegins(TurnStep.End, PlayerFilter.You), Step.Do(Effect.Untap(Objects.Each(and(token, you))))),
        )

    // CR 106.1
    @Test
    fun snibbetTheJunction() = card(
        name = "Snibbet, the Junction",
        cost = "{2}{G}",
        type = "Legendary Creature — Beast Noble",
        pt = "2/1",
        text = "{T}: Choose a color. Add one mana of that color for each different power among creatures you control.",
    ) parsesTo abilities(activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(
            Value.CountDistinct(Objects.Each(creatureYouControl), SharedQuality.Number(NumericProperty.Power)))), Players.You))))

    @Test
    fun tindraTheKeeper() = card(
        name = "Tindra, the Keeper",
        cost = "{1}{G}",
        type = "Legendary Creature — Elf Druid",
        pt = "3/2",
        text = """
            Lands you control have hexproof. (They can't be the targets of spells or abilities your opponents control.)
            {2}: Return target land card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
            static(Mechanic.GainAbilities(listOf(Keyword.Hexproof)), affects = Objects.Each(and(land, you))),
            activated(cost(mana("{2}")), move(target("card"), Zone.Hand), targets = listOf(slot("card", and(land, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))))),
        )

    // CR 118.12
    @Test
    fun ashaAetherHermit() = card(
        name = "Asha, Aether Hermit",
        cost = "{2}{G}",
        type = "Legendary Creature — Human Druid",
        pt = "2/2",
        text = "When Asha enters, you may discard a card. If you do, search your library for an enchantment card, reveal it, put it into your hand, then shuffle.",
    ) parsesTo abilities(triggered(entersSelf, Step.Pay(cost(discardACard), payer = Players.You, bind = "paid"), Step.If(Condition.Did("paid"), then = listOf(
            Step.Do(Effect.Search(Players.You, ObjectFilter.Type(CardType.Enchantment), n(1), reveal = true, zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
            move(Objects.Bound("found"), Zone.Hand),
            Step.Do(Effect.Shuffle(Players.You))))))

    // CR 702.11
    @Test
    fun gorrumVaxSpellsmasher() = card(
        name = "Gorrum Vax, Spellsmasher",
        cost = "{5}{G}{G}",
        type = "Legendary Creature — Ogre Warrior",
        pt = "7/7",
        text = """
            This spell can't be countered.
            Reach, vigilance, trample
            Gorrum Vax has hexproof as long as they haven't dealt combat damage yet. (They can't be the target of spells or abilities your opponents control.)
        """,
    ) parsesTo abilities(
            static(Mechanic.CantBeCountered),
            Keyword.Reach,
            Keyword.Vigilance,
            Keyword.Trample,
            static(Mechanic.GainAbilities(listOf(Keyword.Hexproof)), condition = Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasDealtCombatDamage))),
        )

    // CR 701.9
    @Test
    fun colossbonesLoomingHeart() = card(
        name = "Colossbones, Looming Heart",
        cost = "{3}{G}",
        type = "Legendary Creature — Skeleton Druid",
        pt = "4/3",
        text = """
            Reach
            Whenever you gain life, put two +1/+1 counters on Colossbones.
            When you discard this card, you gain 3 life.
        """,
    ) parsesTo abilities(
            Keyword.Reach,
            triggered(youGainLife, counters(Objects.Self, 2)),
            triggered(on(EventPattern.Discard(ObjectFilter.Is(Objects.Self), PlayerFilter.You)), gainLife(3)),
        )

    // CR 701.14
    @Test
    fun kotaroScrappyMutt() = card(
        name = "Kotaro, Scrappy Mutt",
        cost = "{1}{G}",
        type = "Legendary Creature — Dog",
        pt = "1/1",
        text = """
            When Kotaro enters, another target creature you control fights up to one target creature an opponent controls. (Each deals damage equal to its power to the other.)
            {6}: Put a +1/+1 counter on target nonlegendary creature.
        """,
    ) parsesTo abilities(
            triggered(entersSelf, Step.Do(Effect.Fight(target("creature"), target("second creature"))),
                targets = listOf(slot("creature", and(anotherObject, creature, you)), slot("second creature", creatureOpponentControls, min = 0))),
            activated(cost(mana("{6}")), counters(target("creature")), targets = listOf(slot("creature", and(not(ObjectFilter.Super(Supertype.Legendary)), creature)))),
        )

    @Test
    fun corvinElderBloodsire() = card(
        name = "Corvin, Elder Bloodsire",
        cost = "{W}{B}",
        type = "Legendary Creature — Vampire Noble",
        pt = "2/3",
        text = """
            Whenever another creature or planeswalker you control dies, you gain 1 life.
            {2}, Sacrifice another creature or planeswalker: Put a +1/+1 counter on Corvin. He gains menace until end of turn. (He can't be blocked except by two or more creatures.)
        """,
    ) parsesTo abilities(
            triggered(on(EventPattern.ZoneChange(and(anotherObject, creatureOrPlaneswalker, you), from = Zone.Battlefield, to = Zone.Graveyard)), gainLife(1)),
            activated(cost(mana("{2}"), CostPart.Sacrifice(CostObjects.Choose(and(anotherObject, creatureOrPlaneswalker), n(1)))),
                counters(Objects.Self), gains(Objects.Self, Keyword.Menace)),
        )

    // CR 601.2c
    @Test
    fun nefrahTheDuneFang() = card(
        name = "Nefrah, the Dune Fang",
        cost = "{2}{B}{B}{G}",
        type = "Legendary Creature — Human Cleric",
        pt = "3/3",
        text = "When Nefrah enters, for each opponent, put X -1/-1 counters on up to one target creature that player controls, where X is the greatest mana value among cards in your graveyard.",
    ) parsesTo abilities(triggered(entersSelf, Step.Do(Effect.PutCounters(target("creature"), CounterKind.PowerToughness(-1, -1),
            Value.Of(NumericProperty.ManaValue, Objects.Each(and(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))), Combine.Greatest))),
            targets = listOf(TargetSlot("creature", TargetKind.Objects(and(creature, controlledBy(PlayerFilter.Is(Players.SlotPlayer)))), min = n(0), max = n(1),
                perPlayer = PlayerFilter.Opponent))))

    // CR 105.2
    @Test
    fun golvakGildedSentinel() = card(
        name = "Golvak, Gilded Sentinel",
        cost = "{2/W}{2/U}{2/B}{2/R}{2/G}",
        type = "Legendary Artifact Creature — Golem",
        pt = "5/5",
        text = """
            Vigilance, trample
            When Golvak enters, draw a card for each color among other artifacts you control.
        """,
    ) parsesTo abilities(Keyword.Vigilance, Keyword.Trample, triggered(entersSelf,
            Step.Do(Effect.Draw(Players.You, Value.CountDistinct(Objects.Each(and(anotherObject, artifact, you)), SharedQuality.Color)))))

    @Test
    fun pipwickValeHero() = card(
        name = "Pipwick, Vale Hero",
        cost = "{1}{R}{W}",
        type = "Legendary Creature — Mouse Soldier",
        pt = "1/2",
        text = "Whenever Pipwick or another creature you control enters, put a +1/+1 counter on target creature that entered this turn.",
    ) parsesTo abilities(triggered(on(EventPattern.ZoneChange(or(ObjectFilter.Is(Objects.Self), and(anotherObject, creature, you)), to = Zone.Battlefield)),
            counters(target("creature")),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, to = Zone.Battlefield)))))))

    @Test
    fun tamsinGemOfTheFoundry() = card(
        name = "Tamsin, Gem of the Foundry",
        cost = "{2}{U}{R}",
        type = "Legendary Creature — Human Artificer",
        pt = "2/4",
        text = """
            Thopters you control have haste.
            Whenever you cast a noncreature spell, create a 1/1 colorless Thopter artifact creature token with flying.
        """,
    ) parsesTo abilities(
            static(Mechanic.GainAbilities(listOf(Keyword.Haste)), affects = Objects.Each(and(sub("Thopter"), you))),
            triggered(youCast(not(creature)), Step.Do(Effect.CreateToken(creatureToken("Thopter", emptyList(), 1, 1, abilities = listOf(Keyword.Flying), types = listOf(CardType.Artifact, CardType.Creature)), n(1), Players.You))),
        )

    @Test
    fun gorgaHeartOfStone() = card(
        name = "Gorga, Heart of Stone",
        cost = "{U}{R}{W}",
        type = "Legendary Creature — Gorgon Wizard",
        pt = "3/3",
        text = """
            Artifact creatures you control have vigilance.
            Whenever you cast a noncreature spell, create a 1/1 colorless Sculpture Treasure artifact creature token with "{T}, Sacrifice this token: Add one mana of any color."
        """,
    ) parsesTo abilities(
            static(Mechanic.GainAbilities(listOf(Keyword.Vigilance)), affects = Objects.Each(and(artifact, creature, you))),
            triggered(youCast(not(creature)), Step.Do(Effect.CreateToken(creatureToken("Sculpture Treasure", emptyList(), 1, 1,
                abilities = listOf(activated(cost(tap, sacrificeSelf), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You)))),
                types = listOf(CardType.Artifact, CardType.Creature)), n(1), Players.You))),
        )

    // CR 603.4
    @Test
    fun gorgaTheSeveringGaze() = card(
        name = "Gorga, the Severing Gaze",
        cost = "{B}{B}{G}",
        type = "Legendary Creature — Gorgon Assassin",
        pt = "4/4",
        text = """
            Deathtouch
            When Gorga enters, if you control six or more lands, destroy target permanent an opponent controls. They create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
    ) parsesTo abilities(Keyword.Deathtouch, triggered(entersSelf,
            Step.Do(Effect.Destroy(target("permanent"))),
            Step.Do(Effect.CreateToken(TokenSpec.Predefined("Treasure"), n(1), Players.ControllerOf(target("permanent")))),
            targets = listOf(slot("permanent", and(permanentFilter, controlledBy(PlayerFilter.Opponent)))),
            interveningIf = Condition.Exists(and(land, you), 6)))

    // CR 603.2
    @Test
    fun golvakSilverBulwark() = card(
        name = "Golvak, Silver Bulwark",
        cost = "{2}",
        type = "Legendary Artifact Creature — Golem",
        pt = "1/3",
        text = "Artifacts and creatures entering the battlefield don't cause abilities to trigger.",
    ) parsesTo abilities(static(Mechanic.EventDoesntTrigger(EventPattern.ZoneChange(or(artifact, creature), to = Zone.Battlefield))))

    // CR 502.3
    @Test
    fun vortanUndyingScourge() = card(
        name = "Vortan, Undying Scourge",
        cost = "{4}",
        type = "Legendary Artifact Creature — Dragon Construct",
        pt = "5/4",
        text = """
            Trample
            Vortan doesn't untap during your untap step.
            Whenever you cast an artifact or creature spell, untap Vortan.
        """,
    ) parsesTo abilities(
            Keyword.Trample,
            static(Mechanic.DoesntUntap),
            triggered(youCast(or(artifact, creature)), Step.Do(Effect.Untap(Objects.Self))),
        )
}
