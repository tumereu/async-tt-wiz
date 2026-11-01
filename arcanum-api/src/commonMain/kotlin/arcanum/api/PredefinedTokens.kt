package arcanum.api

/**
 * The predefined tokens of CR 111.10, which [TokenSpec.Predefined] names: their full definitions, written in the
 * model. Tokens the rules don't define yet return null.
 */
object PredefinedTokens {
    fun face(name: String): FaceDefinition? = when (name) {
        // CR 111.10a: "{T}, Sacrifice this token: Add one mana of any color."
        "Treasure" -> artifact("Treasure", Ability.Activated(Cost(listOf(CostPart.TapSelf, sacrificeSelf)),
            steps(Effect.AddMana(ManaProduction(ManaKind.Choose(Value.Fixed(1)))))))
        // CR 111.10b: "{2}, {T}, Sacrifice this token: You gain 3 life."
        "Food" -> artifact("Food", Ability.Activated(Cost(listOf(mana("{2}"), CostPart.TapSelf, sacrificeSelf)),
            steps(Effect.GainLife(Players.You, Value.Fixed(3)))))
        // CR 111.10c: "Sacrifice this token: Add one mana of any color."
        "Gold" -> artifact("Gold", Ability.Activated(Cost(listOf(sacrificeSelf)),
            steps(Effect.AddMana(ManaProduction(ManaKind.Choose(Value.Fixed(1)))))))
        // CR 111.10d: a 2/2 black Zombie creature token named Walker
        "Walker" -> FaceDefinition("Walker", colorIndicator = listOf(Color.Black), types = listOf(CardType.Creature),
            subtypes = listOf(Subtype("Zombie")), power = PrintedNumber.Fixed(2), toughness = PrintedNumber.Fixed(2))
        // CR 111.10e: a Shard enchantment token with "{2}, Sacrifice this token: Scry 1, then draw a card."
        "Shard" -> FaceDefinition("Shard Token", colorIndicator = emptyList(), types = listOf(CardType.Enchantment),
            subtypes = listOf(Subtype("Shard")), abilities = listOf(Ability.Activated(Cost(listOf(mana("{2}"), sacrificeSelf)),
                steps(Effect.Scry(Players.You, Value.Fixed(1)), Effect.Draw(Players.You)))))
        // CR 111.10f: "{2}, Sacrifice this token: Draw a card."
        "Clue" -> artifact("Clue", Ability.Activated(Cost(listOf(mana("{2}"), sacrificeSelf)), steps(Effect.Draw(Players.You))))
        // CR 111.10g: "{1}, {T}, Discard a card, Sacrifice this token: Draw a card."
        "Blood" -> artifact("Blood", Ability.Activated(
            Cost(listOf(mana("{1}"), CostPart.TapSelf, CostPart.Discard(CostObjects.Choose(ObjectFilter.Any)), sacrificeSelf)),
            steps(Effect.Draw(Players.You))))
        else -> null
    }

    /** Every name [face] knows. */
    val names: List<String> = listOf("Treasure", "Food", "Gold", "Walker", "Shard", "Clue", "Blood")

    private val sacrificeSelf = CostPart.Sacrifice(CostObjects.Self)
    private fun mana(text: String) = CostPart.Mana(ManaCost.parse(text)!!)
    private fun steps(vararg effects: Effect) = Instructions(steps = effects.map { Step.Do(it) })
    private fun artifact(subtype: String, ability: Ability) = FaceDefinition("$subtype Token", colorIndicator = emptyList(),
        types = listOf(CardType.Artifact), subtypes = listOf(Subtype(subtype)), abilities = listOf(ability))
}
