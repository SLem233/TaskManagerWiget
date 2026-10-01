package ru.slem.taskwidget

object VaultIdentity {
    fun nameFromDocumentId(documentId: String): String =
        documentId.substringAfterLast('/').substringAfterLast(':')
}