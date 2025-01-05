package com.rcmiku.media.control.tweak.utils

import android.content.res.Resources.getSystem
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.util.TypedValue

object AppUtils {

    val Int.dp: Int
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            getSystem().displayMetrics
        ).toInt()

    fun Bitmap.scale(width: Int, height: Int): Bitmap {
        return Bitmap.createScaledBitmap(this, width, height, true)
    }

    fun Bitmap.mesh(floats: FloatArray): Bitmap {
        val meshArray = FloatArray(72)
        val width = width.toFloat()
        val height = height.toFloat()
        val rows = MeshUtil.MESH_ROWS
        val cols = MeshUtil.MESH_COLS
        val bitmap = Bitmap.createBitmap(this)

        for (row in 0..rows) {
            for (col in 0..cols) {
                val index = row * 12 + col * 2
                meshArray[index] = floats[index] * width
                meshArray[index + 1] = floats[index + 1] * height
            }
        }

        Canvas(bitmap).drawBitmapMesh(bitmap, rows, cols, meshArray, 0, null, 0, null)
        return bitmap
    }

    fun Bitmap.blur(radius: Float): Bitmap {
        val imageReader = ImageReader.newInstance(
            this.width, this.height,
            PixelFormat.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        )
        val renderNode = RenderNode("BlurEffect").apply {
            setPosition(0, 0, imageReader.width, imageReader.height)
            setRenderEffect(
                RenderEffect.createBlurEffect(
                    radius, radius, Shader.TileMode.MIRROR
                )
            )
        }
        val hardwareRenderer = HardwareRenderer().apply {
            setSurface(imageReader.surface)
            setContentRoot(renderNode)
        }
        renderNode.beginRecording().apply {
            drawBitmap(this@blur, 0f, 0f, null)
        }
        renderNode.endRecording()
        hardwareRenderer.createRenderRequest()
            .setWaitForPresent(true)
            .syncAndDraw()

        return runCatching {
            imageReader.acquireNextImage().use { image ->
                image.hardwareBuffer?.use { hardwareBuffer ->
                    Bitmap.wrapHardwareBuffer(hardwareBuffer, null)
                        ?.copy(Bitmap.Config.ARGB_8888, true)
                        ?: throw RuntimeException("Bitmap is null")
                } ?: throw RuntimeException("HardwareBuffer is null")
            }
        }.onFailure { e ->
            throw RuntimeException("Failed to blur bitmap", e)
        }.also {
            renderNode.discardDisplayList()
            hardwareRenderer.destroy()
        }.getOrThrow()
    }

    fun Bitmap.zoom(scaleFactor: Int = 8): Bitmap {
        return Bitmap.createScaledBitmap(
            this,
            this.width / scaleFactor,
            this.height / scaleFactor,
            true
        )
    }

    fun Bitmap.handleImageEffect(saturation: Float): Bitmap {
        val colorMatrix = ColorMatrix().apply {
            setSaturation(saturation)
        }
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawBitmap(this, 0F, 0F, paint)
        return bitmap
    }

    fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            drawable.bitmap?.let {
                return it
            }
        }

        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }

}