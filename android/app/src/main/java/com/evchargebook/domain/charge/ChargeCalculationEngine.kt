package com.evchargebook.domain.charge

import kotlin.math.abs
import kotlin.math.max

enum class ChargeBillingField {
    TOTAL_COST,
    UNIT_PRICE,
    METER_ENERGY
}

enum class ChargeCalculationIssue {
    NEGATIVE_VALUE,
    BILLING_CONFLICT,
    END_NOT_AFTER_START,
    VEHICLE_ENERGY_EXCEEDS_METER
}

data class ChargeCalculationInput(
    val totalCost: Double? = null,
    val unitPrice: Double? = null,
    val meterEnergyKwh: Double? = null,
    val vehicleEnergyKwh: Double? = null,
    val startTimeEpochMillis: Long? = null,
    val endTimeEpochMillis: Long? = null
)

data class ChargeCalculationResult(
    val input: ChargeCalculationInput,
    val durationMillis: Long?,
    val averagePowerKw: Double?,
    val lossEnergyKwh: Double?,
    val lossRate: Double?,
    val issues: Set<ChargeCalculationIssue>
)

/**
 * Central charging calculation rules for v0.7.
 *
 * Billing precedence is intentionally asymmetric:
 * total cost > unit price > meter energy.
 *
 * Editing a higher-priority value may recompute a lower-priority value. Editing meter energy never
 * silently overwrites an already-known total cost or unit price. If all three facts are present and
 * a manual meter-energy edit makes them disagree, the engine preserves the user's facts and reports
 * [ChargeCalculationIssue.BILLING_CONFLICT].
 *
 * The engine keeps full Double precision. UI formatting/rounding must happen at the presentation
 * boundary so repeated edits do not accumulate display-rounding drift.
 */
object ChargeCalculationEngine {
    private const val ABSOLUTE_COST_TOLERANCE = 0.02
    private const val RELATIVE_COST_TOLERANCE = 0.001
    private const val ENERGY_EPSILON = 1e-9

    fun calculate(input: ChargeCalculationInput): ChargeCalculationResult = derive(input)

    fun editBilling(
        input: ChargeCalculationInput,
        field: ChargeBillingField,
        value: Double?
    ): ChargeCalculationResult {
        val updated = when (field) {
            ChargeBillingField.TOTAL_COST -> editTotalCost(input, value)
            ChargeBillingField.UNIT_PRICE -> editUnitPrice(input, value)
            ChargeBillingField.METER_ENERGY -> editMeterEnergy(input, value)
        }
        return derive(updated)
    }

    private fun editTotalCost(input: ChargeCalculationInput, value: Double?): ChargeCalculationInput {
        var updated = input.copy(totalCost = value)
        if (value == null || value < 0.0) return updated

        val price = input.unitPrice
        val energy = input.meterEnergyKwh
        updated = when {
            price != null && price > 0.0 -> updated.copy(meterEnergyKwh = value / price)
            price != null -> updated
            energy != null && energy > 0.0 -> updated.copy(unitPrice = value / energy)
            else -> updated
        }
        return updated
    }

    private fun editUnitPrice(input: ChargeCalculationInput, value: Double?): ChargeCalculationInput {
        var updated = input.copy(unitPrice = value)
        if (value == null || value < 0.0) return updated

        val cost = input.totalCost
        val energy = input.meterEnergyKwh
        updated = when {
            cost != null && cost >= 0.0 && value > 0.0 -> updated.copy(meterEnergyKwh = cost / value)
            cost != null -> updated
            energy != null && energy >= 0.0 -> updated.copy(totalCost = value * energy)
            else -> updated
        }
        return updated
    }

    private fun editMeterEnergy(input: ChargeCalculationInput, value: Double?): ChargeCalculationInput {
        var updated = input.copy(meterEnergyKwh = value)
        if (value == null || value < 0.0) return updated

        val cost = input.totalCost
        val price = input.unitPrice
        updated = when {
            cost != null && price != null -> updated
            cost != null && cost >= 0.0 && value > 0.0 -> updated.copy(unitPrice = cost / value)
            price != null && price >= 0.0 -> updated.copy(totalCost = price * value)
            else -> updated
        }
        return updated
    }

    private fun derive(input: ChargeCalculationInput): ChargeCalculationResult {
        val issues = linkedSetOf<ChargeCalculationIssue>()

        if (listOf(input.totalCost, input.unitPrice, input.meterEnergyKwh, input.vehicleEnergyKwh)
                .filterNotNull()
                .any { it < 0.0 }
        ) {
            issues += ChargeCalculationIssue.NEGATIVE_VALUE
        }

        if (billingConflicts(input)) {
            issues += ChargeCalculationIssue.BILLING_CONFLICT
        }

        val durationMillis = durationMillis(input, issues)
        val averagePowerKw = if (
            durationMillis != null && durationMillis > 0L &&
            input.meterEnergyKwh != null && input.meterEnergyKwh >= 0.0
        ) {
            input.meterEnergyKwh / (durationMillis / 3_600_000.0)
        } else {
            null
        }

        val (lossEnergyKwh, lossRate) = lossMetrics(input, issues)

        return ChargeCalculationResult(
            input = input,
            durationMillis = durationMillis,
            averagePowerKw = averagePowerKw,
            lossEnergyKwh = lossEnergyKwh,
            lossRate = lossRate,
            issues = issues
        )
    }

    private fun billingConflicts(input: ChargeCalculationInput): Boolean {
        val cost = input.totalCost ?: return false
        val price = input.unitPrice ?: return false
        val energy = input.meterEnergyKwh ?: return false
        if (cost < 0.0 || price < 0.0 || energy < 0.0) return false
        if (price == 0.0) return cost > ABSOLUTE_COST_TOLERANCE

        val expectedCost = price * energy
        val tolerance = max(ABSOLUTE_COST_TOLERANCE, abs(cost) * RELATIVE_COST_TOLERANCE)
        return abs(expectedCost - cost) > tolerance
    }

    private fun durationMillis(
        input: ChargeCalculationInput,
        issues: MutableSet<ChargeCalculationIssue>
    ): Long? {
        val start = input.startTimeEpochMillis ?: return null
        val end = input.endTimeEpochMillis ?: return null
        if (end <= start) {
            issues += ChargeCalculationIssue.END_NOT_AFTER_START
            return null
        }
        return end - start
    }

    private fun lossMetrics(
        input: ChargeCalculationInput,
        issues: MutableSet<ChargeCalculationIssue>
    ): Pair<Double?, Double?> {
        val meter = input.meterEnergyKwh ?: return null to null
        val vehicle = input.vehicleEnergyKwh ?: return null to null
        if (meter <= 0.0 || vehicle < 0.0) return null to null
        if (vehicle - meter > ENERGY_EPSILON) {
            issues += ChargeCalculationIssue.VEHICLE_ENERGY_EXCEEDS_METER
            return null to null
        }

        val loss = (meter - vehicle).coerceAtLeast(0.0)
        return loss to (loss / meter)
    }
}
