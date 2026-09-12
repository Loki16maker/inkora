package com.inkora.app

import com.inkora.domain.model.DocumentContent
import com.inkora.domain.model.Folder
import kotlinx.serialization.Serializable

/** Portable, versioned local backup format. It contains document JSON and no account data. */
@Serializable
data class InkoraBackup(
    val formatVersion: Int = InkoraConfig.backupFormatVersion,
    val createdAtEpochMs: Long,
    val folders: List<Folder> = emptyList(),
    val documents: List<DocumentContent> = emptyList(),
)
