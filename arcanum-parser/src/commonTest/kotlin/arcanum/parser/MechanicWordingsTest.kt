package arcanum.parser

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Condition
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.KeywordAction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.ObjectMark
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.drawCards
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test
import kotlin.test.assertEquals

/** Wordings of mechanics that used to be done by hand ([MechanicWordings]), one test per wording, on invented cards. */
class MechanicWordingsTest {
    private val tempts = Step.Do(Effect.Action(KeywordAction.TheRingTemptsYou))
    private val yourRingBearer = ObjectFilter.And(listOf(ObjectFilter.HasStatus(Status.Marked(ObjectMark.RingBearer)),
        ObjectFilter.ControlledBy(PlayerFilter.You)))

    // CR 701.54a: "the Ring tempts you" as an effect of a trigger
    @Test
    fun ringTemptsYouAsItEnters() = card(
        name = "Barrow Lurker",
        cost = "{1}{B}",
        type = "Creature — Wraith",
        pt = "2/1",
        text = "When this creature enters, the Ring tempts you.",
    ) parsesTo abilities(whenThisEnters(tempts))

    // CR 701.54a: "The Ring tempts you." as a spell's last sentence
    @Test
    fun ringTemptsYouAfterASpell() = card(
        name = "Shadow's Reach",
        cost = "{2}{B}",
        type = "Instant",
        text = "Destroy target creature. The Ring tempts you.",
    ) parsesTo abilities(Ability.Spell(Instructions(
        targets = listOf(TargetSlot("creature", TargetKind.Objects(ObjectFilter.Type(CardType.Creature)), Value.Fixed(1), Value.Fixed(1))),
        steps = listOf(Step.Do(Effect.Destroy(Objects.Target("creature"))), tempts))))

    // CR 701.54d: "Whenever the Ring tempts you, if you chose a creature other than [this] as your Ring-bearer, …"
    @Test
    fun wheneverTheRingTemptsYouAnotherBearer() = card(
        name = "Grey Wanderer",
        cost = "{2}{U}",
        type = "Legendary Creature — Avatar Wizard",
        pt = "2/3",
        text = "Whenever the Ring tempts you, if you chose a creature other than Grey Wanderer as your Ring-bearer, draw a card.",
    ) parsesTo abilities(Ability.Triggered(Trigger.On(EventPattern.RingTempts(PlayerFilter.You)), Instructions(steps = listOf(drawCards(1))),
        interveningIf = Condition.ObjectMatches(Objects.EventObject(EventRole.Object), ObjectFilter.Not(ObjectFilter.Is(Objects.Self)))))

    // CR 701.54e: "if you don't control a Ring-bearer"
    @Test
    fun temptsIfNoRingBearer() = card(
        name = "Shire Wayfarer",
        cost = "{G}",
        type = "Creature — Halfling Scout",
        pt = "1/1",
        text = "Whenever a land you control enters, if you don't control a Ring-bearer, the Ring tempts you.",
    ) parsesTo abilities(Ability.Triggered(Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Land),
        ObjectFilter.ControlledBy(PlayerFilter.You))), to = Zone.Battlefield)), Instructions(steps = listOf(tempts)),
        interveningIf = Condition.Not(Condition.Exists(yourRingBearer, 1))))

    // CR 701.54e: "As long as [this] is your Ring-bearer, …"
    @Test
    fun asLongAsItIsYourRingBearer() = card(
        name = "Bold Ringward",
        cost = "{1}{R}",
        type = "Legendary Creature — Human Soldier",
        pt = "3/2",
        text = "As long as Bold Ringward is your Ring-bearer, it gets +2/+0.",
    ) parsesTo abilities(Ability.Static(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(0))), Affected.Objects(Objects.Self),
        condition = Condition.ObjectMatches(Objects.Self, yourRingBearer)))

    // ---- Soulbond (CR 702.95)

    private val pair = Affected.Objects(Objects.Union(listOf(Objects.Self, Objects.PairedWith(Objects.Self))))
    private val paired = Condition.ObjectMatches(Objects.Self, ObjectFilter.Paired)

    // CR 702.95b: "both creatures have [keywords]"
    @Test
    fun pairedBothHaveKeywords() = card(
        name = "Bonded Skystalker",
        cost = "{2}{W}",
        type = "Creature — Human Soldier",
        pt = "2/2",
        text = """
            Soulbond
            As long as this creature is paired with another creature, both creatures have flying and lifelink.
        """,
    ) parsesTo abilities(arcanum.api.Keyword.Soulbond,
        Ability.Static(listOf(Mechanic.GainAbilities(listOf(arcanum.api.Keyword.Flying, arcanum.api.Keyword.Lifelink))), pair, condition = paired))

    // CR 702.95b: "each of those creatures gets +N/+N"
    @Test
    fun pairedEachGets() = card(
        name = "Bonded Grovebrute",
        cost = "{3}{G}",
        type = "Creature — Beast",
        pt = "3/3",
        text = """
            Soulbond
            As long as Bonded Grovebrute is paired with another creature, each of those creatures gets +2/+2.
        """,
    ) parsesTo abilities(arcanum.api.Keyword.Soulbond,
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(2))), pair, condition = paired))

    // CR 702.95b: "each of those creatures has "[ability]""
    @Test
    fun pairedEachHasAnAbility() = card(
        name = "Bonded Tinkerer",
        cost = "{1}{U}",
        type = "Creature — Human Wizard",
        pt = "1/2",
        text = """
            Soulbond
            As long as this creature is paired with another creature, each of those creatures has "{2}{U}: Untap this creature."
        """,
    ) parsesTo abilities(arcanum.api.Keyword.Soulbond,
        Ability.Static(listOf(Mechanic.GainAbilities(listOf(Ability.Activated(arcanum.api.Cost.mana(arcanum.api.ManaCost.parse("{2}{U}")!!),
            Instructions(steps = listOf(Step.Do(Effect.Untap(Objects.Self)))), arcanum.api.ActivationRules(arcanum.api.Timing.Any, PlayerFilter.You)
            )))), pair, condition = paired))

    // ---- Spree, tiered, partner with, bargain, tribute, disturb

    private fun mana(text: String) = arcanum.api.Cost.mana(arcanum.api.ManaCost.parse(text)!!)
    private val counterTarget = Instructions(targets = listOf(TargetSlot("spell", TargetKind.Objects(ObjectFilter.Category(arcanum.api.ObjectCategory.Spell)),
        Value.Fixed(1), Value.Fixed(1))), steps = listOf(Step.Do(Effect.Counter(Objects.Target("spell")))))

    // CR 702.172a: "+ [cost] — [effect]" modes, one or more, each cost an additional cost when chosen
    @Test
    fun spreeModes() = card(
        name = "Quick Getaway",
        cost = "{U}",
        type = "Instant",
        text = """
            Spree (Choose one or more additional costs.)
            + {1}{U} — Counter target spell.
            + {2} — Draw two cards, then discard a card.
        """,
    ) parsesTo abilities(arcanum.api.Keyword.Spree, Ability.Spell(Instructions(modes = arcanum.api.Modes(listOf(
        arcanum.api.Mode(counterTarget, extraCost = mana("{1}{U}")),
        arcanum.api.Mode(Instructions(steps = listOf(drawCards(2),
            Step.Do(Effect.Discard(arcanum.api.Players.You, Value.Fixed(1), ObjectFilter.Any)))), extraCost = mana("{2}"))),
        min = 1, max = 2, chooser = arcanum.api.Players.You))))

    // CR 702.183a: tiered chooses exactly one
    @Test
    fun tieredModes() = card(
        name = "Graded Insight",
        cost = "{U}",
        type = "Sorcery",
        text = """
            Tiered (Choose one additional cost.)
            + {0} — Draw a card.
            + {2} — Draw two cards.
        """,
    ) parsesTo abilities(arcanum.api.Keyword.Tiered, Ability.Spell(Instructions(modes = arcanum.api.Modes(listOf(
        arcanum.api.Mode(Instructions(steps = listOf(drawCards(1))), extraCost = mana("{0}")),
        arcanum.api.Mode(Instructions(steps = listOf(drawCards(2))), extraCost = mana("{2}"))),
        min = 1, max = 1, chooser = arcanum.api.Players.You))))

    // CR 702.124j: "Partner with [name]", the name with a comma
    @Test
    fun partnerWith() = card(
        name = "Ledger Twin",
        cost = "{2}{U}",
        type = "Legendary Creature — Human Wizard",
        pt = "2/2",
        text = "Partner with Okto, Eye of Ledgers (When this creature enters, target player may put Okto into their hand from their library, then shuffle.)",
    ) parsesTo abilities(arcanum.api.Keyword.PartnerWith("Okto, Eye of Ledgers"))

    // CR 702.166b: "If this spell was bargained"
    @Test
    fun ifBargained() = card(
        name = "Bargained Vigor",
        cost = "{1}{G}",
        type = "Instant",
        text = """
            Bargain (You may sacrifice an artifact, enchantment, or token as you cast this spell.)
            Draw a card. If this spell was bargained, draw two cards.
        """,
    ) parsesTo abilities(arcanum.api.Keyword.Bargain, Ability.Spell(Instructions(steps = listOf(drawCards(1),
        Step.If(Condition.Paid(arcanum.api.Keyword.Bargain, times = 1, of = Objects.Self), listOf(drawCards(2)))))))

    // CR 702.166b: a permanent's "if it was bargained"
    @Test
    fun ifItWasBargained() = card(
        name = "Bargained Courtier",
        cost = "{2}{B}",
        type = "Creature — Human Noble",
        pt = "2/2",
        text = """
            Bargain (You may sacrifice an artifact, enchantment, or token as you cast this spell.)
            When this creature enters, if it was bargained, draw a card.
        """,
    ) parsesTo abilities(arcanum.api.Keyword.Bargain, whenThisEnters(drawCards(1),
        interveningIf = Condition.Paid(arcanum.api.Keyword.Bargain, times = 1, of = Objects.Self)))

    // CR 702.104b: "if tribute wasn't paid"
    @Test
    fun tributeWasntPaid() = card(
        name = "Tithe Brute",
        cost = "{3}{R}",
        type = "Creature — Minotaur",
        pt = "3/3",
        text = """
            Tribute 2 (As this creature enters, an opponent of your choice may put two +1/+1 counters on it.)
            When this creature enters, if tribute wasn't paid, you gain 4 life.
        """,
    ) parsesTo abilities(arcanum.api.Keyword.Tribute(2), whenThisEnters(Step.Do(Effect.GainLife(arcanum.api.Players.You, Value.Fixed(4))),
        interveningIf = Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasStatus(Status.Marked(arcanum.api.KeywordExpansions.TRIBUTE_PAID))))))

    // CR 702.146b, 614.1a: a disturb back face's "If [this] would be put into a graveyard from anywhere, exile it instead."
    @Test
    fun exileItInstead() = card(
        name = "Lingering Shade",
        cost = null,
        type = "Creature — Spirit",
        pt = "2/2",
        colorIndicator = "W",
        text = """
            Flying
            If Lingering Shade would be put into a graveyard from anywhere, exile it instead.
        """,
    ) parsesTo abilities(arcanum.api.Keyword.Flying, Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self),
        to = Zone.Graveyard), modify = listOf(arcanum.api.Modification.ChangeDestination(arcanum.api.Destination(Zone.Exile))))), Affected.Objects(Objects.Self)))

    // ---- Haunt (CR 702.55)

    private val hauntedDies = EventPattern.ZoneChange(ObjectFilter.Is(Objects.LinkedTo(arcanum.api.Keyword.Haunt)), from = Zone.Battlefield, to = Zone.Graveyard)

    // CR 702.55b, c: "When this creature enters or the creature it haunts dies" works on the battlefield and in exile
    @Test
    fun entersOrHauntedDies() = card(
        name = "Gloom Specter",
        cost = "{3}{B}",
        type = "Creature — Spirit",
        pt = "2/2",
        text = """
            Haunt (When this creature dies, exile it haunting target creature.)
            When this creature enters or the creature it haunts dies, target player loses 2 life and you gain 2 life.
        """,
    ) parsesTo abilities(arcanum.api.Keyword.Haunt, Ability.Triggered(Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self),
        to = Zone.Battlefield), hauntedDies)), Instructions(
        targets = listOf(TargetSlot("player", TargetKind.Players(PlayerFilter.Any), Value.Fixed(1), Value.Fixed(1))),
        steps = listOf(Step.Do(Effect.LoseLife(arcanum.api.Players.Target("player"), Value.Fixed(2))),
            Step.Do(Effect.GainLife(arcanum.api.Players.You, Value.Fixed(2))))), functionsIn = listOf(Zone.Battlefield, Zone.Exile)))

    // CR 702.55b, c: a haunt spell's "When the creature this card haunts dies" works in exile only
    @Test
    fun hauntedCreatureDiesSpell() = card(
        name = "Grim Haunting",
        cost = "{1}{B}",
        type = "Sorcery",
        text = """
            Target player discards a card.
            Haunt (When this spell card is put into a graveyard after resolving, exile it haunting target creature.)
            When the creature this card haunts dies, target player discards a card.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(targets = listOf(TargetSlot("player", TargetKind.Players(PlayerFilter.Any), Value.Fixed(1), Value.Fixed(1))),
            steps = listOf(Step.Do(Effect.Discard(arcanum.api.Players.Target("player"), Value.Fixed(1), ObjectFilter.Any))))),
        arcanum.api.Keyword.Haunt,
        Ability.Triggered(Trigger.On(hauntedDies), Instructions(
            targets = listOf(TargetSlot("player", TargetKind.Players(PlayerFilter.Any), Value.Fixed(1), Value.Fixed(1))),
            steps = listOf(Step.Do(Effect.Discard(arcanum.api.Players.Target("player"), Value.Fixed(1), ObjectFilter.Any)))),
            functionsIn = listOf(Zone.Exile)))

    // CR 702.140a, d: mutate is a keyword with a cost; "Whenever this creature mutates" is its own event
    @Test
    fun wheneverThisCreatureMutates() = card(
        name = "Gnarled Mutant",
        cost = "{3}{G}",
        type = "Creature — Beast",
        pt = "4/4",
        text = """
            Mutate {1}{G}{G}
            Whenever this creature mutates, draw a card.
        """.trimIndent(),
    ) parsesTo abilities(
        arcanum.api.Keyword.Mutate(arcanum.parser.cube.manaCost("{1}{G}{G}")),
        Ability.Triggered(Trigger.On(EventPattern.Mutates(ObjectFilter.Is(Objects.Self))), Instructions(steps = listOf(drawCards(1)))),
    )

    // CR 701.49a: "venture into the dungeon"
    @Test
    fun ventureIntoTheDungeon() = card(
        name = "Torch Delver",
        cost = "{1}{W}",
        type = "Creature — Human Rogue",
        pt = "2/1",
        text = "When this creature enters, venture into the dungeon.",
    ) parsesTo abilities(whenThisEnters(Step.Do(Effect.Action(KeywordAction.Venture))))

    // CR 726.1, 726.3: "you take the initiative"
    @Test
    fun takeTheInitiative() = card(
        name = "Gate Warden",
        cost = "{2}{W}",
        type = "Creature — Human Soldier",
        pt = "2/3",
        text = "When this creature enters, you take the initiative.",
    ) parsesTo abilities(whenThisEnters(Step.Do(Effect.GiveDesignation(arcanum.api.Players.You, arcanum.api.Designation.Initiative))))

    // CR 309.7: "Whenever you complete a dungeon" and "as long as you've completed a dungeon"
    @Test
    fun completeADungeon() = card(
        name = "Vault Cartographer",
        cost = "{2}{U}",
        type = "Creature — Human Wizard",
        pt = "1/3",
        text = "Whenever you complete a dungeon, draw a card.",
    ) parsesTo abilities(Ability.Triggered(Trigger.On(EventPattern.CompletesDungeon(PlayerFilter.You)), Instructions(steps = listOf(drawCards(1)))))

    // CR 309.4: a dungeon card's rooms, their effects and their arrows
    @Test
    fun dungeonRooms() {
        val face = OracleParser.parse(OracleCard("Test Crypt", null, "Dungeon — Undercity", """
            Crypt Door — Scry 1. (→ Bone Hall or Dark Well)
            Bone Hall — Draw a card. (→ Final Vault)
            Dark Well — Draw a card. (→ Final Vault)
            Final Vault — Draw a card.
        """.trimIndent()))
        kotlin.test.assertEquals(listOf(arcanum.api.CardType.Dungeon), face.types)
        kotlin.test.assertEquals(listOf(arcanum.api.Subtype("Undercity")), face.subtypes)
        kotlin.test.assertEquals(listOf("Crypt Door", "Bone Hall", "Dark Well", "Final Vault"), face.rooms.map { it.name })
        kotlin.test.assertEquals(listOf(listOf(1, 2), listOf(3), listOf(3), emptyList()), face.rooms.map { it.next })
        kotlin.test.assertEquals(Instructions(steps = listOf(drawCards(1))), face.rooms[3].instructions)
    }

    // CR 309.4, 701.49d: the rooms' arrows as card data writes them ("(Leads to: [room], [room])"), and a dungeon only "venture into
    // Undercity" may enter
    @Test
    fun dungeonRoomsAsCardDataWritesThem() {
        val face = OracleParser.parse(OracleCard("Test Sunken City", null, "Dungeon — Undercity", """
            You can't enter this dungeon unless you "venture into Undercity."
            Flooded Stair — Scry 1. (Leads to: Coral Hall, Salt Vault)
            Coral Hall — Draw a card. (Leads to: Abyss Throne)
            Salt Vault — Draw a card. (Leads to: Abyss Throne)
            Abyss Throne — Draw a card.
        """.trimIndent()))
        kotlin.test.assertEquals(listOf("Flooded Stair", "Coral Hall", "Salt Vault", "Abyss Throne"), face.rooms.map { it.name })
        kotlin.test.assertEquals(listOf(listOf(1, 2), listOf(3), listOf(3), emptyList()), face.rooms.map { it.next })
        kotlin.test.assertEquals(listOf(Ability.Static(listOf(Mechanic.OnlyVentureInto(ObjectFilter.Sub(arcanum.api.SubtypeRef.Literal(
            arcanum.api.Subtype("Undercity"))))), Affected.Objects(Objects.Self), functionsIn = listOf(Zone.Outside),
            text = "You can't enter this dungeon unless you \"venture into Undercity.\"")), face.abilities)
    }

    // CR 702.55c: a modal "When this creature enters or the creature it haunts dies, choose one —" works on the battlefield and in exile
    @Test
    fun modalHauntTrigger() {
        val face = OracleParser.parse(arcanum.parser.OracleCard("Gloom Arbiter", "{2}{W}{B}", "Creature — Spirit Cleric", """
            Haunt
            When this creature enters or the creature it haunts dies, choose one —
            • Creatures you control get +1/+1 until end of turn.
            • Creatures you don't control get -1/-1 until end of turn.
        """.trimIndent(), "2/2"))
        val trigger = face.abilities.filterIsInstance<Ability.Triggered>().single()
        kotlin.test.assertEquals(listOf(Zone.Battlefield, Zone.Exile), trigger.functionsIn)
        kotlin.test.assertEquals(2, trigger.instructions.modes?.options?.size)
    }

    // CR 701.54a, d: "Whenever you choose a creature as your Ring-bearer": the Ring tempting you with a creature chosen, "that creature"
    // its object
    @Test
    fun wheneverYouChooseARingBearer() = card(
        name = "Barrow Herald",
        cost = "{1}{W}",
        type = "Creature — Human Soldier",
        pt = "2/2",
        text = "Whenever you choose a creature as your Ring-bearer, put a +1/+1 counter on that creature.",
    ) parsesTo abilities(Ability.Triggered(Trigger.On(EventPattern.RingTempts(PlayerFilter.You, chosen = true)), Instructions(steps = listOf(
        Step.Do(Effect.PutCounters(Objects.EventObject(EventRole.Object), arcanum.api.CounterKind.PowerToughness(1, 1), Value.Fixed(1)))))))

    // CR 702.37f, 702.168e: X in "When this creature is turned face up" is the X of the morph or disguise cost it was turned up for
    @Test
    fun xWhenTurnedFaceUpIsTheCostsX() = card(
        name = "Veiled Tally Witness",
        cost = "{3}{W}",
        type = "Creature — Human Detective",
        pt = "3/4",
        text = """
            Disguise {X}{3}{W}
            When this creature is turned face up, you gain X life.
        """.trimIndent(),
    ) parsesTo abilities(
        arcanum.api.Keyword.Disguise(arcanum.api.Cost.mana(arcanum.api.ManaCost.parse("{X}{3}{W}")!!)),
        Ability.Triggered(Trigger.On(EventPattern.TurnedFaceUp(ObjectFilter.Is(Objects.Self))), Instructions(steps = listOf(
            Step.Do(Effect.GainLife(arcanum.api.Players.You, Value.EventX))))),
    )

    // CR 115.1, 601.2c: "for each isolation counter on it, exile up to one target …" is one target per counter, each exiled once
    @Test
    fun oneTargetForEachCounterOnIt() {
        val face = OracleParser.parse(OracleCard("Sealing Ward", "{X}{X}{W}{W}", "Enchantment", """
            This enchantment enters with X isolation counters on it.
            When this enchantment enters, for each isolation counter on it, exile up to one target nonland permanent an opponent controls until this enchantment leaves the battlefield.
        """.trimIndent()))
        val trigger = face.abilities.filterIsInstance<Ability.Triggered>().single()
        val slot = trigger.instructions.targets.single()
        assertEquals(Value.Fixed(0), slot.min)
        assertEquals(Value.Of(arcanum.api.NumericProperty.Counters(arcanum.api.CounterKind.Named("isolation")),
            Objects.EventObject(EventRole.After), arcanum.api.Combine.Single), slot.max)
        assertEquals(listOf(Step.Do(Effect.Move(Objects.Target(slot.name), arcanum.api.Destination(Zone.Exile), untilLeaves = Objects.Self))),
            trigger.instructions.steps)
    }

    // CR 701.54e: "your Ring-bearer" as the affected object of a static ability, a trigger's subject and an effect's object
    @Test
    fun yourRingBearerAsAnObject() = card(
        name = "Shire Banner",
        cost = "{2}",
        type = "Artifact",
        text = """
            Your Ring-bearer gets +1/+1.
            Whenever your Ring-bearer attacks, draw a card.
            At the beginning of your end step, put a +1/+1 counter on your Ring-bearer.
        """.trimIndent(),
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(1))), affects = Affected.Objects(Objects.Each(yourRingBearer))),
        Ability.Triggered(Trigger.On(EventPattern.Attack(yourRingBearer)), Instructions(steps = listOf(drawCards(1)))),
        Ability.Triggered(Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.End, PlayerFilter.You)), Instructions(steps = listOf(
            Step.Do(Effect.PutCounters(Objects.Each(yourRingBearer), arcanum.api.CounterKind.PowerToughness(1, 1), Value.Fixed(1)))))),
    )
}
