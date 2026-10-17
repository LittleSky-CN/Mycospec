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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fungalsentinel.app.R
import org.fungalsentinel.app.ui.theme.AppColors

private val Parallelogram = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            moveTo(size.width * 0.14f, 0f); lineTo(size.width, 0f)
            lineTo(size.width * 0.86f, size.height); lineTo(0f, size.height); close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun NeonBackPill(onBack: () -> Unit) {
    Box(
        modifier = Modifier.height(44.dp).width(64.dp)
            .background(AppColors.Accent, RoundedCornerShape(22.dp))
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = AppColors.OnAccent)
    }
}

@Composable
fun NeonStepTab(label: String) {
    Box(modifier = Modifier.background(AppColors.Accent, Parallelogram).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(label, color = AppColors.OnAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun NeonHeader(stepLabel: String?, title: String, onBack: () -> Unit, tutorial: TutorialController? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NeonBackPill(onBack)
        if (stepLabel != null) NeonStepTab(stepLabel)
        Box(modifier = Modifier.weight(1f)) {
            // 投影改为中性抬升面，不再使用深品红
            Box(modifier = Modifier.offset(x = 4.dp, y = 4.dp).fillMaxWidth().height(50.dp)
                .background(AppColors.SurfaceRaised, RoundedCornerShape(4.dp)))
            Box(
                modifier = Modifier.fillMaxWidth().height(50.dp)
                    .background(AppColors.Surface, RoundedCornerShape(4.dp))
                    .border(1.dp, AppColors.Outline, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 44.dp)
                )
                if (tutorial != null) {
                    TutorialButton(
                        onClick = tutorial::show,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp)
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
fun NeonOutlineBox(borderColor: Color, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(AppColors.Surface, RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) { Column(content = content) }
}

@Composable
fun NeonYellowButton(text: String, onClick: () -> Unit, enabled: Boolean = true, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Box(modifier = Modifier.matchParentSize().offset(x = 3.dp, y = 3.dp)
            .background(AppColors.SurfaceRaised, RoundedCornerShape(10.dp)))
        Button(
            onClick = onClick, enabled = enabled, shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.Accent, contentColor = AppColors.OnAccent,
                disabledContainerColor = AppColors.SurfaceRaised, disabledContentColor = AppColors.TextSecondary
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (icon != null) { Icon(icon, contentDescription = null); Spacer(Modifier.width(6.dp)) }
                Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 15.sp, maxLines = 2, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun NeonConfirmButton(text: String, onClick: () -> Unit, enabled: Boolean) {
    Button(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.Accent, contentColor = AppColors.OnAccent,
            disabledContainerColor = AppColors.SurfaceRaised, disabledContentColor = AppColors.TextSecondary
        ),
        modifier = Modifier.fillMaxWidth(0.62f).height(58.dp)
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
}

@Composable
fun NeonHelpCircle(onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(40.dp).background(AppColors.Accent, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.tutorial_button_desc),
            tint = AppColors.OnAccent, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun NeonStepIndicator(current: Int, total: Int = 4) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        for (s in 1..total) {
            val active = s == current
            Box(
                modifier = Modifier.size(30.dp).background(if (active) AppColors.Accent else AppColors.SurfaceRaised, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("$s", color = if (active) AppColors.OnAccent else AppColors.TextSecondary,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            if (s < total) Box(modifier = Modifier.width(2.dp).height(16.dp).background(AppColors.Outline))
        }
    }
}

@Composable
fun NeonButtonGroup(vertical: Boolean, content: @Composable (Modifier) -> Unit) {
    if (vertical) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content(Modifier.fillMaxWidth()) }
    else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { content(Modifier.weight(1f)) }
}