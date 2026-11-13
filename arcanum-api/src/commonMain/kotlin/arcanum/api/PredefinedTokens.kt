package arcanum.api

/**
 * The predefined tokens of CR 111.10, which [TokenSpec.Predefined] names: their full definitions, written in the
 * model. Tokens the rules don't define yet return null.
 */
object PredefinedTokens {
    fun face(name: String): FaceDefinition? = when (name) {
        // CR 111.10a: "{T}, Sacrifice this token: Add one mana of any color."
        "Treasure" -> artifact("Treasure", Ability.Activated(Cost(listOf(CostPart.TapSelf, sacrificeSelf)),
            steps(Effect.AddMana(ManaProduction(ManaKind.Choose(Value.Fixed(1))), players = Players.You)), rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)))
        // CR 111.10b: "{2}, {T}, Sacrifice this token: You gain 3 life."
        "Food" -> artifact("Food", Ability.Activated(Cost(listOf(mana("{2}"), CostPart.TapSelf, sacrificeSelf)),
            steps(Effect.GainLife(Players.You, Value.Fixed(3))), rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)))
        // CR 111.10c: "Sacrifice this token: Add one mana of any color."
        "Gold" -> artifact("Gold", Ability.Activated(Cost(listOf(sacrificeSelf)),
            steps(Effect.AddMana(ManaProduction(ManaKind.Choose(Value.Fixed(1))), players = Players.You)), rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)))
        // CR 111.10d: a 2/2 black Zombie creature token named Walker
        "Walker" -> FaceDefinition("Walker", colorIndicator = listOf(Color.Black), types = listOf(CardType.Creature),
            subtypes = listOf(Subtype("Zombie")), power = PrintedNumber.Fixed(2), toughness = PrintedNumber.Fixed(2))
        // CR 111.10e: a Shard enchantment token with "{2}, Sacrifice this token: Scry 1, then draw a card."
        "Shard" -> FaceDefinition("Shard Token", colorIndicator = emptyList(), types = listOf(CardType.Enchantment),
            subtypes = listOf(Subtype("Shard")), abilities = listOf(Ability.Activated(Cost(listOf(mana("{2}"), sacrificeSelf)),
                steps(Effect.Scry(Players.You, Value.Fixed(1)), Effect.Draw(Players.You, count = Value.Fixed(1))), rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You))))
        // CR 111.10f: "{2}, Sacrifice this token: Draw a card."
        "Clue" -> artifact("Clue", Ability.Activated(Cost(listOf(mana("{2}"), sacrificeSelf)), steps(Effect.Draw(Players.You, count = Value.Fixed(1))), rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)))
        // CR 111.10g: "{1}, {T}, Discard a card, Sacrifice this token: Draw a card."
        "Blood" -> artifact("Blood", Ability.Activated(
            Cost(listOf(mana("{1}"), CostPart.TapSelf, CostPart.Discard(CostObjects.Choose(ObjectFilter.Any, count = Value.Fixed(1))), sacrificeSelf)),
            steps(Effect.Draw(Players.You, count = Value.Fixed(1))), rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)))
        // fra: CR 111.10x: a red and green Heartwood artifact token with "{T}: Add {R} or {G}."
        "Heartwood" -> FaceDefinition("Heartwood Token", colorIndicator = listOf(Color.Red, Color.Green), types = listOf(CardType.Artifact),
            subtypes = listOf(Subtype("Heartwood")), abilities = listOf(Ability.Activated(Cost(listOf(CostPart.TapSelf)),
                steps(Effect.AddMana(ManaProduction(ManaKind.Choose(Value.Fixed(1), colors = listOf(ColorRef.Literal(Color.Red), ColorRef.Literal(Color.Green)))),
                    players = Players.You)), rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You))))
        // tmt chunk 01: CR 111.10v: a Mutagen artifact token with "{1}, {T}, Sacrifice this token: Put a +1/+1 counter on target
        // creature. Activate only as a sorcery."
        "Mutagen" -> artifact("Mutagen", Ability.Activated(Cost(listOf(mana("{1}"), CostPart.TapSelf, sacrificeSelf)),
            Instructions(targets = listOf(TargetSlot("creature", TargetKind.Objects(ObjectFilter.Type(CardType.Creature)), min = Value.Fixed(1), max = Value.Fixed(1))),
                steps = listOf(Step.Do(Effect.PutCounters(Objects.Target("creature"), CounterKind.PowerToughness(1, 1), Value.Fixed(1))))),
            rules = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You)))
        // lci chunk 03: CR 111.10s: a Map artifact token with "{1}, {T}, Sacrifice this token: Target creature you control explores.
        // Activate only as a sorcery."
        "Map" -> artifact("Map", Ability.Activated(Cost(listOf(mana("{1}"), CostPart.TapSelf, sacrificeSelf)),
            Instructions(targets = listOf(TargetSlot("creature", TargetKind.Objects(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Creature),
                ObjectFilter.ControlledBy(PlayerFilter.You)))), min = Value.Fixed(1), max = Value.Fixed(1))),
                steps = listOf(Step.Do(Effect.Action(KeywordAction.Explore(Objects.Target("creature")))))),
            rules = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You)))
        // eoe: CR 111.10u: a Lander artifact token with "{2}, {T}, Sacrifice this token: Search your library for a basic land card, put
        // it onto the battlefield tapped, then shuffle."
        "Lander" -> artifact("Lander", Ability.Activated(Cost(listOf(mana("{2}"), CostPart.TapSelf, sacrificeSelf)), Instructions(steps = listOf(
            Step.Do(Effect.Search(Players.You, ObjectFilter.And(listOf(ObjectFilter.Super(Supertype.Basic), ObjectFilter.Type(CardType.Land))),
                max = Value.Fixed(1), bind = "found", zones = listOf(Zone.Library), searcher = Players.You)),
            Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield, tapped = true))),
            Step.Do(Effect.Shuffle(Players.You)))), rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)))
        else -> null
    }

    /** Every name [face] knows. */
    val names: List<String> = listOf("Treasure", "Food", "Gold", "Walker", "Shard", "Clue", "Blood", "Heartwood", "Mutagen", "Lander", "Map")

    private val sacrificeSelf = CostPart.Sacrifice(CostObjects.Self)
    private fun mana(text: String) = CostPart.Mana(ManaCost.parse(text)!!)
    private fun steps(vararg effects: Effect) = Instructions(steps = effects.map { Step.Do(it) })
    private fun artifact(subtype: String, ability: Ability) = FaceDefinition("$subtype Token", colorIndicator = emptyList(),
        types = listOf(CardType.Artifact), subtypes = listOf(Subtype(subtype)), abilities = listOf(ability))
}
