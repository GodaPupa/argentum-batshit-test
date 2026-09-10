package com.wingedsheep.sdk.scripting.effects

import com.wingedsheep.sdk.scripting.targets.DEFAULT_SELF_NOUN
import com.wingedsheep.sdk.scripting.targets.resolveSelfNoun

/**
 * Mixin for an [Effect] whose generated text refers to its own source permanent and must therefore
 * adapt the noun to that permanent's type — "this creature" on a creature, "this land"/"this
 * artifact"/… on a non-creature. Implementations build [descriptionTemplate] with
 * [com.wingedsheep.sdk.scripting.targets.SELF_NOUN_TOKEN] (via
 * [com.wingedsheep.sdk.scripting.targets.selfNounToken]) wherever they mean "this permanent", and
 * delegate their [Effect.description] to [defaultResolvedDescription].
 *
 * The default resolution replaces the token with [DEFAULT_SELF_NOUN] ("this permanent") — a type-safe
 * fallback for any consumer without the host permanent in hand — so the raw token never leaks. The
 * server's `ClientStateTransformer` instead re-resolves [descriptionTemplate] against the host
 * permanent's *projected* type to render the exact noun. See `docs/card-sdk-language-reference.md`.
 *
 * Deliberately a standalone interface, **not** a subtype of the sealed `@Serializable` [Effect]:
 * adding an intermediate sub-interface to that sealed hierarchy would break kotlinx polymorphic
 * serialization. Implementors list both supertypes: `Effect, SelfReferentialDescription`.
 */
interface SelfReferentialDescription {
    /** Description text still containing the [com.wingedsheep.sdk.scripting.targets.SELF_NOUN_TOKEN]. */
    val descriptionTemplate: String

    /** [descriptionTemplate] with the self-noun placeholder resolved to the type-neutral default. */
    val defaultResolvedDescription: String
        get() = resolveSelfNoun(descriptionTemplate, DEFAULT_SELF_NOUN)
}
