package com.wingedsheep.gym.matchup

const val PEST_MONSTER_TRON_R1_ACCEPTED_C2 =
    "38e834c1275a861e87487195842fb4d98e373ee7"
const val PEST_MONSTER_TRON_R1_ACCEPTED_C2_IDENTIFIER =
    "421a75c0aad541d9604840b1757c790c7f313155a215e55bdc156858514e2659"
const val PEST_MONSTER_TRON_R1_FREEZE_COMMIT =
    "41716f6918ec559ee60b14815b5b0f23d3aaf38a"
const val PEST_MONSTER_TRON_R1_FREEZE_TREE =
    "eddfe0f11b132349876470943e9ae57ebe0487e1"
const val PEST_MONSTER_TRON_R1_RAW_SHA256 =
    "0804de7809cc8eaad0f92adb3f26cbb0d564aa92011a39143859aefd30c67a31"
const val PEST_MONSTER_TRON_R1_ARCHIVE_SHA256 =
    "e9ddafe0a7aec28cb07950f2f1d5908a31af677ff58415e809117575f46d6817"
const val PEST_MONSTER_TRON_R1_VECTOR_SHA256 =
    "8cdba4018aac3f423d92981581b628a8647e15be1f916a32c81ca1aef6ccff89"
const val PEST_MONSTER_TRON_R1_ASSIGNMENTS_SHA256 =
    "edd4d831ed0e9cd319ce908e71cca117203a1f5970e00441f3c9310f10e412c7"
const val PEST_MONSTER_TRON_R1_INPUT_ASSIGNMENTS_SHA256 =
    "c1a9b63d3ffd5279d663bcbdde7d4595557bc189aa45e29a87fc5db2148b180d"
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
const val PEST_MONSTER_TRON_R1_IDENTITY_SHA256 =
    "99d7182e9e0ffcbe7ced689959991e0ffdb7a985beac1da712f375483ffe8c76"

data class MonsterTronR1ExecutionIdentityInspection(
    val errors: List<String>,
    val identitySha256: String,
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
    fun inspect(): MonsterTronR1ExecutionIdentityInspection {
        val proof = listOf(
            "pest-monster-tron-r1-execution-identity-v1",
            "acceptedC2=$PEST_MONSTER_TRON_R1_ACCEPTED_C2",
            "acceptedC2Identifier=$PEST_MONSTER_TRON_R1_ACCEPTED_C2_IDENTIFIER",
            "blockId=$PEST_MONSTER_TRON_SMOKE_BLOCK_ID",
            "freezeCommit=$PEST_MONSTER_TRON_R1_FREEZE_COMMIT",
            "freezeTree=$PEST_MONSTER_TRON_R1_FREEZE_TREE",
            "rawSha256=$PEST_MONSTER_TRON_R1_RAW_SHA256",
            "archiveSha256=$PEST_MONSTER_TRON_R1_ARCHIVE_SHA256",
            "vectorSha256=$PEST_MONSTER_TRON_R1_VECTOR_SHA256",
            "assignmentsSha256=$PEST_MONSTER_TRON_R1_ASSIGNMENTS_SHA256",
            "inputAssignmentsSha256=$PEST_MONSTER_TRON_R1_INPUT_ASSIGNMENTS_SHA256",
            "freezeManifestSha256=$PEST_MONSTER_TRON_R1_FREEZE_MANIFEST_SHA256",
            "quarantineSha256=$PEST_MONSTER_TRON_R1_QUARANTINE_SHA256",
            "checksumsSha256=$PEST_MONSTER_TRON_R1_CHECKSUMS_SHA256",
            "inputChecksumsSha256=$PEST_MONSTER_TRON_R1_INPUT_CHECKSUMS_SHA256",
            "rulesSha256=$PEST_MONSTER_TRON_R1_RULES_SHA256",
            "rulesSummarySha256=$PEST_MONSTER_TRON_R1_RULES_SUMMARY_SHA256",
            "oracleLegalitySha256=$PEST_MONSTER_TRON_R1_ORACLE_LEGALITY_SHA256",
            "futureClaimRef=$PEST_MONSTER_TRON_R1_FUTURE_CLAIM_REF",
            "historicalClaimRef=$PEST_MONSTER_TRON_R1_HISTORICAL_CLAIM_REF",
            "historicalClaimCommit=$PEST_MONSTER_TRON_R1_HISTORICAL_CLAIM_COMMIT",
            "runnerEnabled=false",
            "a2Active=false",
        ).joinToString("\n", postfix = "\n").toByteArray()
        val digest = sha256(proof)
        val errors = buildList {
            if (digest != PEST_MONSTER_TRON_R1_IDENTITY_SHA256) add("R1 identity proof mismatch")
            if (PEST_MONSTER_TRON_SMOKE_BLOCK_ID !=
                "${PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID}_NONEXPERIMENTAL_REPLACEMENT_SMOKE_4_R1"
            ) add("R1 block mismatch")
        }
        return MonsterTronR1ExecutionIdentityInspection(errors, digest)
    }
}
