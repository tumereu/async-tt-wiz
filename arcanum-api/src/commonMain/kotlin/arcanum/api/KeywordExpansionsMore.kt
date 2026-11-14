package arcanum.api

/**
 * More of the shared keyword table ([KeywordExpansions]): keywords that used to be done by hand, each written out from its
 * CR definition with the same building blocks. [KeywordExpansions.expand] asks here for the keywords it doesn't list.
 */
internal object KeywordExpansionsMore {
    fun expand(keyword: Keyword): List<Ability>? = when (keyword) {
        // CR 702.140a: "You may pay [cost] rather than pay this spell's mana cost. If you do, it becomes a mutating creature spell and
        // targets a non-Human creature with the same owner as this spell." (the target and the merging are the engine's, CR 702.140b–f)
        is Keyword.Mutate -> listOf(alternative(Keyword.Mutate.Kind, keyword.cost))
        // CR 702.106c, f: "Any time you have priority, you may turn a face-down conspiracy card you control in the command zone face
        // up" (a special action, CR 116.2j), revealing the chosen name(s); putting it into the command zone face down and choosing the
        // name(s) is the engine's, as the game starts
        Keyword.HiddenAgenda, Keyword.DoubleAgenda -> listOf(Ability.SpecialAction(Cost(emptyList()),
            Instructions(steps = listOf(Step.Do(Effect.TurnFaceUp(Objects.Self)))), timing = Timing.Any, functionsIn = listOf(Zone.Command),
            condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasStatus(Status.FaceDown)),
            text = "Turn this conspiracy face up, revealing the chosen name"))
        // CR 702.55a: on a permanent "When this permanent is put into a graveyard from the battlefield, exile it haunting target creature";
        // on an instant or sorcery "When this spell is put into a graveyard during its resolution, exile it haunting target creature"
        Keyword.Haunt -> {
            val target = TargetSlot("haunted", TargetKind.Objects(ObjectFilter.Type(CardType.Creature)), min = Value.Fixed(1), max = Value.Fixed(1))
            val haunt = Instructions(targets = listOf(target), steps = listOf(Step.Do(Effect.Haunt(Objects.EventObject(EventRole.After), Objects.Target("haunted")))))
            listOf(
                Ability.Triggered(Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard)), haunt),
                Ability.Triggered(Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Stack, to = Zone.Graveyard, resolving = true)),
                    haunt),
            )
        }
        // CR 702.95a: "When this creature enters, if you control both this creature and another creature and both are unpaired, you may
        // pair this creature with another unpaired creature you control for as long as both remain creatures on the battlefield under
        // your control" and "Whenever another creature you control enters, if you control both that creature and this one and both are
        // unpaired, you may pair that creature with this creature …"
        Keyword.Soulbond -> {
            val unpairedYours = ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Creature), ObjectFilter.ControlledBy(PlayerFilter.You),
                ObjectFilter.Not(ObjectFilter.Paired)))
            val other = ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), unpairedYours))
            val entered = Objects.EventObject(EventRole.After)
            listOf(
                Ability.Triggered(Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
                    Instructions(steps = listOf(Step.May(listOf(Step.Do(Effect.Pair(Objects.Self, Objects.Each(other)))), Players.You))),
                    interveningIf = Condition.And(listOf(Condition.ObjectMatches(Objects.Self, unpairedYours), Condition.Exists(other, 1)))),
                Ability.Triggered(Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)),
                    ObjectFilter.Type(CardType.Creature), ObjectFilter.ControlledBy(PlayerFilter.You))), to = Zone.Battlefield)),
                    Instructions(steps = listOf(Step.May(listOf(Step.Do(Effect.Pair(Objects.Self, entered))), Players.You))),
                    interveningIf = Condition.And(listOf(Condition.ObjectMatches(Objects.Self, unpairedYours), Condition.ObjectMatches(entered, unpairedYours)))),
            )
        }
        // CR 702.154a, b: the optional cost to attack is the engine's (a declared attacker's enlisted creature, CR 508.1g); the linked
        // trigger: "When you do, this creature gets +X/+0 until end of turn, where X is the tapped creature's power."
        Keyword.Enlist -> listOf(Ability.Triggered(Trigger.On(EventPattern.Enlists(ObjectFilter.Is(Objects.Self))), steps(Effect.ApplyMechanics(
            listOf(Mechanic.ModifyPowerToughness(Value.Of(NumericProperty.Power, Objects.EventObject(EventRole.OtherCombatant), Combine.Single), Value.Fixed(0))),
            self, Duration.EndOfTurn))))
        // CR 702.109a: "You may cast this card by paying [cost] rather than its mana cost," "If this spell's dash cost was paid, return
        // the permanent this spell becomes to its owner's hand at the beginning of the next end step," and "As long as this permanent's
        // dash cost was paid, it has haste." (the return as an end step trigger of the permanent: the first end step after it entered)
        is Keyword.Dash -> {
            val paid = paid(Keyword.Dash.Kind)
            listOf(
                alternative(Keyword.Dash.Kind, keyword.cost),
                Ability.Static(listOf(Mechanic.GainAbilities(listOf(Keyword.Haste))), condition = paid, affects = self),
                Ability.Triggered(nextEndStep, steps(Effect.Move(Objects.Self, Destination(Zone.Hand))), interveningIf = paid))
        }
        // CR 702.152a: "You may cast this card by paying [cost] rather than its mana cost," "If this spell's blitz cost was paid, sacrifice
        // the permanent this spell becomes at the beginning of the next end step," and "As long as this permanent's blitz cost was paid, it
        // has haste and 'When this permanent is put into a graveyard from the battlefield, draw a card.'"
        is Keyword.Blitz -> {
            val paid = paid(Keyword.Blitz.Kind)
            listOf(
                alternative(Keyword.Blitz.Kind, keyword.cost),
                Ability.Static(listOf(Mechanic.GainAbilities(listOf(Keyword.Haste,
                    Ability.Triggered(diesSelf, steps(Effect.Draw(Players.You, Value.Fixed(1))))))), condition = paid, affects = self),
                Ability.Triggered(nextEndStep, steps(Effect.Sacrifice(Objects.Self)), interveningIf = paid))
        }
        // CR 702.30a: "At the beginning of your upkeep, if this permanent came under your control since the beginning of your last
        // upkeep, sacrifice it unless you pay [cost]." (it enters marked as due; the upkeep that asks for the echo removes the mark)
        is Keyword.Echo -> listOf(
            Ability.Static(listOf(Mechanic.Replacement(entersSelfEvent, modify = listOf(Modification.EntersMarked(ECHO_DUE)))), affects = self),
            Ability.Triggered(yourUpkeep, Instructions(steps = listOf(
                Step.Do(Effect.Mark(Objects.Self, ECHO_DUE, remove = true)),
                Step.Pay(keyword.cost, payer = Players.You, bind = "echo"),
                Step.If(Condition.Not(Condition.Did("echo")), listOf(Step.Do(Effect.Sacrifice(Objects.Self)))))),
                interveningIf = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasStatus(Status.Marked(ECHO_DUE)))))
        // CR 702.24a: "At the beginning of your upkeep, if this permanent is on the battlefield, put an age counter on this permanent. Then
        // you may pay [cost] for each age counter on it. If you don't, sacrifice it."
        is Keyword.CumulativeUpkeep -> {
            val ages = Value.Of(NumericProperty.Counters(AGE), Objects.Self, Combine.Single)
            listOf(Ability.Triggered(yourUpkeep, Instructions(steps = listOf(
                Step.Do(Effect.PutCounters(Objects.Self, AGE, Value.Fixed(1))),
                Step.Pay(times(keyword.cost, ages), payer = Players.You, bind = "upkeep"),
                Step.If(Condition.Not(Condition.Did("upkeep")), listOf(Step.Do(Effect.Sacrifice(Objects.Self))))))))
        }
        // CR 702.128a: "[Cost], Exile this card from your graveyard: Create a token that's a copy of this card, except it's white, it has
        // no mana cost, and it's a Zombie in addition to its other types. Activate only as a sorcery."
        is Keyword.Embalm -> listOf(fromGraveyard(keyword.cost, "embalmed", listOf(
            Step.Do(Effect.CreateToken(TokenSpec.CopyOf(Objects.Bound("embalmed"), except = listOf(CopyException.Colors(listOf(ColorRef.Literal(Color.White))),
                CopyException.ManaCostOf(null), zombie)), count = Value.Fixed(1), controller = Players.You)))))
        // CR 702.129a: "… except it's black, it's 4/4, it has no mana cost, and it's a Zombie in addition to its other types."
        is Keyword.Eternalize -> listOf(fromGraveyard(keyword.cost, "eternalized", listOf(
            Step.Do(Effect.CreateToken(TokenSpec.CopyOf(Objects.Bound("eternalized"), except = listOf(CopyException.Colors(listOf(ColorRef.Literal(Color.Black))),
                CopyException.PowerToughness(4, 4), CopyException.ManaCostOf(null), zombie)), count = Value.Fixed(1), controller = Players.You)))))
        // CR 702.141a: "[Cost], Exile this card from your graveyard: For each opponent, create a token that's a copy of this card that
        // attacks that opponent this turn if able. The tokens gain haste. Sacrifice them at the beginning of the next end step. Activate
        // only as a sorcery."
        is Keyword.Encore -> listOf(fromGraveyard(keyword.cost, "encored", listOf(
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "encore-opponent", listOf(
                Step.Do(Effect.CreateToken(TokenSpec.CopyOf(Objects.Bound("encored")), count = Value.Fixed(1), controller = Players.You, bind = "encore")),
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MustAttack(Recipients(players = Players.Bound("encore-opponent")))),
                    Affected.Objects(Objects.Bound("encore")), Duration.EndOfTurn)))),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Haste))), Affected.Objects(Objects.Bound("encore")), Duration.Indefinitely)),
            Step.Do(Effect.CreateDelayedTrigger(nextEndStep, steps(Effect.Sacrifice(Objects.Bound("encore"))))))))
        // CR 702.181a: "Whenever this creature attacks, create N 1/1 red Warrior creature tokens. Those tokens enter tapped and attacking.
        // Sacrifice them at the beginning of the next end step."
        is Keyword.Mobilize -> listOf(Ability.Triggered(attacks, Instructions(steps = listOf(
            Step.Do(Effect.CreateToken(TokenSpec.Defined(WARRIOR), count = Value.Fixed(keyword.amount), controller = Players.You, tapped = true,
                attacking = AttackTarget.Chosen, bind = "mobilized")),
            Step.Do(Effect.CreateDelayedTrigger(nextEndStep, steps(Effect.Sacrifice(Objects.Bound("mobilized")))))))))
        // CR 702.116a: "Whenever this creature attacks, for each opponent other than defending player, you may create a token that's a
        // copy of this creature that's tapped and attacking that player or a planeswalker they control. If one or more tokens are created
        // this way, exile the tokens at end of combat." (attacking that player)
        Keyword.Myriad -> listOf(Ability.Triggered(attacks, Instructions(steps = listOf(
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.And(listOf(PlayerFilter.Opponent,
                PlayerFilter.Not(PlayerFilter.Is(Players.DefendingPlayerOf(Objects.Self))))))), "myriad-opponent", listOf(
                Step.May(listOf(Step.Do(Effect.CreateToken(TokenSpec.CopyOf(Objects.Self), count = Value.Fixed(1), controller = Players.You, tapped = true,
                    attacking = AttackTarget.These(Recipients(players = Players.Bound("myriad-opponent"))), bind = "myriad"))), player = Players.You))),
            Step.If(Condition.NonEmpty("myriad"), listOf(Step.Do(Effect.CreateDelayedTrigger(
                Trigger.On(EventPattern.StepBegins(TurnStep.EndOfCombat, PlayerFilter.Any)), steps(Effect.Move(Objects.Bound("myriad"), Destination(Zone.Exile)))))))))))
        // CR 702.121a: "Whenever this creature attacks, it gets +1/+1 until end of turn for each opponent you attacked with a creature
        // this combat."
        Keyword.Melee -> {
            val attacked = Value.CountPlayers(Players.Each(PlayerFilter.And(listOf(PlayerFilter.Opponent,
                PlayerFilter.Is(Players.DefendingPlayerOf(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.HasStatus(Status.Attacking),
                    ObjectFilter.ControlledBy(PlayerFilter.You))))))))))
            listOf(Ability.Triggered(attacks, steps(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(attacked, attacked)),
                Affected.Objects(Objects.Self), Duration.EndOfTurn))))
        }
        // CR 702.105a: "Whenever this creature attacks the player with the most life or tied for most life, put a +1/+1 counter on this
        // creature."
        Keyword.Dethrone -> listOf(Ability.Triggered(Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self), target = RecipientFilter(
            players = PlayerFilter.Life(Comparison.AtLeast, Value.GreatestFor(Players.Each(PlayerFilter.Any), Value.LifeOf(Players.Tested)))))),
            steps(Effect.PutCounters(Objects.Self, CounterKind.PowerToughness(1, 1), Value.Fixed(1)))))
        // CR 702.39a: "Whenever this creature attacks, you may choose to have target creature defending player controls block this
        // creature this combat if able. If you do, untap that creature."
        Keyword.Provoke -> listOf(Ability.Triggered(attacks, Instructions(
            targets = listOf(TargetSlot("provoked", TargetKind.Objects(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Creature),
                ObjectFilter.ControlledBy(PlayerFilter.Is(Players.DefendingPlayerOf(Objects.Self)))))), min = Value.Fixed(1), max = Value.Fixed(1))),
            steps = listOf(Step.May(listOf(
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MustBlock(ObjectFilter.Is(Objects.Self))), Affected.Objects(Objects.Target("provoked")),
                    Duration.EndOfCombat)),
                Step.Do(Effect.Untap(Objects.Target("provoked")))), player = Players.You)))))
        // CR 702.23a: "Whenever this creature becomes blocked, it gets +N/+N until end of turn for each creature blocking it beyond the
        // first." (worked out once, as it resolves, 702.23b)
        is Keyword.Rampage -> {
            val beyond = Value.Times(Value.Difference(Value.Count(Objects.Each(ObjectFilter.Blocking(Objects.Self))), Value.Fixed(1)), keyword.amount)
            listOf(Ability.Triggered(Trigger.On(EventPattern.BecomesBlocked(ObjectFilter.Is(Objects.Self), ObjectFilter.Any)),
                steps(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(beyond, beyond)), Affected.Objects(Objects.Self), Duration.EndOfTurn))))
        }
        // CR 702.46a: "When this permanent is put into a graveyard from the battlefield, you may return target Spirit card with mana value N
        // or less from your graveyard to your hand."
        is Keyword.Soulshift -> listOf(Ability.Triggered(diesSelf, Instructions(
            targets = listOf(TargetSlot("spirit", TargetKind.Objects(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.You), ObjectFilter.Category(ObjectCategory.Card), ObjectFilter.Sub(Subtype("Spirit")),
                ObjectFilter.ManaValue(Comparison.AtMost, Value.Fixed(keyword.amount))))), min = Value.Fixed(1), max = Value.Fixed(1))),
            steps = listOf(Step.May(listOf(Step.Do(Effect.Move(Objects.Target("spirit"), Destination(Zone.Hand)))), player = Players.You)))))
        // CR 702.54a: "If an opponent was dealt damage this turn, this permanent enters with N +1/+1 counters on it."
        is Keyword.Bloodthirst -> listOf(Ability.Static(listOf(Mechanic.Replacement(entersSelfEvent, modify = listOf(
            Modification.EntersWithCounters(CounterKind.PowerToughness(1, 1), Value.Fixed(keyword.amount))))),
            condition = Condition.HappenedThisTurn(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(players = PlayerFilter.Opponent)),
                atLeast = 1, measure = Measure.Events), affects = self))
        // CR 702.115a: "Whenever this creature deals combat damage to a player, that player exiles the top card of their library."
        Keyword.Ingest -> listOf(Ability.Triggered(Trigger.On(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(players = PlayerFilter.Any),
            combat = true)), steps(Effect.Move(Objects.LibraryCards(Players.EventPlayer, Value.Fixed(1), fromTop = true), Destination(Zone.Exile)))))
        // CR 702.125a: "This spell costs {1} less to cast for each opponent you have."
        Keyword.Undaunted -> listOf(Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self),
            CostChange.Less(ManaCost(listOf(ManaSymbol.Generic(1))), Value.CountPlayers(Players.Each(PlayerFilter.Opponent))))), affects = self))
        // CR 702.131a, b: on a permanent, "Any time you control ten or more permanents and you don't have the city's blessing, you get the
        // city's blessing for the rest of the game"; on an instant or sorcery, a spell ability: "If you control ten or more permanents and
        // you don't have the city's blessing, you get the city's blessing for the rest of the game."
        Keyword.Ascend -> {
            val ten = Condition.Exists(ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Permanent), ObjectFilter.ControlledBy(PlayerFilter.You))), atLeast = 10)
            listOf(
                Ability.Static(listOf(Mechanic.GetsDesignation(Designation.CitysBlessing)), affects = Affected.Players(Players.You), condition = ten),
                Ability.Spell(Instructions(steps = listOf(Step.If(ten, listOf(Step.Do(Effect.GiveDesignation(Players.You, Designation.CitysBlessing))))))))
        }
        // CR 702.69a: "When you cast this spell, copy it for each permanent that was put into a graveyard from the battlefield this turn.
        // If the spell has any targets, you may choose new targets for any of the copies."
        Keyword.Gravestorm -> listOf(Ability.Triggered(castSelf, Instructions(steps = listOf(Step.Repeat(
            Value.EventsThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, from = Zone.Battlefield, to = Zone.Graveyard), Measure.Objects),
            listOf(Step.Do(Effect.CopySpell(Objects.Self, Players.You, newTargets = true))))))))
        // CR 702.56a: "As an additional cost to cast this spell, you may pay [cost] any number of times" and "When you cast this spell, if
        // a replicate cost was paid for it, copy it for each time its replicate cost was paid. If the spell has any targets, you may choose
        // new targets for any of the copies."
        is Keyword.Replicate -> listOf(
            Ability.Static(listOf(Mechanic.AdditionalCost(Keyword.Replicate.Kind, keyword.cost, optional = true, repeatable = true)), affects = self),
            Ability.Triggered(castSelf, Instructions(steps = listOf(Step.Repeat(Value.TimesPaid(Keyword.Replicate.Kind, Objects.Self),
                listOf(Step.Do(Effect.CopySpell(Objects.Self, Players.You, newTargets = true)))))),
                interveningIf = paid(Keyword.Replicate.Kind)))
        // CR 702.157a: "As an additional cost to cast this spell, you may pay [cost] any number of times" and "When this creature enters,
        // if its squad cost was paid, create a token that's a copy of it for each time its squad cost was paid."
        is Keyword.Squad -> listOf(
            Ability.Static(listOf(Mechanic.AdditionalCost(Keyword.Squad.Kind, keyword.cost, optional = true, repeatable = true)), affects = self),
            Ability.Triggered(entersSelf, steps(Effect.CreateToken(TokenSpec.CopyOf(Objects.Self), count = Value.TimesPaid(Keyword.Squad.Kind, Objects.Self),
                controller = Players.You)), interveningIf = paid(Keyword.Squad.Kind)))
        // CR 702.166a: "As an additional cost to cast this spell, you may sacrifice an artifact, enchantment, or token."
        Keyword.Bargain -> listOf(Ability.Static(listOf(Mechanic.AdditionalCost(Keyword.Bargain, Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(
            ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Artifact), ObjectFilter.Type(CardType.Enchantment), ObjectFilter.Category(ObjectCategory.Token))),
            Value.Fixed(1))))), optional = true)), affects = self))
        // CR 702.144a: "When you cast this spell, you may copy it. If you do, choose an opponent to also copy it. Players who copy this
        // spell may choose new targets for their copies."
        Keyword.Demonstrate -> listOf(Ability.Triggered(castSelf, Instructions(steps = listOf(
            Step.Pay(steps = listOf(Step.Do(Effect.CopySpell(Objects.Self, Players.You, newTargets = true))), payer = Players.You, bind = "demonstrated"),
            Step.If(Condition.Did("demonstrated"), listOf(
                Step.Choose(Choice.Player(PlayerFilter.Opponent), bind = "demonstrate-opponent", chooser = Players.You),
                Step.Do(Effect.CopySpell(Objects.Self, Players.Bound("demonstrate-opponent"), newTargets = true))))))))
        // CR 702.137a: "You may cast this spell for [cost] if an opponent lost life this turn" (rather than its mana cost)
        is Keyword.Spectacle -> listOf(alternative(Keyword.Spectacle.Kind, keyword.cost,
            Condition.HappenedThisTurn(EventPattern.LoseLife(PlayerFilter.Opponent), atLeast = 1, measure = Measure.Events)))
        // CR 702.117a: "You may cast this spell by paying [cost] rather than its mana cost if you or one of your teammates has cast another
        // spell this turn."
        is Keyword.Surge -> listOf(alternative(Keyword.Surge.Kind, keyword.cost,
            Condition.HappenedThisTurn(EventPattern.Cast(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), PlayerFilter.You), atLeast = 1, measure = Measure.Events)))
        // CR 702.76a: "You may cast this by paying [cost] rather than paying its mana cost if a player was dealt combat damage this turn by a
        // source that, at the time it dealt that damage, was under your control and had any of this spell's creature types."
        is Keyword.Prowl -> listOf(alternative(Keyword.Prowl.Kind, keyword.cost, Condition.HappenedThisTurn(EventPattern.Damage(
            ObjectFilter.And(listOf(ObjectFilter.ControlledBy(PlayerFilter.You), ObjectFilter.Shares(SharedQuality.CreatureType, Objects.Self))),
            RecipientFilter(players = PlayerFilter.Any), combat = true), atLeast = 1, measure = Measure.Events)))
        // CR 702.173a: "You may cast this spell by paying [cost] rather than its mana cost if a player was dealt combat damage this turn by
        // a source that, at the time it dealt that damage, was under your control and was an Assassin or a commander."
        is Keyword.Freerunning -> listOf(alternative(Keyword.Freerunning.Kind, keyword.cost, Condition.HappenedThisTurn(EventPattern.Damage(
            ObjectFilter.And(listOf(ObjectFilter.ControlledBy(PlayerFilter.You), ObjectFilter.Sub(Subtype("Assassin")))),
            RecipientFilter(players = PlayerFilter.Any), combat = true), atLeast = 1, measure = Measure.Events)))
        // CR 702.146a, b: "You may cast this card transformed from your graveyard by paying [cost] rather than its mana cost." (the back
        // face's "If it would be put into a graveyard from anywhere, exile it instead" is printed there)
        is Keyword.Disturb -> listOf(Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, cost = keyword.cost, transformed = true,
            holder = PlayerFilter.You)), affects = self))
        // CR 702.167a, c: "[Cost], Exile this permanent, Exile [materials] from among permanents you control and/or cards in your graveyard:
        // Return this card to the battlefield transformed under its owner's control. Activate only as a sorcery." (the returned permanent
        // keeps the exiled materials under the keyword's id)
        is Keyword.Craft -> listOf(Ability.Activated(
            Cost(keyword.cost.parts + CostPart.Exile(CostObjects.Self, Zone.Battlefield, postBind = CRAFTED) + keyword.materials.map {
                CostPart.Exile(it, Zone.Battlefield, remember = Keyword.Craft.Kind.id, alsoFrom = listOf(Zone.Graveyard))
            }),
            steps(Effect.Move(Objects.Bound(CRAFTED), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound(CRAFTED)),
                transformed = true, keeps = listOf(Keyword.Craft.Kind.id)))),
            ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You)))
        // CR 702.59a: "When a creature is put into your graveyard from the battlefield, you may pay [cost]. If you do, return this card from
        // your graveyard to your hand. Otherwise, exile this card." (functions only in the graveyard)
        is Keyword.Recover -> listOf(Ability.Triggered(Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Creature),
            ObjectFilter.OwnedBy(PlayerFilter.You))), from = Zone.Battlefield, to = Zone.Graveyard)), Instructions(steps = listOf(
            Step.Pay(keyword.cost, payer = Players.You, bind = "recovered"),
            Step.If(Condition.Did("recovered"), listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Hand)))),
                otherwise = listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Exile))))))), functionsIn = listOf(Zone.Graveyard)))
        // CR 702.53a: "[Cost], Discard this card: Search your library for a card with the same mana value as the discarded card, reveal that
        // card, and put it into your hand. Then shuffle your library. Activate only as a sorcery."
        is Keyword.Transmute -> listOf(Ability.Activated(Cost(keyword.cost.parts + CostPart.Discard(CostObjects.Self, preBind = "transmuted")), Instructions(steps = listOf(
            Step.Do(Effect.Search(Players.You, ObjectFilter.ManaValue(Comparison.Equal, Value.Of(NumericProperty.ManaValue, Objects.Bound("transmuted"), Combine.Single)),
                max = Value.Fixed(1), reveal = true, zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
            Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Hand))),
            Step.Do(Effect.Shuffle(Players.You)))),
            ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You), functionsIn = listOf(Zone.Hand)))
        // CR 702.136a: "You may have this permanent enter with an additional +1/+1 counter on it. If you don't, it gains haste."
        Keyword.Riot -> listOf(Ability.Static(listOf(Mechanic.Replacement(entersSelfEvent, modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.Option(listOf(RIOT_COUNTER, RIOT_HASTE)), bind = "riot", chooser = Players.You),
            Step.If(Condition.Chose("riot", RIOT_COUNTER), listOf(Step.Modify(Modification.EntersWithCounters(CounterKind.PowerToughness(1, 1), Value.Fixed(1)))),
                otherwise = listOf(Step.Modify(Modification.EntersWithAbilities(listOf(Keyword.Haste)))))))))), affects = self))
        // CR 702.156a: "This permanent enters with X +1/+1 counters on it" and "When this permanent enters, if X is 5 or more, draw a card."
        Keyword.Ravenous -> listOf(
            Ability.Static(listOf(Mechanic.Replacement(entersSelfEvent, modify = listOf(Modification.EntersWithCounters(CounterKind.PowerToughness(1, 1),
                Value.CastX(Objects.Self))))), affects = self),
            Ability.Triggered(entersSelf, steps(Effect.Draw(Players.You, Value.Fixed(1))),
                interveningIf = Condition.Compare(Value.CastX(Objects.Self), Comparison.AtLeast, Value.Fixed(5))))
        // CR 702.38a: "As this object enters, reveal any number of cards from your hand that share a creature type with it. This permanent
        // enters with N +1/+1 counters on it for each card revealed this way."
        is Keyword.Amplify -> listOf(Ability.Static(listOf(Mechanic.Replacement(entersSelfEvent, modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.Objects(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You),
                ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), ObjectFilter.Shares(SharedQuality.CreatureType, Objects.Self))), Value.Fixed(0), null),
                bind = "amplified", chooser = Players.You),
            Step.Do(Effect.Reveal(Objects.Bound("amplified"))),
            Step.Modify(Modification.EntersWithCounters(CounterKind.PowerToughness(1, 1),
                Value.Times(Value.Count(Objects.Bound("amplified")), keyword.amount)))))))), affects = self))
        // CR 702.58a: "This permanent enters with N +1/+1 counters on it" and "Whenever another creature enters, if this permanent has a
        // +1/+1 counter on it, you may move a +1/+1 counter from this permanent onto that creature."
        is Keyword.Graft -> listOf(
            Ability.Static(listOf(Mechanic.Replacement(entersSelfEvent, modify = listOf(Modification.EntersWithCounters(CounterKind.PowerToughness(1, 1),
                Value.Fixed(keyword.amount))))), affects = self),
            Ability.Triggered(Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Creature),
                ObjectFilter.Not(ObjectFilter.Is(Objects.Self)))), to = Zone.Battlefield)),
                Instructions(steps = listOf(Step.May(listOf(Step.Do(Effect.MoveCounters(Objects.Self, Objects.EventObject(EventRole.After),
                    CounterKind.PowerToughness(1, 1), Value.Fixed(1)))), player = Players.You))),
                interveningIf = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.PowerToughness(1, 1), atLeast = 1))))
        // CR 702.171a: "Tap any number of other untapped creatures you control with total power N or more: This permanent becomes saddled
        // until end of turn. Activate only as a sorcery." (saddled ends in the cleanup step)
        is Keyword.Saddle -> listOf(Ability.Activated(Cost(listOf(CostPart.TapObjects(CostObjects.Choose(otherUntappedCreatureYouControl, count = null,
            total = Total(NumericProperty.PowerFor(Keyword.Saddle.Kind), Value.Fixed(keyword.amount)))))), steps(Effect.Mark(Objects.Self, ObjectMark.Saddled)),
            ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You)))
        // CR 702.124j: "When this permanent enters, target player may search their library for a card named [name], reveal it, put it into
        // their hand, then shuffle."
        is Keyword.PartnerWith -> listOf(Ability.Triggered(entersSelf, Instructions(
            targets = listOf(TargetSlot("partner-player", TargetKind.Players(PlayerFilter.Any), min = Value.Fixed(1), max = Value.Fixed(1))),
            steps = listOf(Step.May(listOf(
                Step.Do(Effect.Search(Players.Target("partner-player"), ObjectFilter.Named(keyword.partner), max = Value.Fixed(1), reveal = true,
                    zones = listOf(Zone.Library), bind = "partner", searcher = Players.Target("partner-player"))),
                Step.Do(Effect.Move(Objects.Bound("partner"), Destination(Zone.Hand))),
                Step.Do(Effect.Shuffle(Players.Target("partner-player")))), player = Players.Target("partner-player"))))))
        // CR 702.172a, 702.183a: spree ("choose one or more modes") and tiered ("choose one") are in the spell's modes, each mode's
        // cost an additional cost of the spell when chosen ([Mode.extraCost])
        Keyword.Spree, Keyword.Tiered -> emptyList()
        // CR 702.42a: "You may choose all modes of this spell instead of just the number specified. If you do, you pay an additional
        // [cost]." (the engine lets all modes be chosen; the cost is added once more than one is)
        is Keyword.Entwine -> listOf(Ability.Static(listOf(Mechanic.AdditionalCost(Keyword.Entwine.Kind, keyword.cost,
            times = Value.Min(listOf(Value.Fixed(1), Value.Difference(Value.ModesChosen, Value.Fixed(1)))))), affects = self))
        // CR 702.168a, d: "You may cast this card as a 2/2 face-down creature with ward {2}, no name, no subtypes, and no mana cost by
        // paying {3} rather than paying its mana cost"; turned face up for its disguise cost as a special action (ward {2} while face
        // down: the engine's face-down values for a disguised permanent)
        is Keyword.Disguise -> listOf(
            Ability.Static(listOf(Mechanic.AlternativeCost(Keyword.Disguise.Kind, Cost.mana(ManaCost.parse("{3}")!!), faceDown = true)), affects = self),
            Ability.SpecialAction(keyword.cost, steps(Effect.TurnFaceUp(Objects.Self, x = KeywordExpansions.turnedUpX(keyword.cost))), timing = Timing.Any, functionsIn = listOf(Zone.Battlefield),
                condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasStatus(Status.FaceDown)), costChange = keyword.costChange))
        // CR 702.170a, b: "Any time you have priority during your main phase while the stack is empty, you may exile this card from your
        // hand and pay [cost]. It becomes a plotted card." (a special action; casting a plotted card, 702.170d, is the engine's)
        is Keyword.Plot -> listOf(Ability.SpecialAction(keyword.cost, Instructions(steps = listOf(
            Step.Do(Effect.Move(Objects.Self, Destination(Zone.Exile))),
            Step.Do(Effect.Mark(Objects.Self, ObjectMark.Plotted)))), timing = Timing.Sorcery, functionsIn = listOf(Zone.Hand)))
        // CR 702.113a, b: "You may pay [cost] rather than pay this spell's mana cost as you cast this spell" and "If this spell's awaken
        // cost was paid, put N +1/+1 counters on target land you control. That land becomes a 0/0 Elemental creature with haste. It's
        // still a land." (the target only when the awaken cost is paid)
        is Keyword.Awaken -> {
            val paid = paid(Keyword.Awaken.Kind)
            val land = Objects.Target("awakened")
            listOf(
                alternative(Keyword.Awaken.Kind, keyword.cost),
                Ability.Spell(Instructions(
                    targets = listOf(TargetSlot("awakened", TargetKind.Objects(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Land),
                        ObjectFilter.ControlledBy(PlayerFilter.You)))), min = Value.Fixed(1), max = Value.Fixed(1), onlyIf = paid)),
                    steps = listOf(Step.If(paid, listOf(
                        Step.Do(Effect.PutCounters(land, CounterKind.PowerToughness(1, 1), Value.Fixed(keyword.amount))),
                        Step.Do(Effect.ApplyMechanics(listOf(
                            Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Elemental")))),
                            Mechanic.SetPowerToughness(Value.Fixed(0), Value.Fixed(0)), Mechanic.GainAbilities(listOf(Keyword.Haste))),
                            Affected.Objects(land), Duration.Indefinitely)))))),
                ))
        }
        // CR 702.104a, b: "As this creature enters, choose an opponent. That player may put an additional N +1/+1 counters on it as it
        // enters." (it enters marked when they do: "if tribute wasn't paid" reads that)
        is Keyword.Tribute -> listOf(Ability.Static(listOf(Mechanic.Replacement(entersSelfEvent, modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.Player(PlayerFilter.Opponent), bind = "tribute-opponent", chooser = Players.You),
            Step.May(listOf(
                Step.Modify(Modification.EntersWithCounters(CounterKind.PowerToughness(1, 1), Value.Fixed(keyword.amount))),
                Step.Modify(Modification.EntersMarked(TRIBUTE_PAID))), player = Players.Bound("tribute-opponent"))))))), affects = self))
        // CR 702.119a, c: "You may cast this spell by paying [cost] and sacrificing a creature rather than paying its mana cost" and "If
        // you chose to pay this spell's emerge cost, its total cost is reduced by an amount of generic mana equal to the sacrificed
        // creature's mana value." (the spell's own reduction reads the creature chosen for the cost, CR 601.2f)
        // mh3: CR 702.119b: "Emerge from [quality]": a permanent of that quality is sacrificed instead
        is Keyword.Emerge -> listOf(
            alternative(Keyword.Emerge.Kind, Cost(keyword.cost.parts + CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.And(listOf(
                keyword.from ?: ObjectFilter.Type(CardType.Creature), ObjectFilter.ControlledBy(PlayerFilter.You))), Value.Fixed(1)), preBind = EMERGED))),
            Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost(listOf(ManaSymbol.Generic(1))),
                Value.Of(NumericProperty.ManaValue, Objects.Bound(EMERGED), Combine.Total)))), affects = self))
        // CR 702.180a, b: "You may cast this card from your graveyard by paying [cost] and tapping up to one untapped creature you control
        // rather than paying this spell's mana cost," "If you cast this spell using its harmonize ability, its total cost is reduced by an
        // amount of generic mana equal to the tapped creature's power," and "If the harmonize cost was paid, exile this card instead of
        // putting it anywhere else any time it would leave the stack."
        is Keyword.Harmonize -> listOf(
            Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, cost = keyword.cost, then = Destination(Zone.Exile), holder = PlayerFilter.You,
                additionalCost = Cost(listOf(CostPart.TapObjects(CostObjects.Choose(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Creature),
                    ObjectFilter.ControlledBy(PlayerFilter.You), ObjectFilter.Not(ObjectFilter.HasStatus(Status.Tapped)))), Value.Fixed(1), upTo = true),
                    bind = HARMONIZED))))), affects = self),
            Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost(listOf(ManaSymbol.Generic(1))),
                Value.Of(NumericProperty.Power, Objects.Bound(HARMONIZED), Combine.Total)))), affects = self))
        // CR 702.160a, 718.3: cast prototyped for its prototype cost, with that mana cost, power and toughness as it is on the Stack and the
        // battlefield (the engine's copiable values of a spell cast this way)
        is Keyword.Prototype -> listOf(alternative(Keyword.Prototype.Kind, keyword.cost))
        // CR 702.127a: "You may cast this half of this split card from your graveyard," "This half of this split card can't be cast from any
        // zone other than a graveyard," and "If this spell was cast from a graveyard, exile it instead of putting it anywhere else any time
        // it would leave the stack." (the engine keeps each half to its zone)
        Keyword.Aftermath -> listOf(Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, then = Destination(Zone.Exile),
            holder = PlayerFilter.You)), affects = self))
        // CR 702.60a: "When you cast this spell, you may reveal the top N cards of your library … you may cast any of those cards with the
        // same name as this spell without paying their mana costs, then put all revealed cards not cast this way on the bottom of your
        // library in any order."
        is Keyword.Ripple -> listOf(Ability.Triggered(castSelf, Instructions(steps = listOf(Step.May(listOf(
            Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, Value.Fixed(keyword.amount), fromTop = true), bind = "rippled")),
            Step.Choose(Choice.Objects(ObjectFilter.SameNameAs(Objects.Self), Value.Fixed(0), null, from = Objects.Bound("rippled")), bind = "ripple-same",
                chooser = Players.You),
            Step.ForEach(Each.Objects(Objects.Bound("ripple-same")), "ripple-one", listOf(
                Step.Do(Effect.Cast(Objects.Bound("ripple-one"), PlayKind.Cast, withoutPaying = true, caster = Players.You)))),
            Step.Do(Effect.Move(Objects.Where(Objects.Bound("rippled"), ObjectFilter.InZone(Zone.Library)),
                Destination(Zone.Library, libraryPosition = -1)))), player = Players.You)))))
        else -> null
    }

    private const val EMERGED = "emerged"
    private const val HARMONIZED = "harmonized"

    /** A creature whose tribute was paid (CR 702.104b). */
    val TRIBUTE_PAID: ObjectMark = ObjectMark.Named("tribute paid")

    /** A mark for a permanent with echo that came under its controller's control since their last upkeep. */
    private val ECHO_DUE = ObjectMark.Named("echo")
    private val AGE = CounterKind.Named("age")
    private const val RIOT_COUNTER = "+1/+1 counter"
    private const val RIOT_HASTE = "haste"

    /** The token mobilize creates (CR 702.181a): a 1/1 red Warrior creature token. */
    private val WARRIOR = FaceDefinition("Warrior Token", colorIndicator = listOf(Color.Red), types = listOf(CardType.Creature),
        subtypes = listOf(Subtype("Warrior")), power = PrintedNumber.Fixed(1), toughness = PrintedNumber.Fixed(1))

    private val zombie = CopyException.Types(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Zombie"))))
    private val self = Affected.Objects(Objects.Self)
    private val entersSelfEvent = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)
    private val entersSelf = Trigger.On(entersSelfEvent)
    private val diesSelf = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard))
    private val castSelf = Trigger.On(EventPattern.Cast(ObjectFilter.Is(Objects.Self), by = PlayerFilter.Any))
    private val attacks = Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self)))
    private val yourUpkeep = Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You))
    /** "At the beginning of the next end step", as a trigger of the permanent: its first end step after it entered. */
    private val nextEndStep = Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any))
    private val otherUntappedCreatureYouControl = ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Creature), ObjectFilter.ControlledBy(PlayerFilter.You),
        ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), ObjectFilter.Not(ObjectFilter.HasStatus(Status.Tapped))))

    /** The card a craft ability exiled from the battlefield as its cost (CR 702.167a). */
    private const val CRAFTED = "crafted"

    private fun paid(keyword: KeywordKind) = Condition.Paid(keyword, times = 1, of = Objects.Self)
    private fun steps(vararg effects: Effect) = Instructions(steps = effects.map { Step.Do(it) })

    private fun alternative(keyword: KeywordKind, cost: Cost, condition: Condition? = null) =
        Ability.Static(listOf(Mechanic.AlternativeCost(keyword, cost)), condition = condition, affects = self)

    /** "[Cost], Exile this card from your graveyard: [steps] Activate only as a sorcery." (the exiled card named [bind]) */
    private fun fromGraveyard(cost: Cost, bind: String, steps: List<Step>) = Ability.Activated(
        Cost(cost.parts + CostPart.Exile(CostObjects.Self, Zone.Graveyard, postBind = bind)), Instructions(steps = steps),
        ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You), functionsIn = listOf(Zone.Graveyard))

    /** [cost] paid [times] times (its mana parts; cumulative upkeep's "for each age counter"). */
    private fun times(cost: Cost, times: Value) = Cost(cost.parts.map { if (it is CostPart.Mana && it.times == null) it.copy(times = times) else it })
}
