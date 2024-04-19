package org.totschnig.myexpenses.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

inline fun Modifier.conditional(
    condition: Boolean,
    ifTrue: Modifier.() -> Modifier,
) =  if (condition) ifTrue() else this

inline fun <T> Modifier.optional(
    optional: T?,
    ifAbsent: (Modifier.() -> Modifier) = { this },
    ifPresent: Modifier.(T) -> Modifier
) = if (optional != null) ifPresent(optional) else ifAbsent()

fun Modifier.size(spSize: TextUnit) =
    composed { this.size(with(LocalDensity.current) { spSize.toDp() }) }

@Preview
@Composable
fun OptionalAbsentTest() {
    val value: Color? = null
    Box(
        Modifier
            .size(50.dp)
            .tagBorder(Color.Red)
            .optional(value, ifPresent = {
                background(it)
            })
    )
}

@Preview
@Composable
fun OptionalPresentTest() {
    val value: Color = Color.Green
    Box(
        Modifier
            .size(50.dp)
            .tagBorder(Color.Red)
            .optional(value, ifPresent = {
                background(it)
            })
    )
}

@Preview
@Composable
fun ConditionalTest() {
    Text(
        modifier = Modifier
            .padding(15.dp)
            .border(width = 2.dp, color = Color.Green)
            .conditional(true, ifTrue = {
                background(Color.Red)
            })
            .padding(16.dp),
        text = "Text"
    )
}