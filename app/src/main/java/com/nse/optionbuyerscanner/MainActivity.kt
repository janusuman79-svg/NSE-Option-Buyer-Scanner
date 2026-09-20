package com.nse.optionbuyerscanner
import android.content.*
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nse.optionbuyerscanner.data.*
import com.nse.optionbuyerscanner.service.ScannerService
import kotlinx.coroutines.launch
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity:ComponentActivity(){ override fun onCreate(b:Bundle?){super.onCreate(b);setContent{MaterialTheme(colorScheme=darkColorScheme()){App()}}}
 @Composable fun App(){val store=remember{SettingsStore(this)}; val saved by store.flow.collectAsState(initial=AppSettings()); var s by remember(saved){mutableStateOf(saved)}; val scope=rememberCoroutineScope(); var running by remember{mutableStateOf(false)}
  Scaffold(topBar={TopAppBar(title={Text("NSE Option Buyer Scanner")})}){pad->Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Text("Build 10 • True completed candles",style=MaterialTheme.typography.titleMedium); Text("15m • EMA 13/48/200 • VWAP • RSI • SMI • ATR • Volume")
   Field("Angel One API key",s.angelApiKey){s=s.copy(angelApiKey=it)}; Field("Client code",s.clientCode){s=s.copy(clientCode=it)}; Field("PIN",s.pin,true){s=s.copy(pin=it)}; Field("TOTP secret",s.totpSecret,true){s=s.copy(totpSecret=it)}; Field("Telegram bot token",s.telegramToken,true){s=s.copy(telegramToken=it)}; Field("Telegram chat ID",s.telegramChatId){s=s.copy(telegramChatId=it)}
   Button(onClick={scope.launch{store.save(s)}}){Text("Save settings")}; Button(onClick={running=!running;if(running)startForegroundService(Intent(this@MainActivity,ScannerService::class.java))else stopService(Intent(this@MainActivity,ScannerService::class.java))}){Text(if(running)"Stop scanner" else "Start scanner")}
   HorizontalDivider(); Text("Risk guardrails",style=MaterialTheme.typography.titleMedium); Text("No auto-order placement in Build 10. Signals should be paper-tested and validated before live use. Stops are based on the underlying, not only option premium.")
  }}}
 @Composable
 fun Field(
     label: String,
     v: String,
     secret: Boolean = false,
     on: (String) -> Unit
 ) {
     OutlinedTextField(
         value = v,
         onValueChange = on,
         label = { Text(label) },
         visualTransformation = if (secret)
             PasswordVisualTransformation()
         else
             androidx.compose.ui.text.input.VisualTransformation.None,
         modifier = Modifier.fillMaxWidth()
     )
 }
}
