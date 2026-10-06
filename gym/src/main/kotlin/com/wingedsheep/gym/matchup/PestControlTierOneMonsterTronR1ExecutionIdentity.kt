package com.wingedsheep.gym.matchup

const val PEST_MONSTER_TRON_R1_BLOCK_ID =
    "${PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID}_NONEXPERIMENTAL_REPLACEMENT_SMOKE_4_R1"
const val PEST_MONSTER_TRON_R1_ACCEPTED_C2 =
    "38e834c1275a861e87487195842fb4d98e373ee7"
const val PEST_MONSTER_TRON_R1_ACCEPTED_C2_IDENTIFIER =
    "421a75c0aad541d9604840b1757c790c7f313155a215e55bdc156858514e2659"
const val PEST_MONSTER_TRON_R1_ENGINE_SOURCE =
    "433df3310efe31c49f27034b50e6d8d7e60561f7"
const val PEST_MONSTER_TRON_R1_FREEZE_COMMIT =
    "41716f6918ec559ee60b14815b5b0f23d3aaf38a"
const val PEST_MONSTER_TRON_R1_FREEZE_TREE =
    "eddfe0f11b132349876470943e9ae57ebe0487e1"
const val PEST_MONSTER_TRON_R1_RAW_SHA256 =
    "0804de7809cc8eaad0f92adb3f26cbb0d564aa92011a39143859aefd30c67a31"
const val PEST_MONSTER_TRON_R1_ARCHIVE_SHA256 =
    "afcc24f0a3f8fd56819e2fb3f074ea37252063dfcf063e85e2a8d9728b4f7f6c"
const val PEST_MONSTER_TRON_R1_VECTOR_SHA256 =
    "8cdba4018aac3f423d92981581b628a8647e15be1f916a32c81ca1aef6ccff89"
const val PEST_MONSTER_TRON_R1_ASSIGNMENTS_SHA256 =
    "edd4d831ed0e9cd319ce908e71cca117203a1f5970e00441f3c9310f10e412c7"
const val PEST_MONSTER_TRON_R1_INPUT_ASSIGNMENTS_SHA256 =
    "91bf6f8f5ffdcc77faa1100df6f167a71369087712bd019e3a40676c202e60e2"
const val PEST_MONSTER_TRON_R1_FREEZE_MANIFEST_SHA256 =
    "8c678ac397016c1e68046e1101869967d9c9544594ae9af642f035bd11738e2a"
const val PEST_MONSTER_TRON_R1_QUARANTINE_SHA256 =
    "c59ef02660abdc99a298bbb3aec9010d29ee745bcf8bfd48ccfe684f3787896b"
const val PEST_MONSTER_TRON_R1_CHECKSUMS_SHA256 =
    "994a9e5b2d33cfe7d2e4010d3fb5c59a2a394936ff3f6fe092d5e3bd8"
const val PEST_MONSTER_TRON_R1_INPUT_CHECKSUMS_SHA256 =
    "1cbe37abfe55b858e9cc27b791c41d12a30a5f41dfd5a4c1e8fa37f7a6c31a2a"
const val PEST_MONSTER_TRON_R1_RULES_SHA256 =
    "8d860e451f20f38865b725b42d82feb714c725373dd8f3b32b8652b3eeb070ca"
const val PEST_MONSTER_TRON_R1_RULES_SUMMARY_SHA256 =
    "68f75e76e67b73a0e952661226303565a4e6251a72604828863168bcad31bb5f"
const val PEST_MONSTER_TRON_R1_ORACLE_LEGALITY_SHA256 =
    "3f411dfd65efcfeb228c7d5bad46ca16e0dc5d3e758a710c07466f8c5855eb37"
const val PEST_MONSTER_TRON_R1_FUTURE_CLAIM_REF =
    "refs/heads/pest-control/official-attempts/monster-tron-replacement-smoke-r1"
const val PEST_MONSTER_TRON_R1_HISTORICAL_CLAIM_REF =
    "refs/heads/pest-control/official-attempts/monster-tron-smoke-v1"
const val PEST_MONSTER_TRON_R1_HISTORICAL_CLAIM_COMMIT =
    "1c2e253ad7f5a7652304f7c9aaafc30e547a481e"

data class MonsterTronR1ExecutionIdentityInspection(
    val errors: List<String>,
    val runnerEnabled: Boolean = false,
    val a2Active: Boolean = false,
    val claims: Int = 0,
    val games: Int = 0,
    val actions: Int = 0,
    val outcomes: Int = 0,
) {
    val green: Boolean get() = errors.isEmpty()
}

object PestControlTierOneMonsterTronR1ExecutionIdentity {
    fun inspect(): MonsterTronR1ExecutionIdentityInspection =
        MonsterTronR1ExecutionIdentityInspection(
            errors = buildList {
                if (PEST_MONSTER_TRON_R1_BLOCK_ID !=
                    "${PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID}_NONEXPERIMENTAL_REPLACEMENT_SMOKE_4_R1"
                ) add("R1 block mismatch")
                if (PEST_MONSTER_TRON_R1_ACCEPTED_C2 !=
                    "38e834c1275a861e87487195842fb4d98e373ee7"
                ) add("accepted C2 mismatch")
                if (PEST_MONSTER_TRON_R1_ENGINE_SOURCE !=
                    "433df3310efe31c49f27034b50e6d8d7e60561f7"
                ) add("accepted engine source mismatch")
            }
        )
}
