package org.fungalsentinel.app.ui.result

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fungalsentinel.app.R
import org.fungalsentinel.app.ui.components.HelpTopBar
import org.fungalsentinel.app.ui.components.TutorialHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    regressionFormula: String = "—",
    rSquared: Double = Double.NaN,
    predictedConcentration: Double? = null,
    onFinish: () -> Unit,
    onHome: () -> Unit
) {
    val tutorial = TutorialHost(
        stepId = "result",
        titleRes = R.string.tutorial_result_title,
        bodyRes = R.string.tutorial_result_body
    )

    Scaffold(
        topBar = {
            HelpTopBar(
                title = stringResource(R.string.result_title),
                tutorial = tutorial,
                onBack = onHome
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.result_regression), fontWeight = FontWeight.Bold)
                    Text(regressionFormula)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${stringResource(R.string.result_r2)}: " +
                                (if (rSquared.isNaN()) "—" else String.format("%.4f", rSquared)),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${stringResource(R.string.result_predicted)}: " +
                                (predictedConcentration?.let { String.format("%.2f", it) } ?: "—"),
                        fontWeight = FontWeight.Bold
                    )
                    if (!rSquared.isNaN() && rSquared < 0.95) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "⚠️ Low R² — curve may not be reliable",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (predictedConcentration != null && predictedConcentration < 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "⚠️ Negative prediction — check calibration",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(stringResource(R.string.result_finish))
            }
            OutlinedButton(
                onClick = onHome,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.result_back_home))
            }
        }
    }
}