package org.fungalsentinel.app.ui.result

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.fungalsentinel.app.R
import org.fungalsentinel.app.ui.components.HalfScreenLayout
import org.fungalsentinel.app.ui.components.NeonHeader
import org.fungalsentinel.app.ui.components.NeonOutlineBox
import org.fungalsentinel.app.ui.components.NeonYellowButton
import org.fungalsentinel.app.ui.components.TutorialHost
import org.fungalsentinel.app.ui.theme.AppColors
import java.util.Locale

@Composable
fun ResultScreen(
    slope: Double? = null,
    intercept: Double? = null,
    isLogFit: Boolean = false,
    fallbackFormula: String = "—",
    rSquared: Double = Double.NaN,
    predictedConcentration: Double? = null,
    isHalfScreen: Boolean = false,
    coverRatio: Float = 0.5f,
    onFinish: () -> Unit,
    onHome: () -> Unit
) {
    val tutorial = TutorialHost(
        stepId = "result",
        titleRes = R.string.tutorial_result_title,
        bodyRes = R.string.tutorial_result_body
    )
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val missingValue = stringResource(R.string.value_not_set)
    val formula = if (slope != null && intercept != null) {
        val formulaRes = if (isLogFit) R.string.result_formula_log else R.string.result_formula_linear
        stringResource(formulaRes, slope, intercept)
    } else {
        fallbackFormula.takeIf { it != "—" } ?: missingValue
    }
    val r2Text = if (rSquared.isFinite()) String.format(Locale.getDefault(), "%.4f", rSquared) else missingValue
    val predictedText = predictedConcentration
        ?.takeIf { it.isFinite() }
        ?.let { String.format(Locale.getDefault(), "%.3f", it) }
        ?: missingValue

    HalfScreenLayout(forceHalfScreen = isHalfScreen, coverRatio = coverRatio) { innerModifier ->
        Column(
            modifier = innerModifier
                .fillMaxSize()
                .background(AppColors.Bg)
                .verticalScroll(rememberScrollState())
                .padding(if (isLandscape) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 10.dp else 14.dp)
        ) {
            NeonHeader(
                stepLabel = null,
                title = stringResource(R.string.result_title),
                onBack = onHome,
                tutorial = tutorial
            )

            NeonOutlineBox(AppColors.Accent) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.result_regression),
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = formula,
                        color = AppColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    MetricRow(stringResource(R.string.result_r2), r2Text)
                    MetricRow(stringResource(R.string.result_predicted), predictedText)
                }
            }

            if (rSquared.isFinite() && rSquared < 0.95) {
                WarningPanel(stringResource(R.string.warn_low_r2))
            }
            if (predictedConcentration != null && predictedConcentration.isFinite() && predictedConcentration < 0.0) {
                WarningPanel(stringResource(R.string.warn_negative_pred))
            }

            NeonYellowButton(
                text = stringResource(R.string.result_finish),
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth()
            )
            NeonYellowButton(
                text = stringResource(R.string.result_back_home),
                onClick = onHome,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = AppColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            value,
            color = AppColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun WarningPanel(text: String) {
    NeonOutlineBox(AppColors.Error) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("!", color = AppColors.Error, fontWeight = FontWeight.Bold)
            Text(text, color = AppColors.Error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
