package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.*

/** Bot seats in pod setup and the pod screens (owner 2026-11-21). */
class PodBotSeatsTest {
    private fun account(n: Int) = AccountIdentity("id-$n", "P$n")
    private fun detail(settings: DraftSettings, members: Int, invited: Int = 0, phase: PodPhase = PodPhase.SETUP, bots: Int = 0) =
        PodDetail("pod", "Pod", phase, account(0), settings, PodSource("Cube", 540), DraftSupply(0, 540),
            List(members) { PodMember(account(it)) } + List(bots) { PodMember(PodBots.identity(it + 1), bot = true) },
            List(invited) { account(100 + it) }, updatedAt = "")

    // DRF-296 (owner, 2026-11-21, 2026-11-22 and 2026-11-23): bot seats are added among the members (Add bot), not on a tile: 0 up to the
    // engine's limit less one (the creator may draft alone with bots), none in Sealed and Arena
    @Test fun theBotSeatsFollowTheMode() {
        val cube = PodSource("Cube", 540)
        for (mode in DraftMode.entries) assertEquals(PodSetting.SIDEBOARD, podSettingFields(DraftSettings.defaults(mode), cube).last(), "$mode: no Bots tile")
        assertEquals(0..7, podBotRange(DraftSettings.booster()))
        assertEquals(0..3, podBotRange(DraftSettings.grid()))
        assertEquals(0..0, podBotRange(DraftSettings.arena()))
        assertEquals(3, DraftSettings.booster().withBots(3).bots)
        // Never past the range
        assertEquals(7, DraftSettings.booster().withBots(8).bots)
        assertEquals(0, DraftSettings.booster().withBots(-1).bots)
        // DRF-028: a bot switches Grid's two picks off
        assertFalse(DraftSettings.grid().copy(twoPicks = true).withBots(1).twoPicks)
        assertTrue(DraftSettings.grid().copy(twoPicks = true).withBots(0).twoPicks)
    }

    // DRF-035, DRF-296, DRF-301 (owner, 2026-11-23): a solo draft: the creator alone starts with a bot, not without one, nor in
    // Sealed or Arena; setup warns there is no one to play against while the creator is alone with bots and no invitation pending
    @Test fun theCreatorAloneWithBotsStartsASoloDraftAndIsWarned() {
        val warning = "No one to play against: bots only draft, so this pod plays no games."
        val booster = DraftSettings.booster()
        assertFalse(podCanStart(detail(booster, 1)))
        assertTrue(podCanStart(detail(booster.copy(bots = 1), 1)))
        assertTrue(podCanStart(detail(booster.copy(bots = 7), 1)))
        assertFalse(podCanStart(detail(DraftSettings.sealed(), 1)))
        assertFalse(podCanStart(detail(DraftSettings.arena(), 1)))
        assertEquals(warning, podSoloWarning(detail(booster.copy(bots = 1), 1)))
        assertNull(podSoloWarning(detail(booster, 1)), "no bots")
        assertNull(podSoloWarning(detail(booster.copy(bots = 1), 1, invited = 1)), "someone may still join")
        assertNull(podSoloWarning(detail(booster.copy(bots = 1), 2)), "two people")
        assertNull(podSoloWarning(detail(booster.copy(bots = 1), 1, phase = PodPhase.DRAFTING)), "setup only")
        // As setup shows the settings being edited
        assertEquals(warning, podSoloWarning(detail(booster, 1), booster.copy(bots = 2)))
    }

    // DRF-020, DRF-296 (owner, 2026-11-23): another mode keeps the bots, at most its own most (its limit less one); Sealed and Arena take none
    @Test fun switchingModesKeepsTheBotsWithinTheNewMost() {
        val booster = DraftSettings.booster().copy(bots = 5)
        assertEquals(5, podModeSettings(DraftMode.ROTISSERIE, booster).bots)
        assertEquals(3, podModeSettings(DraftMode.WINSTON, booster).bots)
        assertEquals(0, podModeSettings(DraftMode.SEALED, booster).bots)
        assertEquals(0, podModeSettings(DraftMode.ARENA, booster).bots)
        assertTrue(podModeSettings(DraftMode.GRID, booster).let { it.validate(it.seats(1)) }.isEmpty())
    }

    // DRF-035, DRF-150, DRF-163, DRF-030, DRF-297: bots count as players, not as members; (owner, 2026-11-23) the creator alone
    // with bots can start a solo draft
    @Test fun botsCountAsPlayersButNotAsMembers() {
        val grid = DraftSettings.grid().copy(bots = 2)
        assertTrue(podCanStart(detail(grid, 1)))
        assertTrue(podCanStart(detail(grid, 2)))
        assertNull(podPlayerLimitText(detail(grid, 2)))
        assertEquals("Grid takes at most 4 players; 3 have joined, with 2 bots.", podPlayerLimitText(detail(grid, 3)))
        assertFalse(podCanStart(detail(grid, 3)))
        assertEquals("Winston takes at most 4 players; 4 are joined or invited, with 1 bot.",
            podPlayerLimitText(detail(DraftSettings.winston().copy(bots = 1), 2, invited = 2)))
        assertEquals(listOf("Bot 1", "Bot 2"), podSetupBots(grid).map { it.username })
        assertEquals(emptyList(), podSetupBots(DraftSettings.booster()))
    }

    // DRF-297, DRF-301: bots are never challenged and leaving counts only people
    @Test fun botsPlayNoGames() {
        val drafted = detail(DraftSettings.booster().copy(bots = 2), 2, phase = PodPhase.DRAFTED, bots = 2)
        val bot = drafted.members.first { it.bot }
        val other = drafted.members.first { !it.bot && it.account.accountId != "id-0" }
        assertFalse(podChallengeOffered(drafted, bot, "id-0"))
        assertEquals(listOf("P0", "P1"), podPeople(drafted).map { it.username })
        assertTrue(podChallengeOffered(drafted, other, "id-0"))
        assertEquals("This cancels the draft for all 2 players and discards all picks.",
            podConfirmationText(PodConfirmation.LEAVE, drafted.copy(phase = PodPhase.DRAFTING)))
    }
}
