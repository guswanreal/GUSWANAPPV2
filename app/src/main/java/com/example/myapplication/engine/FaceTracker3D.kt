package com.example.myapplication.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt

data class FacePose3D(val time: Float, val x: Float, val y: Float, val width: Float,
    val pitch: Float, val yaw: Float, val roll: Float, val landmarks: List<List<Float>> = emptyList())

data class FaceSolve3D(val poses: List<FacePose3D>, val analyzedFrames: Int, val detectedFrames: Int) {
    val coverage: Float get() = detectedFrames.toFloat()/analyzedFrames.coerceAtLeast(1)
    fun at(t: Float): FacePose3D? = poses.minByOrNull { abs(it.time-t) }?.takeIf { abs(it.time-t) < .16f }
    fun toJson() = JSONObject().put("analyzed",analyzedFrames).put("detected",detectedFrames)
        .put("poses",JSONArray(poses.map { p -> JSONArray(listOf(p.time,p.x,p.y,p.width,p.pitch,p.yaw,p.roll)).put(JSONArray(p.landmarks.map { JSONArray(it) })) }))
    companion object {
        fun fromJson(o: JSONObject): FaceSolve3D {
            val array=o.getJSONArray("poses")
            return FaceSolve3D(List(array.length()) { i ->
                val p=array.getJSONArray(i); val points=p.optJSONArray(7)
                FacePose3D(p.getDouble(0).toFloat(),p.getDouble(1).toFloat(),p.getDouble(2).toFloat(),p.getDouble(3).toFloat(),
                    p.getDouble(4).toFloat(),p.getDouble(5).toFloat(),p.getDouble(6).toFloat(),
                    if(points==null) emptyList() else List(points.length()) { j -> points.getJSONArray(j).let { listOf(it.getDouble(0).toFloat(),it.getDouble(1).toFloat()) } })
            },o.getInt("analyzed"),o.getInt("detected"))
        }
    }
}

// Keep the input bitmap alive until ML Kit finishes, even when the analysis job is cancelled.
private suspend fun <T> Task<T>.result(): T = suspendCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
    addOnCanceledListener { c.resumeWithException(kotlinx.coroutines.CancellationException("Face detection cancelled")) }
}

class FacePoseDetector : AutoCloseable {
    private val detector=FaceDetection.getClient(FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL).enableTracking().build())

    suspend fun detect(bitmap: Bitmap): List<Face> = detector.process(InputImage.fromBitmap(bitmap,0)).result()
    override fun close() { detector.close() }
}

object FaceTracker3D {
    suspend fun solve(layer: Layer, smoothness: Float = .08f, onProgress: (Float) -> Unit): FaceSolve3D = withContext(Dispatchers.Default) {
        val footage=layer.footage ?: throw IllegalArgumentException("Impor video wajah terlebih dahulu.")
        require(!footage.isImage) { "Tracking gerak membutuhkan video." }
        val rate=minOf(footage.fps,15f).coerceAtLeast(1f)
        val count=((layer.end-layer.start)*rate).roundToInt().coerceAtLeast(1)
        val samples=ArrayList<FacePose3D>()
        var previous: FacePose3D?=null
        var identity: Int?=null
        var lost=0
        var detected=0
        var analyzed=0
        FacePoseDetector().use { detector ->
            for(i in 0..count) {
                currentCoroutineContext().ensureActive()
                analyzed++
                val time=minOf(layer.end,layer.start+i/rate)
                val bitmap=BitmapFactory.decodeFile(footage.proxyFile(footage.frameIndex(layer.sourceTime(time))).path)
                    ?: throw IllegalArgumentException("Proxy video tidak dapat dibaca. Impor ulang video.")
                try {
                    val faces=detector.detect(bitmap)
                    val old=previous
                    val face=faces.firstOrNull { identity!=null && it.trackingId==identity }
                        ?: faces.filter { f -> old==null || hypot(f.boundingBox.exactCenterX()/bitmap.width-old.x,
                            f.boundingBox.exactCenterY()/bitmap.height-old.y)<.25f }
                            .maxByOrNull { it.boundingBox.width()*it.boundingBox.height() }
                    if(face==null) {
                        lost++
                        if(lost/rate > .3f && samples.isNotEmpty()) break
                    } else {
                        identity=face.trackingId
                        val points=listOf(FaceLandmark.LEFT_EYE,FaceLandmark.RIGHT_EYE,FaceLandmark.NOSE_BASE,
                            FaceLandmark.MOUTH_LEFT,FaceLandmark.MOUTH_RIGHT,FaceLandmark.MOUTH_BOTTOM)
                            .mapNotNull { face.getLandmark(it)?.position?.let { p -> listOf(p.x/bitmap.width,p.y/bitmap.height) } }
                        val raw=FacePose3D(time,face.boundingBox.exactCenterX()/bitmap.width,face.boundingBox.exactCenterY()/bitmap.height,
                            face.boundingBox.width().toFloat()/bitmap.width,-face.headEulerAngleX,face.headEulerAngleY,-face.headEulerAngleZ,points)
                        val alpha=if(old==null || smoothness<=0f) 1f else (1-exp(-(time-old.time)/smoothness)).coerceIn(0f,1f)
                        fun lerp(a:Float,b:Float)=a+(b-a)*alpha
                        fun angle(a:Float,b:Float)=a+((b-a+540f)%360f-180f)*alpha
                        val pose=if(old==null) raw else raw.copy(x=lerp(old.x,raw.x),y=lerp(old.y,raw.y),width=lerp(old.width,raw.width),
                            pitch=angle(old.pitch,raw.pitch),yaw=angle(old.yaw,raw.yaw),roll=angle(old.roll,raw.roll))
                        samples+=pose; previous=pose; lost=0; detected++
                    }
                } finally { bitmap.recycle() }
                withContext(Dispatchers.Main) { onProgress((i+1f)/(count+1f)) }
            }
        }
        require(samples.size>=3) { "Wajah belum terlacak. Gunakan video dengan wajah besar, terang, dan tidak tertutup." }
        FaceSolve3D(samples,analyzed,detected)
    }
}
