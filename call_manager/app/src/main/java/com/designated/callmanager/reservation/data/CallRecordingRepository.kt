package com.designated.callmanager.reservation.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns

/**
 * 삼성 자동 통화녹음 접근. 1순위 MediaStore(`Recordings/Call/`), 폴백 SAF.
 *
 * 실측(2026-06-03, S21+): `/sdcard/Recordings/Call/` 의 m4a 가 audio 컬렉션에 인덱싱됨,
 * `READ_MEDIA_AUDIO`(일반 권한)로 쿼리 가능. 막히면 SAF 1회 허용으로 100% 폴백.
 */
class CallRecordingRepository(private val context: Context) {

    /** MediaStore 에서 통화녹음 목록(파일명 파싱되는 것만). 권한 거부/0건이면 빈 리스트 → UI 가 SAF 유도. */
    fun listFromMediaStore(limit: Int = 50): List<RecordingFile> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
        )
        // RELATIVE_PATH 는 Q+ 에만 존재 → 가드. 구버전은 전체에서 파일명으로 거른다.
        val selection: String?
        val args: Array<String>?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
            args = arrayOf("%Recordings/Call/%")
        } else {
            selection = null
            args = null
        }
        val sort = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val out = mutableListOf<RecordingFile>()
        runCatching {
            context.contentResolver.query(collection, projection, selection, args, sort)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                while (c.moveToNext() && out.size < limit) {
                    val name = c.getString(nameCol) ?: continue
                    val parsed = RecordingFilenameParser.parse(name) ?: continue // 통화녹음 형식만
                    val uri = ContentUris.withAppendedId(collection, c.getLong(idCol))
                    out.add(RecordingFile(uri, name, c.getLong(sizeCol), parsed))
                }
            }
        }
        return out
    }

    /** SAF 등으로 직접 고른 단일 URI → RecordingFile (파일명 파싱 시도). */
    fun fromPickedUri(uri: Uri): RecordingFile {
        var name = "녹음.m4a"
        var size = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }
                        ?.let { name = c.getString(it) ?: name }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }
                        ?.let { size = c.getLong(it) }
                }
            }
        }
        return RecordingFile(uri, name, size, RecordingFilenameParser.parse(name))
    }

    /** content URI(MediaStore·SAF 공통) → bytes. */
    fun readBytes(uri: Uri): ByteArray? =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
}
