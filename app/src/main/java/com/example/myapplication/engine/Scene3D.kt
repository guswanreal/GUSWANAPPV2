package com.example.myapplication.engine

import android.opengl.Matrix as M4
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.tan

data class CameraPose3D(val time: Float, val rotation: List<Float>, val translation: List<Float>, val inliers: Int)
data class ScenePoint3D(val x: Float, val y: Float, val z: Float)
data class CameraSolve3D(
    val poses: List<CameraPose3D>, val points: List<ScenePoint3D>,
    val focalRatio: Float, val referenceDepth: Float, val solvedUntil: Float,
    val sourceAspect: Float = 16f/9f,
) {
    fun at(t: Float): CameraPose3D? {
        if (poses.isEmpty()) return null
        if (t <= poses.first().time) return poses.first()
        if (t >= poses.last().time) return poses.last()
        var lo = 0; var hi = poses.lastIndex
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (poses[mid].time <= t) lo = mid else hi = mid
        }
        val a = poses[lo]; val b = poses[hi]
        val u = ((t - a.time) / (b.time - a.time)).coerceIn(0f, 1f)
        return CameraPose3D(t, interpolateRotation(a.rotation, b.rotation, u),
            List(3) { a.translation[it] + (b.translation[it] - a.translation[it]) * u }, minOf(a.inliers, b.inliers))
    }
    fun toJson() = JSONObject().put("focal",focalRatio).put("depth",referenceDepth).put("until",solvedUntil).put("aspect",sourceAspect)
        .put("poses",JSONArray(poses.map { p -> JSONObject().put("time",p.time).put("r",JSONArray(p.rotation)).put("t",JSONArray(p.translation)).put("inliers",p.inliers) }))
        .put("points",JSONArray(points.map { JSONArray(listOf(it.x,it.y,it.z)) }))
    companion object {
        fun fromJson(o: JSONObject): CameraSolve3D {
            val poses=o.getJSONArray("poses"); val points=o.getJSONArray("points")
            return CameraSolve3D((0 until poses.length()).map { i ->
                val p=poses.getJSONObject(i)
                CameraPose3D(p.getDouble("time").toFloat(),List(9){p.getJSONArray("r").getDouble(it).toFloat()},List(3){p.getJSONArray("t").getDouble(it).toFloat()},p.getInt("inliers"))
            }, (0 until points.length()).map { i -> points.getJSONArray(i).let { ScenePoint3D(it.getDouble(0).toFloat(),it.getDouble(1).toFloat(),it.getDouble(2).toFloat()) } },
                o.getDouble("focal").toFloat(),o.getDouble("depth").toFloat(),o.getDouble("until").toFloat(),o.optDouble("aspect",16.0/9).toFloat())
        }
    }
}

/** Flat image/text/shape planes in 3D, projected using a camera into a Canvas homography. */
class Scene3D(private val layers: List<Layer>, private val time: Float, private val size: Size) {
    private val byId=layers.associateBy { it.id }
    private val worlds=HashMap<Int,FloatArray>()
    private val bases=HashMap<Int,FloatArray>()
    private fun identity()=FloatArray(16).also { M4.setIdentityM(it,0) }
    private fun multiply(a: FloatArray,b: FloatArray)=FloatArray(16).also { M4.multiplyMM(it,0,a,0,b,0) }
    private fun world(layer: Layer): FloatArray {
        worlds[layer.id]?.let { return it }
        val chain=ArrayList<Layer>(); val seen=HashSet<Int>(); var cursor: Layer?=layer
        while(cursor!=null && !worlds.containsKey(cursor.id) && seen.add(cursor.id)) { chain+=cursor; cursor=cursor.parentId?.let(byId::get) }
        var parent=cursor?.let { worlds[it.id] } ?: identity()
        for(node in chain.asReversed()) {
            val o=node.parentOffset; val aspect=size.width/size.height
            val offset=identity().apply {
                this[0]=o.a; this[1]=o.b/aspect; this[4]=o.c*aspect; this[5]=o.d
                this[12]=((o.a-1+o.c)*.5f+o.x)*aspect
                this[13]=(o.b+o.d-1)*.5f+o.y
            }
            val p=layerPosition(node,time,size); val a=anchorOffset(node,time,size)
            val local=identity()
            M4.translateM(local,0,(p.x-size.width/2)/size.height,(p.y-size.height/2)/size.height,if(node.is3D) node.value(Prop.PosZ,time) else 0f)
            M4.rotateM(local,0,planarRotation(node,time),0f,0f,1f)
            if(node.is3D) {
                M4.rotateM(local,0,node.value(Prop.OrbitY,time),0f,1f,0f)
                M4.rotateM(local,0,node.value(Prop.OrbitX,time),1f,0f,0f)
            }
            val scale=layerScale(node,time); M4.scaleM(local,0,scale,scale,scale)
            M4.translateM(local,0,-a.x/size.height,-a.y/size.height,0f)
            val basis=multiply(multiply(parent,offset), if(node.spatialOffset.size==16) node.spatialOffset.toFloatArray() else identity())
            bases[node.id]=basis
            parent=multiply(basis,local); worlds[node.id]=parent
        }
        return worlds.getValue(layer.id)
    }

    fun reparent(child: Layer, newParent: Layer?): Layer {
        world(child)
        val oldBasis=bases.getValue(child.id)
        val newWorld=newParent?.let(::world) ?: identity()
        val inverse=FloatArray(16)
        require(M4.invertM(inverse,0,newWorld,0)) { "Parent memiliki Scale 0." }
        return child.copy(parentId=newParent?.id, parentOffset=ParentOffset(), spatialOffset=multiply(inverse,oldBasis).toList(), is3D=true)
    }

    private val camera=layers.lastOrNull { it.type==LayerType.Camera && it.activeAt(time) }
    private val solvedPose=camera?.cameraSolve?.at(time)
    private val inverseRig: FloatArray? by lazy {
        camera?.let { cam ->
            world(cam)
            FloatArray(16).takeIf { M4.invertM(it, 0, bases.getValue(cam.id), 0) }
        }
    }
    fun cameraRigMatrix(): FloatArray {
        val cam=camera ?: return identity()
        world(cam)
        return bases.getValue(cam.id).copyOf()
    }
    private data class CameraPoint(val x:Float,val y:Float,val z:Float,val focalPixels:Float)
    private fun cameraPoint(v: FloatArray, ignoreCamera: Boolean): CameraPoint? {
        val cam=camera.takeUnless { ignoreCamera }
        val input = if (cam != null) {
            val inverse = inverseRig ?: return null
            FloatArray(4).also { M4.multiplyMV(it, 0, inverse, 0, v, 0) }
        } else v
        val solve=cam?.cameraSolve
        if(solve!=null) {
            val controls=cam ?: return null
            val pose=solvedPose ?: return null
            val fitHeight=minOf(size.height,size.width/solve.sourceAspect)
            val k=solve.referenceDepth/solve.focalRatio*size.height/fitHeight
            val x=input[0]*k; val y=input[1]*k; val z=solve.referenceDepth+input[2]*k
            val r=pose.rotation; val tr=pose.translation
            val px=r[0]*x+r[1]*y+r[2]*z+tr[0]
            val py=r[3]*x+r[4]*y+r[5]*z+tr[1]
            val pz=r[6]*x+r[7]*y+r[8]*z+tr[2]
            val manual=identity()
            M4.rotateM(manual,0,-controls.value(Prop.Rotation,time),0f,0f,1f)
            M4.rotateM(manual,0,-controls.value(Prop.OrbitX,time),1f,0f,0f)
            M4.rotateM(manual,0,-controls.value(Prop.OrbitY,time),0f,1f,0f)
            M4.translateM(manual,0,-controls.value(Prop.PosX,time)*size.width/size.height*.5f*k,
                -controls.value(Prop.PosY,time)*.5f*k,-controls.value(Prop.PosZ,time)*k)
            val out=FloatArray(4)
            M4.multiplyMV(out,0,manual,0,floatArrayOf(px,py,pz,1f),0)
            return CameraPoint(out[0],out[1],out[2],fitHeight*solve.focalRatio*controls.value(Prop.Scale,time).coerceAtLeast(.05f))
        }
        val focal=(.5/tan(PI/6)).toFloat()
        val view=identity()
        val target=cam?.targetId?.takeIf { it != cam.id }?.let(byId::get)?.let { world(it) }
        val x=(cam?.value(Prop.PosX,time) ?: 0f)*size.width/size.height/2+(target?.get(12) ?: 0f)
        val y=(cam?.value(Prop.PosY,time) ?: 0f)/2+(target?.get(13) ?: 0f)
        M4.translateM(view,0,0f,0f,focal/(cam?.value(Prop.Scale,time) ?: 1f).coerceAtLeast(.05f))
        M4.rotateM(view,0,-(cam?.value(Prop.Rotation,time) ?: 0f),0f,0f,1f)
        M4.rotateM(view,0,-(cam?.value(Prop.OrbitX,time) ?: 0f),1f,0f,0f)
        M4.rotateM(view,0,-(cam?.value(Prop.OrbitY,time) ?: 0f),0f,1f,0f)
        M4.translateM(view,0,-x,-y,-(cam?.value(Prop.PosZ,time) ?: 0f))
        val out=FloatArray(4); M4.multiplyMV(out,0,view,0,input,0)
        return CameraPoint(out[0],out[1],out[2],size.height*focal)
    }

    private fun project(v:FloatArray,ignoreCamera:Boolean):Offset? {
        val p=cameraPoint(v,ignoreCamera) ?: return null
        if(p.z<=.001f) return null
        return Offset(size.width/2+p.focalPixels*p.x/p.z,size.height/2+p.focalPixels*p.y/p.z)
    }

    /** Back-to-front planes; an ordinary 2D layer is a compositing boundary. */
    fun drawOrder():List<Layer> {
        val result=ArrayList<Layer>(); val group=ArrayList<Layer>()
        fun flush() {
            result+=group.sortedByDescending { layer ->
                val w=world(layer)
                cameraPoint(floatArrayOf(w[12],w[13],w[14],1f),layer.cameraIgnore)?.z ?: Float.NEGATIVE_INFINITY
            }
            group.clear()
        }
        for(layer in layers) {
            if(layer.type==LayerType.Camera || layer.type==LayerType.Null) continue
            if(layer.is3D && layer.supports3D()) group+=layer else { flush(); result+=layer }
        }
        flush()
        return result
    }

    fun screenPoint(layer: Layer, point: Offset): Offset? {
        val v=floatArrayOf(point.x/size.height,point.y/size.height,0f,1f)
        val out=FloatArray(4); M4.multiplyMV(out,0,world(layer),0,v,0)
        return project(out,layer.cameraIgnore)
    }

    fun cameraPlaneMatrix(depth: Float): Matrix? {
        val src = floatArrayOf(0f, 0f, size.width, 0f, size.width, size.height, 0f, size.height)
        val dst = FloatArray(8)
        for (i in 0..3) {
            val p = project(floatArrayOf((src[i*2]-size.width/2)/size.height,
                (src[i*2+1]-size.height/2)/size.height, depth, 1f), false) ?: return null
            dst[i*2] = p.x; dst[i*2+1] = p.y
        }
        val native = android.graphics.Matrix()
        if (!native.setPolyToPoly(src, 0, dst, 0, 4)) return null
        val v = FloatArray(9); native.getValues(v)
        return Matrix().apply {
            values[0]=v[0]; values[1]=v[3]; values[3]=v[6]
            values[4]=v[1]; values[5]=v[4]; values[7]=v[7]
            values[12]=v[2]; values[13]=v[5]; values[15]=v[8]
        }
    }

    fun matrix(layer: Layer): Matrix? {
        // Homography is exact for a plane; no per-pixel mesh tessellation is needed.
        val u=size.minDimension*.5f
        val src=floatArrayOf(-u,-u,u,-u,u,u,-u,u)
        val dst=FloatArray(8)
        for(i in 0..3) { val p=screenPoint(layer,Offset(src[2*i],src[2*i+1])) ?: return null; dst[2*i]=p.x; dst[2*i+1]=p.y }
        val native=android.graphics.Matrix()
        if(!native.setPolyToPoly(src,0,dst,0,4)) return null
        val v=FloatArray(9); native.getValues(v)
        return Matrix().apply {
            values[0]=v[0]; values[1]=v[3]; values[3]=v[6]
            values[4]=v[1]; values[5]=v[4]; values[7]=v[7]
            values[12]=v[2]; values[13]=v[5]; values[15]=v[8]
        }
    }
}

fun Layer.supports3D() = type in setOf(LayerType.Text,LayerType.Shape,LayerType.Image,LayerType.Video,LayerType.Null,LayerType.Camera)
fun Layer.transformProperties(): List<Prop> = if(type == LayerType.Camera) type.transformProps() else if(is3D && supports3D())
    listOf(Prop.AnchorX,Prop.AnchorY,Prop.PosX,Prop.PosY,Prop.PosZ,Prop.Scale,Prop.Rotation,Prop.OrbitX,Prop.OrbitY)
        .let { if(type==LayerType.Null) it else it+Prop.Opacity }
    else type.transformProps()

