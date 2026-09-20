package com.nse.optionbuyerscanner.service
import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nse.optionbuyerscanner.MainActivity
import com.nse.optionbuyerscanner.data.*
import com.nse.optionbuyerscanner.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class ScannerService:Service(){
 private val job=SupervisorJob();private val scope=CoroutineScope(job+Dispatchers.IO);private val angel=AngelOneClient();private val tg=TelegramClient()
 override fun onCreate(){super.onCreate();val id="scanner";getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(id,"F&O Scanner",NotificationManager.IMPORTANCE_LOW));val pi=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE);startForeground(101,NotificationCompat.Builder(this,id).setContentTitle("NSE Option Buyer Scanner").setContentText("Build 5 • Trend + SMI reversal").setSmallIcon(android.R.drawable.ic_menu_search).setContentIntent(pi).build());scope.launch{scan()}}
 private suspend fun scan(){val nm=getSystemService(NotificationManager::class.java);try{
  val s=SettingsStore(this).flow.first();status(nm,"Angel One login…");val session=angel.login(s);val stocks=angel.loadFnoStocks()
  tg.send(s.telegramToken,s.telegramChatId,"✅ Build 5 login OK\nUniverse: ${stocks.size} F&O stocks\nRunning RELIANCE diagnostic…")
  val rel=stocks.firstOrNull{it.name=="RELIANCE"}
  if(rel==null)tg.send(s.telegramToken,s.telegramChatId,"⚠️ RELIANCE not found in F&O universe")
  else try{
   val c=angel.candles(s,session,rel.token)
   if(c.isEmpty())tg.send(s.telegramToken,s.telegramChatId,"⚠️ RELIANCE candle request returned 0 candles")
   else{val closes=c.map{it.close};val last=c.last()
    val msg=if(c.size>=205){val e13=Indicators.ema(closes,13).last();val e48=Indicators.ema(closes,48).last();val e200=Indicators.ema(closes,200).last();val rsi=Indicators.rsi(closes);val smi=Indicators.smi(c);val atr=Indicators.atr(c);val vw=Indicators.vwap(c.takeLast(30));val sig=StrategyEngine.evaluate("RELIANCE",c)
     "🔬 RELIANCE diagnostic\nCandles: ${c.size}\nOldest: ${java.time.Instant.ofEpochMilli(c.first().time)}\nNewest: ${java.time.Instant.ofEpochMilli(c.last().time)}\nLatest: ${"%.2f".format(last.close)}\nEMA13/48/200: ${"%.2f".format(e13)} / ${"%.2f".format(e48)} / ${"%.2f".format(e200)}\nVWAP: ${"%.2f".format(vw)}\nRSI: ${"%.1f".format(rsi)} | SMI: ${"%.1f".format(smi)} | ATR: ${"%.2f".format(atr)}\nSignal: ${sig?.side ?: "NONE"}"
    }else "🔬 RELIANCE diagnostic\nCandles: ${c.size}\nOldest: ${java.time.Instant.ofEpochMilli(c.first().time)}\nNewest: ${java.time.Instant.ofEpochMilli(c.last().time)}\nLatest: ${"%.2f".format(last.close)}\nInsufficient history: strategy requires 205 candles."
    tg.send(s.telegramToken,s.telegramChatId,msg)}
  }catch(e:Exception){tg.send(s.telegramToken,s.telegramChatId,"❌ RELIANCE candle error\n${e.message?.take(300)}")}
  var attempted=0;var success=0;var failed=0;var valid=0;var call=0;var put=0;val errors=ArrayList<String>()
  for(x in stocks){if(!currentCoroutineContext().isActive)break;attempted++
   try{val c=angel.candles(s,session,x.token);success++;if(c.size>=205){valid++;val sig=StrategyEngine.evaluate(x.name,c);when(sig?.side){Side.CALL->{call++;sendSignal(s,sig)};Side.PUT->{put++;sendSignal(s,sig)};null->{}}}}
   catch(e:Exception){failed++;if(errors.size<10)errors.add("${x.name}: ${e.message?.take(100)}")}
   status(nm,"$attempted/${stocks.size} • OK $success • Fail $failed • Signals ${call+put}");delay(450)}
  tg.send(s.telegramToken,s.telegramChatId,"✅ Build 5 scan complete\nUniverse: ${stocks.size}\nAttempted: $attempted\nCandle success: $success\nCandle failures: $failed\nValid ≥205 candles: $valid\nCALL signals: $call\nPUT signals: $put")
  if(errors.isNotEmpty())tg.send(s.telegramToken,s.telegramChatId,"⚠️ First candle errors:\n"+errors.joinToString("\n"))
  status(nm,"Complete • OK $success • Fail $failed • Signals ${call+put}")
 }catch(e:Exception){status(nm,"Error: ${e.message?.take(80)}")}}
 private fun sendSignal(s:AppSettings,x:Signal){tg.send(s.telegramToken,s.telegramChatId,"🚨 ${x.side} • ${x.symbol}\nUnderlying ${"%.2f".format(x.entry)}\nSL ${"%.2f".format(x.stop)} | T1 ${"%.2f".format(x.target1)} | T2 ${"%.2f".format(x.target2)}\nScore ${x.score}/100\n"+x.reasons.joinToString(" • "))}
 private fun status(nm:NotificationManager,t:String){nm.notify(101,NotificationCompat.Builder(this,"scanner").setContentTitle("NSE Option Buyer Scanner").setContentText(t).setSmallIcon(android.R.drawable.ic_menu_search).setOngoing(true).build())}
 override fun onDestroy(){job.cancel();super.onDestroy()};override fun onBind(i:Intent?):IBinder?=null
}
