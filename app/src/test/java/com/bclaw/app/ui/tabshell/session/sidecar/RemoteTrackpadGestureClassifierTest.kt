package com.bclaw.app.ui.tabshell.session.sidecar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteTrackpadGestureClassifierTest {
    @Test
    fun scrollLocksWhenPanDominatesPinchDrift() {
        val gesture = classifyRemoteTrackpadTwoFingerGesture(
            panDistance = 100f,
            spanDelta = 20f,
            cumulativeZoom = 1.1f,
            scrollSlopPx = 8f,
            pinchSlopPx = 18f,
        )

        assertEquals(RemoteTrackpadTwoFingerGesture.Scroll, gesture)
    }

    @Test
    fun zoomLocksWhenSpanChangeDominatesPan() {
        val gesture = classifyRemoteTrackpadTwoFingerGesture(
            panDistance = 8f,
            spanDelta = 28f,
            cumulativeZoom = 1.12f,
            scrollSlopPx = 8f,
            pinchSlopPx = 18f,
        )

        assertEquals(RemoteTrackpadTwoFingerGesture.Zoom, gesture)
    }

    @Test
    fun ambiguousEarlyMotionStaysPending() {
        val gesture = classifyRemoteTrackpadTwoFingerGesture(
            panDistance = 5f,
            spanDelta = 6f,
            cumulativeZoom = 1.03f,
            scrollSlopPx = 8f,
            pinchSlopPx = 18f,
        )

        assertNull(gesture)
    }

    @Test
    fun tinyEarlyMotionStaysPending() {
        val gesture = classifyRemoteTrackpadTwoFingerGesture(
            panDistance = 3f,
            spanDelta = 4f,
            cumulativeZoom = 1.03f,
            scrollSlopPx = 8f,
            pinchSlopPx = 18f,
        )

        assertNull(gesture)
    }

    @Test
    fun scrollMomentumStartsOnlyForFastScrolls() {
        val slow = startRemoteScrollMomentumVelocity(Offset(x = 0f, y = 180f))
        val fast = startRemoteScrollMomentumVelocity(Offset(x = 0f, y = 600f))

        assertEquals(0f, slow.getDistance(), 0.0001f)
        assertTrue(fast.y > 0f)
        assertTrue(fast.y < 600f)
    }

    @Test
    fun remoteScrollYMatchesMacTrackpadDirection() {
        val delta = remotePanToScrollDelta(Offset(x = 0f, y = 10f))

        assertEquals(30f, delta.y, 0.0001f)
    }

    @Test
    fun remoteScrollRemainderKeepsSubUnitMotion() {
        val first = unsentRemoteScrollRemainder(
            remainder = Offset.Zero,
            delta = Offset(x = 0f, y = 0.4f),
        )
        val second = unsentRemoteScrollRemainder(
            remainder = first,
            delta = Offset(x = 0f, y = 0.4f),
        )

        assertEquals(0.4f, first.y, 0.0001f)
        assertEquals(0.8f, second.y, 0.0001f)
    }

    @Test
    fun remoteZoomTouchPositionDampensSpanChange() {
        val damped = dampRemoteZoomTouchPosition(
            startPosition = Offset(x = 90f, y = 100f),
            currentPosition = Offset(x = 70f, y = 100f),
            startCentroid = Offset(x = 100f, y = 100f),
            currentCentroid = Offset(x = 100f, y = 100f),
            gain = 0.4f,
        )

        assertEquals(82f, damped.x, 0.0001f)
        assertEquals(100f, damped.y, 0.0001f)
    }

    @Test
    fun scrollMomentumVelocityDecays() {
        val decayed = decayRemoteScrollMomentumVelocity(
            velocity = Offset(x = 0f, y = 1_000f),
            elapsedMs = 16L,
        )

        assertTrue(decayed.y > 0f)
        assertTrue(decayed.y < 1_000f)
    }

    @Test
    fun remoteViewportClampMatchesFullViewportWhenImeIsInactive() {
        val clamped = clampRemoteViewportOffset(
            offset = Offset(x = 1_000f, y = 1_000f),
            scale = 2f,
            renderSize = IntSize(width = 300, height = 200),
            viewportSize = IntSize(width = 100, height = 100),
        )

        assertEquals(250f, clamped.x, 0.0001f)
        assertEquals(150f, clamped.y, 0.0001f)
    }

    @Test
    fun remoteViewportClampDoesNotPanUnzoomedCanvasThatFitsVisibleArea() {
        val clamped = clampRemoteViewportOffset(
            offset = Offset(x = 0f, y = 1_000f),
            scale = 1f,
            renderSize = IntSize(width = 800, height = 400),
            viewportSize = IntSize(width = 400, height = 500),
        )

        assertEquals(0f, clamped.y, 0.0001f)
    }

    @Test
    fun remoteViewportClampUsesCurrentVisibleViewportHeight() {
        val clamped = clampRemoteViewportOffset(
            offset = Offset(x = 0f, y = 1_000f),
            scale = 3f,
            renderSize = IntSize(width = 400, height = 400),
            viewportSize = IntSize(width = 400, height = 500),
        )

        assertEquals(350f, clamped.y, 0.0001f)
    }

    @Test
    fun remoteViewportClampTightensAgainWhenVisibleViewportGrows() {
        val clamped = clampRemoteViewportOffset(
            offset = Offset(x = 0f, y = 1_000f),
            scale = 3f,
            renderSize = IntSize(width = 400, height = 400),
            viewportSize = IntSize(width = 400, height = 800),
        )

        assertEquals(200f, clamped.y, 0.0001f)
    }
}
