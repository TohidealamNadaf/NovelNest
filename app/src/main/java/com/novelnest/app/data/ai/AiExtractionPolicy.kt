package com.novelnest.app.data.ai

import java.text.Normalizer
import java.util.Locale

/** Cheap, conservative category guard, not a semantic classifier. Only complete, familiar topic
 * labels narrow the schema. Mixed, custom and other-language topics retain the general contract. */
internal object AiExtractionPolicy {
    val allKinds = listOf("CHARACTER", "LOCATION", "ABILITY", "ITEM", "ORGANIZATION", "CONCEPT")
    data class Scope(val kinds: List<String>, val guidance: String)

    private val cultivation = Regex("cultivation (?:levels?|realms?|stages?|ranks?|tiers?|hierarchy)")
    private val monsters = Regex("(?:monster|monsters|beast|beasts|creature|creatures) (?:levels?|ranks?|tiers?|grades?|hierarchy)")
    private val spaces = Regex("\\s+")
    private val general = Scope(allKinds,
        "Interpret extractionTarget as the requested subject, including custom or combined subjects. " +
            "Include only facts directly about that subject, not other named entities nearby.")

    fun forTarget(target: String?): Scope {
        val label = Normalizer.normalize(target.orEmpty(), Normalizer.Form.NFKC)
            .trim().lowercase(Locale.ROOT).replace(spaces, " ")
        return when {
            cultivation.matches(label) -> Scope(listOf("CONCEPT"),
                "Extract only cultivation realm, stage, tier or level names and their stated requirements, " +
                    "order or properties. A cultivator's name, sect, location, item or technique is not a level. " +
                    "Use the level itself as name; all results use CONCEPT. Never infer an unmentioned hierarchy.")
            monsters.matches(label) -> Scope(listOf("CONCEPT"),
                "Extract only monster/beast rank, grade, tier or level labels and their stated properties or order. " +
                    "A species or individual monster is not a rank. Use the rank itself as name and CONCEPT as kind. " +
                    "Do not infer missing ranks or invent a power scale.")
            label in setOf("world building", "worldbuilding", "world-building", "world lore") -> Scope(
                listOf("LOCATION", "ABILITY", "ITEM", "ORGANIZATION", "CONCEPT"),
                "Extract setting facts: geography, cultures, institutions, history, species, resources, " +
                    "world rules and power systems. Mere mentions of places/items and personal actions are insufficient. " +
                    "For a rule without a formal name, copy a short subject phrase from its evidence as name. " +
                    "Explain the actual setting fact in meaning, not a generic dictionary definition.")
            label in setOf("characters", "character", "people") -> Scope(listOf("CHARACTER"),
                "Extract characters and their explicitly stated roles or traits; exclude locations, items and abstract concepts.")
            else -> general
        }
    }
}
