package arcanum.parser.sets.vow

import arcanum.api.*
import arcanum.parser.OracleCard
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import kotlin.test.Test

/*
 * VOW chunk 03: Disturb (CR 702.146): a transform card cast from the graveyard transformed (CR 712), its back face exiled instead of going to a
 * graveyard (CR 702.146b, 614.1a).
 */
class VowChunk03DisturbTest {
    private val spirit = creatureToken("Spirit", listOf(Color.White), 1, 1, abilities = listOf(Keyword.Flying))
    private val auraSpell = and(sub("Aura"), spellFilter)
    private val judgment = CounterKind.Named("judgment")
    private val spiritsAndEnchantments = count(and(permanentFilter, you, or(sub("Spirit"), enchantment)))
    /** The same count where X is defined ("where X is the number of …"): an Each looks on the battlefield, where every object is a permanent. */
    private val spiritsAndEnchantmentsX = count(and(you, or(sub("Spirit"), enchantment)))
    private val asSpirit = listOf(CopyException.Types(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Spirit")))))
    private val untilEndOfCombat = stepBegins(TurnStep.EndOfCombat, PlayerFilter.Any)

    // CR 702.146a, 303.4a (an Aura spell targets what it will enchant), 603.2 (one ability, two events)
    @Test
    fun brineComber() = cardFaces(Layout.Transform,
        card(
            name = "Tidewash Wisp",
            cost = "{1}{W}{U}",
            type = "Creature — Spirit",
            pt = "1/1",
            text = """
                Whenever this creature enters or becomes the target of an Aura spell, create a 1/1 white Spirit creature token with flying.
                Disturb {W}{U} $disturbLine
            """,
        ) to abilities(
            triggered(Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                EventPattern.BecomesTarget(ObjectFilter.Is(Objects.Self), by = auraSpell))), create(spirit)),
            disturb("{W}{U}"),
        ),
        card(
            name = "Tidebound Offering",
            cost = null,
            type = "Enchantment — Aura",
            colorIndicator = "WU",
            text = """
                Enchant creature
                Whenever this Aura enters or enchanted creature becomes the target of an Aura spell, create a 1/1 white Spirit creature token with flying.
                If this Aura would be put into a graveyard from anywhere, exile it instead.
            """,
        ) to abilities(
            enchant(creature),
            triggered(Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                EventPattern.BecomesTarget(ObjectFilter.Is(enchanted), by = auraSpell))), create(spirit)),
            exileInstead,
        ),
    )

    // CR 603.4 (intervening if), 702.3b (attack despite defender), 303.4e/104.3a (enchanted player loses the game), 122.1
    @Test
    fun faithboundJudge() = cardFaces(Layout.Transform,
        card(
            name = "Oathsworn Arbiter",
            cost = "{1}{W}{W}",
            type = "Creature — Spirit Soldier",
            pt = "4/4",
            text = """
                Defender, flying, vigilance
                At the beginning of your upkeep, if this creature has two or fewer judgment counters on it, put a judgment counter on it.
                As long as this creature has three or more judgment counters on it, it can attack as though it didn't have defender.
                Disturb {5}{W}{W}
            """,
        ) to abilities(
            Keyword.Defender,
            Keyword.Flying,
            Keyword.Vigilance,
            triggered(yourUpkeep, counters(Objects.Self, 1, judgment),
                interveningIf = Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(judgment, atLeast = 3)))),
            static(Mechanic.CanAttackDespiteDefender, condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(judgment, atLeast = 3))),
            disturb("{5}{W}{W}"),
        ),
        card(
            name = "Wrongdoer's Verdict",
            cost = null,
            type = "Enchantment — Aura Curse",
            colorIndicator = "W",
            text = """
                Enchant player
                At the beginning of your upkeep, put a judgment counter on this Aura. Then if there are three or more judgment counters on it, enchanted player loses the game.
                If Wrongdoer's Verdict would be put into a graveyard from anywhere, exile it instead.
            """,
        ) to abilities(
            Keyword.Enchant(TargetKind.Players(PlayerFilter.Any)),
            triggered(yourUpkeep,
                counters(Objects.Self, 1, judgment),
                ifThen(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(judgment, atLeast = 3)),
                    doing(Effect.LoseGame(Players.AttachedTo(Objects.Self))))),
            exileInstead,
        ),
    )

    // CR 506.5 (attacking alone), 509.1b
    @Test
    fun gutterSkulker() = cardFaces(Layout.Transform,
        card(
            name = "Alley Lurker",
            cost = "{3}{U}",
            type = "Creature — Spirit",
            pt = "3/3",
            text = """
                This creature can't be blocked as long as it's attacking alone.
                Disturb {3}{U} $disturbLine
            """,
        ) to abilities(
            static(Mechanic.CantBeBlocked, condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.AttackingAlone)),
            disturb("{3}{U}"),
        ),
        card(
            name = "Alley Passage",
            cost = null,
            type = "Enchantment — Aura",
            colorIndicator = "U",
            text = """
                Enchant creature
                Enchanted creature can't be blocked as long as it's attacking alone.
                If Alley Passage would be put into a graveyard from anywhere, exile it instead.
            """,
        ) to abilities(
            enchant(creature),
            static(Mechanic.CantBeBlocked, affects = enchanted, condition = Condition.ObjectMatches(enchanted, ObjectFilter.AttackingAlone)),
            exileInstead,
        ),
    )

    // CR 604.3 (characteristic-defining), 702.16 (protection from a creature type), 613.4c, 205.3 ("Spirits and/or enchantments": either)
    @Test
    fun katildaDawnhartMartyr() = cardFaces(Layout.Transform,
        card(
            name = "Brenna, Duskward Martyr",
            cost = "{1}{W}{W}",
            type = "Legendary Creature — Spirit Warlock",
            pt = "*/*",
            text = """
                Flying, lifelink, protection from Vampires
                Brenna's power and toughness are each equal to the number of permanents you control that are Spirits and/or enchantments.
                Disturb {3}{W}{W} $disturbLine
            """,
        ) to abilities(
            Keyword.Flying,
            Keyword.Lifelink,
            Keyword.Protection(sub("Vampire")),
            static(Mechanic.SetPowerToughness(spiritsAndEnchantments, spiritsAndEnchantments), characteristicDefining = true),
            disturb("{3}{W}{W}"),
        ),
        card(
            name = "Brenna's Rising Light",
            cost = null,
            type = "Legendary Enchantment — Aura",
            colorIndicator = "W",
            text = """
                Enchant creature
                Enchanted creature has flying, lifelink, and protection from Vampires, and it gets +X/+X, where X is the number of permanents you control that are Spirits and/or enchantments.
                If Brenna's Rising Light would be put into a graveyard from anywhere, exile it instead.
            """,
        ) to abilities(
            enchant(creature),
            static(has(Keyword.Flying, Keyword.Lifelink, Keyword.Protection(sub("Vampire"))),
                Mechanic.ModifyPowerToughness(spiritsAndEnchantmentsX, spiritsAndEnchantmentsX), affects = enchanted),
            exileInstead,
        ),
    )

    // CR 707.9b (a copy with an extra subtype), 707.2 (copiable values of the enchanted creature), 111.1, 706.1 (enters as a copy)
    @Test
    fun mirrorhallMimic() = cardFaces(Layout.Transform,
        card(
            name = "Glasshall Mimic",
            cost = "{3}{U}",
            type = "Creature — Spirit",
            pt = "0/0",
            text = """
                You may have this creature enter as a copy of any creature on the battlefield, except it's a Spirit in addition to its other types.
                Disturb {3}{U}{U} $disturbLine
            """,
        ) to abilities(
            Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersAsCopy(of = creature, except = asSpirit, optional = true)))),
                affects = Affected.Objects(Objects.Self)),
            disturb("{3}{U}{U}"),
        ),
        card(
            name = "Eerie Imitation",
            cost = null,
            type = "Enchantment — Aura",
            colorIndicator = "U",
            text = """
                Enchant creature
                At the beginning of your upkeep, create a token that's a copy of enchanted creature, except it's a Spirit in addition to its other types.
                If Eerie Imitation would be put into a graveyard from anywhere, exile it instead.
            """,
        ) to abilities(
            enchant(creature),
            triggered(yourUpkeep, doing(Effect.CreateToken(TokenSpec.CopyOf(enchanted, asSpirit), n(1), Players.You))),
            exileInstead,
        ),
    )

    // CR 508.1m (attack trigger), 611.2a, 613.4c
    @Test
    fun bindingGeist() = cardFaces(Layout.Transform,
        card(
            name = "Fettering Geist",
            cost = "{2}{U}",
            type = "Creature — Spirit",
            pt = "3/1",
            text = """
                Whenever this creature attacks, target creature an opponent controls gets -2/-0 until end of turn.
                Disturb {1}{U} $disturbLine
            """,
        ) to abilities(
            triggered(attacksSelf, pump(target("creature"), -2, 0), targets = listOf(slot("creature", creatureOpponentControls))),
            disturb("{1}{U}"),
        ),
        card(
            name = "Ghostly Fetter",
            cost = null,
            type = "Enchantment — Aura",
            colorIndicator = "U",
            text = """
                Enchant creature
                Enchanted creature gets -2/-0.
                If Ghostly Fetter would be put into a graveyard from anywhere, exile it instead.
            """,
        ) to abilities(
            enchant(creature),
            static(pt(-2, 0), affects = enchanted),
            exileInstead,
        ),
    )

    // CR 506.2 (defending player), 113.10 (an Aura granting a triggered ability)
    @Test
    fun distractingGeist() {
        val tapDefenders = triggered(attacksSelf, tapIt(target("creature")),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.DefendingPlayerOf(Objects.Self)))))))
        cardFaces(Layout.Transform,
            card(
                name = "Diverting Geist",
                cost = "{2}{W}",
                type = "Creature — Spirit",
                pt = "2/1",
                text = """
                    Whenever this creature attacks, tap target creature defending player controls.
                    Disturb {4}{W} $disturbLine
                """,
            ) to abilities(tapDefenders, disturb("{4}{W}")),
            card(
                name = "Sly Diversion",
                cost = null,
                type = "Enchantment — Aura",
                colorIndicator = "W",
                text = """
                    Enchant creature
                    Enchanted creature has "Whenever this creature attacks, tap target creature defending player controls."
                    If Sly Diversion would be put into a graveyard from anywhere, exile it instead.
                """,
            ) to abilities(enchant(creature), static(has(tapDefenders), affects = enchanted), exileInstead),
        )
    }

    // CR 603.7 (delayed trigger at end of combat, CR 511), 701.21a (sacrifice), 508.4 (a token put onto the battlefield attacking)
    @Test
    fun dorotheaVengefulVictim() = cardFaces(Layout.Transform,
        card(
            name = "Marisol, Wronged Spirit",
            cost = "{W}{U}",
            type = "Legendary Creature — Spirit",
            pt = "4/4",
            text = """
                Flying
                When Marisol attacks or blocks, sacrifice it at end of combat.
                Disturb {1}{W}{U} $disturbLine
            """,
        ) to abilities(
            Keyword.Flying,
            triggered(Trigger.On(listOf(EventPattern.Attack(ObjectFilter.Is(Objects.Self)), EventPattern.Block(ObjectFilter.Is(Objects.Self), attacker = ObjectFilter.Any))),
                doing(Effect.CreateDelayedTrigger(untilEndOfCombat,
                    Instructions(steps = listOf(doing(Effect.Sacrifice(Objects.Self, by = Players.You))))))),
            disturb("{1}{W}{U}"),
        ),
        card(
            name = "Marisol's Reprisal",
            cost = null,
            type = "Enchantment — Aura",
            colorIndicator = "WU",
            text = """
                Enchant creature
                Enchanted creature has "Whenever this creature attacks, create a 4/4 white Spirit creature token with flying that's tapped and attacking. Sacrifice that token at end of combat."
                If Marisol's Reprisal would be put into a graveyard from anywhere, exile it instead.
            """,
        ) to abilities(
            enchant(creature),
            static(has(triggered(attacksSelf,
                doing(Effect.CreateToken(creatureToken("Spirit", listOf(Color.White), 4, 4, abilities = listOf(Keyword.Flying)), n(1), Players.You,
                    tapped = true, attacking = AttackTarget.Chosen, bind = "token")),
                doing(Effect.CreateDelayedTrigger(untilEndOfCombat,
                    Instructions(steps = listOf(doing(Effect.Sacrifice(Objects.Bound("token"), by = Players.You)))))),
            )), affects = enchanted),
            exileInstead,
        ),
    )

    /** The chunk's plain disturb cards: a creature front and an Aura back granting one bonus. */
    private fun plainDisturb(front: OracleCard, frontAbilities: List<Ability>, backName: String, color: String, bonusText: String, bonus: Ability) =
        cardFaces(Layout.Transform,
            front to frontAbilities,
            card(
                name = backName,
                cost = null,
                type = "Enchantment — Aura",
                colorIndicator = color,
                text = """
                    Enchant creature
                    $bonusText
                    If $backName would be put into a graveyard from anywhere, exile it instead.
                """,
            ) to abilities(enchant(creature), bonus, exileInstead),
        )

    // CR 702.146a, 613.4c
    @Test
    fun drogskolInfantry() = plainDisturb(
        card(name = "Spectral Footman", cost = "{1}{W}", type = "Creature — Spirit Soldier", pt = "2/2", text = "Disturb {3}{W} $disturbLine"),
        abilities(disturb("{3}{W}")),
        backName = "Spectral Arms", color = "W", bonusText = "Enchanted creature gets +2/+2.",
        bonus = static(pt(2, 2), affects = enchanted),
    )

    // CR 702.15 (lifelink), 613.1f
    @Test
    fun kindlyAncestor() = plainDisturb(
        card(name = "Gentle Forebear", cost = "{2}{W}", type = "Creature — Spirit", pt = "2/3", text = """
            Lifelink
            Disturb {1}{W} $disturbLine
        """),
        abilities(Keyword.Lifelink, disturb("{1}{W}")),
        backName = "Forebear's Hug", color = "W", bonusText = "Enchanted creature has lifelink.",
        bonus = static(has(Keyword.Lifelink), affects = enchanted),
    )

    // CR 702.9 (flying), 613.4c
    @Test
    fun lanternBearer() = plainDisturb(
        card(name = "Wick Carrier", cost = "{U}", type = "Creature — Spirit", pt = "1/1", text = """
            Flying
            Disturb {2}{U} $disturbLine
        """),
        abilities(Keyword.Flying, disturb("{2}{U}")),
        backName = "Wicks' Ascent", color = "U", bonusText = "Enchanted creature gets +1/+1 and has flying.",
        bonus = static(pt(1, 1), has(Keyword.Flying), affects = enchanted),
    )

    // CR 510.3a (combat damage trigger), 113.10 (granted)
    @Test
    fun mischievousCatgeist() = plainDisturb(
        card(name = "Prowling Catshade", cost = "{1}{U}", type = "Creature — Cat Spirit", pt = "1/1", text = """
            Whenever this creature deals combat damage to a player, draw a card.
            Disturb {2}{U} $disturbLine
        """),
        abilities(triggered(combatDamageToAPlayer, draw(1)), disturb("{2}{U}")),
        backName = "Feline Inquisitiveness", color = "U",
        bonusText = "Enchanted creature has \"Whenever this creature deals combat damage to a player, draw a card.\"",
        bonus = static(has(triggered(combatDamageToAPlayer, draw(1))), affects = enchanted),
    )

    // CR 702.4 (double strike)
    @Test
    fun twinbladeGeist() = plainDisturb(
        card(name = "Dualedge Geist", cost = "{1}{W}", type = "Creature — Spirit Warrior", pt = "1/1", text = """
            Double strike
            Disturb {2}{W} $disturbLine
        """),
        abilities(Keyword.DoubleStrike, disturb("{2}{W}")),
        backName = "Dualedge Summoning", color = "W", bonusText = "Enchanted creature has double strike.",
        bonus = static(has(Keyword.DoubleStrike), affects = enchanted),
    )
}
