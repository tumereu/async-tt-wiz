package arcanum.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

/**
 * Invented cards with real oracle wording, written in the definition model, to show how common card text is expressed
 * and that whole definitions survive JSON unchanged. Names and costs are ours.
 */
class ExampleCardsTest {
    private val json = Json { prettyPrint = false }
    private fun mana(text: String) = ManaCost.parse(text)!!
    private fun n(value: Int) = Value.Fixed(value)
    private fun creature(name: String, cost: String, power: Int, toughness: Int, vararg abilities: Ability) = FaceDefinition(
        name, mana(cost), types = listOf(CardType.Creature), power = PrintedNumber.Fixed(power),
        toughness = PrintedNumber.Fixed(toughness), abilities = abilities.toList())
    private val creatures = ObjectFilter.Type(CardType.Creature)
    private val youControl = ObjectFilter.ControlledBy(PlayerFilter.You)
    private fun all(vararg filters: ObjectFilter) = ObjectFilter.And(filters.toList())
    private val entersSelf = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield))

    /** "Sparkbolt deals 3 damage to any target." */
    private val sparkbolt = FaceDefinition("Sparkbolt", mana("{R}"), types = listOf(CardType.Instant), abilities = listOf(
        Ability.Spell(Instructions(
            targets = listOf(TargetSlot("t", TargetKind.Either(
                ObjectFilter.Or(listOf(creatures, ObjectFilter.Type(CardType.Planeswalker), ObjectFilter.Type(CardType.Battle))),
                PlayerFilter.Any))),
            steps = listOf(Step.Do(Effect.DealDamage(Recipients(Objects.Target("t"), Players.Target("t")), n(3))))))))

    /** "Other artifact creatures you control get +1/+1." */
    private val banner = FaceDefinition("Brightforge Banner", mana("{2}"), types = listOf(CardType.Artifact), abilities = listOf(
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(n(1), n(1))),
            Affected.Objects(Objects.Each(all(creatures, ObjectFilter.Type(CardType.Artifact), youControl,
                ObjectFilter.Not(ObjectFilter.Is(Objects.Self))))))))

    /** "Target creature gets +3/+3 until end of turn." */
    private val surge = FaceDefinition("Surge of Vigor", mana("{G}"), types = listOf(CardType.Instant), abilities = listOf(
        Ability.Spell(Instructions(
            targets = listOf(TargetSlot("c", TargetKind.Objects(creatures))),
            steps = listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(n(3), n(3))),
                Affected.Objects(Objects.Target("c")), Duration.EndOfTurn)))))))

    /** Flying / "When this creature enters, draw a card." */
    private val scout = creature("Thornveil Scout", "{2}{U}", 1, 1, Keyword.Flying,
        Ability.Triggered(entersSelf, Instructions(steps = listOf(Step.Do(Effect.Draw(Players.You))))))

    /** "{T}: Add {G}." */
    private val elf = creature("Mossgrown Elf", "{G}", 1, 1,
        Ability.Activated(Cost(listOf(CostPart.TapSelf)), Instructions(steps = listOf(
            Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(mana("{G}")))))))))

    /** "This land enters tapped." / "{T}: Add {G} or {W}." */
    private val grove = FaceDefinition("Quiet Grove", types = listOf(CardType.Land), abilities = listOf(
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersTapped)))),
        Ability.Activated(Cost(listOf(CostPart.TapSelf)), Instructions(steps = listOf(Step.Do(Effect.AddMana(
            ManaProduction(ManaKind.Choose(n(1), listOf(Color.Green, Color.White))))))))))

    /** "When this enchantment enters, exile target nonland permanent an opponent controls until this enchantment
     * leaves the battlefield." */
    private val prison = FaceDefinition("Warden's Prison", mana("{1}{W}"), types = listOf(CardType.Enchantment), abilities = listOf(
        Ability.Triggered(entersSelf, Instructions(
            targets = listOf(TargetSlot("p", TargetKind.Objects(all(ObjectFilter.Category(ObjectCategory.Permanent),
                ObjectFilter.Not(ObjectFilter.Type(CardType.Land)), ObjectFilter.ControlledBy(PlayerFilter.Opponent))))),
            // CR 610.3: one effect that returns the card the moment this enchantment leaves, without a trigger
            steps = listOf(Step.Do(Effect.Move(Objects.Target("p"), Destination(Zone.Exile), untilLeaves = Objects.Self)))))))

    /** "Choose one — • Counter target noncreature spell unless its controller pays {2}. • ~ deals 2 damage to target
     * creature. • Draw two cards, then discard two cards." */
    private val charm = FaceDefinition("Splitmind Charm", mana("{U}{R}"), types = listOf(CardType.Instant), abilities = listOf(
        Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(
                targets = listOf(TargetSlot("s", TargetKind.Objects(all(ObjectFilter.Category(ObjectCategory.Spell),
                    ObjectFilter.Not(creatures))))),
                steps = listOf(Step.Unless(Players.ControllerOf(Objects.Target("s")), Cost.mana(mana("{2}")),
                    listOf(Step.Do(Effect.Counter(Objects.Target("s")))))))),
            Mode(Instructions(
                targets = listOf(TargetSlot("c", TargetKind.Objects(creatures))),
                steps = listOf(Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("c")), n(2)))))),
            Mode(Instructions(steps = listOf(Step.Do(Effect.Draw(Players.You, n(2))),
                Step.Do(Effect.Discard(Players.You, n(2))))))))))))

    /** "If this creature would die, exile it instead." */
    private val gravewalker = creature("Restless Gravewalker", "{1}{B}", 2, 2,
        Ability.Static(listOf(Mechanic.Replacement(
            EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile)))))))

    /** Saga: "I, II — Create a 1/1 white Soldier creature token. III — Creatures you control get +1/+1 until end of
     * turn." */
    private val saga = FaceDefinition("Tale of the Watch", mana("{2}{W}"), types = listOf(CardType.Enchantment),
        subtypes = listOf(Subtype("Saga")), abilities = listOf(
            Ability.Triggered(Trigger.Chapter(listOf(1, 2)), Instructions(steps = listOf(Step.Do(Effect.CreateToken(
                TokenSpec.Defined(FaceDefinition("Soldier Token", colorIndicator = listOf(Color.White), types = listOf(CardType.Creature),
                    subtypes = listOf(Subtype("Soldier")), power = PrintedNumber.Fixed(1), toughness = PrintedNumber.Fixed(1)))))))),
            Ability.Triggered(Trigger.Chapter(listOf(3)), Instructions(steps = listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.ModifyPowerToughness(n(1), n(1))), Affected.Objects(Objects.Each(all(creatures, youControl))),
                Duration.EndOfTurn)))))))

    /** Planeswalker: "+1: Draw a card." / "−3: ~ deals 3 damage to target creature." */
    private val walker = FaceDefinition("Velka, Storm Herald", mana("{2}{R}{R}"), supertypes = listOf(Supertype.Legendary),
        types = listOf(CardType.Planeswalker), subtypes = listOf(Subtype("Velka")), loyalty = PrintedNumber.Fixed(4),
        abilities = listOf(
            Ability.Activated(Cost(listOf(CostPart.Loyalty(n(1)))), Instructions(steps = listOf(Step.Do(Effect.Draw(Players.You))))),
            Ability.Activated(Cost(listOf(CostPart.Loyalty(n(-3)))), Instructions(
                targets = listOf(TargetSlot("c", TargetKind.Objects(creatures))),
                steps = listOf(Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("c")), n(3))))))))

    /** Equipment: "Equipped creature gets +2/+0." / Equip {1} */
    private val blade = FaceDefinition("Gearwright Blade", mana("{1}"), types = listOf(CardType.Artifact),
        subtypes = listOf(Subtype("Equipment")), abilities = listOf(
            Ability.Static(listOf(Mechanic.ModifyPowerToughness(n(2), n(0))), Affected.Objects(Objects.AttachedTo())),
            Keyword.Equip(Cost.mana(mana("{1}")))))

    /** "As an additional cost to cast this spell, sacrifice a creature. / Hurl the Fallen deals damage equal to the
     * sacrificed creature's power to any target." The cost names the creature as it was (last known information). */
    private val hurl = FaceDefinition("Hurl the Fallen", mana("{1}{R}"), types = listOf(CardType.Instant), abilities = listOf(
        Ability.Static(listOf(Mechanic.AdditionalCost(cost = Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(creatures),
            preBind = "sacrificed")))))),
        Ability.Spell(Instructions(
            targets = listOf(TargetSlot("t", TargetKind.Either(
                ObjectFilter.Or(listOf(creatures, ObjectFilter.Type(CardType.Planeswalker), ObjectFilter.Type(CardType.Battle))),
                PlayerFilter.Any))),
            steps = listOf(Step.Do(Effect.DealDamage(Recipients(Objects.Target("t"), Players.Target("t")),
                Value.Of(NumericProperty.Power, Objects.Bound("sacrificed"), Combine.Single))))))))

    /** "Forked Ember deals 3 damage divided as you choose among one, two, or three targets." */
    private val forked = FaceDefinition("Forked Ember", mana("{1}{R}"), types = listOf(CardType.Sorcery), abilities = listOf(
        Ability.Spell(Instructions(
            targets = listOf(TargetSlot("t", TargetKind.Either(
                ObjectFilter.Or(listOf(creatures, ObjectFilter.Type(CardType.Planeswalker), ObjectFilter.Type(CardType.Battle))),
                PlayerFilter.Any), min = n(1), max = n(3), divided = n(3))),
            steps = listOf(Step.Do(Effect.DealDamage(Recipients(Objects.Target("t"), Players.Target("t")), Value.DividedShare)))))))

    /** "Whenever this creature enters or attacks, draw cards equal to the greatest power among creatures you control." */
    private val watch = creature("Ridge Watcher", "{3}{G}", 3, 3, Ability.Triggered(
        Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            EventPattern.Attack(ObjectFilter.Is(Objects.Self)))),
        Instructions(steps = listOf(Step.Do(Effect.Draw(Players.You,
            Value.Of(NumericProperty.Power, Objects.Each(all(creatures, youControl)), Combine.Greatest)))))))

    /** "{X}{U}: Tap X target creatures. Look at the top three cards of your library. Put one of them into your hand
     * and the rest on the bottom of your library in any order." */
    private val tide = creature("Tidecaller Adept", "{2}{U}", 1, 3, Ability.Activated(
        Cost.mana(mana("{X}{U}")),
        Instructions(
            announced = listOf("X"),
            targets = listOf(TargetSlot("c", TargetKind.Objects(creatures), min = Value.Announced(), max = Value.Announced())),
            steps = listOf(
                Step.Do(Effect.Tap(Objects.Target("c"))),
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(3)), bind = "seen")),
                Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("seen")), bind = "kept"),
                Step.Do(Effect.Move(Objects.Bound("kept"), Destination(Zone.Hand))),
                Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")),
                    Destination(Zone.Library, libraryPosition = -1)))))))

    /** "As this land enters, you may pay 2 life. If you don't, it enters tapped." (plus its basic land types) */
    private val shockland = FaceDefinition("Ashen Crossing", types = listOf(CardType.Land),
        subtypes = listOf(Subtype("Mountain"), Subtype("Swamp")), abilities = listOf(
            Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.AsEnters(listOf(
                    Step.Pay(Cost(listOf(CostPart.PayLife(n(2)))), bind = "paid"),
                    Step.If(Condition.Not(Condition.Did("paid")), listOf(Step.Do(Effect.Tap(Objects.Self))))))))))))

    /** "Whenever this creature attacks, you may sacrifice another creature. When you do, this creature deals 2 damage
     * to any target." */
    private val reaver = creature("Pyre Reaver", "{2}{R}", 3, 2, Ability.Triggered(
        Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self))),
        Instructions(steps = listOf(
            Step.May(listOf(
                Step.Choose(Choice.Objects(all(creatures, youControl, ObjectFilter.Not(ObjectFilter.Is(Objects.Self))), n(1), n(1)), bind = "victim"),
                Step.Do(Effect.Sacrifice(Objects.Bound("victim")))), bind = "sacrificed"),
            Step.If(Condition.Did("sacrificed"), listOf(Step.Do(Effect.ReflexiveTrigger(Instructions(
                targets = listOf(TargetSlot("t", TargetKind.Either(
                    ObjectFilter.Or(listOf(creatures, ObjectFilter.Type(CardType.Planeswalker), ObjectFilter.Type(CardType.Battle))),
                    PlayerFilter.Any))),
                steps = listOf(Step.Do(Effect.DealDamage(Recipients(Objects.Target("t"), Players.Target("t")), n(2)))))))))))))

    /** "When this creature dies, if it had no -1/-1 counters on it, return it to the battlefield under its owner's
     * control with a -1/-1 counter on it." The condition reads the creature as it was, the return moves the new card. */
    private val persistent = creature("Unyielding Shade", "{3}{B}", 3, 3, Ability.Triggered(
        Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard)),
        Instructions(steps = listOf(Step.Do(Effect.Move(Objects.EventObject(EventRole.After),
            Destination(Zone.Battlefield, counters = listOf(CountersToPut(CounterKind.PowerToughness(-1, -1)))))))),
        interveningIf = Condition.Not(Condition.ObjectMatches(Objects.EventObject(EventRole.Before),
            ObjectFilter.HasCounter(CounterKind.PowerToughness(-1, -1))))))

    /** "Each opponent sacrifices a creature." Every opponent chooses first, then all sacrifice at once (CR 608.2e). */
    private val edict = FaceDefinition("Grim Tithe", mana("{1}{B}"), types = listOf(CardType.Sorcery), abilities = listOf(
        Ability.Spell(Instructions(steps = listOf(Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "p", together = true,
            steps = listOf(
                Step.Choose(Choice.Objects(all(creatures, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("p")))), n(1), n(1)),
                    bind = "victim", chooser = Players.Bound("p")),
                Step.Do(Effect.Sacrifice(Objects.Bound("victim"))))))))))

    /** Kicker {2}{R} / "~ deals 2 damage to target creature. If this spell was kicked, it deals 4 damage instead." */
    private val kicked = FaceDefinition("Flarecall Blast", mana("{R}"), types = listOf(CardType.Sorcery), abilities = listOf(
        Keyword.Kicker(listOf(Cost.mana(mana("{2}{R}")))),
        Ability.Spell(Instructions(
            targets = listOf(TargetSlot("c", TargetKind.Objects(creatures))),
            steps = listOf(Step.If(Condition.Paid(Keyword.Kicker.Kind),
                then = listOf(Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("c")), n(4)))),
                otherwise = listOf(Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("c")), n(2))))))))))

    /** Adventure: creature face and its Adventure instant. */
    private val adventure = CardDefinition(Layout.Adventure, listOf(
        creature("Hedge Rider", "{1}{G}", 2, 2),
        FaceDefinition("Trample the Hedge", mana("{G}"), types = listOf(CardType.Instant), subtypes = listOf(Subtype("Adventure")),
            abilities = listOf(Ability.Spell(Instructions(
                targets = listOf(TargetSlot("c", TargetKind.Objects(creatures))),
                steps = listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(n(2), n(2))),
                    Affected.Objects(Objects.Target("c")), Duration.EndOfTurn)))))))))

    @Test
    fun exampleCardsSurviveJson() {
        val cards = listOf(sparkbolt, banner, surge, scout, elf, grove, prison, charm, gravewalker, saga, walker, blade, kicked, hurl, forked, watch, tide, shockland, reaver, persistent, edict)
            .map { CardDefinition(Layout.Normal, listOf(it)) } + adventure
        cards.forEach { card ->
            val text = json.encodeToString(CardDefinition.serializer(), card)
            assertEquals(card, json.decodeFromString(CardDefinition.serializer(), text), card.faces.first().name)
        }
    }

    @Test
    fun exampleCardsPassDefinitionChecks() {
        (listOf(sparkbolt, banner, surge, scout, elf, grove, prison, charm, gravewalker, saga, walker, blade, kicked, hurl, forked, watch, tide, shockland, reaver, persistent, edict)
            .map { CardDefinition(Layout.Normal, listOf(it)) } + adventure)
            .forEach { assertEquals(emptyList(), DefinitionChecks.problems(it), it.faces.first().name) }
    }

    // CR 613.1f: "enchanted creature has deathtouch" grants the keyword (layer 6, lost with "loses all abilities");
    // the keyword-only mechanic used directly would skip that.
    @Test
    fun keywordOnlyMechanicsAreReportedOutsideTheKeywordTable() {
        fun card(static: Ability.Static) = CardDefinition(Layout.Normal, listOf(FaceDefinition("Venom Charm", abilities = listOf(static))))
        val enchanted = Affected.Objects(Objects.AttachedTo())
        assertEquals(emptyList(), DefinitionChecks.problems(card(Ability.Static(listOf(Mechanic.GainAbilities(listOf(Keyword.Deathtouch))), enchanted))))
        assertEquals(1, DefinitionChecks.problems(card(Ability.Static(listOf(Mechanic.Deathtouch), enchanted))).size)
    }

    // D37: names, target slots and event roles must exist where they are used.
    @Test
    fun undefinedReferencesAndWrongRolesAreReported() {
        fun spell(vararg steps: Step) = CardDefinition(Layout.Normal, listOf(FaceDefinition("Loose Ends", abilities = listOf(
            Ability.Spell(Instructions(steps = steps.toList()))))))
        assertEquals(1, DefinitionChecks.problems(spell(Step.Do(Effect.Tap(Objects.Bound("nowhere"))))).size)
        assertEquals(1, DefinitionChecks.problems(spell(Step.Do(Effect.Tap(Objects.Target("t"))))).size)
        assertEquals(1, DefinitionChecks.problems(spell(Step.Do(Effect.GainLife(Players.You, Value.Announced())))).size)
        val dies = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard))
        fun onDeath(role: EventRole) = CardDefinition(Layout.Normal, listOf(FaceDefinition("Last Rites", abilities = listOf(
            Ability.Triggered(dies, Instructions(steps = listOf(Step.Do(Effect.Move(Objects.EventObject(role), Destination(Zone.Hand))))))))))
        assertEquals(emptyList(), DefinitionChecks.problems(onDeath(EventRole.After)))
        assertEquals(1, DefinitionChecks.problems(onDeath(EventRole.Attacker)).size)
    }

    // D27: a number read from a group must say how it combines.
    @Test
    fun singleValueOverAGroupIsReported() {
        fun card(value: Value) = CardDefinition(Layout.Normal, listOf(FaceDefinition("Count Up", abilities = listOf(
            Ability.Spell(Instructions(steps = listOf(Step.Do(Effect.GainLife(Players.You, value)))))))))
        val mine = Objects.Each(all(creatures, youControl))
        assertEquals(emptyList(), DefinitionChecks.problems(card(Value.Of(NumericProperty.Power, mine, Combine.Total))))
        assertEquals(1, DefinitionChecks.problems(card(Value.Of(NumericProperty.Power, mine, Combine.Single))).size)
    }

    @Test
    fun readableJsonForASimpleSpell() {
        assertEquals(
            """{"name":"Surge of Vigor","manaCost":"{G}","types":[{"type":"instant"}],"abilities":[{"type":"spell",""" +
                """"instructions":{"targets":[{"name":"c","what":{"type":"objects","filter":{"type":"type","cardType":""" +
                """{"type":"creature"}}}}],"steps":[{"type":"do","effect":{"type":"apply","mechanics":[{"type":"modify-pt",""" +
                """"power":{"type":"fixed","value":3},"toughness":{"type":"fixed","value":3}}],"affects":{"type":"objects",""" +
                """"objects":{"type":"target","slot":"c"}},"duration":{"type":"end-of-turn"}}}]}}]}""",
            json.encodeToString(FaceDefinition.serializer(), surge))
    }

    // CR 702.9b, 702.17: flying expands to a blocking restriction naming flying and reach; reach adds nothing itself.
    @Test
    fun flyingExpandsToAGeneralBlockingRestriction() {
        assertEquals(listOf(Ability.Static(listOf(Mechanic.CanBeBlockedOnlyBy(ObjectFilter.Or(listOf(
            ObjectFilter.HasKeyword(Keyword.Flying), ObjectFilter.HasKeyword(Keyword.Reach))))))),
            KeywordExpansions.expand(Keyword.Flying))
        assertEquals(emptyList(), KeywordExpansions.expand(Keyword.Reach))
    }

    // CR 702.2: deathtouch maps one to one to its mechanic.
    @Test
    fun deathtouchExpandsToItsOwnMechanic() {
        assertEquals(listOf(Ability.Static(listOf(Mechanic.Deathtouch))), KeywordExpansions.expand(Keyword.Deathtouch))
    }

    // CR 702.63a: vanishing is three abilities; "when the last time counter is removed" has no intervening if.
    @Test
    fun vanishingExpandsToThreeAbilitiesThatSurviveJson() {
        val abilities = KeywordExpansions.expand(Keyword.Vanishing(3))!!
        assertEquals(3, abilities.size)
        assertEquals(null, (abilities[2] as Ability.Triggered).interveningIf)
        val face = FaceDefinition("Fading Wisp", abilities = abilities)
        assertEquals(face, json.decodeFromString(FaceDefinition.serializer(), json.encodeToString(FaceDefinition.serializer(), face)))
    }
}
