package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.sdk.core.AttackMode
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable
import kotlin.reflect.full.memberProperties

/** Complete current GameConfig data; the engine configuration itself is not serializable. */
@Serializable
internal data class FerocityRecordedPlayerConfig(
    val name: String,
    val deck: Deck,
    val startingLife: Int,
    val playerId: EntityId?,
    val commanderCardName: String?,
)

@Serializable
internal data class FerocityRecordedGameConfig(
    val players: List<FerocityRecordedPlayerConfig>,
    val startingHandSize: Int,
    val skipMulligans: Boolean,
    val useHandSmoother: Boolean,
    val handSmootherCandidates: Int,
    val startingPlayerIndex: Int?,
    val format: Format,
    val attackMode: AttackMode,
    val teams: List<List<Int>>?,
    val seed: Long,
) {
    fun toEngine(): GameConfig {
        verifyConfigSchemas()
        players.forEach { player ->
            require(player.deck.cardEntries.isEmpty() || player.deck.cards == player.deck.cardEntries.map { it.name }) {
                "Conflicting ordered deck representations"
            }
        }
        return GameConfig(
            players = players.map { PlayerConfig(it.name, it.deck, it.startingLife, it.playerId, it.commanderCardName) },
            startingHandSize = startingHandSize,
            skipMulligans = skipMulligans,
            useHandSmoother = useHandSmoother,
            handSmootherCandidates = handSmootherCandidates,
            startingPlayerIndex = startingPlayerIndex,
            format = format,
            attackMode = attackMode,
            teams = teams,
            seed = seed,
        )
    }

    companion object {
        fun capture(config: GameConfig): FerocityRecordedGameConfig {
            verifyConfigSchemas()
            return FerocityRecordedGameConfig(
                config.players.map { FerocityRecordedPlayerConfig(it.name, it.deck, it.startingLife, it.playerId, it.commanderCardName) },
                config.startingHandSize, config.skipMulligans, config.useHandSmoother,
                config.handSmootherCandidates, config.startingPlayerIndex, config.format,
                config.attackMode, config.teams, requireNotNull(config.seed) { "A recorded game requires an explicit seed" },
            )
        }
    }
}

private fun verifyConfigSchemas() {
    require(GameConfig::class.memberProperties.map { it.name }.toSet() == setOf(
        "players", "startingHandSize", "skipMulligans", "useHandSmoother", "handSmootherCandidates",
        "startingPlayerIndex", "format", "attackMode", "teams", "seed",
    )) { "GameConfig schema changed; update the complete recording before running" }
    require(PlayerConfig::class.memberProperties.map { it.name }.toSet() == setOf(
        "name", "deck", "startingLife", "playerId", "commanderCardName",
    )) { "PlayerConfig schema changed; update the complete recording before running" }
}
