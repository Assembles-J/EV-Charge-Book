package com.evchargebook.domain

object TripDiagnosticSamplingRules {
    const val REJECT_SAMPLE_INTERVAL = 25

    fun shouldPersistRejectedPoint(rejectedCount: Int): Boolean =
        rejectedCount == 1 || rejectedCount % REJECT_SAMPLE_INTERVAL == 0
}
