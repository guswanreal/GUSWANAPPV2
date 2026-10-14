package com.example.myapplication

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.AtomicFile
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
class EditorAdvancedTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun layer(id:Int)=Layer(id,"Layer",LayerType.Shape,Color.Red,0f,10f)
    private fun snapshot(layers:List<Layer>)=ProjectSnapshot("test-project",ProjectSettings(),layers,2f,1,12345L)
    private val size=Size(1000f,500f)

    @Test fun projectRoundTripPreservesAnimatedEffectsParentingAndTracking() {
        val keys=Track(.2f,listOf(Keyframe(0f,.1f,Easing.Bezier,.4f,.5f,.6f,.7f),Keyframe(5f,.9f)),true)
        val source=layer(1).copy(parentId=2,parentOffset=ParentOffset(1.2f,.1f,.2f,.8f,.3f,.4f),is3D=true,
            effects=listOf(EffectInstance.of(FxType.OpticalFlares,22,0 to keys)),
            tracked=TrackPath(floatArrayOf(.1f,Float.NaN),floatArrayOf(.2f,Float.NaN)),
            footage=Footage("f","file:///source",false,1920,1080,10f,30f,300,"/proxy"))
            .withTrack(Prop.OrbitX,keys)
        val restored=ProjectCodec.decode(ProjectCodec.encode(snapshot(listOf(source,layer(2)))))
        val actual=restored.layers.first()
        assertEquals(source.effects,actual.effects); assertEquals(source.parentOffset,actual.parentOffset)
        assertEquals(keys,actual.track(Prop.OrbitX)); assertEquals(source.footage,actual.footage)
        assertTrue(actual.is3D); assertTrue(actual.tracked!!.x[1].isNaN())
        assertEquals(2f,restored.time,0f)
    }

    @Test fun interruptedWriteAndCorruptionRecoverValidSnapshots() {
        val directory=File(context.cacheDir,"store-test-${UUID.randomUUID()}")
        try {
            val store=ProjectStore(directory)
            val first=snapshot(listOf(layer(1)))
            store.save(first)
            // Simulates death after writing an incomplete .new, before atomic commit.
            AtomicFile(File(directory,"test-project.json")).startWrite().use { it.write("partial".toByteArray()) }
            assertEquals(first.layers.first().name,ProjectStore(directory).load(first.id)!!.layers.first().name)
            store.save(first.copy(layers=listOf(layer(1).copy(name="Revision 2"))))
            File(directory,"test-project.json").writeText("corrupt")
            assertEquals("Layer",ProjectStore(directory).load(first.id)!!.layers.first().name)
        } finally { directory.deleteRecursively() }
    }

    @Test fun editsAndUndoAreSavedOutsideEditorLifetime()=runBlocking {
        val directory=File(context.cacheDir,"queue-test-${UUID.randomUUID()}")
        try {
            val store=ProjectStore(directory)
            val state=EditorState(ProjectSettings(),null,onSave=store::enqueue)
            state.commit(listOf(layer(1)))
            state.commit(listOf(layer(1).copy(name="Changed")))
            state.undo(); store.flush()
            val loaded=ProjectStore(directory).load(state.projectId)!!
            assertEquals("Layer",loaded.layers.single().name)
            val reopened=EditorState(loaded.settings,null,loaded)
            assertEquals(state.projectId,reopened.projectId); assertEquals(state.layers,reopened.layers)
        } finally { directory.deleteRecursively() }
    }

    @Test fun importedPhotoAndSourceSurviveOriginalDeletion()=runBlocking {
        val source=File(context.cacheDir,"photo-test-${UUID.randomUUID()}.png")
        val bitmap=Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GREEN) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        var imported:Footage?=null
        try {
            imported=FootageImporter.import(context,Uri.fromFile(source),5f) {}
            source.delete()
            assertTrue(File(Uri.parse(imported.uri).path!!).isFile)
            assertTrue(imported.proxyDir.startsWith(context.filesDir.path))
            ExportFrames(context,640).use { assertNotNull(it.frame(imported,0f)) }
        } finally { source.delete(); imported?.let { File(it.proxyDir).deleteRecursively() } }
    }

    @Test fun perspectiveMatches2DAtZeroAndForeshortensTiltedPlanes() {
        val plane=layer(1).copy(is3D=true)
        val screen=Scene3D(listOf(plane),0f,size)
        val p=screen.screenPoint(plane,Offset(100f,50f))!!
        assertEquals(600f,p.x,.001f); assertEquals(300f,p.y,.001f)
        val tilted=plane.withTrack(Prop.OrbitY,Track(60f))
        val space=Scene3D(listOf(tilted),0f,size)
        val left=space.screenPoint(tilted,Offset(-100f,0f))!!
        val right=space.screenPoint(tilted,Offset(100f,0f))!!
        assertTrue(right.x-left.x<150f)
        val mapped=space.matrix(tilted)!!.map(Offset(100f,0f))
        assertEquals(right.x,mapped.x,.005f); assertEquals(right.y,mapped.y,.005f)
    }

    @Test fun spatialReparentPreservesProjectedCornersAndSaveReload() {
        val parent=layer(1).copy(type=LayerType.Null,is3D=true).withTrack(Prop.OrbitX,Track(20f)).withTrack(Prop.OrbitY,Track(30f)).withTrack(Prop.PosZ,Track(.3f))
        val child=layer(2).copy(is3D=true).withTrack(Prop.PosX,Track(.2f))
        val old=listOf(parent,child); val changed=reparentLayer(old,2,1,0f,size)
        val next=ProjectCodec.decode(ProjectCodec.encode(snapshot(listOf(parent,changed)))).layers
        for(point in listOf(Offset.Zero,Offset(50f,30f),Offset(-40f,20f))) {
            val a=Scene3D(old,0f,size).screenPoint(child,point)!!
            val b=Scene3D(next,0f,size).screenPoint(next[1],point)!!
            assertEquals(a.x,b.x,.01f); assertEquals(a.y,b.y,.01f)
        }
    }

    @Test fun depthOrdersPlanesAndRespects2DCompositingBoundaries() {
        val near=layer(1).copy(is3D=true)
        val far=layer(2).copy(is3D=true).withTrack(Prop.PosZ,Track(1f))
        val overlay=layer(3)
        val behindOverlay=layer(4).copy(is3D=true).withTrack(Prop.PosZ,Track(2f))
        val input=listOf(near,far,overlay,behindOverlay)
        assertEquals(listOf(2,1,3,4),Scene3D(input,0f,size).drawOrder().map { it.id })
    }

    @Test fun generatedMotionHasValidIdsKeyframesAndAllowedEffects() {
        val existing=listOf(layer(100))
        val generated=MotionBuilder.build(MotionPlan("HELLO","Motion test"),existing,2f,10f)
        assertTrue(generated.all { it.id>100 && it.start>=2f && it.end<=10f })
        assertEquals(generated.size,generated.map { it.id }.distinct().size)
        assertTrue(generated.any { it.is3D && it.type==LayerType.Text })
        assertTrue(generated.any { it.has(FxType.OpticalFlares) })
        assertTrue(generated.filter { it.parentId!=null }.all { child -> generated.any { it.id==child.parentId } })
        val decoded=MotionBuilder.decodePlan("""{"title":"Hi","seconds":999,"style":"unknown","effects":["OpticalFlares","execute-code"],"accent":"invalid"}""",4f)
        assertEquals(4f,decoded.seconds,0f); assertEquals(listOf(FxType.OpticalFlares),decoded.effects)
    }

    @Test fun opticalFlaresCompilesOnDeviceGpu() {
        assertTrue(shaderCompileErrors().joinToString(),shaderCompileErrors().isEmpty())
        val effect=buildRenderEffect(listOf(EffectInstance.of(FxType.OpticalFlares,1)),FxContext(640f,360f,0f,Offset(320f,180f),Color.White,Easing.Linear))
        assertNotNull(effect)
    }

    @Test(timeout=120000) fun generatedMotionExportsPlayableMp4()=runBlocking<Unit> {
        val output=File(context.cacheDir,"motion-test-${UUID.randomUUID()}.mp4")
        try {
            val layers=MotionBuilder.build(MotionPlan("MOTION","Export test",1f),emptyList(),0f,1f)
            VideoExporter.export(context,Uri.fromFile(output),layers,1f,320,180,24,Color.Black,createFontFamilyResolver(context)) {}
            assertTrue(output.length()>1000)
            val reader=MediaMetadataRetriever()
            try {
                reader.setDataSource(output.path)
                assertEquals("320",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
                assertEquals("24",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT))
                assertTrue(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()>=900)
                val bitmap=reader.getFrameAtTime(500000,MediaMetadataRetriever.OPTION_CLOSEST)
                assertNotNull(bitmap)
                bitmap!!
                try {
                    val pixels=IntArray(bitmap.width*bitmap.height)
                    bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
                    assertTrue("Export must contain rendered content",pixels.distinct().size>100)
                    if(InstrumentationRegistry.getArguments().getString("captureExport")=="true") {
                        File(context.cacheDir,"motion-export-check.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                    }
                } finally { bitmap.recycle() }
            } finally { reader.release() }
        } finally { output.delete() }
    }
}
