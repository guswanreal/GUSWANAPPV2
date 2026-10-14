package com.example.myapplication

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.engine.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Random
import java.util.UUID
import kotlin.math.tan

@RunWith(AndroidJUnit4::class)
class CameraTracker3DTest {
    @Test(timeout=120000) fun solvesSyntheticCameraTranslationWithNonPlanarLandmarks()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(context.cacheDir,"sfm-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val random=Random(17)
            val points=List(160) { doubleArrayOf((random.nextDouble()-.5)*4.4,(random.nextDouble()-.5)*2.2,3+random.nextDouble()*5) }
            val width=640; val height=360; val focal=height/(2*tan(Math.toRadians(30.0)))
            val footage=Footage("synthetic","file:///synthetic",false,width,height,.75f,24f,18,directory.path)
            val paint=Paint().apply { color=android.graphics.Color.WHITE; isAntiAlias=false }
            for(frame in 0 until footage.frameCount) {
                val image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                image.eraseColor(android.graphics.Color.BLACK)
                val canvas=Canvas(image)
                points.forEach { p ->
                    val x=(width/2+focal*(p[0]-frame*.04)/p[2]).toFloat()
                    val y=(height/2+focal*p[1]/p[2]).toFloat()
                    canvas.drawRect(x-3,y-3,x+3,y+3,paint)
                }
                footage.proxyFile(frame).outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
                image.recycle()
            }
            val layer=Layer(1,"Synthetic",LayerType.Video,Color.White,0f,.75f,footage=footage)
            val solve=CameraTracker3D.solve(layer)
            assertTrue("${solve.points.size} landmarks",solve.points.size>=25)
            assertTrue("${solve.poses.size} poses",solve.poses.size>=10)
            assertTrue(solve.referenceDepth>0)
            assertTrue("Camera should move right",solve.poses.last().translation[0]<-.1f)
            assertTrue(solve.poses.all { p -> p.rotation.all { it.isFinite() } && p.translation.all { it.isFinite() } })
            val loaded=CameraSolve3D.fromJson(solve.toJson())
            assertEquals(solve,loaded)
        } finally { directory.deleteRecursively() }
    }

    @Test fun texturelessVideoIsRejectedInsteadOfInventingCameraMotion()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(context.cacheDir,"sfm-empty-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val footage=Footage("empty","file:///empty",false,160,90,1f,10f,10,directory.path)
            val bitmap=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888)
            for(frame in 0 until 10) footage.proxyFile(frame).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
            val layer=Layer(1,"Empty",LayerType.Video,Color.White,0f,1f,footage=footage)
            val failure=runCatching { CameraTracker3D.solve(layer) }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
        } finally { directory.deleteRecursively() }
    }
}
