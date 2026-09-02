package dev.corebuilds.doctor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.corebuilds.doctor.diagnostics.CheckResult
import dev.corebuilds.doctor.diagnostics.DoctorEngine
import dev.corebuilds.doctor.diagnostics.DoctorInput
import dev.corebuilds.doctor.diagnostics.DoctorReport
import dev.corebuilds.doctor.diagnostics.ReportCard
import dev.corebuilds.doctor.diagnostics.Verdict
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DoctorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BrandColors.Background
                ) {
                    DoctorScreen()
                }
            }
        }
    }
}

private object BrandColors {
    val Background = Color(0xFF0D1117)
    val Surface = Color(0xFF161B22)
    val Border = Color(0xFF30363D)
    val TextPrimary = Color(0xFFE6EDF3)
    val TextSecondary = Color(0xFF8B949E)
    val Accent = Color(0xFF58A6FF)
    val Pass = Color(0xFF3FB950)
    val Warn = Color(0xFFD29922)
    val Fail = Color(0xFFF85149)
}

@Composable
private fun DoctorTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

@Composable
private fun DoctorScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var addonUrl by remember { mutableStateOf("") }
    var rdKey by remember { mutableStateOf("") }
    var torboxKey by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    val results = remember { mutableStateListOf<CheckResult>() }
    var finished by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "Core Doctor",
            color = BrandColors.TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif
        )
        Text(
            text = "Streaming diagnostics",
            color = BrandColors.TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(24.dp))

        DoctorTextField(
            value = addonUrl,
            onValueChange = { addonUrl = it },
            label = "Addon URL",
            placeholder = "https://...",
            masked = false
        )
        DoctorTextField(
            value = rdKey,
            onValueChange = { rdKey = it },
            label = "Real-Debrid API key",
            placeholder = "Paste key",
            masked = true
        )
        DoctorTextField(
            value = torboxKey,
            onValueChange = { torboxKey = it },
            label = "TorBox API key",
            placeholder = "Paste key",
            masked = true
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                results.clear()
                finished = false
                running = true
                val input = DoctorInput(
                    addonUrl = addonUrl.trim(),
                    rdKey = rdKey.trim(),
                    torboxKey = torboxKey.trim()
                )
                scope.launch {
                    DoctorEngine.run(context, input).collect { result ->
                        results.add(result)
                    }
                    running = false
                    finished = true
                }
            },
            enabled = !running,
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandColors.Accent,
                disabledContainerColor = BrandColors.Border
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (running) {
                CircularProgressIndicator(
                    color = BrandColors.TextPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = if (running) "Running..." else "Run checks",
                color = BrandColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(20.dp))

        for (result in results) {
            ResultCard(result)
            Spacer(Modifier.height(8.dp))
        }

        if (finished && results.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            val counts = results.groupingBy { it.verdict }.eachCount()
            Text(
                text = "${results.size} checks: " +
                    "${counts[Verdict.PASS] ?: 0} passed, " +
                    "${counts[Verdict.WARN] ?: 0} warnings, " +
                    "${counts[Verdict.FAIL] ?: 0} failed",
                color = BrandColors.TextSecondary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            TextButton(
                onClick = {
                    val report = DoctorReport(checks = results.toList())
                    val text = ReportCard.render(report)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(
                        Intent.createChooser(intent, "Share report")
                    )
                }
            ) {
                Text(
                    text = "Share report",
                    color = BrandColors.Accent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun DoctorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    masked: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = BrandColors.TextSecondary) },
        placeholder = { Text(placeholder, color = BrandColors.Border) },
        visualTransformation = if (masked) PasswordVisualTransformation()
        else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = if (masked) KeyboardOptions(keyboardType = KeyboardType.Password)
        else KeyboardOptions.Default,
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = BrandColors.TextPrimary,
            unfocusedTextColor = BrandColors.TextPrimary,
            focusedBorderColor = BrandColors.Accent,
            unfocusedBorderColor = BrandColors.Border,
            cursorColor = BrandColors.Accent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}

@Composable
private fun ResultCard(result: CheckResult) {
    val verdictColor = when (result.verdict) {
        Verdict.PASS -> BrandColors.Pass
        Verdict.WARN -> BrandColors.Warn
        Verdict.FAIL -> BrandColors.Fail
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = BrandColors.Surface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(verdictColor)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = result.name,
                        color = BrandColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = result.verdict.name,
                        color = verdictColor,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = result.summary,
                    color = BrandColors.TextSecondary,
                    fontSize = 13.sp
                )
                if (result.fix != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = result.fix,
                        color = BrandColors.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
