package dev.finnley.pcswitch.state

/**
 * Konfiguration für einen einzelnen Switch.
 *
 * [type] bestimmt das Verhalten:
 * - "toggle": Ein Entity wird toggled (einschalten/auswechseln)
 * - "dual": Zwei Entities, eine zum Einschalten, eine zum Ausschalten
 */
data class SwitchConfig(
    val id: String,
    var name: String,
    var type: String, // "toggle" or "dual"
    var primaryEntity: String,  // on/toggle entity
    var secondaryEntity: String = "",  // off entity (dual only)
    var stateEntity: String = ""  // optional entity that reflects on/off state
) {
    companion object {
        fun newSwitch(id: String, name: String, type: String, primaryEntity: String, secondaryEntity: String = ""): SwitchConfig {
            return SwitchConfig(id, name, type, primaryEntity, secondaryEntity)
        }
    }
}