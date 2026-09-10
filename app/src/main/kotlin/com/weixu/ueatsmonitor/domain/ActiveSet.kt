package com.weixu.ueatsmonitor.domain

/**
 * Calculation. Which set of suburbs is in force, when two places can choose one.
 *
 * The laptop names a set inside rules.json; the phone can pick another while
 * driving. Neither owns the answer - the later of the two does. A push from the
 * laptop is a deliberate act and must take effect, and so is a tap in the car.
 */
object ActiveSet {

    /**
     * [picked] is what the phone chose and [pickedAtMillis] when, [named] is what
     * the laptop's file says and [namedAtMillis] when that file last changed.
     * [known] is every set that exists; a name that is gone counts for nothing.
     */
    fun inForce(
        picked: String?,
        pickedAtMillis: Long,
        named: String?,
        namedAtMillis: Long,
        known: Set<String>,
    ): String? {
        val phone = picked?.takeIf { it in known }
        val laptop = named?.takeIf { it in known }
        if (phone == null) return laptop
        if (laptop == null) return phone
        return if (pickedAtMillis >= namedAtMillis) phone else laptop
    }
}
