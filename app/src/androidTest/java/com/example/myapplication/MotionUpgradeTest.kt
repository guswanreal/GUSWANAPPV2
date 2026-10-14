package com.example.myapplication

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.engine.*
import com.example.myapplication.ui.editor.EditorState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MotionUpgradeTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val size=Size(1280f,720f)
    private fun layer(id:Int,type:LayerType)=Layer(id,"Layer $id",type,Color.Green,0f,2f,is3D=type!=LayerType.Camera)

    @Test fun cameraRigPreservesViewThenDrivesCameraAndDetachesWithoutJump() {
        val rig=layer(1,LayerType.Null).withTrack(Prop.PosX,Track(.3f)).withTrack(Prop.OrbitY,Track(24f)).withTrack(Prop.Scale,Track(1.3f))
        val camera=layer(2,LayerType.Camera).withTrack(Prop.Scale,Track(1.2f))
        val plane=layer(3,LayerType.Shape).withTrack(Prop.PosX,Track(.2f))
        val before=listOf(rig,camera,plane)
        assertTrue(canParentLayer(before,camera.id,rig.id))
        val linked=reparentLayer(before,camera.id,rig.id,0f,size)
        val attached=listOf(rig,linked,plane)
        for(p in listOf(Offset.Zero,Offset(80f,40f))) {
            val a=Scene3D(before,0f,size).screenPoint(plane,p)!!
            val b=Scene3D(attached,0f,size).screenPoint(plane,p)!!
            assertEquals(a.x,b.x,.02f); assertEquals(a.y,b.y,.02f)
        }
        val moved=rig.withTrack(Prop.PosX,Track(.5f))
        val driven=listOf(moved,linked,plane)
        val old=Scene3D(attached,0f,size).screenPoint(plane,Offset.Zero)!!
        val next=Scene3D(driven,0f,size).screenPoint(plane,Offset.Zero)!!
        assertTrue((old-next).getDistance()>10)
        val detached=reparentLayer(driven,2,null,0f,size)
        val final=Scene3D(listOf(moved,detached,plane),0f,size).screenPoint(plane,Offset.Zero)!!
        assertEquals(next.x,final.x,.02f); assertEquals(next.y,final.y,.02f)
    }

    @Test fun cameraCannotBeAParentOrFormCycles() {
        val rig=layer(1,LayerType.Null); val cam=layer(2,LayerType.Camera)
        assertFalse(canParentLayer(listOf(rig,cam),1,2))
        assertFalse(canParentLayer(listOf(rig.copy(parentId=2),cam),2,1))
        assertFalse(canParentLayer(listOf(layer(1,LayerType.Gradient),cam),2,1))
    }

    @Test fun videoTrimPreservesSourceExposureAndCannotExtendPastSource() {
        val f=Footage("test","file:///test",false,640,480,2f,30f,60,"/unused")
        val state=EditorState(ProjectSettings(duration=4f),null)
        state.commit(listOf(layer(1,LayerType.Video).copy(footage=f,start=1f,end=3f)))
        state.trim(1,.5f,0f)
        val clip=state.layers.single()
        assertEquals(.5f,clip.sourceIn,.001f)
        assertEquals(1f,clip.sourceTime(2f),.001f)
        state.trim(1,0f,2f)
        assertEquals(3f,state.layers.single().end,.001f)
        state.undo()
    }

    @Test fun faceDetectorAndVideoTrackingProduceEditablePersistedPose()=runBlocking<Unit> {
        val directory=File(context.cacheDir,"face-${UUID.randomUUID()}").apply { mkdirs() }
        val source=InstrumentationRegistry.getInstrumentation().context.assets.open("grace_hopper.jpg").use { BitmapFactory.decodeStream(it) }!!
        try {
            FacePoseDetector().use { detector ->
                val faces=detector.detect(source)
                assertTrue("Real sample face must be detected",faces.isNotEmpty())
                assertTrue(faces.first().headEulerAngleX.isFinite())
            }
            for(i in 0..11) {
                val frame=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888)
                Canvas(frame).apply { drawColor(android.graphics.Color.BLACK); drawBitmap(source,null,Rect(50+i*3,0,450+i*3,480),null) }
                File(directory,"f%05d.jpg".format(i)).outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG,95,it) }
                frame.recycle()
            }
            val f=Footage("face","file:///test",false,640,480,1.1f,10f,12,directory.path)
            val video=layer(1,LayerType.Video).copy(is3D=false,footage=f,end=1f)
            val result=FaceTracker3D.solve(video,0f) {}
            assertTrue(result.poses.size>=8)
            assertTrue(result.poses.last().x>result.poses.first().x)
            val state=EditorState(ProjectSettings(duration=2f),null)
            state.commit(listOf(video)); state.applyFaceSolve(1,result)
            val rig=state.selected!!
            assertTrue(rig.is3D); assertEquals(LayerType.Null,rig.type)
            assertTrue(rig.track(Prop.OrbitY).animated)
            val restored=ProjectCodec.decode(ProjectCodec.encode(state.snapshot()))
            assertEquals(result.poses.size,restored.layers.first().faceSolve!!.poses.size)
            state.undo(); assertEquals(1,state.layers.size)
        } finally { source.recycle(); directory.deleteRecursively() }
    }

    @Test fun blankVideoReportsMissingFaceInsteadOfFabricatingTracking()=runBlocking<Unit> {
        val directory=File(context.cacheDir,"blank-face-${UUID.randomUUID()}").apply { mkdirs() }
        val bitmap=Bitmap.createBitmap(480,360,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLACK) }
        try {
            for(i in 0..5) File(directory,"f%05d.jpg".format(i)).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,95,it) }
            val video=layer(1,LayerType.Video).copy(footage=Footage("blank","file:///test",false,480,360,.5f,10f,6,directory.path),end=.5f)
            try { FaceTracker3D.solve(video) {}; fail("Blank footage must fail") }
            catch(e:IllegalArgumentException) { assertTrue(e.message.orEmpty().contains("Wajah")) }
        } finally { bitmap.recycle(); directory.deleteRecursively() }
    }

    @Test(timeout=120000) fun chromaKeyActuallyRemovesGreenInExportedPixels()=runBlocking<Unit> {
        val output=File(context.cacheDir,"key-${UUID.randomUUID()}.mp4")
        try {
            val green=layer(1,LayerType.Shape).copy(is3D=false,shape=ShapeKind.Rect,effects=listOf(EffectInstance.of(FxType.ChromaKey,1)))
            VideoExporter.export(context,Uri.fromFile(output),listOf(green),.25f,320,180,24,Color.Blue,createFontFamilyResolver(context)) {}
            val reader=MediaMetadataRetriever()
            try {
                reader.setDataSource(output.path)
                val frame=reader.getFrameAtTime(100000,MediaMetadataRetriever.OPTION_CLOSEST)!!
                try {
                    val center=frame.getPixel(frame.width/2,frame.height/2)
                    assertTrue(android.graphics.Color.blue(center)>180)
                    assertTrue(android.graphics.Color.green(center)<80)
                } finally { frame.recycle() }
            } finally { reader.release() }
        } finally { output.delete() }
    }
}

