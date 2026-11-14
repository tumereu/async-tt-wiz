package arcanum.parser.sets.lci

import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** LCI sweep: siblings of the parser bugs LCI set work fixed. Invented names, real oracle wordings. */
class LciSweepTest {
    private val permanentSpell = or(artifact, ObjectFilter.Type(CardType.Battle), creature, enchantment, ObjectFilter.Type(CardType.Planeswalker))

    // ---- "permanent" beside "spell" or a zone other than the battlefield (lci chunks 01, 13: it was the battlefield category, which never
    // matches a spell or a card in a graveyard)

    // CR 110.4b: an opponent's permanent spell is an artifact, battle, creature, enchantment, or planeswalker spell
    @Test
    fun sweepSpireWatcher() = card(
        name = "Sweep Spire Watcher", cost = "{1}{U}", type = "Creature — Human Wizard", pt = "1/2",
        text = "Whenever an opponent casts a permanent spell, draw a card.",
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.Cast(permanentSpell, PlayerFilter.Opponent)), Step.Do(Effect.Draw(Players.You, Value.Fixed(1)))),
    )

    // CR 110.4b: "noncreature permanent spell"
    @Test
    fun sweepGearTinkerer() = card(
        name = "Sweep Gear Tinkerer", cost = "{1}{U}", type = "Creature — Human Artificer", pt = "1/1",
        text = "Whenever you cast a noncreature permanent spell, this creature gets +1/+1 until end of turn.",
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.Cast(and(not(creature), permanentSpell), PlayerFilter.You)),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(1))), arcanum.api.Affected.Objects(Objects.Self),
                arcanum.api.Duration.EndOfTurn))),
    )

    // CR 110.4b: a permanent spell on the Stack
    @Test
    fun sweepStoneDenial() = card(
        name = "Sweep Stone Denial", cost = "{1}{U}", type = "Instant",
        text = "Counter target noncreature permanent spell.",
    ) parsesTo abilities(
        spell(Step.Do(Effect.Counter(target("spell"))), targets = listOf(slot("spell", and(not(creature), permanentSpell, spellFilter)))),
    )

    // CR 110.4b, 601.3: permanent spells cast from the top of the library
    @Test
    fun sweepCrestSeer() = card(
        name = "Sweep Crest Seer", cost = "{2}{G}", type = "Creature — Elf Druid", pt = "2/2",
        text = "You may cast permanent spells from the top of your library.",
    ) parsesTo abilities(
        static(Mechanic.MayPlay(Zone.Library, PlayKind.Cast, topOnly = true, holder = PlayerFilter.You),
            affects = Objects.Each(and(permanentCard, inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))),
    )

    // CR 110.4a: a permanent card in a graveyard (artifact, battle, creature, enchantment, land, or planeswalker card)
    @Test
    fun sweepGraveDelver() = card(
        name = "Sweep Grave Delver", cost = "{2}{G}", type = "Creature — Human", pt = "2/2",
        text = "When this creature enters, mill three cards. Then return a permanent card from your graveyard to your hand.",
    ) parsesTo abilities(
        triggered(entersSelf, Step.Do(Effect.Mill(Players.You, Value.Fixed(3))),
            Step.Choose(Choice.Objects(inYourGraveyard(permanentCard), Value.Fixed(1), Value.Fixed(1)), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Move(Objects.Bound("chosen"), arcanum.api.Destination(Zone.Hand)))),
    )

    // ---- An ability word with a digit before a rule that reads ability words itself (lci chunk 04: "Descend 4 —" before "instead if")

    // CR 207.2c, 608.2c: "Descend 4 —" is an ability word; with four or more permanent cards in your graveyard you choose where the card goes
    @Test
    fun sweepDeepRootSeeking() = card(
        name = "Sweep Deep-Root Seeking", cost = "{G}", type = "Sorcery",
        text = """
            Search your library for a basic land card, reveal it, put it into your hand, then shuffle.
            Descend 4 — You may put that card onto the battlefield instead of putting it into your hand if there are four or more permanent cards in your graveyard.
        """,
    ) parsesTo abilities(
        spell(
            search(basicLand(), reveal = true),
            Step.If(arcanum.api.Condition.Exists(and(permanentCard, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)), atLeast = 4),
                then = listOf(
                    Step.Pay(steps = listOf(move(Objects.Bound("found"), Zone.Battlefield)), payer = Players.You, bind = "put"),
                    Step.If(arcanum.api.Condition.Not(arcanum.api.Condition.Did("put")), then = listOf(move(Objects.Bound("found"), Zone.Hand))),
                ),
                otherwise = listOf(move(Objects.Bound("found"), Zone.Hand)),
            ),
            shuffle,
        ).copy(abilityWord = "Descend 4"),
    )

    // ---- "its" in a trigger whose event object isn't this object (lci chunk 03: Tribute to the World Tree read its own power)

    // CR 603.10a, 608.2h: "its power" is the creature that died, as it last existed, not this enchantment
    @Test
    fun sweepCarrionTithe() = card(
        name = "Sweep Carrion Tithe", cost = "{1}{B}", type = "Enchantment",
        text = "Whenever a creature you control dies, you gain life equal to its power.",
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.ZoneChange(creatureYouControl, from = Zone.Battlefield, to = Zone.Graveyard)),
            Step.Do(Effect.GainLife(Players.You, Value.Of(NumericProperty.Power, Objects.EventObject(EventRole.Before), Combine.Single)))),
    )

    // CR 603.2: "its power" is the creature that entered
    @Test
    fun sweepWelcomingGrove() = card(
        name = "Sweep Welcoming Grove", cost = "{1}{G}", type = "Enchantment",
        text = "Whenever another creature you control enters, you gain life equal to its power.",
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.ZoneChange(and(another, creatureYouControl), to = Zone.Battlefield)),
            Step.Do(Effect.GainLife(Players.You, Value.Of(NumericProperty.Power, Objects.EventObject(EventRole.After), Combine.Single)))),
    )

    // CR 603.10a, 608.2h: "When enchanted creature dies": "its" is the creature that died, not this Aura
    @Test
    fun sweepMourningBond() = card(
        name = "Sweep Mourning Bond", cost = "{1}{B}", type = "Enchantment — Aura",
        text = "Enchant creature\nWhen enchanted creature dies, you gain life equal to its power.",
    ) parsesTo abilities(
        arcanum.api.Keyword.Enchant(arcanum.api.TargetKind.Objects(creature)),
        triggered(Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.AttachedTo(Objects.Self)), from = Zone.Battlefield, to = Zone.Graveyard)),
            Step.Do(Effect.GainLife(Players.You, Value.Of(NumericProperty.Power, Objects.EventObject(EventRole.Before), Combine.Single)))),
    )

    // CR 603.10a, 608.2h: an X "where X is its power" defines is the creature that died too
    @Test
    fun sweepGraveTally() = card(
        name = "Sweep Grave Tally", cost = "{2}{B}", type = "Enchantment",
        text = "Whenever another creature you control dies, draw X cards, where X is its power.",
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.ZoneChange(and(another, creatureYouControl), from = Zone.Battlefield, to = Zone.Graveyard)),
            Step.Do(Effect.Draw(Players.You, Value.Of(NumericProperty.Power, Objects.EventObject(EventRole.Before), Combine.Single)))),
    )

    // ---- A number of an object the same effect moved from the battlefield (CR 608.2h: as it last existed there, not the exiled card)

    @Test
    fun sweepDawnBanishing() = card(
        name = "Sweep Dawn Banishing", cost = "{W}", type = "Instant",
        text = "Exile target creature you control. You gain life equal to its toughness.",
    ) parsesTo abilities(
        spell(move(target("creature"), Zone.Exile, postBind = "exiled"),
            Step.Do(Effect.GainLife(Players.You, Value.Of(NumericProperty.Toughness, target("creature"), Combine.Single))),
            targets = listOf(slot("creature", creatureYouControl))),
    )
}
