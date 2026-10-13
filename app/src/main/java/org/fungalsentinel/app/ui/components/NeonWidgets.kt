package org.fungalsentinel.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fungalsentinel.app.R

/** Acid-punk design-system widgets (design team mockups, 2026-10). */
/** Status strings are "TOKEN|localized message"; UI shows only the message. */
fun String.statusMessage(): String = substringAfter('|', this)
fun String.statusToken(): String = substringBefore('|')
private val NeonShadowGray = Color(0xFF8F8F8F)
private val NeonHeaderShadow = Color(0xFF7A0044)
private val NeonCream = Color(0xFFF5F5C0)
private val NeonIdleDot = Color(0xFF79917C)

private val Parallelogram = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            moveTo(size.width * 0.14f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width * 0.86f, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun NeonBackPill(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .height(44.dp)
            .width(64.dp)
            .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(22.dp))
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
    }
}

@Composable
fun NeonStepTab(label: String) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondary, Parallelogram)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/** Magenta title bar with hard offset shadow; optional help button at right end. */
@Composable
fun NeonHeader(
    stepLabel: String?,
    title: String,
    onBack: () -> Unit,
    tutorial: TutorialController? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NeonBackPill(onBack)
        if (stepLabel != null) NeonStepTab(stepLabel)
        Box(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .offset(x = 5.dp, y = 5.dp)
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(NeonHeaderShadow)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 44.dp)
                )
                if (tutorial != null) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = stringResource(R.string.tutorial_button_desc),
                        tint = NeonCream,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 10.dp)
                            .size(22.dp)
                            .clickable { tutorial.show() }
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

/** Black box with neon border: green = instructions, white = live status. */
@Composable
fun NeonOutlineBox(
    borderColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .border(2.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column(content = content)
    }
}

/** Yellow action button with hard gray offset shadow. */
@Composable
fun NeonYellowButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(NeonShadowGray, RoundedCornerShape(10.dp))
        )
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = Color.Black,
                disabledContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f),
                disabledContentColor = Color.Black.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = text,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Magenta confirm pill (capture page). */
@Composable
fun NeonConfirmButton(text: String, onClick: () -> Unit, enabled: Boolean) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth(0.62f)
            .height(58.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
    }
}

/** Green help circle (capture page top-right). */
@Composable
fun NeonHelpCircle(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.secondary, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Info,
            contentDescription = stringResource(R.string.tutorial_button_desc),
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Vertical numbered step dots (current = neon green). */
@Composable
fun NeonStepIndicator(current: Int, total: Int = 4) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        for (s in 1..total) {
            val active = s == current
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(
                        if (active) MaterialTheme.colorScheme.secondary else NeonIdleDot,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$s",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            if (s < total) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(16.dp)
                        .background(NeonIdleDot)
                )
            }
        }
    }
}

/** Responsive button group: vertical when the available width is tight. */
@Composable
fun NeonButtonGroup(vertical: Boolean, content: @Composable (Modifier) -> Unit) {
    if (vertical) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content(Modifier.fillMaxWidth()) }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { content(Modifier.weight(1f)) }
    }
}