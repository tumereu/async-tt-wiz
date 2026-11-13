package arcanum.parser.sets.woe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Wilds of Eldraine chunk 14: destroy, discard, draw and gain-abilities wordings (several of them Adventure cards). */
class WoeChunk14DestroyDiscardDrawTest {
    private val flyingBlocker = creatureToken("Faerie", listOf(Color.Blue), 1, 1, listOf(Keyword.Flying,
        static(Mechanic.CanBlockOnly(and(creature, ObjectFilter.HasKeyword(Keyword.Flying))))))
    private val ratToken = creatureToken("Rat", listOf(Color.Black), 1, 1, listOf(static(Mechanic.CantBlock(ObjectFilter.Any))))

    // CR 701.8a (destroy), 603.6a (enters trigger), 120.1 (dealt damage this turn)
    @Test
    fun gnatbladeCutthroat() = card(
        name = "Gnatblade Cutthroat",
        cost = "{3}{B}",
        type = "Creature — Faerie Assassin",
        text = """
            Flash
            Flying
            When this creature enters, destroy target creature an opponent controls that was dealt damage this turn.
        """,
        pt = "3/1",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        triggered(entersSelf, destroy(target("creature")),
            targets = listOf(slot("creature", and(creature, opponents,
                ObjectFilter.ThisTurn(EventPattern.Damage(ObjectFilter.Any, RecipientFilter()), role = EventRole.Object))))),
    )

    // CR 715.2 (Adventure faces), 701.8a (destroy)
    @Test
    fun spindleChoir() = cardFaces(Layout.Adventure,
        card(
            name = "Spindle Choir",
            cost = "{3}{U}",
            type = "Creature — Faerie",
            text = "Flying",
            pt = "3/3",
        ) to abilities(Keyword.Flying),
        card(
            name = "Snip the Hems",
            cost = "{2}{W}",
            type = "Instant — Adventure",
            text = "Destroy target tapped creature. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(spell(destroy(target("creature")), targets = listOf(slot("creature", and(tapped, creature))))),
    )

    // CR 700.2 (choose one or more), 701.9 (discard), 119 (life), 701.21 (sacrifice), 101.4 (APNAP, each player chooses)
    @Test
    fun pallOfJests() = card(
        name = "Pall of Jests",
        cost = "{2}{B}{B}",
        type = "Sorcery",
        text = """
            Choose one or more —
            • Each player discards two cards.
            • Each player loses 4 life.
            • Each player sacrifices two creatures of their choice.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(discard(2, Players.Each(PlayerFilter.Any))),
            mode(loseLife(4, Players.Each(PlayerFilter.Any))),
            mode(Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), bind = "player", together = true, steps = listOf(
                Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))),
                    min = n(2), max = n(2)), bind = "sacrificed", chooser = Players.Bound("player")),
                doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            ))),
            min = 1, max = 3))),
    )

    // CR 715.2, 701.9 (each opponent discards), 701.5a? no: 400.7 return a spell to its owner's hand (it ceases to be a spell)
    @Test
    fun hexscornChorus() = cardFaces(Layout.Adventure,
        card(
            name = "Hexscorn Chorus",
            cost = "{3}{B}",
            type = "Creature — Faerie Warlock",
            text = """
                Flying
                When this creature enters, each opponent discards a card.
            """,
            pt = "2/3",
        ) to abilities(Keyword.Flying, triggered(entersSelf, discard(1, Players.Each(PlayerFilter.Opponent)))),
        card(
            name = "Give It Back",
            cost = "{2}{U}",
            type = "Instant — Adventure",
            text = "Return target spell to its owner's hand. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(spell(move(target("spell"), Zone.Hand), targets = listOf(slot("spell", spellFilter)))),
    )

    // CR 700.4 (put into a graveyard from the battlefield), 603.2
    @Test
    fun glimmerwoodReaper() = card(
        name = "Glimmerwood Reaper",
        cost = "{3}{B}",
        type = "Creature — Nightmare",
        text = "Whenever an enchantment you control is put into a graveyard from the battlefield, draw a card.",
        pt = "3/3",
    ) parsesTo abilities(
        triggered(on(EventPattern.ZoneChange(and(enchantment, you), from = Zone.Battlefield, to = Zone.Graveyard)), draw(1)),
    )

    // CR 603.4 (intervening if), 608.2d (may discard, if you do), 701.9
    @Test
    fun duskRangerPatrol() = card(
        name = "Dusk Ranger Patrol",
        cost = "{1}{R}",
        type = "Creature — Human Ranger",
        text = "At the beginning of combat on your turn, if you control a creature with power 4 or greater, you may discard a card. If you do, draw a card.",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn,
            Step.Pay(cost(discardACard), payer = Players.You, mandatory = false, bind = "paid"),
            ifThen(Condition.Did("paid"), draw(1)),
            interveningIf = Condition.Exists(and(creature, power(Comparison.AtLeast, 4), you), 1)),
    )

    // CR 111.1 (create a token), 702.9 (flying), 509.1b (can block only creatures with flying)
    @Test
    fun courtOfFaeLore() = card(
        name = "Court of Fae Lore",
        cost = "{3}{U}{U}",
        type = "Sorcery",
        text = "Draw three cards. Create a 1/1 blue Faerie creature token with flying and \"This token can block only creatures with flying.\"",
    ) parsesTo abilities(
        spell(draw(3), Step.Do(Effect.CreateToken(flyingBlocker, n(1), Players.You, bind = "token"))),
    )

    // CR 707.9a and 707.9b (copy with exceptions), 613.1a, 602.5d (sorcery timing), 107.3 (X)
    @Test
    fun mimicLooter() = card(
        name = "Mimic Looter",
        cost = "{U}{B}",
        type = "Creature — Faerie Shapeshifter",
        text = """
            Flying
            {T}: Draw a card, then discard a card.
            {X}: This creature becomes a copy of target creature card in your graveyard with mana value X, except it has flying and this ability. Activate only as a sorcery.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Flying,
        activated(cost(tap), draw(1), discard(1)),
        activated(cost(mana("{X}")),
            Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.CopyOf(target("card"), except = listOf(CopyException.AddAbilities(listOf(Keyword.Flying)), CopyException.ThisAbility))),
                Affected.Objects(Objects.Self), Duration.Indefinitely)),
            targets = listOf(slot("card", and(creature, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You), ObjectFilter.ManaValue(Comparison.Equal, Value.Announced("X"))))),
            rules = asSorcery).let { it.copy(instructions = it.instructions.copy(announced = listOf("X"))) },
    )

    // CR 601.2 and 603.2 (cast trigger), 715.4 (Adventure is a subtype)
    @Test
    fun pixieChronicler() = card(
        name = "Pixie Chronicler",
        cost = "{3}{U}",
        type = "Creature — Faerie Wizard",
        text = """
            Flying
            Whenever you cast an Adventure spell, draw a card.
        """,
        pt = "3/3",
    ) parsesTo abilities(Keyword.Flying, triggered(youCast(sub("Adventure")), draw(1)))

    // CR 603.6a (enters the battlefield), 303.4 (Aura)
    @Test
    fun thicketLookout() = card(
        name = "Thicket Lookout",
        cost = "{2}{G}",
        type = "Creature — Satyr",
        text = "Whenever an Aura you control enters, draw a card.",
        pt = "2/3",
    ) parsesTo abilities(triggered(enters(and(sub("Aura"), you)), draw(1)))

    // CR 603.2 (one ability with two triggers), 202.3 (mana value of a spell)
    @Test
    fun beanstalkBoon() = card(
        name = "Beanstalk Boon",
        cost = "{1}{G}",
        type = "Enchantment",
        text = "When this enchantment enters and whenever you cast a spell with mana value 5 or greater, draw a card.",
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            EventPattern.Cast(and(spellFilter, manaValue(Comparison.AtLeast, 5)), PlayerFilter.You))), draw(1)),
    )

    // CR 715.2, 401.2 (top of library)
    @Test
    fun valeAcolyte() = cardFaces(Layout.Adventure,
        card(
            name = "Vale Acolyte",
            cost = "{2}{W}",
            type = "Creature — Human Cleric",
            text = "When this creature enters, draw a card.",
            pt = "2/2",
        ) to abilities(triggered(entersSelf, draw(1))),
        card(
            name = "Mend the Glade",
            cost = "{G}",
            type = "Instant — Adventure",
            text = "Put target permanent card from your graveyard on top of your library. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(spell(move(target("card"), Zone.Library, position = 0),
            targets = listOf(slot("card", and(permanentCard, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))))),
    )

    // CR 613.1f and 611.3a (static ability granting a keyword while a condition holds), 602 (pump)
    @Test
    fun gravemothNaughty() = card(
        name = "Gravemoth Naughty",
        cost = "{1}{B}",
        type = "Creature — Faerie",
        text = """
            Flying
            This creature has lifelink as long as you control another Faerie.
            {2}{B}: This creature gets +1/+0 until end of turn.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        Keyword.Flying,
        static(has(Keyword.Lifelink), condition = Condition.Exists(and(another, sub("Faerie"), you), 1)),
        activated(cost(mana("{2}{B}")), pump(Objects.Self, 1, 0)),
    )

    // CR 715.2, 702.15 (lifelink), 613.1f (granting to spells on the stack), 120.3 (damage)
    @Test
    fun cinderDuelist() = cardFaces(Layout.Adventure,
        card(
            name = "Cinder Duelist",
            cost = "{1}{W}",
            type = "Creature — Human Knight",
            text = "Instant and sorcery spells you control have lifelink.",
            pt = "3/1",
        ) to abilities(static(has(Keyword.Lifelink), affects = Objects.Each(and(or(instant, sorcery), spellFilter, you)))),
        card(
            name = "Cinder Slash",
            cost = "{2}{R}",
            type = "Instant — Adventure",
            text = "Cinder Slash deals 3 damage to any target. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(spell(damage(3, toAny), targets = listOf(anyTarget))),
    )

    // CR 715.2, 702.7 (first strike), 611.3 (only during your turn), 111.1, 509.1b (can't block)
    @Test
    fun rodentWrangler() = cardFaces(Layout.Adventure,
        card(
            name = "Rodent Wrangler",
            cost = "{1}{R}",
            type = "Creature — Human Peasant",
            text = "During your turn, this creature has first strike.",
            pt = "2/1",
        ) to abilities(static(has(Keyword.FirstStrike), condition = Condition.YourTurn)),
        card(
            name = "Vermin Trouble",
            cost = "{2}{R}",
            type = "Instant — Adventure",
            text = "Create two 1/1 black Rat creature tokens with \"This token can't block.\" (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(spell(create(ratToken, n(2)))),
    )
}
