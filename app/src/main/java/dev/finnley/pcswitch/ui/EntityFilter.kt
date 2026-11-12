package dev.finnley.pcswitch.ui

import dev.finnley.pcswitch.ha.HaClient.EntityState

/**
 * Domain filter options (most relevant for PC Switch use cases first).
 * "all" means no domain filter.
 */
val DOMAIN_FILTERS = listOf(
    "all", "button", "input_button", "switch", "light",
    "cover", "lock", "fan", "sensor", "input_boolean", "person"
)

/**
 * Filter entities by domain and query.
 *
 * @param entities all entities to filter
 * @param query text query (searches entity ID and friendly name)
 * @param domain domain filter ("all" means no filter)
 * @return filtered list of entities
 */
fun filterEntities(
    entities: List<EntityState>,
    query: String,
    domain: String
): List<EntityState> {
    if (query.isBlank() && domain == "all") {
        return entities
    }
    return entities.filter { e ->
        val domainMatches = domainMatchesEntity(domain, e.entityId)
        val queryMatches = queryMatchesEntity(query, e)
        domainMatches && queryMatches
    }
}

/** Check if an entity ID matches the selected domain filter. */
fun domainMatchesEntity(domain: String, entityId: String): Boolean {
    return when (domain) {
        "all" -> true
        // button filter matches both button.* and input_button.*
        "button" -> entityId.startsWith("button.") || entityId.startsWith("input_button.")
        else -> entityId.startsWith("$domain.")
    }
}

/** Check if an entity matches the text query. */
fun queryMatchesEntity(query: String, entity: EntityState): Boolean {
    if (query.isBlank()) return true
    return entity.entityId.contains(query, ignoreCase = true) ||
        entity.friendlyName.contains(query, ignoreCase = true)
}

/**
 * Normalize HA entity state for display in search results.
 * Maps various on/off-like states to a simple on/off/unknown classification.
 */
fun normalizeStateForDisplay(raw: String?): String {
    val v = raw?.trim()?.lowercase() ?: "unknown"
    return when (v) {
        "on", "true", "1", "active", "connected" -> "on"
        "off", "false", "0", "inactive", "disconnected", "closed", "locked", "stopped", "paused" -> "off"
        else -> "unknown"
    }
}
