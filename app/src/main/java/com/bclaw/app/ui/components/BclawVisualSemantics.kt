package com.bclaw.app.ui.components

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

val CornerRadiusDpKey = SemanticsPropertyKey<Float>("BclawCornerRadiusDp")
var SemanticsPropertyReceiver.bclawCornerRadiusDp by CornerRadiusDpKey

val ElevationDpKey = SemanticsPropertyKey<Float>("BclawElevationDp")
var SemanticsPropertyReceiver.bclawElevationDp by ElevationDpKey

val FontFamilyTokenKey = SemanticsPropertyKey<String>("BclawFontFamilyToken")
var SemanticsPropertyReceiver.bclawFontFamilyToken by FontFamilyTokenKey
