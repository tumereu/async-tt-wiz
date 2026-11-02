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
        Keyword.Defender -> static(Mechanic.CantAttack(fromDefender = true))
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
        is Keyword.HexproofFrom -> static(Mechanic.CantBeTargeted(
            by = ObjectFilter.ControlledBy(PlayerFilter.Opponent), fromSource = keyword.from))
        // CR 702.14c: can't be blocked as long as the defending player controls such a land
        is Keyword.Landwalk -> listOf(Ability.Static(listOf(Mechanic.CantBeBlocked),
            condition = Condition.Exists(ObjectFilter.And(listOf(keyword.land, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.DefendingPlayerOf(Objects.Self))))), atLeast = 1), affects = Affected.Objects(Objects.Self)))
        // CR 702.5a / 303.4: restricts what the Aura targets and enchants
        is Keyword.Enchant -> static(Mechanic.CanEnchant(keyword.what))
        // CR 702.6a: "[Cost]: Attach this permanent to target creature you control. Activate only as a sorcery."
        is Keyword.Equip -> listOf(Ability.Activated(keyword.cost,
            Instructions(
                targets = listOf(TargetSlot("creature", TargetKind.Objects(ObjectFilter.And(listOfNotNull(
                    ObjectFilter.Type(CardType.Creature), ObjectFilter.ControlledBy(PlayerFilter.You), keyword.onto))), min = Value.Fixed(1), max = Value.Fixed(1))),
                steps = listOf(Step.Do(Effect.Attach(Objects.Self, Recipients(objects = Objects.Target("creature")))))),
            ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You)))
        // CR 702.21a: "Whenever this permanent becomes the target of a spell or ability an opponent controls, counter
        // that spell or ability unless that player pays [cost]."
        is Keyword.Ward -> listOf(Ability.Triggered(
            Trigger.On(EventPattern.BecomesTarget(ObjectFilter.Is(Objects.Self), ObjectFilter.ControlledBy(PlayerFilter.Opponent))),
            Instructions(steps = listOf(
                Step.Pay(keyword.cost, payer = Players.ControllerOf(Objects.EventObject(EventRole.Source)), bind = "paid"),
                Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.Counter(Objects.EventObject(EventRole.Source)))))))))
        // CR 702.29a: "[Cost], Discard this card: Draw a card." (works from the hand)
        is Keyword.Cycling -> listOf(cycling(keyword.cost, Effect.Draw(Players.You, count = Value.Fixed(1))))
        // CR 702.29e: "[Cost], Discard this card: Search your library for a [type] card, reveal it, put it into your
        // hand. Then shuffle."
        is Keyword.Typecycling -> listOf(cycling(keyword.cost, null, listOf(
            Step.Do(Effect.Search(Players.You, keyword.cards, reveal = true, bind = "found", max = Value.Fixed(1), zones = listOf(Zone.Library), searcher = Players.You)),
            Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Hand))),
            Step.Do(Effect.Shuffle(Players.You)))))
        // CR 702.34a: may cast from the graveyard by paying the flashback cost; exiled when it leaves the Stack
        is Keyword.Flashback -> static(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast,
            resultingSpell = or(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery)),
            cost = keyword.cost, then = Destination(Zone.Exile), holder = PlayerFilter.You))
        // CR 702.33a: optional additional cost(s)
        is Keyword.Kicker -> keyword.costs.map { cost ->
            Ability.Static(listOf(Mechanic.AdditionalCost(Keyword.Kicker.Kind, cost, optional = true)), affects = Affected.Objects(Objects.Self))
        }
        is Keyword.Multikicker -> static(Mechanic.AdditionalCost(Keyword.Kicker.Kind,
            keyword.cost, optional = true, repeatable = true))
        // blb: CR 702.174a, b, j: "As an additional cost to cast this spell, you may choose an opponent"; the chosen
        // player's gift happens on an instant or sorcery before its other spell abilities (a spell ability, which works
        // only for instants and sorceries, CR 113.3a), on a permanent when it enters (a triggered ability on the
        // battlefield)
        is Keyword.Gift -> {
            val promised = Condition.Paid(Keyword.Gift.Kind, times = 1, of = Objects.Self)
            val gift = giftSteps(keyword.gift, Players.ChosenAsCost(Objects.Self))
            listOf(
                Ability.Static(listOf(Mechanic.AdditionalCost(Keyword.Gift.Kind, Cost(listOf(CostPart.ChoosePlayer(PlayerFilter.Opponent))),
                    optional = true)), affects = Affected.Objects(Objects.Self)),
                Ability.Spell(Instructions(steps = listOf(Step.If(promised, gift)))),
                Ability.Triggered(entersSelf, Instructions(steps = gift), interveningIf = promised),
            )
        }
        // blb: CR 716.2a: "[Cost]: This Class's level becomes N. Activate only if this Class is level N-1 and only as a
        // sorcery" and "As long as this Class is level N or greater, it has [abilities]"
        is Keyword.ClassLevel -> listOf(
            Ability.Activated(keyword.cost, steps(Effect.SetClassLevel(Objects.Self, keyword.level)), ActivationRules(timing = Timing.Sorcery,
                activatedBy = PlayerFilter.You, condition = Condition.And(listOf(Condition.ClassLevelAtLeast(keyword.level - 1),
                    Condition.Not(Condition.ClassLevelAtLeast(keyword.level)))))),
            Ability.Static(listOf(Mechanic.GainAbilities(keyword.abilities)), affects = Affected.Objects(Objects.Self),
                condition = Condition.ClassLevelAtLeast(keyword.level)),
        )
        // blb: CR 702.175a: "You may pay an additional [cost] as you cast this spell" and "When this permanent enters, if
        // its offspring cost was paid, create a token that's a copy of it, except it's 1/1."
        is Keyword.Offspring -> listOf(
            Ability.Static(listOf(Mechanic.AdditionalCost(Keyword.Offspring.Kind, keyword.cost, optional = true)), affects = Affected.Objects(Objects.Self)),
            Ability.Triggered(entersSelf, steps(Effect.CreateToken(TokenSpec.CopyOf(Objects.Self, except = listOf(CopyException.PowerToughness(1, 1))),
                count = Value.Fixed(1), controller = Players.You)), interveningIf = Condition.Paid(Keyword.Offspring.Kind, times = 1, of = Objects.Self)),
        )
        // CR 702.63a: enters with N time counters; upkeep: remove one; when the last is removed, sacrifice it
        is Keyword.Vanishing -> {
            val time = CounterKind.Time
            // Vanishing without a number (parsed as 0) has no "enters with" ability (CR 702.63b).
            listOfNotNull(
                if (keyword.amount == 0) null else Ability.Static(listOf(Mechanic.Replacement(
                    EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                    modify = listOf(Modification.EntersWithCounters(time, Value.Fixed(keyword.amount))))), affects = Affected.Objects(Objects.Self)),
                Ability.Triggered(Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You)),
                    steps(Effect.RemoveCounters(Objects.Self, time, Value.Fixed(1))),
                    interveningIf = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(time, atLeast = 1))),
                // "When the last time counter is removed": no intervening if, so it is sacrificed even if a counter
                // was put on in response
                Ability.Triggered(Trigger.On(EventPattern.CountersRemoved(ObjectFilter.Is(Objects.Self), time, last = true)),
                    steps(Effect.Sacrifice(Objects.Self))),
            )
        }
        // CR 702.79a: "When this permanent is put into a graveyard from the battlefield, if it had no -1/-1 counters on it,
        // return it to the battlefield under its owner's control with a -1/-1 counter on it."
        Keyword.Persist -> listOf(returnFromGraveyard(CounterKind.PowerToughness(-1, -1)))
        // CR 702.40a: "When you cast this spell, copy it for each other spell that was cast before it this turn. If the
        // spell has any targets, you may choose new targets for any of the copies."
        Keyword.Storm -> listOf(Ability.Triggered(Trigger.On(EventPattern.Cast(ObjectFilter.Is(Objects.Self), by = PlayerFilter.Any)),
            Instructions(steps = listOf(Step.Repeat(Value.SpellsCastBefore(of = Objects.Self), listOf(Step.Do(Effect.CopySpell(Objects.Self, controller = Players.You, newTargets = true))))))))
        // CR 702.73a: characteristic-defining, every creature type
        Keyword.Changeling -> listOf(Ability.Static(listOf(Mechanic.AllCreatureTypes), characteristicDefining = true, affects = Affected.Objects(Objects.Self)))
        // CR 702.66a, 702.51a: other ways to pay the spell's mana
        Keyword.Delve -> static(Mechanic.PayManaBy(ManaPaymentWay.ExileFromGraveyard))
        Keyword.Convoke -> static(Mechanic.PayManaBy(ManaPaymentWay.TapCreatures))
        // CR 702.83a: "Whenever a creature you control attacks alone, that creature gets +1/+1 until end of turn."
        Keyword.Exalted -> listOf(Ability.Triggered(
            Trigger.On(EventPattern.Attack(ObjectFilter.ControlledBy(PlayerFilter.You), alone = true)),
            steps(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(1))),
                Affected.Objects(Objects.EventObject(role = EventRole.Object)), Duration.EndOfTurn))))
        // CR 702.81a: cast from your graveyard by discarding a land card as an additional cost
        Keyword.Retrace -> static(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast,
            additionalCost = Cost(listOf(CostPart.Discard(CostObjects.Choose(ObjectFilter.Type(CardType.Land), count = Value.Fixed(1))))), holder = PlayerFilter.You))
        // CR 702.44a: enters with a +1/+1 counter (as a creature) or a charge counter for each colour of mana spent
        Keyword.Sunburst -> listOf(
            Ability.Static(listOf(entersWith(CounterKind.PowerToughness(1, 1), Value.ManaSpent(distinctColors = true, of = Objects.Self))),
                condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.Type(CardType.Creature, ignoringTypeChanges = true)), affects = Affected.Objects(Objects.Self)),
            Ability.Static(listOf(entersWith(CounterKind.Named("charge"), Value.ManaSpent(distinctColors = true, of = Objects.Self))),
                condition = Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.Type(CardType.Creature, ignoringTypeChanges = true))), affects = Affected.Objects(Objects.Self)))
        // CR 702.88a: cast from your hand, it is exiled instead of going to the graveyard as it resolves, and at the
        // beginning of your next upkeep you may cast it from exile without paying its mana cost
        Keyword.Rebound -> listOf(Ability.Static(listOf(Mechanic.Replacement(
            EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Stack, to = Zone.Graveyard, resolving = true),
            instead = Instructions(steps = listOf(
                Step.Do(Effect.Move(Objects.Self, Destination(Zone.Exile), postBind = "rebound")),
                Step.Do(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You)),
                    Instructions(steps = listOf(Step.May(listOf(Step.Do(Effect.Cast(Objects.Bound("rebound"), withoutPaying = true, how = PlayKind.Cast, caster = Players.You))), player = Players.You))))))))),
            condition = Condition.CastFrom(Zone.Hand, of = Objects.Self), affects = Affected.Objects(Objects.Self)))
        // CR 702.147a: "This creature can't block" and "When this creature attacks, sacrifice it at end of combat."
        Keyword.Decayed -> listOf(
            Ability.Static(listOf(Mechanic.CantBlock(attackers = ObjectFilter.Any)), affects = Affected.Objects(Objects.Self)),
            Ability.Triggered(Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self))),
                steps(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.EndOfCombat, whose = PlayerFilter.Any)),
                    steps(Effect.Sacrifice(Objects.Self))))))
        // CR 702.124a: deck construction in Commander only
        Keyword.Partner -> emptyList()
        // CR 702.28b: can't be blocked by creatures without shadow, and can block only creatures with shadow
        Keyword.Shadow -> static(Mechanic.CantBeBlockedBy(ObjectFilter.Not(has(Keyword.Shadow))), Mechanic.CanBlockOnly(has(Keyword.Shadow)))
        // CR 702.92a: "When this Equipment enters, create a 0/0 black Phyrexian Germ creature token, then attach this
        // Equipment to it."
        Keyword.LivingWeapon -> listOf(Ability.Triggered(entersSelf, Instructions(steps = listOf(
            Step.Do(Effect.CreateToken(TokenSpec.Defined(FaceDefinition("Phyrexian Germ Token", colorIndicator = listOf(Color.Black),
                types = listOf(CardType.Creature), subtypes = listOf(Subtype("Phyrexian"), Subtype("Germ")),
                power = PrintedNumber.Fixed(0), toughness = PrintedNumber.Fixed(0))), bind = "germ", count = Value.Fixed(1), controller = Players.You)),
            Step.Do(Effect.Attach(Objects.Self, Recipients(objects = Objects.Bound("germ"))))))))
        // CR 702.85a: exile cards from the top until a nonland card with lower mana value; you may cast it without paying
        // its mana cost if the resulting spell's mana value is lower; the rest go to the bottom in a random order
        Keyword.Cascade -> listOf(Ability.Triggered(Trigger.On(EventPattern.Cast(ObjectFilter.Is(Objects.Self), by = PlayerFilter.Any)), Instructions(steps = listOf(
            Step.Do(Effect.RevealUntil(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Type(CardType.Land)), lowerManaValue)),
                into = Zone.Exile, foundBind = "hit", restBind = "missed", library = Players.You, count = Value.Fixed(1), reveal = true)),
            Step.May(listOf(Step.Do(Effect.Cast(Objects.Bound("hit"), withoutPaying = true, bind = "cast", resultingSpell = lowerManaValue, how = PlayKind.Cast, caster = Players.You))), player = Players.You),
            Step.Do(Effect.Move(Objects.Except(Objects.Union(listOf(Objects.Bound("hit"), Objects.Bound("missed"))), Objects.Bound("cast")),
                Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))))))
        // CR 702.91a: "Whenever this creature attacks, each other attacking creature gets +1/+0 until end of turn."
        Keyword.BattleCry -> listOf(Ability.Triggered(Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self))),
            steps(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(0))),
                Affected.Objects(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.HasStatus(Status.Attacking), ObjectFilter.Not(ObjectFilter.Is(Objects.Self)))))),
                Duration.EndOfTurn))))
        // CR 702.149a: "Whenever this creature and at least one other creature with power greater than this creature's
        // power attack, put a +1/+1 counter on this creature."
        Keyword.Training -> listOf(Ability.Triggered(Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self))),
            steps(Effect.PutCounters(Objects.Self, CounterKind.PowerToughness(1, 1), Value.Fixed(1))),
            triggersOnlyIf = Condition.Exists(ObjectFilter.And(listOf(ObjectFilter.HasStatus(Status.Attacking),
                ObjectFilter.Not(ObjectFilter.Is(Objects.Self)),
                ObjectFilter.Power(Comparison.Greater, Value.Of(NumericProperty.Power, Objects.Self, Combine.Single)))), atLeast = 1)))
        is Keyword.Ninjutsu -> listOf(ninjutsu(keyword.cost, listOf(Zone.Hand)))
        is Keyword.CommanderNinjutsu -> listOf(ninjutsu(keyword.cost, listOf(Zone.Hand, Zone.Command)))
        // CR 702.84a: return to the battlefield; it gains haste; exile it at the next end step; if it would leave the
        // battlefield, exile it instead; activate only as a sorcery
        is Keyword.Unearth -> listOf(Ability.Activated(keyword.cost, Instructions(steps = listOf(
            Step.Do(Effect.Move(Objects.Self, Destination(Zone.Battlefield), postBind = "back")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Haste))),
                Affected.Objects(Objects.Bound("back")), Duration.Indefinitely)),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Bound("back")), from = Zone.Battlefield),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), null, Duration.Indefinitely)),
            Step.Do(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.End, whose = PlayerFilter.Any)),
                steps(Effect.Move(Objects.Bound("back"), Destination(Zone.Exile))))))),
            ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You)))
        // CR 702.41a: "This spell costs {1} less to cast for each [text] you control."
        is Keyword.Affinity -> static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost(listOf(ManaSymbol.Generic(1))),
            Value.Count(Objects.Each(ObjectFilter.And(listOf(keyword.forWhat, ObjectFilter.ControlledBy(PlayerFilter.You))))))))
        // CR 702.74a: alternative cost, and "When this permanent enters, if its evoke cost was paid, its controller
        // sacrifices it."
        is Keyword.Evoke -> listOf(
            Ability.Static(listOf(Mechanic.AlternativeCost(Keyword.Evoke.Kind, keyword.cost)), affects = Affected.Objects(Objects.Self)),
            Ability.Triggered(entersSelf, steps(Effect.Sacrifice(Objects.Self)), interveningIf = Condition.Paid(Keyword.Evoke.Kind, times = 1, of = Objects.Self)))
        // CR 702.35a: discarded into exile instead of the graveyard; "When this card is exiled this way, its owner may
        // cast it by paying [cost] rather than paying its mana cost. If that player doesn't, they put this card into
        // their graveyard."
        is Keyword.Madness -> listOf(Ability.Static(listOf(Mechanic.Replacement(EventPattern.Discard(ObjectFilter.Is(Objects.Self), players = PlayerFilter.Any),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))),
            whenApplied = Instructions(steps = listOf(
                Step.Pay(steps = listOf(Step.Do(Effect.Cast(Objects.EventObject(EventRole.After), cost = keyword.cost,
                    caster = Players.OwnerOf(Objects.EventObject(EventRole.After)), how = PlayKind.Cast))),
                    payer = Players.OwnerOf(Objects.EventObject(EventRole.After)), bind = "cast"),
                Step.If(Condition.Not(Condition.Did("cast")),
                    listOf(Step.Do(Effect.Move(Objects.EventObject(EventRole.After), Destination(Zone.Graveyard))))))))),
            functionsIn = listOf(Zone.Hand), affects = Affected.Objects(Objects.Self)))
        // CR 702.43a: enters with N +1/+1 counters; "When this permanent is put into a graveyard from the battlefield,
        // you may put a +1/+1 counter on target artifact creature for each +1/+1 counter on this permanent."
        is Keyword.Modular -> listOf(
            Ability.Static(listOf(entersWith(CounterKind.PowerToughness(1, 1), Value.Fixed(keyword.amount))), affects = Affected.Objects(Objects.Self)),
            Ability.Triggered(diesSelf, Instructions(
                targets = listOf(TargetSlot("artifact", TargetKind.Objects(ObjectFilter.And(listOf(
                    ObjectFilter.Type(CardType.Artifact), ObjectFilter.Type(CardType.Creature)))), min = Value.Fixed(1), max = Value.Fixed(1))),
                steps = listOf(Step.May(listOf(Step.Do(Effect.PutCounters(Objects.Target("artifact"), CounterKind.PowerToughness(1, 1),
                    Value.Of(NumericProperty.Counters(CounterKind.PowerToughness(1, 1)), Objects.EventObject(EventRole.Before), Combine.Single)))), player = Players.You)))))
        // CR 702.52a: as long as you have N cards in your library, instead of drawing you may mill N and return this
        // card to your hand
        is Keyword.Dredge -> listOf(Ability.Static(listOf(Mechanic.Replacement(EventPattern.Draw(PlayerFilter.You),
            instead = Instructions(steps = listOf(Step.Do(Effect.Mill(Players.You, Value.Fixed(keyword.amount))),
                Step.Do(Effect.Move(Objects.Self, Destination(Zone.Hand))))), optional = true)),
            condition = Condition.Exists(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You))),
                atLeast = keyword.amount),
            functionsIn = listOf(Zone.Graveyard), affects = Affected.Objects(Objects.Self)))
        // CR 702.62a: exile it with N time counters as a special action; remove one each upkeep; when the last is
        // removed, you may play it without paying its mana cost; a creature cast this way gains haste
        is Keyword.Suspend -> {
            val suspended = Condition.ObjectMatches(Objects.Self, ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Exile),
                ObjectFilter.HasCounter(CounterKind.Time, atLeast = 1))))
            listOf(
                Ability.SpecialAction(keyword.cost, steps(Effect.Move(Objects.Self, Destination(Zone.Exile,
                    counters = listOf(CountersToPut(CounterKind.Time, Value.Fixed(keyword.amount)))))),
                    timing = Timing.AsThisCard, functionsIn = listOf(Zone.Hand)),
                Ability.Triggered(Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You)),
                    steps(Effect.RemoveCounters(Objects.Self, CounterKind.Time, Value.Fixed(1))), interveningIf = suspended,
                    functionsIn = listOf(Zone.Exile)),
                Ability.Triggered(Trigger.On(EventPattern.CountersRemoved(ObjectFilter.Is(Objects.Self), CounterKind.Time, last = true)),
                    Instructions(steps = listOf(
                        Step.May(listOf(Step.Do(Effect.Cast(Objects.Self, how = PlayKind.Play, withoutPaying = true, bind = "played", caster = Players.You))), player = Players.You),
                        Step.If(Condition.ObjectMatches(Objects.Bound("played"), ObjectFilter.Type(CardType.Creature)), listOf(
                            Step.Do(Effect.SpellGets(Objects.Bound("played"), listOf(Mechanic.GainAbilities(listOf(Keyword.Haste))),
                                duration = Duration.AsLongAs(Condition.ObjectMatches(Objects.Bound("played"),
                                    ObjectFilter.ControlledBy(PlayerFilter.You))))))))),
                    interveningIf = Condition.ObjectMatches(Objects.Self, ObjectFilter.InZone(Zone.Exile)), functionsIn = listOf(Zone.Exile)))
        }
        // CR 702.122a: "Tap any number of other untapped creatures you control with total power N or greater: This
        // permanent becomes an artifact creature until end of turn."
        is Keyword.Crew -> listOf(Ability.Activated(
            Cost(listOf(CostPart.TapObjects(CostObjects.Choose(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Creature),
                ObjectFilter.Not(ObjectFilter.Is(Objects.Self)))), count = null,
                total = Total(NumericProperty.Power, Value.Fixed(keyword.amount)))))),
            steps(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature))),
                Affected.Objects(Objects.Self), Duration.EndOfTurn)), rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)))
        // CR 702.37a: cast face down as a 2/2 for {3}; turning it face up for its morph cost is a special action (116.2b)
        is Keyword.Morph -> morph(keyword.cost, megamorph = false)
        is Keyword.Megamorph -> morph(keyword.cost, megamorph = true)
        // CR 702.120a: an additional [cost] for each mode chosen beyond the first
        is Keyword.Escalate -> static(Mechanic.AdditionalCost(Keyword.Escalate.Kind, keyword.cost,
            times = Value.Difference(Value.ModesChosen, Value.Fixed(1))))
        // CR 702.148a: alternative cost; its bracketed text is written as If(Not(Paid(Cleave))) in the instructions
        is Keyword.Cleave -> static(Mechanic.AlternativeCost(Keyword.Cleave.Kind, keyword.cost))
        // CR 702.87a: "[Cost]: Put a level counter on this permanent. Activate only as a sorcery."
        is Keyword.LevelUp -> listOf(Ability.Activated(keyword.cost,
            steps(Effect.PutCounters(Objects.Self, CounterKind.Level, Value.Fixed(1))), ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You)))
        // CR 702.151a: attach to another target creature you control, or unattach; both only as a sorcery
        is Keyword.Reconfigure -> listOf(
            Ability.Activated(keyword.cost, Instructions(
                targets = listOf(TargetSlot("creature", TargetKind.Objects(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Creature),
                    ObjectFilter.ControlledBy(PlayerFilter.You), ObjectFilter.Not(ObjectFilter.Is(Objects.Self))))), min = Value.Fixed(1), max = Value.Fixed(1))),
                steps = listOf(Step.Do(Effect.Attach(Objects.Self, Recipients(objects = Objects.Target("creature")))))),
                ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You)),
            Ability.Activated(keyword.cost, steps(Effect.Unattach(Objects.Self)), ActivationRules(timing = Timing.Sorcery,
                condition = Condition.ObjectMatches(Objects.AttachedTo(of = Objects.Self), ObjectFilter.Type(CardType.Creature)), activatedBy = PlayerFilter.You)),
            // CR 702.151b: attached to a creature, it stops being a creature until it becomes unattached
            Ability.Static(listOf(Mechanic.SetTypes(remove = listOf(CardType.Creature))),
                condition = Condition.ObjectMatches(Objects.AttachedTo(of = Objects.Self), ObjectFilter.Type(CardType.Creature)), affects = Affected.Objects(Objects.Self)))
        // CR 702.123a: "When this permanent enters, you may put N +1/+1 counters on it. If you don't, create N 1/1
        // colorless Servo artifact creature tokens."
        is Keyword.Fabricate -> listOf(Ability.Triggered(entersSelf, Instructions(steps = listOf(
            Step.Pay(steps = listOf(Step.Do(Effect.PutCounters(Objects.Self, CounterKind.PowerToughness(1, 1), Value.Fixed(keyword.amount)))),
                bind = "counters", payer = Players.You),
            Step.If(Condition.Not(Condition.Did("counters")), listOf(Step.Do(Effect.CreateToken(TokenSpec.Defined(
                FaceDefinition("Servo Token", colorIndicator = emptyList(), types = listOf(CardType.Artifact, CardType.Creature),
                    subtypes = listOf(Subtype("Servo")), power = PrintedNumber.Fixed(1), toughness = PrintedNumber.Fixed(1))),
                count = Value.Fixed(keyword.amount), controller = Players.You))))))))
        // CR 702.75a: look at the top N, exile one face down (linked to this permanent, which lets its controller look at
        // it), put the rest on the bottom in a random order
        is Keyword.Hideaway -> listOf(
            Ability.Triggered(entersSelf, Instructions(steps = listOf(
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Fixed(keyword.amount), fromTop = true), bind = "seen", players = Players.You)),
                Step.Choose(Choice.Objects(ObjectFilter.Any, Value.Fixed(1), Value.Fixed(1), from = Objects.Bound("seen")), bind = "hidden", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("hidden"), Destination(Zone.Exile, faceDown = true), postBind = "exiled")),
                Step.Do(Effect.Remember(Keyword.Hideaway.Kind.id, Objects.Bound("exiled"))),
                // The exiled card gains "the player who controls the permanent that exiled it may look at it"
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayLookAt(Objects.Affected)), Affected.Objects(Objects.Bound("exiled")),
                    Duration.Indefinitely)),
                Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("hidden")),
                    Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))))))
        // CR 702.27a: optional additional cost; if paid, the spell goes to its owner's hand instead of the graveyard
        is Keyword.Buyback -> listOf(
            Ability.Static(listOf(Mechanic.AdditionalCost(Keyword.Buyback.Kind, keyword.cost, optional = true)), affects = Affected.Objects(Objects.Self)),
            Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Stack,
                to = Zone.Graveyard, resolving = true),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Hand))))), condition = Condition.Paid(Keyword.Buyback.Kind, times = 1, of = Objects.Self), affects = Affected.Objects(Objects.Self)))
        // CR 702.138a: cast from your graveyard by paying [cost] rather than its mana cost
        is Keyword.Escape -> static(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, cost = keyword.cost, holder = PlayerFilter.You))
        // CR 702.96a: alternative cost; when paid, the spell uses [Keyword.Overload.instructions] ("target" → "each")
        is Keyword.Overload -> static(Mechanic.AlternativeCost(Keyword.Overload.Kind, keyword.cost))
        // CR 702.103a: cast bestowed for [cost]: an Aura spell with enchant creature; the engine applies 702.103b–f
        is Keyword.Bestow -> static(Mechanic.AlternativeCost(Keyword.Bestow.Kind, keyword.cost))
        // CR 702.47a: reveal it as you cast a [quality] spell and pay [cost]; that spell gains this card's text
        is Keyword.Splice -> listOf(Ability.Static(listOf(Mechanic.SpliceOnto(keyword.onto, keyword.cost)), functionsIn = listOf(Zone.Hand), affects = Affected.Objects(Objects.Self)))
        // CR 702.139a, 116.2g: once per game, pay {3} and put it from outside the game into your hand (main phase,
        // empty Stack)
        is Keyword.CompanionAbility -> listOf(Ability.SpecialAction(Cost.mana(ManaCost.parse("{3}")!!),
            steps(Effect.Move(Objects.Self, Destination(Zone.Hand))), timing = Timing.Sorcery, functionsIn = listOf(Zone.Outside),
            oncePerGame = true, condition = Condition.RevealedCompanion))
        // CR 702.93a: "When this permanent is put into a graveyard from the battlefield, if it had no +1/+1 counters on
        // it, return it to the battlefield under its owner's control with a +1/+1 counter on it."
        Keyword.Undying -> listOf(returnFromGraveyard(CounterKind.PowerToughness(1, 1)))
        // CR 702.114a: characteristic-defining, "This object is colorless."
        Keyword.Devoid -> listOf(Ability.Static(listOf(Mechanic.SetColors(emptyList())), characteristicDefining = true, affects = Affected.Objects(Objects.Self)))
        // CR 702.31b: can't be blocked by creatures without horsemanship
        Keyword.Horsemanship -> static(Mechanic.CanBeBlockedOnlyBy(has(Keyword.Horsemanship)))
        // CR 702.118b: can't be blocked by creatures with greater power
        Keyword.Skulk -> static(Mechanic.CantBeBlockedBy(ObjectFilter.Power(Comparison.Greater, Value.Of(NumericProperty.Power, Objects.Self, Combine.Single))))
        // CR 702.89a: "If enchanted permanent would be destroyed, instead remove all damage marked on it and destroy this
        // Aura."
        Keyword.UmbraArmor -> listOf(Ability.Static(listOf(Mechanic.Replacement(EventPattern.Destroyed(ObjectFilter.Is(Objects.AttachedTo(Objects.Self))),
            instead = Instructions(steps = listOf(
                Step.Do(Effect.RemoveAllDamage(Objects.EventObject(EventRole.Object))),
                Step.Do(Effect.Destroy(Objects.Self)))))), affects = Affected.Objects(Objects.Self)))
        // CR 702.110a: "When this creature enters, you may sacrifice a creature."
        Keyword.Exploit -> listOf(Ability.Triggered(entersSelf, Instructions(steps = listOf(
            Step.Choose(Choice.Objects(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Creature), ObjectFilter.ControlledBy(PlayerFilter.You))),
                Value.Fixed(0), Value.Fixed(1)), bind = "exploited", chooser = Players.You),
            Step.Do(Effect.Sacrifice(Objects.Bound("exploited")))))))
        // CR 702.126a: tap an untapped artifact you control rather than pay a generic mana
        Keyword.Improvise -> static(Mechanic.PayManaBy(ManaPaymentWay.TapArtifacts))
        // CR 702.134a: "Whenever this creature attacks, put a +1/+1 counter on target attacking creature with power less
        // than this creature's power."
        Keyword.Mentor -> listOf(Ability.Triggered(Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self))), Instructions(
            targets = listOf(TargetSlot("creature", TargetKind.Objects(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Creature),
                ObjectFilter.HasStatus(Status.Attacking), ObjectFilter.Power(Comparison.Less, Value.Of(NumericProperty.Power, Objects.Self, Combine.Single))))),
                min = Value.Fixed(1), max = Value.Fixed(1))),
            steps = listOf(Step.Do(Effect.PutCounters(Objects.Target("creature"), CounterKind.PowerToughness(1, 1), Value.Fixed(1)))))))
        // CR 702.133a: cast from your graveyard by discarding a card in addition to its other costs; exiled when it
        // leaves the Stack
        Keyword.JumpStart -> static(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast,
            resultingSpell = or(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery)),
            additionalCost = Cost(listOf(CostPart.Discard(CostObjects.Choose(ObjectFilter.Any, count = Value.Fixed(1))))),
            then = Destination(Zone.Exile), holder = PlayerFilter.You))
        // CR 702.161a: "During your turn, this permanent is an artifact creature in addition to its other types."
        Keyword.LivingMetal -> listOf(Ability.Static(listOf(Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature))),
            condition = Condition.YourTurn, affects = Affected.Objects(Objects.Self)))
        // CR 702.163a: "When this Equipment enters, create a 2/2 red Rebel creature token, then attach this Equipment to it."
        Keyword.ForMirrodin -> listOf(createAndAttach(FaceDefinition("Rebel Token", colorIndicator = listOf(Color.Red), types = listOf(CardType.Creature),
            subtypes = listOf(Subtype("Rebel")), power = PrintedNumber.Fixed(2), toughness = PrintedNumber.Fixed(2))))
        // CR 702.182a: "When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this Equipment
        // to it."
        Keyword.JobSelect -> listOf(createAndAttach(FaceDefinition("Hero Token", colorIndicator = emptyList(), types = listOf(CardType.Creature),
            subtypes = listOf(Subtype("Hero")), power = PrintedNumber.Fixed(1), toughness = PrintedNumber.Fixed(1))))
        // CR 702.25a: "Whenever this creature becomes blocked by a creature without flanking, the blocking creature gets
        // -1/-1 until end of turn."
        Keyword.Flanking -> listOf(Ability.Triggered(Trigger.On(EventPattern.BecomesBlocked(ObjectFilter.Is(Objects.Self),
            ObjectFilter.Not(has(Keyword.Flanking)), perBlocker = true)),
            steps(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(-1), Value.Fixed(-1))),
                Affected.Objects(Objects.EventObject(EventRole.OtherCombatant)), Duration.EndOfTurn))))
        // CR 702.32a: enters with N fade counters; "At the beginning of your upkeep, remove a fade counter from this
        // permanent. If you can't, sacrifice the permanent."
        is Keyword.Fading -> listOf(
            Ability.Static(listOf(entersWith(FADE, Value.Fixed(keyword.amount))), affects = Affected.Objects(Objects.Self)),
            Ability.Triggered(Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You)), Instructions(steps = listOf(
                Step.If(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(FADE, atLeast = 1)),
                    then = listOf(Step.Do(Effect.RemoveCounters(Objects.Self, FADE, Value.Fixed(1)))),
                    otherwise = listOf(Step.Do(Effect.Sacrifice(Objects.Self))))))))
        // CR 702.45a: "Whenever this creature blocks or becomes blocked, it gets +N/+N until end of turn."
        is Keyword.Bushido -> listOf(Ability.Triggered(Trigger.On(listOf(EventPattern.Block(ObjectFilter.Is(Objects.Self), ObjectFilter.Any),
            EventPattern.BecomesBlocked(ObjectFilter.Is(Objects.Self), ObjectFilter.Any))), pump(keyword.amount, keyword.amount)))
        // CR 702.64a: "If a source would deal damage to this creature, prevent N of that damage."
        is Keyword.Absorb -> static(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self))),
            each = Value.Fixed(keyword.amount)))
        // CR 702.68a: "Whenever this creature attacks and isn't blocked, it gets +N/+0 until end of turn."
        is Keyword.Frenzy -> listOf(Ability.Triggered(Trigger.On(EventPattern.AttacksUnblocked(ObjectFilter.Is(Objects.Self))), pump(keyword.amount, 0)))
        // CR 702.70a: "Whenever this creature deals combat damage to a player, that player gets N poison counters."
        is Keyword.Poisonous -> listOf(Ability.Triggered(Trigger.On(combatDamageToPlayer),
            steps(Effect.GivePlayerCounters(Players.EventPlayer, CounterKind.Poison, Value.Fixed(keyword.amount)))))
        // CR 702.86a: "Whenever this creature attacks, defending player sacrifices N permanents."
        is Keyword.Annihilator -> {
            val defending = Players.DefendingPlayerOf(Objects.Self)
            listOf(Ability.Triggered(Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self))), Instructions(steps = listOf(
                Step.Choose(Choice.Objects(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Battlefield), ObjectFilter.ControlledBy(PlayerFilter.Is(defending)))),
                    Value.Fixed(keyword.amount), Value.Fixed(keyword.amount)), bind = "annihilated", chooser = defending),
                Step.Do(Effect.Sacrifice(Objects.Bound("annihilated")))))))
        }
        // CR 702.112a: "When this creature deals combat damage to a player, if it isn't renowned, put N +1/+1 counters on
        // it and it becomes renowned."
        is Keyword.Renown -> listOf(Ability.Triggered(Trigger.On(combatDamageToPlayer),
            steps(Effect.PutCounters(Objects.Self, CounterKind.PowerToughness(1, 1), Value.Fixed(keyword.amount)), Effect.Mark(Objects.Self, ObjectMark.Renowned)),
            interveningIf = Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasStatus(Status.Marked(ObjectMark.Renowned))))))
        // CR 702.130a: "Whenever this creature becomes blocked, defending player loses N life."
        is Keyword.Afflict -> listOf(Ability.Triggered(Trigger.On(EventPattern.BecomesBlocked(ObjectFilter.Is(Objects.Self), ObjectFilter.Any)),
            steps(Effect.LoseLife(Players.DefendingPlayerOf(Objects.Self), Value.Fixed(keyword.amount)))))
        // CR 702.135a: "When this permanent is put into a graveyard from the battlefield, create N 1/1 white and black
        // Spirit creature tokens with flying."
        is Keyword.Afterlife -> listOf(Ability.Triggered(diesSelf, steps(Effect.CreateToken(TokenSpec.Defined(FaceDefinition("Spirit Token",
            colorIndicator = listOf(Color.White, Color.Black), types = listOf(CardType.Creature), subtypes = listOf(Subtype("Spirit")),
            power = PrintedNumber.Fixed(1), toughness = PrintedNumber.Fixed(1), abilities = listOf(Keyword.Flying))),
            count = Value.Fixed(keyword.amount), controller = Players.You))))
        // CR 702.67a: "[Cost]: Attach this Fortification to target land you control. Activate only as a sorcery."
        is Keyword.Fortify -> listOf(Ability.Activated(keyword.cost,
            Instructions(
                targets = listOf(TargetSlot("land", TargetKind.Objects(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Land),
                    ObjectFilter.ControlledBy(PlayerFilter.You)))), min = Value.Fixed(1), max = Value.Fixed(1))),
                steps = listOf(Step.Do(Effect.Attach(Objects.Self, Recipients(objects = Objects.Target("land")))))),
            ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You)))
        // rtr chunk 04: CR 702.97a: "[Cost], Exile this card from your graveyard: Put a number of +1/+1 counters equal to the power
        // of the card you exiled on target creature. Activate only as a sorcery." It functions only in a graveyard.
        is Keyword.Scavenge -> listOf(Ability.Activated(Cost(keyword.cost.parts + CostPart.Exile(CostObjects.Self, from = Zone.Graveyard, preBind = "scavenged")),
            Instructions(
                targets = listOf(TargetSlot("creature", TargetKind.Objects(ObjectFilter.Type(CardType.Creature)), min = Value.Fixed(1), max = Value.Fixed(1))),
                steps = listOf(Step.Do(Effect.PutCounters(Objects.Target("creature"), CounterKind.PowerToughness(1, 1),
                    Value.Of(NumericProperty.Power, Objects.Bound("scavenged"), Combine.Single))))),
            ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You), functionsIn = listOf(Zone.Graveyard)))
        // rtr chunk 04: CR 702.98a: "You may have this permanent enter with an additional +1/+1 counter on it" and "This permanent
        // can't block as long as it has a +1/+1 counter on it."
        Keyword.Unleash -> listOf(
            Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(CounterKind.PowerToughness(1, 1), Value.Fixed(1))), optional = true)),
                affects = Affected.Objects(Objects.Self)),
            Ability.Static(listOf(Mechanic.CantBlock(ObjectFilter.Any)), affects = Affected.Objects(Objects.Self),
                condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.PowerToughness(1, 1), atLeast = 1))))
        // CR 702.107a: "[Cost], {T}: Put a +1/+1 counter on this creature. Activate only as a sorcery."
        is Keyword.Outlast -> listOf(Ability.Activated(Cost(keyword.cost.parts + CostPart.TapSelf),
            steps(Effect.PutCounters(Objects.Self, CounterKind.PowerToughness(1, 1), Value.Fixed(1))),
            ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You)))
        // CR 702.77a: from the hand, "[Cost], Discard this card: Put N +1/+1 counters on target creature."
        is Keyword.Reinforce -> listOf(Ability.Activated(Cost(keyword.cost.parts + CostPart.Discard(CostObjects.Self)), Instructions(
            targets = listOf(TargetSlot("creature", TargetKind.Objects(ObjectFilter.Type(CardType.Creature)), min = Value.Fixed(1), max = Value.Fixed(1))),
            steps = listOf(Step.Do(Effect.PutCounters(Objects.Target("creature"), CounterKind.PowerToughness(1, 1), Value.Fixed(keyword.amount))))),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)))
        // CR 702.176a: "You may choose to pay [cost] rather than pay this spell's mana cost," "If you chose to pay this
        // permanent's impending cost, it enters with N time counters on it," "As long as this permanent's impending cost
        // was paid and it has a time counter on it, it's not a creature," and "At the beginning of your end step, if this
        // permanent's impending cost was paid and it has a time counter on it, remove a time counter from it."
        is Keyword.Impending -> {
            val time = CounterKind.Time
            val paid = Condition.Paid(Keyword.Impending.Kind, times = 1, of = Objects.Self)
            val waiting = Condition.And(listOf(paid, Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(time, atLeast = 1))))
            listOf(
                Ability.Static(listOf(Mechanic.AlternativeCost(Keyword.Impending.Kind, keyword.cost)), affects = Affected.Objects(Objects.Self)),
                Ability.Static(listOf(entersWith(time, Value.Fixed(keyword.amount))), condition = paid, affects = Affected.Objects(Objects.Self)),
                Ability.Static(listOf(Mechanic.SetTypes(remove = listOf(CardType.Creature))), condition = waiting, affects = Affected.Objects(Objects.Self)),
                Ability.Triggered(Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.You)),
                    steps(Effect.RemoveCounters(Objects.Self, time, Value.Fixed(1))), interveningIf = waiting))
        }
        // gtc chunk 03: CR 702.101a: "Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and
        // you gain life equal to the total life lost this way."
        Keyword.Extort -> listOf(Ability.Triggered(Trigger.On(EventPattern.Cast(ObjectFilter.Any, by = PlayerFilter.You)), Instructions(steps = listOf(
            Step.Pay(Cost.mana(ManaCost.parse("{W/B}")!!), payer = Players.You, bind = "extorted"),
            Step.If(Condition.Did("extorted"), then = listOf(
                Step.Do(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), Value.Fixed(1), bind = "lost")),
                Step.Do(Effect.GainLife(Players.You, Value.Bound("lost")))))))))
        // gtc chunk 04: CR 702.100a: "Whenever a creature you control enters, if that creature's power is greater than this
        // creature's power and/or that creature's toughness is greater than this creature's toughness, put a +1/+1 counter
        // on this creature." (the entered creature as it last existed if it has left, CR 603.10a)
        Keyword.Evolve -> {
            val entered = Objects.EventObject(EventRole.After)
            fun greater(p: NumericProperty) = Condition.Compare(Value.Of(p, entered, Combine.Single), Comparison.Greater, Value.Of(p, Objects.Self, Combine.Single))
            listOf(Ability.Triggered(
                Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Creature), ObjectFilter.ControlledBy(PlayerFilter.You))), to = Zone.Battlefield)),
                steps(Effect.PutCounters(Objects.Self, CounterKind.PowerToughness(1, 1), Value.Fixed(1))),
                interveningIf = Condition.Or(listOf(greater(NumericProperty.Power), greater(NumericProperty.Toughness)))))
        }
        else -> null
    }

    /**
     * What a keyword action does, as steps (CR 701). Actions the engine performs directly (proliferate, populate,
     * explore, amass, venture, …) return null.
     */
    fun expand(action: KeywordAction): List<Step>? = when (action) {
        // CR 701.16a: create a Clue token (CR 111.10f)
        is KeywordAction.Investigate -> listOf(Step.Do(Effect.CreateToken(TokenSpec.Predefined("Clue"), count = action.count, controller = Players.You)))
        // CR 701.50a: draw N, then discard N; for each nonland card discarded, put a +1/+1 counter on the conniving creature
        is KeywordAction.Connive -> listOf(Step.ForEach(Each.Objects(action.objects), "conniver", listOf(
            Step.Do(Effect.Draw(Players.ControllerOf(Objects.Bound("conniver")), action.count)),
            Step.Do(Effect.Discard(Players.ControllerOf(Objects.Bound("conniver")), action.count, preBind = "connived", filter = ObjectFilter.Any)),
            Step.Do(Effect.PutCounters(Objects.Bound("conniver"), CounterKind.PowerToughness(1, 1), Value.Count(Objects.Where(
                Objects.Bound("connived"), ObjectFilter.Not(ObjectFilter.Type(CardType.Land)))))))))
        // CR 701.39a: put N +1/+1 counters on a creature with the least toughness among creatures you control
        is KeywordAction.Bolster -> {
            val mine = ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Creature), ObjectFilter.ControlledBy(PlayerFilter.You)))
            listOf(
                Step.Choose(Choice.Objects(ObjectFilter.And(listOf(mine, ObjectFilter.Toughness(Comparison.Equal,
                    Value.Of(NumericProperty.Toughness, Objects.Each(mine), Combine.Least)))), Value.Fixed(1), Value.Fixed(1)), bind = "bolstered", chooser = Players.You),
                Step.Do(Effect.PutCounters(Objects.Bound("bolstered"), CounterKind.PowerToughness(1, 1), action.count)))
        }
        else -> null
    }

    /** blb: what the chosen [player] gets from a gift (CR 702.174d–i). */
    private fun giftSteps(gift: Gifted, player: Players): List<Step> {
        fun token(subtype: String, power: Int) = TokenSpec.Defined(FaceDefinition("$subtype Token", colorIndicator = listOf(Color.Blue),
            types = listOf(CardType.Creature), subtypes = listOf(Subtype(subtype)), power = PrintedNumber.Fixed(power), toughness = PrintedNumber.Fixed(power)))
        val effect = when (gift) {
            Gifted.Food -> Effect.CreateToken(TokenSpec.Predefined("Food"), count = Value.Fixed(1), controller = player)
            Gifted.Card -> Effect.Draw(player, count = Value.Fixed(1))
            Gifted.TappedFish -> Effect.CreateToken(token("Fish", 1), count = Value.Fixed(1), controller = player, tapped = true)
            Gifted.ExtraTurn -> Effect.ExtraTurn(player)
            Gifted.Treasure -> Effect.CreateToken(TokenSpec.Predefined("Treasure"), count = Value.Fixed(1), controller = player)
            Gifted.Octopus -> Effect.CreateToken(token("Octopus", 8), count = Value.Fixed(1), controller = player)
        }
        return listOf(Step.Do(effect))
    }

    private val FADE = CounterKind.Named("fade")
    private val combatDamageToPlayer = EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(players = PlayerFilter.Any), combat = true)

    /** "It gets +[power]/+[toughness] until end of turn." */
    private fun pump(power: Int, toughness: Int) = steps(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(power), Value.Fixed(toughness))),
        Affected.Objects(Objects.Self), Duration.EndOfTurn))

    /** "When this Equipment enters, create [token], then attach this Equipment to it." (for Mirrodin!, job select) */
    private fun createAndAttach(token: FaceDefinition) = Ability.Triggered(entersSelf, Instructions(steps = listOf(
        Step.Do(Effect.CreateToken(TokenSpec.Defined(token), bind = "token", count = Value.Fixed(1), controller = Players.You)),
        Step.Do(Effect.Attach(Objects.Self, Recipients(objects = Objects.Bound("token")))))))

    private val entersSelf = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield))
    private val diesSelf = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard))
    private val lowerManaValue = ObjectFilter.ManaValue(Comparison.Less, Value.Of(NumericProperty.ManaValue, Objects.Self, Combine.Single))

    private fun entersWith(kind: CounterKind, amount: Value) = Mechanic.Replacement(
        EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersWithCounters(kind, amount)))

    /** Persist and undying: return the new card with [counter] if the creature that died had none of them. */
    private fun returnFromGraveyard(counter: CounterKind) = Ability.Triggered(diesSelf,
        steps(Effect.Move(Objects.EventObject(EventRole.After), Destination(Zone.Battlefield,
            controller = Players.OwnerOf(Objects.EventObject(EventRole.After)), counters = listOf(CountersToPut(counter, amount = Value.Fixed(1)))))),
        interveningIf = Condition.Not(Condition.ObjectMatches(Objects.EventObject(EventRole.Before), ObjectFilter.HasCounter(counter, atLeast = 1))))

    /** CR 702.49a, d: "[Cost], Reveal this card, Return an unblocked attacking creature you control to its owner's hand:
     * Put this card onto the battlefield tapped and attacking" (the same player or planeswalker as the returned creature). */
    private fun ninjutsu(cost: Cost, zones: List<Zone>) = Ability.Activated(
        Cost(cost.parts + CostPart.Reveal(CostObjects.Self) + CostPart.ReturnToHand(CostObjects.Choose(ObjectFilter.And(listOf(
            ObjectFilter.Type(CardType.Creature), ObjectFilter.ControlledBy(PlayerFilter.You),
            ObjectFilter.HasStatus(Status.Attacking), ObjectFilter.HasStatus(Status.Unblocked))), count = Value.Fixed(1)), preBind = "returned")),
        steps(Effect.Move(Objects.Self, Destination(Zone.Battlefield, tapped = true, attacking = AttackTarget.SameAs(Objects.Bound("returned"))))),
        functionsIn = zones, rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You))

    /** CR 702.37a–b: cast face down for {3}; turn face up for [cost] (megamorph: and put a +1/+1 counter on it). */
    private fun morph(cost: Cost, megamorph: Boolean): List<Ability> = listOf(
        Ability.Static(listOf(Mechanic.AlternativeCost(Keyword.Morph, Cost.mana(ManaCost.parse("{3}")!!), faceDown = true)), affects = Affected.Objects(Objects.Self)),
        Ability.SpecialAction(cost, steps(Effect.TurnFaceUp(Objects.Self,
            counters = if (megamorph) listOf(CountersToPut(CounterKind.PowerToughness(1, 1), amount = Value.Fixed(1))) else emptyList())),
            functionsIn = listOf(Zone.Battlefield),
            condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasStatus(Status.FaceDown)), timing = Timing.Any))

    /**
     * [ability] with the rules its [AbilityLabel] adds (CR 702.142a, 702.169b–d, 702.177a, 702.178a, 702.193a). Power-up's
     * cost reduction ("reduced by this permanent's mana cost if it entered this turn") is applied by the engine when the
     * cost is determined; "activate only once" is added here.
     */
    fun withLabelRules(ability: Ability): Ability = when (ability) {
        is Ability.Activated -> when (ability.label) {
            null -> ability
            AbilityLabel.Exhaust, AbilityLabel.PowerUp -> ability.copy(rules = ability.rules.copy(once = true))
            AbilityLabel.Boast -> ability.copy(rules = ability.rules.copy(perTurn = 1,
                condition = and(ability.rules.condition, attackedThisTurn)))
            AbilityLabel.Solved -> ability.copy(rules = ability.rules.copy(condition = and(ability.rules.condition, solved)))
            AbilityLabel.MaxSpeed -> ability.copy(rules = ability.rules.copy(condition = and(ability.rules.condition, Condition.MaxSpeed)))
        }
        is Ability.Triggered -> when (ability.label) {
            AbilityLabel.Solved -> ability.copy(triggersOnlyIf = and(ability.triggersOnlyIf, solved))
            AbilityLabel.MaxSpeed -> ability.copy(triggersOnlyIf = and(ability.triggersOnlyIf, Condition.MaxSpeed))
            else -> ability
        }
        is Ability.Static -> when (ability.label) {
            AbilityLabel.Solved -> ability.copy(condition = and(ability.condition, solved))
            AbilityLabel.MaxSpeed -> ability.copy(condition = and(ability.condition, Condition.MaxSpeed))
            else -> ability
        }
        else -> ability
    }

    private val attackedThisTurn = Condition.ObjectMatches(Objects.Self, ObjectFilter.ThisTurn(EventPattern.Attack(ObjectFilter.Is(Objects.Self))))
    private val solved = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasStatus(Status.Marked(ObjectMark.Solved)))
    private fun and(existing: Condition?, added: Condition) = if (existing == null) added else Condition.And(listOf(existing, added))

    private fun static(vararg mechanics: Mechanic) = listOf(Ability.Static(mechanics.toList(), affects = Affected.Objects(Objects.Self)))
    private fun steps(vararg effects: Effect) = Instructions(steps = effects.map { Step.Do(it) })
    private fun has(kind: KeywordKind) = ObjectFilter.HasKeyword(kind)
    private fun or(vararg filters: ObjectFilter) = ObjectFilter.Or(filters.toList())
    private fun cycling(cost: Cost, draw: Effect?, steps: List<Step> = listOfNotNull(draw?.let { Step.Do(it) })) =
        Ability.Activated(Cost(cost.parts + CostPart.Discard(CostObjects.Self)), Instructions(steps = steps), rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You))
}
