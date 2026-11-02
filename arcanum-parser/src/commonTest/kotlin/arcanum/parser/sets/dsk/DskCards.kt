package arcanum.parser.sets.dsk

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.KeywordAction
import arcanum.api.Layout
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Comparison
import arcanum.api.Effect
import arcanum.api.Zone
import arcanum.parser.OracleCard
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces

/**
 * Shared shapes of the Duskmourn: House of Horror (dsk) parser tests: Rooms (CR 709.5), eerie, survival, delirium,
 * manifest dread.
 */
internal object Dsk {
    /** The reminder line every Room half carries (CR 709.5d, e). */
    const val ROOM_REMINDER = "(You may cast either half. That door unlocks on the battlefield. As a sorcery, you may pay the mana cost of a locked door to unlock it.)"

    /** One half of a Room: "Enchantment — Room", its text and the Room reminder line. */
    fun door(name: String, cost: String, text: String): OracleCard =
        card(name = name, cost = cost, type = "Enchantment — Room", text = text.trimIndent().trim() + "\n" + ROOM_REMINDER)

    /** A Room (CR 709.5): a split card whose halves share the type line. */
    fun room(left: Pair<OracleCard, List<Ability>>, right: Pair<OracleCard, List<Ability>>) = cardFaces(Layout.Split, left, right)

    /** "When you unlock this door" (CR 709.5h). */
    val thisDoorUnlocks = Trigger.On(EventPattern.Unlock(ObjectFilter.Is(Objects.Self), thisDoor = true))

    /** "When you unlock this door, [steps]." */
    fun whenYouUnlockThisDoor(vararg steps: Step, targets: List<TargetSlot> = emptyList()) =
        Ability.Triggered(thisDoorUnlocks, Instructions(targets = targets, steps = steps.toList()))

    val creature = ObjectFilter.Type(CardType.Creature)
    val enchantment = ObjectFilter.Type(CardType.Enchantment)
    val youControl = ObjectFilter.ControlledBy(PlayerFilter.You)
    val creatureYouControl = ObjectFilter.And(listOf(creature, youControl))
    val roomsYouControl = ObjectFilter.And(listOf(ObjectFilter.Sub(Subtype("Room")), youControl))

    /** Eerie: "Whenever an enchantment you control enters and whenever you fully unlock a Room" (CR 709.5i). */
    val eerie = Trigger.On(listOf(
        EventPattern.ZoneChange(ObjectFilter.And(listOf(enchantment, youControl)), to = Zone.Battlefield),
        EventPattern.Unlock(roomsYouControl, fully = true),
    ))

    /** An eerie ability: "Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, …". */
    fun eerie(vararg steps: Step, targets: List<TargetSlot> = emptyList(), interveningIf: Condition? = null) = Ability.Triggered(
        trigger = eerie,
        instructions = Instructions(targets = targets, steps = steps.toList()),
        interveningIf = interveningIf,
        abilityWord = "Eerie",
    )

    /** "At the beginning of your second main phase": the first postcombat main phase of the turn (CR 505.1a, b). */
    val yourSecondMainPhase = Trigger.On(listOf(EventPattern.StepBegins(TurnStep.PostcombatMain, PlayerFilter.You)), nthThisTurn = 1)

    /** Survival: "At the beginning of your second main phase, if this creature is tapped, …". */
    fun survival(vararg steps: Step, targets: List<TargetSlot> = emptyList(), once: Boolean = false) = Ability.Triggered(
        trigger = yourSecondMainPhase,
        instructions = Instructions(targets = targets, steps = steps.toList()),
        interveningIf = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasStatus(arcanum.api.Status.Tapped)),
        once = once,
        abilityWord = "Survival",
    )

    /** Delirium: "there are four or more card types among cards in your graveyard". */
    val delirium: Condition = Condition.Compare(
        Value.CountDistinct(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))),
            SharedQuality.CardType),
        Comparison.AtLeast,
        Value.Fixed(4),
    )

    /** "[players] manifest(s) dread" (CR 701.62a), naming the manifested creature [bind] ("that creature"). */
    fun manifestDread(players: Players = Players.You, bind: String? = null): Step =
        Step.Do(Effect.Action(KeywordAction.ManifestDread(players), bind = bind))

    /** A creature token (CR 111.4) as the parser writes one: subtypes plus "Token" as its name, colours as indicator. */
    fun token(
        subtypes: List<String>, colors: List<Color>, power: Int, toughness: Int, types: List<CardType> = listOf(CardType.Creature),
        abilities: List<Ability> = emptyList(),
    ) = TokenSpec.Defined(FaceDefinition(
        name = subtypes.joinToString(" ") + " Token",
        colorIndicator = colors,
        types = types,
        subtypes = subtypes.map { Subtype(it) },
        power = PrintedNumber.Fixed(power),
        toughness = PrintedNumber.Fixed(toughness),
        abilities = abilities,
    ))

    /** "a 1/1 white Glimmer enchantment creature token". */
    val glimmer = token(listOf("Glimmer"), listOf(Color.White), 1, 1, listOf(CardType.Enchantment, CardType.Creature))

    /** "Create [count] [token]." */
    fun create(token: TokenSpec, count: Int = 1, tapped: Boolean = false) =
        Step.Do(Effect.CreateToken(token, count = Value.Fixed(count), controller = Players.You, tapped = tapped))
}
