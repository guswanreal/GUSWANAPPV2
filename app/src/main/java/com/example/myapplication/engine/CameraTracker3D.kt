package com.example.myapplication.engine

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import kotlin.math.*

/** Monocular, fixed-focal-length camera solve. Reconstruction has relative, not metric, scale. */
object CameraTracker3D {
    private class Mats : AutoCloseable {
        private val owned=ArrayList<Mat>()
        fun <T:Mat> keep(mat:T):T { owned+=mat; return mat }
        override fun close() { owned.asReversed().forEach { it.release() } }
    }
    private fun gray(f:Footage,index:Int):Mat {
        val bitmap=BitmapFactory.decodeFile(f.proxyFile(index).path) ?: error("Frame $index tidak tersedia. Impor ulang video.")
        val rgba=Mat(); val gray=Mat()
        try { Utils.bitmapToMat(bitmap,rgba); Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY); return gray }
        catch(e:Throwable) { gray.release(); throw e }
        finally { rgba.release(); bitmap.recycle() }
    }

    private data class Flow(val oldIndices:List<Int>,val points:List<Point>)
    private fun flow(a:Mat,b:Mat,points:List<Point>):Flow = Mats().use { m ->
        val source=m.keep(MatOfPoint2f()).apply { fromList(points) }
        val target=m.keep(MatOfPoint2f()); val back=m.keep(MatOfPoint2f())
        val status=m.keep(MatOfByte()); val backward=m.keep(MatOfByte()); val error=m.keep(MatOfFloat()); val backError=m.keep(MatOfFloat())
        val window=Size(21.0,21.0); val criteria=TermCriteria(TermCriteria.COUNT+TermCriteria.EPS,30,.01)
        Video.calcOpticalFlowPyrLK(a,b,source,target,status,error,window,3,criteria)
        Video.calcOpticalFlowPyrLK(b,a,target,back,backward,backError,window,3,criteria)
        val next=target.toArray(); val returned=back.toArray(); val ok=status.toArray(); val bok=backward.toArray()
        val indices=points.indices.filter { i -> ok[i].toInt()!=0 && bok[i].toInt()!=0 &&
            hypot(points[i].x-returned[i].x,points[i].y-returned[i].y)<1.2 &&
            next[i].x in 2.0..(b.cols()-3.0) && next[i].y in 2.0..(b.rows()-3.0) }
        Flow(indices,indices.map { next[it] })
    }

    suspend fun solve(layer:Layer, verticalFov:Float=60f, onProgress:(Float)->Unit={}):CameraSolve3D = withContext(Dispatchers.Default) {
        require(OpenCVLoader.initLocal()) { "OpenCV tidak dapat dimuat pada perangkat ini." }
        val f=layer.footage ?: error("Pilih layer video.")
        require(!f.isImage) { "Tracking kamera memerlukan video." }
        require(layer.stabilize==null && layer.effects.none { it.enabled &&
            (it.type.category==FxCategory.Distort || it.type==FxType.Wiggle) }) {
            "Matikan stabilisasi dan efek pengubah posisi/distorsi sebelum solve kamera."
        }
        require(!layer.is3D && layer.parentId==null && layer.parentOffset==ParentOffset() &&
            listOf(Prop.PosX,Prop.PosY,Prop.AnchorX,Prop.AnchorY,Prop.Scale,Prop.Rotation).all { p ->
                !layer.track(p).animated && abs(layer.track(p).static-p.default)<.0001f
            }) { "Reset transform video dan lepas parent sebelum solve; gunakan framing video asli." }
        val first=f.frameIndex(layer.sourceIn)
        val last=f.frameIndex(layer.sourceIn+layer.end-layer.start)
        val baseline=(first+max(2,(f.fps*.35f).roundToInt())).coerceAtMost(last)
        require(baseline>first) { "Klip terlalu pendek untuk tracking kamera." }
        Mats().use { m ->
            val initial=m.keep(gray(f,first)); val second=m.keep(gray(f,baseline))
            val corners=m.keep(MatOfPoint())
            Imgproc.goodFeaturesToTrack(initial,corners,700,.01,8.0)
            val starts=corners.toList()
            require(starts.size>=40) { "Detail video terlalu sedikit. Pilih latar dengan tekstur." }
            val matching=flow(initial,second,starts)
            require(matching.points.size>=35) { "Gerakan terlalu cepat atau terlalu buram untuk solve." }
            val p0=m.keep(MatOfPoint2f()).apply { fromList(matching.oldIndices.map { starts[it] }) }
            val p1=m.keep(MatOfPoint2f()).apply { fromList(matching.points) }
            val focal=initial.rows()/(2*tan(Math.toRadians(verticalFov.coerceIn(25f,110f).toDouble())/2))
            val cx=initial.cols()/2.0; val cy=initial.rows()/2.0
            val k=m.keep(Mat.eye(3,3,CvType.CV_64F)).apply { put(0,0,focal); put(1,1,focal); put(0,2,cx); put(1,2,cy) }
            val mask=m.keep(Mat()); val r=m.keep(Mat()); val translation=m.keep(Mat())
            val essential=m.keep(Calib3d.findEssentialMat(p0,p1,k,Calib3d.RANSAC,.999,1.2,1000,mask))
            require(!essential.empty() && essential.rows()==3) { "Gerakan kamera tidak dapat dipisahkan." }
            val count=Calib3d.recoverPose(essential,p0,p1,k,r,translation,mask)
            require(count>=25 && count>=matching.points.size*.35) { "Paralaks belum cukup. Gunakan kamera yang bergeser dan latar diam." }
            val projection0=m.keep(Mat.zeros(3,4,CvType.CV_64F))
            projection0.put(0,0,focal,0.0,cx,0.0, 0.0,focal,cy,0.0, 0.0,0.0,1.0,0.0)
            val extrinsic=m.keep(Mat.zeros(3,4,CvType.CV_64F))
            for(row in 0..2) { for(col in 0..2) extrinsic.put(row,col,r.get(row,col)[0]); extrinsic.put(row,3,translation.get(row,0)[0]) }
            val projection1=m.keep(Mat()); val empty=m.keep(Mat())
            Core.gemm(k,extrinsic,1.0,empty,0.0,projection1)
            val homogeneous=m.keep(Mat())
            Calib3d.triangulatePoints(projection0,projection1,p0,p1,homogeneous)
            val points0=p0.toArray(); val points1=p1.toArray()
            val landmarks=ArrayList<Point3>(); val pixels=ArrayList<Point>()
            val angles=ArrayList<Double>()
            val cameraCenter=DoubleArray(3) { i -> -(0..2).sumOf { j -> r.get(j,i)[0]*translation.get(j,0)[0] } }
            for(i in points0.indices) {
                if(mask.get(i,0)[0]==0.0) continue
                val w=homogeneous.get(3,i)[0]; if(abs(w)<1e-8) continue
                val point=Point3(homogeneous.get(0,i)[0]/w,homogeneous.get(1,i)[0]/w,homogeneous.get(2,i)[0]/w)
                if(!point.z.isFinite() || point.z<=0 || point.z>1000) continue
                val xyz=doubleArrayOf(point.x,point.y,point.z)
                val transformed=DoubleArray(3) { row -> (0..2).sumOf { col -> r.get(row,col)[0]*xyz[col] }+translation.get(row,0)[0] }
                if(transformed[2]<=0) continue
                val reprojection=hypot(focal*transformed[0]/transformed[2]+cx-points1[i].x,focal*transformed[1]/transformed[2]+cy-points1[i].y)
                if(reprojection>2.5) continue
                val ray=DoubleArray(3) { xyz[it]-cameraCenter[it] }
                val cosine=xyz.indices.sumOf { xyz[it]*ray[it] }/sqrt(xyz.sumOf { it*it }*ray.sumOf { it*it })
                val angle=acos(cosine.coerceIn(-1.0,1.0))*180/PI
                if(angle<.5) continue
                landmarks+=point; pixels+=points0[i]; angles+=angle
            }
            require(landmarks.size>=25 && angles.sorted()[angles.size/2]>.7) { "Kedalaman tidak stabil. Kamera diam, zoom, atau rotasi saja tidak cukup." }
            val scenePoints=landmarks.map { ScenePoint3D(it.x.toFloat(),it.y.toFloat(),it.z.toFloat()) }
            val depth=landmarks.map { it.z }.sorted()[landmarks.size/2].toFloat()
            val poses=ArrayList<CameraPose3D>()
            fun timelineTime(index:Int)=layer.start+(index-first)/f.fps
            poses+=CameraPose3D(timelineTime(first),listOf(1f,0f,0f,0f,1f,0f,0f,0f,1f),listOf(0f,0f,0f),landmarks.size)
            var previous=initial.clone(); var activePoints=pixels.toList(); var activeWorld=landmarks.toList()
            val rvec=m.keep(Mat.zeros(3,1,CvType.CV_64F)); val tvec=m.keep(Mat.zeros(3,1,CvType.CV_64F))
            val distortion=m.keep(MatOfDouble())
            try {
                for(index in first+1..last) {
                    currentCoroutineContext().ensureActive()
                    val next=gray(f,index)
                    try {
                        val tracked=flow(previous,next,activePoints)
                        if(tracked.points.size<12) break
                        val world=tracked.oldIndices.map { activeWorld[it] }
                        val result=Mats().use { frame ->
                            val objects=frame.keep(MatOfPoint3f()).apply { fromList(world) }
                            val image=frame.keep(MatOfPoint2f()).apply { fromList(tracked.points) }
                            val inliers=frame.keep(Mat())
                            val ok=Calib3d.solvePnPRansac(objects,image,k,distortion,rvec,tvec,true,120,2.5f,.995,inliers,Calib3d.SOLVEPNP_ITERATIVE)
                            if(!ok || inliers.rows()<12 || inliers.rows()<world.size*.45) null else {
                                val rotation=frame.keep(Mat()); Calib3d.Rodrigues(rvec,rotation)
                                val indices=(0 until inliers.rows()).map { inliers.get(it,0)[0].toInt() }
                                val pose=CameraPose3D(timelineTime(index),List(9){rotation.get(it/3,it%3)[0].toFloat()},List(3){tvec.get(it,0)[0].toFloat()},indices.size)
                                indices to pose
                            }
                        } ?: break
                        poses+=result.second
                        activePoints=result.first.map { tracked.points[it] }; activeWorld=result.first.map { world[it] }
                        previous.release(); previous=next.clone()
                        onProgress((index-first).toFloat()/(last-first))
                    } finally { next.release() }
                }
            } finally { previous.release() }
            require(poses.size>=3) { "Tracking hilang sebelum kamera dapat dihitung." }
            CameraSolve3D(poses,scenePoints,(focal/initial.rows()).toFloat(),depth,poses.last().time,f.aspect)
        }
    }
}
