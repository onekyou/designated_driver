package com.designated.callmanager.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream

/**
 * QR 코드 생성 유틸리티
 */
object QRCodeGenerator {

    /**
     * QR 코드 Bitmap 생성
     * @param content QR 코드에 담을 내용 (URL 등)
     * @param size QR 코드 크기 (픽셀)
     * @return QR 코드 Bitmap
     */
    fun generateQRCodeBitmap(
        content: String,
        size: Int = 512
    ): Bitmap? {
        return try {
            val hints = hashMapOf<EncodeHintType, Any>().apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
                put(EncodeHintType.MARGIN, 1)
            }

            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(
                        x, y,
                        if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                    )
                }
            }

            bitmap
        } catch (e: Exception) {
            android.util.Log.e("QRCodeGenerator", "QR 코드 생성 실패", e)
            null
        }
    }

    /**
     * QR 코드를 파일로 저장
     * @param context Context
     * @param bitmap QR 코드 Bitmap
     * @param fileName 파일명 (확장자 제외)
     * @return 저장된 파일 경로, 실패 시 null
     */
    fun saveQRCodeToFile(
        context: Context,
        bitmap: Bitmap,
        fileName: String = "qr_code"
    ): File? {
        return try {
            // Pictures 디렉토리에 저장
            val picturesDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            val qrDir = File(picturesDir, "QRCodes")

            if (!qrDir.exists()) {
                qrDir.mkdirs()
            }

            val file = File(qrDir, "$fileName.png")
            val outputStream = FileOutputStream(file)

            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()

            android.util.Log.d("QRCodeGenerator", "QR 코드 저장 완료: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            android.util.Log.e("QRCodeGenerator", "QR 코드 저장 실패", e)
            null
        }
    }

    /**
     * QR 코드를 MediaStore에 저장 (갤러리에 표시됨)
     * Android 10 이상에서 권장
     */
    fun saveQRCodeToMediaStore(
        context: Context,
        bitmap: Bitmap,
        fileName: String = "qr_code_${System.currentTimeMillis()}"
    ): android.net.Uri? {
        return try {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "$fileName.png")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_PICTURES + "/DesignatedDriver")
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                android.util.Log.d("QRCodeGenerator", "QR 코드 MediaStore 저장 완료: $uri")
            }

            uri
        } catch (e: Exception) {
            android.util.Log.e("QRCodeGenerator", "QR 코드 MediaStore 저장 실패", e)
            null
        }
    }

    /**
     * 로고가 포함된 QR 코드 생성 (선택적 기능)
     * @param content QR 코드 내용
     * @param logoBitmap 중앙에 표시할 로고 Bitmap
     * @param size QR 코드 크기
     * @return 로고가 포함된 QR 코드 Bitmap
     */
    fun generateQRCodeWithLogo(
        content: String,
        logoBitmap: Bitmap,
        size: Int = 512
    ): Bitmap? {
        val qrBitmap = generateQRCodeBitmap(content, size) ?: return null

        return try {
            val combinedBitmap = qrBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = android.graphics.Canvas(combinedBitmap)

            // 로고 크기를 QR 코드의 20%로 설정
            val logoSize = (size * 0.2).toInt()
            val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, logoSize, logoSize, true)

            // 중앙에 로고 그리기
            val left = (size - logoSize) / 2f
            val top = (size - logoSize) / 2f

            // 로고 배경 (흰색 원형)
            val paint = android.graphics.Paint().apply {
                color = Color.WHITE
                style = android.graphics.Paint.Style.FILL
            }
            canvas.drawCircle(
                size / 2f,
                size / 2f,
                logoSize / 2f + 10,
                paint
            )

            // 로고 그리기
            canvas.drawBitmap(scaledLogo, left, top, null)

            combinedBitmap
        } catch (e: Exception) {
            android.util.Log.e("QRCodeGenerator", "로고 포함 QR 생성 실패", e)
            qrBitmap
        }
    }
}