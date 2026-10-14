package com.example.myapplication

import com.example.myapplication.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class MotionCurvesTest {
    @Test fun flowPresetsMatchNormalizedBezierInBothDirections() {
        for(curve in FlowCurves) for(delta in listOf(3f,-3f)) {
            val track=curve.apply(Track(1f,listOf(Keyframe(2f,1f),Keyframe(4f,1f+delta))))
            for(i in 0..20) {
                val u=i/20f
                assertEquals(curve.name,1f+delta*bezierEase(curve.x1,curve.y1,curve.x2,curve.y2,u),track.valueAt(2f+u*2f,Easing.Linear),.002f)
            }
        }
    }

    @Test fun presetOnlyChangesChosenSegmentAndKeepsKeyValuesAndTimes() {
        val original=Track(0f,listOf(Keyframe(0f,0f),Keyframe(1f,2f),Keyframe(2f,1f)))
        val changed=FlowCurves.first { it.name=="Overshoot" }.apply(original,1)
        assertEquals(original.keys.map { it.time to it.value },changed.keys.map { it.time to it.value })
        assertEquals(original.keys[0],changed.keys[0])
        assertTrue(changed.keys[1].outSpeed<0f)
    }

    @Test fun speedRetainsNegativeMotionAndHoldHasNoVelocity() {
        val descending=Track(0f,listOf(Keyframe(0f,2f,Easing.Linear),Keyframe(2f,0f)))
        assertEquals(-1f,descending.velocityAt(1f,Easing.Linear),.001f)
        assertEquals(0f,descending.copy(keys=descending.keys.map { it.copy(easing=Easing.Hold) }).velocityAt(1f,Easing.Linear),0f)
    }

    @Test fun duplicateLoopKeysNeverProduceNaN() {
        val track=Track(0f,listOf(Keyframe(1f,3f),Keyframe(1f,4f)),loop=true)
        assertTrue(track.valueAt(5f,Easing.Linear).isFinite())
        assertEquals(0f,track.velocityAt(5f,Easing.Linear),0f)
    }

    private fun rotation(degrees:Float):List<Float> {
        val angle=Math.toRadians(degrees.toDouble()); val c=cos(angle).toFloat(); val s=sin(angle).toFloat()
        return listOf(c,-s,0f,s,c,0f,0f,0f,1f)
    }

    @Test fun rotationInterpolationIsOrthonormal() {
        for(i in 0..20) {
            val r=interpolateRotation(rotation(0f),rotation(120f),i/20f)
            for(row in 0..2) for(other in 0..2) {
                val dot=(0..2).sumOf { (r[row*3+it]*r[other*3+it]).toDouble() }.toFloat()
                assertEquals(if(row==other) 1f else 0f,dot,.0001f)
            }
        }
    }

    @Test fun rotationTakesShortArcAcross180Degrees() {
        val mid=interpolateRotation(rotation(179f),rotation(-179f),.5f)
        assertEquals(-1f,mid[0],.0001f)
        assertEquals(0f,mid[1],.0001f)
    }
}

