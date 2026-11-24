package com.ibrahim.av1mix.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderApprovalTest {
    @Test
    fun providerIsNotAdmittedByDefault() {
        val approval = ProviderApproval(
            termsReviewed = false,
            playbackPermissionConfirmed = false,
            moderationAndTakedownReviewed = false,
            adultContentProvenanceReviewed = false,
        )
        assertFalse(approval.admitted)
    }

    @Test
    fun everyIndependentGateMustPass() {
        val approvals = listOf(
            ProviderApproval(true, false, true, true),
            ProviderApproval(true, true, false, true),
            ProviderApproval(true, true, true, false),
            ProviderApproval(false, true, true, true),
        )
        approvals.forEach { assertFalse(it.admitted) }
    }

    @Test
    fun providerIsAdmittedOnlyWhenAllGatesPass() {
        val approval = ProviderApproval(
            termsReviewed = true,
            playbackPermissionConfirmed = true,
            moderationAndTakedownReviewed = true,
            adultContentProvenanceReviewed = true,
        )
        assertTrue(approval.admitted)
    }
}
