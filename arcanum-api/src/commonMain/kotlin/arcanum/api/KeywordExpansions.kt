package arcanum.api

/**
 * The shared table that says what each keyword does for the engine, written as ordinary abilities built from
 * mechanics and effects, following each keyword's CR definition. Definitions store the short keyword; this table is
 * applied when they are loaded, so a CR change to a keyword means changing one entry here.
 *
 * Returns null for keywords without an expansion yet: the engine then treats them as text to handle by hand.
 * Some keywords expand to nothing (reach): their only meaning is that other rules mention them.
 */
object KeywordExpansions {
    fun expand(keyword: Keyword): List<Ability>? = when (keyword) {
        // CR 702.9b: "can't be blocked except by creatures with flying and/or reach"
        Keyword.Flying -> static(Mechanic.CanBeBlockedOnlyBy(or(has(Keyword.Flying), has(Keyword.Reach))))
        // CR 702.17: only referred to by flying
        Keyword.Reach -> emptyList()
        Keyword.Deathtouch -> static(Mechanic.Deathtouch)
        Keyword.Lifelink -> static(Mechanic.Lifelink)
        Keyword.FirstStrike -> static(Mechanic.FirstStrike)
        Keyword.DoubleStrike -> static(Mechanic.DoubleStrike)
        Keyword.Trample -> static(Mechanic.Trample())
        Keyword.Haste -> static(Mechanic.IgnoresSummoningSickness)
        Keyword.Vigilance -> static(Mechanic.AttacksWithoutTapping)
        Keyword.Indestructible -> static(Mechanic.Indestructible)
        Keyword.Infect -> static(Mechanic.Infect)
        Keyword.Wither -> static(Mechanic.Wither)
        // CR 702.111b: "can't be blocked except by two or more creatures"
        Keyword.Menace -> static(Mechanic.MinimumBlockers(2))
        // CR 702.3b: "can't attack"
        Keyword.Defender -> static(Mechanic.CantAttack())
        // CR 702.11b: can't be the target of spells or abilities your opponents control
        Keyword.Hexproof -> static(Mechanic.CantBeTargeted(by = ObjectFilter.ControlledBy(PlayerFilter.Opponent)))
        // CR 702.18a: can't be the target of spells or abilities
        Keyword.Shroud -> static(Mechanic.CantBeTargeted(by = ObjectFilter.Any))
        // CR 702.36b: can't be blocked except by artifact creatures and/or black creatures
        Keyword.Fear -> static(Mechanic.CanBeBlockedOnlyBy(or(ObjectFilter.Type(CardType.Artifact), ObjectFilter.OfColor(Color.Black))))
        // CR 702.13b: except by artifact creatures and/or creatures that share a colour with it
        Keyword.Intimidate -> static(Mechanic.CanBeBlockedOnlyBy(or(ObjectFilter.Type(CardType.Artifact),
            ObjectFilter.Shares(SharedQuality.Color, Objects.Self))))
        // CR 702.8a: may cast it any time you could cast an instant
        Keyword.Flash -> static(Mechanic.InstantSpeed)
        // CR 702.108a: "Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn."
        Keyword.Prowess -> listOf(Ability.Triggered(
            Trigger.On(EventPattern.Cast(ObjectFilter.Not(ObjectFilter.Type(CardType.Creature)), PlayerFilter.You)),
            steps(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(1))),
                Affected.Objects(Objects.Self), Duration.EndOfTurn))))
        is Keyword.Toxic -> static(Mechanic.Toxic(keyword.amount))
        is Keyword.Protection -> static(Mechanic.Protection(keyword.from))
        is Keyword.HexproofFrom -> static(Mechanic.CantBeTargeted(by = ObjectFilter.And(listOf(
            ObjectFilter.ControlledBy(PlayerFilter.Opponent), keyword.from))))
        // CR 702.14c: can't be blocked as long as the defending player controls such a land
        is Keyword.Landwalk -> listOf(Ability.Static(listOf(Mechanic.CantBeBlocked),
            condition = Condition.Exists(ObjectFilter.And(listOf(keyword.land, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Defending)))))))
        // CR 702.5a / 303.4: restricts what the Aura targets and enchants
        is Keyword.Enchant -> static(Mechanic.CanEnchant(keyword.what))
        // CR 702.6a: "[Cost]: Attach this permanent to target creature you control. Activate only as a sorcery."
        is Keyword.Equip -> listOf(Ability.Activated(keyword.cost,
            Instructions(
                targets = listOf(TargetSlot("creature", TargetKind.Objects(ObjectFilter.And(listOfNotNull(
                    ObjectFilter.Type(CardType.Creature), ObjectFilter.ControlledBy(PlayerFilter.You), keyword.onto))))),
                steps = listOf(Step.Do(Effect.Attach(Objects.Self, Recipients(objects = Objects.Target("creature")))))),
            ActivationRules(timing = Timing.Sorcery)))
        // CR 702.21a: "Whenever this permanent becomes the target of a spell or ability an opponent controls, counter
        // that spell or ability unless that player pays [cost]."
        is Keyword.Ward -> listOf(Ability.Triggered(
            Trigger.On(EventPattern.BecomesTarget(ObjectFilter.Is(Objects.Self), ObjectFilter.ControlledBy(PlayerFilter.Opponent))),
            Instructions(steps = listOf(Step.Unless(Players.ControllerOf(Objects.EventObject(EventRole.Source)), keyword.cost,
                listOf(Step.Do(Effect.Counter(Objects.EventObject(EventRole.Source)))))))))
        // CR 702.29a: "[Cost], Discard this card: Draw a card." (works from the hand)
        is Keyword.Cycling -> listOf(cycling(keyword.cost, Effect.Draw(Players.You)))
        // CR 702.29e: "[Cost], Discard this card: Search your library for a [type] card, reveal it, put it into your
        // hand. Then shuffle."
        is Keyword.Typecycling -> listOf(cycling(keyword.cost, null, listOf(
            Step.Do(Effect.Search(Players.You, keyword.cards, reveal = true, bind = "found")),
            Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Hand))),
            Step.Do(Effect.Shuffle(Players.You)))))
        // CR 702.34a: may cast from the graveyard by paying the flashback cost; exiled when it leaves the Stack
        is Keyword.Flashback -> static(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast,
            cards = or(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery)),
            cost = keyword.cost, then = Destination(Zone.Exile)))
        // CR 702.33a: optional additional cost(s)
        is Keyword.Kicker -> keyword.costs.map { cost ->
            Ability.Static(listOf(Mechanic.AdditionalCost(Keyword.Kicker.Kind, cost, optional = true)))
        }
        is Keyword.Multikicker -> static(Mechanic.AdditionalCost(Keyword.Multikicker.Kind,
            keyword.cost, optional = true, repeatable = true))
        // CR 702.63a: enters with N time counters; upkeep: remove one; when the last is removed, sacrifice it
        is Keyword.Vanishing -> {
            val time = CounterKind.Time
            // Vanishing without a number (parsed as 0) has no "enters with" ability (CR 702.63b).
            listOfNotNull(
                if (keyword.amount == 0) null else Ability.Static(listOf(Mechanic.Replacement(
                    EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                    modify = listOf(Modification.EntersWithCounters(time, Value.Fixed(keyword.amount)))))),
                Ability.Triggered(Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You)),
                    steps(Effect.RemoveCounters(Objects.Self, time, Value.Fixed(1))),
                    interveningIf = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(time))),
                // "When the last time counter is removed": no intervening if, so it is sacrificed even if a counter
                // was put on in response
                Ability.Triggered(Trigger.On(EventPattern.CountersRemoved(ObjectFilter.Is(Objects.Self), time, last = true)),
                    steps(Effect.Sacrifice(Objects.Self))),
            )
        }
        else -> null
    }

    private fun static(mechanic: Mechanic) = listOf(Ability.Static(listOf(mechanic)))
    private fun steps(vararg effects: Effect) = Instructions(steps = effects.map { Step.Do(it) })
    private fun has(kind: KeywordKind) = ObjectFilter.HasKeyword(kind)
    private fun or(vararg filters: ObjectFilter) = ObjectFilter.Or(filters.toList())
    private fun cycling(cost: Cost, draw: Effect?, steps: List<Step> = listOfNotNull(draw?.let { Step.Do(it) })) =
        Ability.Activated(Cost(cost.parts + CostPart.Discard(CostObjects.Self)), Instructions(steps = steps))
}
