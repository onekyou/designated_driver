package com.designated.callmanager.reservation.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** 한 경로(W 또는 C)의 결과 + 지연. */
data class PathResult(val reservation: UniversalReservation, val latencyMs: Long)

/** W(싼 STT 텍스트) vs C(오디오 천장) 채점 결과. */
data class ParseResult(val w: PathResult?, val c: PathResult?)

/** 사용자 표시용 메시지를 담은 예외. */
class ReservationParseException(message: String) : Exception(message)

/**
 * 녹음 업로드 → parseReservation CF → W/C 결과. CF가 처리 후 오디오를 즉시 삭제(PII).
 * Storage 업로드 = chat_audio 패턴(putBytes + audio/mp4 metadata), 호출 = WalletViewModel 패턴.
 */
class ReservationParseClient {

    private val auth = FirebaseAuth.getInstance()
    private val functions: FirebaseFunctions = Firebase.functions("asia-northeast3")
    private val storage = Firebase.storage

    /**
     * @param transcript 온폰 STT 텍스트(있으면 W경로 채점). null/blank 면 서버 STT(mode="W") 또는 C(레거시).
     * @param mode "W"=파일럿(서버 faster-whisper 전사→W, C 안 함). null=레거시(측정 하니스: gcsUri→C).
     * @throws ReservationParseException 표시용 메시지 포함.
     */
    suspend fun parse(
        audioBytes: ByteArray,
        recordedAt: String,
        transcript: String? = null,
        mode: String? = null,
    ): ParseResult {
        val uid = auth.currentUser?.uid
            ?: throw ReservationParseException("로그인이 필요합니다.")

        // 1) Storage 업로드 → gs:// URI
        val ref = storage.reference.child("reservation_test/$uid/${UUID.randomUUID()}.m4a")
        val meta = StorageMetadata.Builder().setContentType("audio/mp4").build()
        val gsUri: String = try {
            ref.putBytes(audioBytes, meta).await()
            ref.toString() // gs://bucket/path
        } catch (e: Exception) {
            throw ReservationParseException("녹음 업로드 실패: ${e.message?.take(60) ?: "알 수 없음"}")
        }

        // 2) parseReservation 호출
        val payload = buildMap<String, Any> {
            put("gcsUri", gsUri)
            put("recordedAt", recordedAt)
            if (!transcript.isNullOrBlank()) put("transcript", transcript)
            if (!mode.isNullOrBlank()) put("mode", mode)
        }
        try {
            val result = functions.getHttpsCallable("parseReservation").call(payload).await()
            val data = result.getData() as? Map<*, *>
                ?: throw ReservationParseException("응답 형식 오류")
            return ParseResult(w = pathFrom(data["W"]), c = pathFrom(data["C"]))
        } catch (e: FirebaseFunctionsException) {
            throw ReservationParseException(messageFor(e))
        } catch (e: ReservationParseException) {
            throw e
        } catch (e: Exception) {
            throw ReservationParseException("파싱 실패: ${e.message?.take(80) ?: "알 수 없음"}")
        }
    }

    private fun pathFrom(node: Any?): PathResult? {
        val map = node as? Map<*, *> ?: return null
        val resv = map["reservation"] as? Map<*, *> ?: return null
        val latency = (map["latencyMs"] as? Number)?.toLong() ?: 0L
        return PathResult(UniversalReservation.fromMap(resv), latency)
    }

    private fun messageFor(e: FirebaseFunctionsException): String = when (e.code) {
        FirebaseFunctionsException.Code.UNAUTHENTICATED,
        FirebaseFunctionsException.Code.PERMISSION_DENIED ->
            "권한이 없습니다. 다시 로그인 후 시도하세요."
        FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
            "입력값이 올바르지 않습니다."
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
        FirebaseFunctionsException.Code.UNAVAILABLE ->
            "네트워크 오류. 잠시 후 다시 시도하세요."
        else -> e.message ?: "파싱 실패"
    }
}
