package com.nse.optionbuyerscanner.service
import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nse.optionbuyerscanner.MainActivity
import com.nse.optionbuyerscanner.data.*
import com.nse.optionbuyerscanner.domain.StrategyEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class ScannerService:Service(){
 private val job=SupervisorJob();private val scope=CoroutineScope(job+Dispatchers.IO);private val angel=AngelOneClient();private val tg=TelegramClient()
 override fun onCreate(){super.onCreate();val id="scanner";getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(id,"F&O Scanner",NotificationManager.IMPORTANCE_LOW));val pi=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE);startForeground(101,NotificationCompat.Builder(this,id).setContentTitle("NSE Option Buyer Scanner").setContentText("Build 2 • starting").setSmallIcon(android.R.drawable.ic_menu_search).setContentIntent(pi).build());scope.launch{scan()}}
 private suspend fun scan(){val nm=getSystemService(NotificationManager::class.java);try{val s=SettingsStore(this).flow.first();status(nm,"Angel One login…");val session=angel.login(s);tg.send(s.telegramToken,s.telegramChatId,"✅ Build 2: Angel One login successful\nSignal-only mode; no orders placed.");val stocks=angel.loadFnoStocks();tg.send(s.telegramToken,s.telegramChatId,"📡 NSE F&O stocks loaded: ${stocks.size}\n15-minute scan starting.");var done=0;var found=0
  for(x in stocks){if(!currentCoroutineContext().isActive)break;try{val sig=StrategyEngine.evaluate(x.name,angel.candles(s,session,x.token));if(sig!=null){found++;tg.send(s.telegramToken,s.telegramChatId,"🚨 ${sig.side} • ${sig.symbol}\nUnderlying ${"%.2f".format(sig.entry)}\nSL ${"%.2f".format(sig.stop)} | T1 ${"%.2f".format(sig.target1)} | T2 ${"%.2f".format(sig.target2)}\nScore ${sig.score}/100\n"+sig.reasons.joinToString(" • "))}}catch(_:Exception){};done++;status(nm,"$done/${stocks.size} scanned • $found signals");delay(450)}
  tg.send(s.telegramToken,s.telegramChatId,"✅ Scan complete: $done stocks, $found signals.");status(nm,"Scan complete • $done stocks • $found signals")
 }catch(e:Exception){status(nm,"Error: ${e.message?.take(80)}")}}
 private fun status(nm:NotificationManager,t:String){nm.notify(101,NotificationCompat.Builder(this,"scanner").setContentTitle("NSE Option Buyer Scanner").setContentText(t).setSmallIcon(android.R.drawable.ic_menu_search).setOngoing(true).build())}
 override fun onDestroy(){job.cancel();super.onDestroy()};override fun onBind(i:Intent?):IBinder?=null
}
