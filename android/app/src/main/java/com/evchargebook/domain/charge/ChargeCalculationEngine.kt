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
    val endTimeEpochMillis: Long? = null,
    /**
     * Billing values that should be treated as user/preset-confirmed facts for the current editor.
     * Values produced by this engine are deliberately removed from this set so they remain live
     * dependants on the next edit instead of accidentally becoming locked facts.
     */
    val authoritativeBillingFields: Set<ChargeBillingField> = emptySet()
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
 * The edited field becomes authoritative. When a higher-priority edit recalculates a dependant,
 * that dependant is marked calculated (non-authoritative). This distinction is important for a
 * mature text editor: a cost calculated from price * energy must keep following later energy edits
 * until the user explicitly edits cost.
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
        val edited = input.copy(
            authoritativeBillingFields = input.authoritativeBillingFields + field
        )
        val updated = when (field) {
            ChargeBillingField.TOTAL_COST -> editTotalCost(edited, value)
            ChargeBillingField.UNIT_PRICE -> editUnitPrice(edited, value)
            ChargeBillingField.METER_ENERGY -> editMeterEnergy(edited, value)
        }
        return derive(updated)
    }

    private fun editTotalCost(input: ChargeCalculationInput, value: Double?): ChargeCalculationInput {
        var updated = input.copy(totalCost = value)
        if (value == null || value < 0.0) return updated

        val price = input.unitPrice
        val energy = input.meterEnergyKwh
        updated = when {
            price != null && price > 0.0 -> updated
                .copy(meterEnergyKwh = value / price)
                .markCalculated(ChargeBillingField.METER_ENERGY)
            price != null -> updated
            energy != null && energy > 0.0 -> updated
                .copy(unitPrice = value / energy)
                .markCalculated(ChargeBillingField.UNIT_PRICE)
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
            cost != null && cost >= 0.0 && value > 0.0 -> updated
                .copy(meterEnergyKwh = cost / value)
                .markCalculated(ChargeBillingField.METER_ENERGY)
            cost != null -> updated
            energy != null && energy >= 0.0 -> updated
                .copy(totalCost = value * energy)
                .markCalculated(ChargeBillingField.TOTAL_COST)
            else -> updated
        }
        return updated
    }

    private fun editMeterEnergy(input: ChargeCalculationInput, value: Double?): ChargeCalculationInput {
        var updated = input.copy(meterEnergyKwh = value)
        if (value == null || value < 0.0) return updated

        val cost = input.totalCost
        val price = input.unitPrice
        val costLocked = ChargeBillingField.TOTAL_COST in input.authoritativeBillingFields
        val priceLocked = ChargeBillingField.UNIT_PRICE in input.authoritativeBillingFields

        updated = when {
            cost != null && price != null && costLocked && priceLocked -> updated
            cost != null && costLocked && value > 0.0 -> updated
                .copy(unitPrice = cost / value)
                .markCalculated(ChargeBillingField.UNIT_PRICE)
            price != null && priceLocked -> updated
                .copy(totalCost = price * value)
                .markCalculated(ChargeBillingField.TOTAL_COST)
            cost != null && price == null && value > 0.0 -> updated
                .copy(unitPrice = cost / value)
                .markCalculated(ChargeBillingField.UNIT_PRICE)
            price != null -> updated
                .copy(totalCost = price * value)
                .markCalculated(ChargeBillingField.TOTAL_COST)
            else -> updated
        }
        return updated
    }

    private fun ChargeCalculationInput.markCalculated(field: ChargeBillingField): ChargeCalculationInput =
        copy(authoritativeBillingFields = authoritativeBillingFields - field)

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
