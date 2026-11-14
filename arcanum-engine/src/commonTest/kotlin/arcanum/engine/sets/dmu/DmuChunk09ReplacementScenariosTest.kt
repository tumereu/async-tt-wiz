package arcanum.engine.sets.dmu

import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The DMU replacement cards of chunk 09, played: the invented names carry the oracle text of the ten tapped dual lands (Contaminated
 * Aquifer is the pattern), Cult Conscript, Nemata, Primeval Warden and Timeless Lotus.
 */
class DmuChunk09ReplacementScenariosTest {
    private val aquifer = Cards.invent("Murkwater Aquifer", null, "Land — Island Swamp", """
        ({T}: Add {U} or {B}.)
        This land enters tapped.
    """)
    private val conscript = Cards.invent("Bonepit Conscript", "{B}", "Creature — Skeleton Warrior", """
        This creature enters tapped.
        {1}{B}: Return this card from your graveyard to the battlefield. Activate only if a non-Skeleton creature died under your control this turn.
    """, "2/1")
    private val treefolk = Cards.invent("Nemlo, Wild Warden", "{2}{B}{G}", "Legendary Creature — Treefolk", """
        Reach
        If a creature an opponent controls would die, exile it instead. When you do, create a 1/1 green Saproling creature token.
        {G}, Sacrifice a Saproling: Nemlo gets +2/+2 until end of turn.
        {1}{B}, Sacrifice two Saprolings: Draw a card.
    """, "3/4")
    private val lotus = Cards.invent("Dormant Lotus", "{5}", "Legendary Artifact", """
        Dormant Lotus enters tapped.
        {T}: Add {W}{U}{B}{R}{G}.
    """)
    private val bear = Cards.invent("Quillbrook Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val skeleton = Cards.invent("Quillbrook Bones", "{B}", "Creature — Skeleton", "", "1/1")
    private val shrub = Cards.invent("Quillbrook Shrub", "{G}", "Creature — Saproling", "", "1/1")
    private val shrub2 = Cards.invent("Quillbrook Sapling", "{G}", "Creature — Saproling", "", "1/1")
    private val ox = Cards.invent("Quillbrook Ox", "{1}{G}", "Creature — Ox", "", "2/2")
    private val hog = Cards.invent("Quillbrook Hog", "{1}{G}", "Creature — Boar", "", "2/2")
    private val zap = Cards.invent("Quillbrook Zap", "{R}", "Instant", "Quillbrook Zap deals 5 damage to target creature.")

    // CR 614.1c, 614.12
    @Test
    fun landEntersTapped() = scenario {
        alice.hand(aquifer)
        alice.plays(aquifer)
        expect { aquifer.tapped shouldBe true }
    }

    // CR 614.1c: a permanent spell resolving enters tapped
    @Test
    fun artifactEntersTapped() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(lotus)
        alice.casts(lotus)
        resolveAll()
        expect {
            lotus.zone shouldBe Zone.Battlefield
            lotus.tapped shouldBe true
        }
    }

    // CR 602.5b, 700.4: only a non-Skeleton creature dying under your control this turn enables the return, and it enters tapped
    @Test
    fun conscriptReturnsAfterANonSkeletonOfYoursDied() = scenario {
        alice.battlefield("Swamp", "Swamp", "Mountain", bear)
        alice.hand(zap)
        alice.graveyard(conscript)
        assertFailsWith<IllegalArgumentException> { alice.activates(conscript, ability = 0) }
        alice.casts(zap, target = bear)
        resolveAll()
        alice.activates(conscript, ability = 0)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            conscript.zone shouldBe Zone.Battlefield
            conscript.tapped shouldBe true
        }
    }

    // CR 700.4: a Skeleton dying, or somebody else's creature dying, doesn't count
    @Test
    fun conscriptNeedsYourOwnNonSkeleton() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Mountain", "Mountain", skeleton)
        bob.battlefield(bear)
        alice.hand(zap, "Mountain")
        alice.graveyard(conscript)
        alice.casts(zap, target = skeleton)
        resolveAll()
        assertFailsWith<IllegalArgumentException> { alice.activates(conscript, ability = 0) }
    }

    // CR 614.6, 603.7: every opponent's dying creature is exiled and makes a Saproling for the treefolk's controller; one's own dies normally
    @Test
    fun treefolkExilesEveryOpponentsCreatureAndMakesSaprolings() = scenario(players = 3) {
        alice.battlefield(treefolk, "Mountain", "Mountain", "Mountain", bear)
        alice.hand(zap, zap, zap)
        bob.battlefield(ox)
        carol.battlefield(hog)
        alice.casts(zap, target = ox)
        resolveAll()
        alice.casts(zap, target = hog)
        resolveAll()
        expect {
            bob.exile has ox
            carol.exile has hog
        }
        assertEquals(2, state.battlefield.count { state.obj(it)?.token == true })
        alice.casts(zap, target = bear)
        resolveAll()
        expect { alice.graveyard has bear }
        assertEquals(2, state.battlefield.count { state.obj(it)?.token == true })
    }

    // CR 602.2, 118.8: sacrificing two Saprolings pays for the draw
    @Test
    fun treefolkSacrificesTwoSaprolingsToDraw() = scenario {
        alice.battlefield(treefolk, "Swamp", "Swamp", shrub, shrub2)
        alice.library("Forest", "Plains")
        alice.activates(treefolk, ability = 1, choosing = listOf(shrub, shrub2))
        resolveAll()
        expect { alice.hand has "Forest" }
    }
}
