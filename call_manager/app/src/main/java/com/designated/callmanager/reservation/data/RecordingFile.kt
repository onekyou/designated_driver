package com.designated.callmanager.reservation.data

import android.net.Uri

/**
 * 화면에 뜨는 녹음 1건. MediaStore 또는 SAF content URI.
 * @param parsed 파일명 파싱 결과. null = 통화녹음 형식 아님(SAF 임의 선택 시).
 */
data class RecordingFile(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val parsed: ParsedRecordingName?,
)
