package ru.slem.taskwidget

import org.junit.Assert.assertEquals
import org.junit.Test

class VaultIdentityTest {
    @Test fun recoversVaultNameFromPersistedTreeId() {
        assertEquals("DemoVault", VaultIdentity.nameFromDocumentId("primary:Documents/DemoVault"))
        assertEquals("Vault", VaultIdentity.nameFromDocumentId("Vault"))
    }
}