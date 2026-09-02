package com.evchargebook.ui.records

import com.evchargebook.domain.charge.ChargeBillingField
import com.evchargebook.domain.charge.ChargeCalculationEngine
import com.evchargebook.domain.charge.ChargeCalculationInput
import com.evchargebook.domain.charge.ChargeCalculationIssue
import java.math.BigDecimal
import java.math.RoundingMode

data class ChargeBillingEditorState(
    val totalCostText: String,
    val unitPriceText: String,
    val meterEnergyText: String,
    val calculationInput: ChargeCalculationInput,
    val issues: Set<ChargeCalculationIssue> = emptySet()
) {
    val totalCost: Double? get() = calculationInput.totalCost
    val unitPrice: Double? = calculationInput.unitPrice
    val meterEnergyKwh: Double? get() = calculationInput.meterEnergyKwh
}

/**
 * Text-safe adapter around [ChargeCalculationEngine].
 *
 * The edited field always keeps the user's raw text so intermediate states such as an empty field,
 * `1.` or `0.` do not jump the cursor. Only dependant fields that were actually recalculated are
 * replaced with formatted text. Full-precision numeric state stays in [calculationInput].
 */
object ChargeBillingEditor {
    fun create(
        totalCostText: String,
        unitPriceText: String,
        meterEnergyText: String,
        authoritativeBillingFields: Set<ChargeBillingField>
    ): ChargeBillingEditorState {
        val input = ChargeCalculationInput(
            totalCost = totalCostText.toFiniteDoubleOrNull(),
            unitPrice = unitPriceText.toFiniteDoubleOrNull(),
            meterEnergyKwh = meterEnergyText.toFiniteDoubleOrNull(),
            authoritativeBillingFields = authoritativeBillingFields
        )
        val calculated = ChargeCalculationEngine.calculate(input)
        return ChargeBillingEditorState(
            totalCostText = totalCostText,
            unitPriceText = unitPriceText,
            meterEnergyText = meterEnergyText,
            calculationInput = calculated.input,
            issues = calculated.issues
        )
    }

    fun edit(
        state: ChargeBillingEditorState,
        field: ChargeBillingField,
        rawText: String
    ): ChargeBillingEditorState {
        val previous = state.calculationInput
        val parsed = rawText.toFiniteDoubleOrNull()
        val result = ChargeCalculationEngine.editBilling(previous, field, parsed)
        val next = result.input

        return ChargeBillingEditorState(
            totalCostText = textFor(
                field = ChargeBillingField.TOTAL_COST,
                editedField = field,
                rawText = rawText,
                previousText = state.totalCostText,
                previousValue = previous.totalCost,
                nextValue = next.totalCost
            ),
            unitPriceText = textFor(
                field = ChargeBillingField.UNIT_PRICE,
                editedField = field,
                rawText = rawText,
                previousText = state.unitPriceText,
                previousValue = previous.unitPrice,
                nextValue = next.unitPrice
            ),
            meterEnergyText = textFor(
                field = ChargeBillingField.METER_ENERGY,
                editedField = field,
                rawText = rawText,
                previousText = state.meterEnergyText,
                previousValue = previous.meterEnergyKwh,
                nextValue = next.meterEnergyKwh
            ),
            calculationInput = next,
            issues = result.issues
        )
    }

    private fun textFor(
        field: ChargeBillingField,
        editedField: ChargeBillingField,
        rawText: String,
        previousText: String,
        previousValue: Double?,
        nextValue: Double?
    ): String {
        if (field == editedField) return rawText
        if (sameNumber(previousValue, nextValue)) return previousText
        return nextValue?.let { formatCalculated(field, it) }.orEmpty()
    }

    private fun sameNumber(left: Double?, right: Double?): Boolean = when {
        left == null && right == null -> true
        left == null || right == null -> false
        else -> left.toBits() == right.toBits()
    }

    private fun formatCalculated(field: ChargeBillingField, value: Double): String {
        val scale = when (field) {
            ChargeBillingField.TOTAL_COST -> 2
            ChargeBillingField.UNIT_PRICE -> 4
            ChargeBillingField.METER_ENERGY -> 3
        }
        return BigDecimal.valueOf(value)
            .setScale(scale, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }

    private fun String.toFiniteDoubleOrNull(): Double? =
        toDoubleOrNull()?.takeIf { it.isFinite() }
}
