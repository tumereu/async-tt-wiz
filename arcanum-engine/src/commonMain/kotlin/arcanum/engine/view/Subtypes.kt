package arcanum.engine.view

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Subtype

/** The set a subtype belongs to (CR 205.3g–m): it corresponds to card types (CR 205.3d). */
sealed interface SubtypeKind {
    /** The card types this kind of subtype goes with. */
    val types: List<CardType>

    data object Creature : SubtypeKind { override val types = listOf(CardType.Creature, CardType.Kindred) }
    data object Land : SubtypeKind { override val types = listOf(CardType.Land) }
    data object Artifact : SubtypeKind { override val types = listOf(CardType.Artifact) }
    data object Enchantment : SubtypeKind { override val types = listOf(CardType.Enchantment) }
    data object Planeswalker : SubtypeKind { override val types = listOf(CardType.Planeswalker) }
    data object Spell : SubtypeKind { override val types = listOf(CardType.Instant, CardType.Sorcery) }
    data object Battle : SubtypeKind { override val types = listOf(CardType.Battle) }
    data object Dungeon : SubtypeKind { override val types = listOf(CardType.Dungeon) }
}

/** The subtype lists of the pinned rules (CR 205.3g–m); creature types are every subtype not in another list. */
object Subtypes {
    /** CR 205.3g */
    private val ARTIFACT = setOf(
        "Attraction", "Blood", "Bobblehead", "Book", "Clue", "Contraption", "Equipment", "Food", "Fortification", "Gold",
        "Heartwood", "Incubator", "Infinity", "Junk", "Lander", "Map", "Mutagen", "Powerstone", "Spacecraft", "Stone",
        "Treasure", "Vehicle", "Vibranium",
    )
    /** CR 205.3h */
    private val ENCHANTMENT = setOf(
        "Aura", "Background", "Cartouche", "Case", "Class", "Curse", "Plan", "Role", "Room", "Rune", "Saga", "Shard", "Shrine",
    )
    /** CR 205.3i */
    private val LAND = setOf(
        "Cave", "Desert", "Forest", "Gate", "Island", "Lair", "Locus", "Mine", "Mountain", "Plains", "Planet", "Power-Plant",
        "Sphere", "Swamp", "Tower", "Town", "Urza's",
    )
    /** CR 205.3k */
    private val SPELL = setOf("Adventure", "Arcane", "Lesson", "Omen", "Trap")
    /** CR 205.3j */
    private val PLANESWALKER = setOf(
        "Ajani", "Aminatou", "Angrath", "Arlinn", "Ashiok", "Bahamut", "Basri", "Bolas", "Calix", "Chandra", "Comet", "Dack",
        "Dakkon", "Daretti", "Davriel", "Dellian", "Dihada", "Domri", "Dovin", "Ellywick", "Elminster", "Elspeth", "Estrid",
        "Freyalise", "Garruk", "Gideon", "Grist", "Guff", "Huatli", "Jace", "Jared", "Jaya", "Jeska", "Kaito", "Karn",
        "Kasmina", "Kaya", "Kiora", "Koth", "Liliana", "Lolth", "Lukka", "Minsc", "Mordenkainen", "Nahiri", "Narset", "Niko",
        "Nissa", "Nixilis", "Oko", "Quintorius", "Ral", "Rowan", "Saheeli", "Samut", "Sarkhan", "Serra", "Sivitri", "Sorin",
        "Szat", "Tamiyo", "Tasha", "Teferi", "Teyo", "Tezzeret", "Tibalt", "Tyvar", "Ugin", "Urza", "Venser", "Vivien",
        "Vraska", "Vronos", "Will", "Windgrace", "Wrenn", "Xenagos", "Yanggu", "Yanling", "Zariel",
    )
    /** Battle types (CR 205.3q). */
    private val BATTLE = setOf("Siege")
    /** Dungeon types (CR 205.3p). */
    private val DUNGEON = setOf("Undercity")

    /** The basic land types and the mana their intrinsic ability adds (CR 305.6). */
    val basicLandTypes: Map<Subtype, Color> = mapOf(
        Subtype("Plains") to Color.White, Subtype("Island") to Color.Blue, Subtype("Swamp") to Color.Black,
        Subtype("Mountain") to Color.Red, Subtype("Forest") to Color.Green,
    )

    /** tla: how many creature types a changeling counts as having (CR 205.3m lists over 250; more than any "to a maximum of"). */
    const val creatureTypeCount: Int = 250

    fun kindOf(subtype: Subtype): SubtypeKind = when (subtype.name) {
        in LAND -> SubtypeKind.Land
        in ARTIFACT -> SubtypeKind.Artifact
        in ENCHANTMENT -> SubtypeKind.Enchantment
        in SPELL -> SubtypeKind.Spell
        in PLANESWALKER -> SubtypeKind.Planeswalker
        in BATTLE -> SubtypeKind.Battle
        in DUNGEON -> SubtypeKind.Dungeon
        else -> SubtypeKind.Creature
    }
}
