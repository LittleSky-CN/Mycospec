package org.fungalsentinel.app.ui.result

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fungalsentinel.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    regressionFormula: String = "y = 0.5x + 0.1",
    rSquared: Double = 0.98,
    predictedConcentration: Double? = 12.5,
    onFinish: () -> Unit,
    onHome: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = stringResource(R.string.result_title)) })
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
                    Text(
                        text = stringResource(R.string.result_regression),
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = regressionFormula)

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${stringResource(R.string.result_r2)}: ${String.format("%.4f", rSquared)}",
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${stringResource(R.string.result_predicted)}: ${predictedConcentration?.let { String.format("%.2f", it) } ?: "N/A"}",
                        fontWeight = FontWeight.Bold
                    )

                    if (rSquared < 0.95) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "⚠️ Low R² — curve may not be reliable",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (predictedConcentration != null && predictedConcentration < 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "⚠️ Negative prediction — check calibration",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onFinish,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(text = stringResource(R.string.result_finish))
            }

            OutlinedButton(
                onClick = onHome,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.result_back_home))
            }
        }
    }
}