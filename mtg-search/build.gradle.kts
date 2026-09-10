plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    // Pure Kotlin — no Spring, no rules-engine, no mtg-sdk. The search language
    // operates on a [SearchCard] projection defined in this module, so consumers
    // adapt their own card representation by implementing the interface.
    testImplementation(libs.kotestRunner)
    testImplementation(libs.kotestAssertions)
    testImplementation(libs.kotestProperty)
}
