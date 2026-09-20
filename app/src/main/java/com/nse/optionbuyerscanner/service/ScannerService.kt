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
 override fun onCreate(){super.onCreate();val id="scanner";getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(id,"F&O Scanner",NotificationManager.IMPORTANCE_LOW));val pi=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE);startForeground(101,NotificationCompat.Builder(this,id).setContentTitle("NSE Option Buyer Scanner").setContentText("Build 12 • ATM option selection").setSmallIcon(android.R.drawable.ic_menu_search).setContentIntent(pi).build());scope.launch{scan()}}
 private suspend fun scan(){val nm=getSystemService(NotificationManager::class.java);try{
  val s=SettingsStore(this).flow.first();status(nm,"Angel One login…");val session=angel.login(s);val stocks=angel.loadFnoStocks();val options=angel.loadStockOptions()
  tg.send(s.telegramToken,s.telegramChatId,"✅ Build 12 login OK\nUniverse: ${stocks.size} F&O stocks\nRunning RELIANCE diagnostic…")
  val rel=stocks.firstOrNull{it.name=="RELIANCE"}
  if(rel==null)tg.send(s.telegramToken,s.telegramChatId,"⚠️ RELIANCE not found in F&O universe")
  else try{
   val c=angel.candles(s,session,rel.token)
   if(c.isEmpty())tg.send(s.telegramToken,s.telegramChatId,"⚠️ RELIANCE candle request returned 0 candles")
   else{val cc=Indicators.completed(c);val closes=cc.map{it.close};val last=cc.last()
    val last3=cc.takeLast(3).joinToString("\n"){z->"${java.time.Instant.ofEpochMilli(z.time)} O=${"%.2f".format(z.open)} H=${"%.2f".format(z.high)} L=${"%.2f".format(z.low)} C=${"%.2f".format(z.close)} V=${"%.0f".format(z.volume)}"}
    val av=cc.dropLast(1).takeLast(20).map{it.volume}.average()
    val vr=if(av>0)last.volume/av else 0.0
    tg.send(s.telegramToken,s.telegramChatId,"🧪 Last 3 COMPLETED candles\n$last3\n20-candle avg vol: ${"%.0f".format(av)}\nVolume ratio: ${"%.2f".format(vr)}x")
    val msg=if(cc.size>=205){val e13=Indicators.ema(closes,13).last();val e48=Indicators.ema(closes,48).last();val e200=Indicators.ema(closes,200).last();val rsi=Indicators.rsi(closes);val smi=Indicators.smi(cc);val atr=Indicators.atr(cc);val vw=Indicators.vwap(cc.takeLast(30));val sig=StrategyEngine.evaluate("RELIANCE",c)
     "🔬 RELIANCE diagnostic\nRaw candles: ${c.size}\nUsable completed: ${cc.size}\nOldest: ${java.time.Instant.ofEpochMilli(c.first().time)}\nNewest: ${java.time.Instant.ofEpochMilli(c.last().time)}\nLatest: ${"%.2f".format(last.close)}\nEMA13/48/200: ${"%.2f".format(e13)} / ${"%.2f".format(e48)} / ${"%.2f".format(e200)}\nVWAP: ${"%.2f".format(vw)}\nRSI: ${"%.1f".format(rsi)} | SMI: ${"%.1f".format(smi)} | ATR: ${"%.2f".format(atr)}\nSignal: ${sig?.side ?: "NONE"}"
    }else "🔬 RELIANCE diagnostic\nRaw candles: ${c.size}\nUsable completed: ${cc.size}\nOldest: ${java.time.Instant.ofEpochMilli(c.first().time)}\nNewest: ${java.time.Instant.ofEpochMilli(c.last().time)}\nLatest: ${"%.2f".format(last.close)}\nInsufficient history: strategy requires 205 candles."
    tg.send(s.telegramToken,s.telegramChatId,msg)}
  }catch(e:Exception){tg.send(s.telegramToken,s.telegramChatId,"❌ RELIANCE candle error\n${e.message?.take(300)}")}
  var attempted=0;var success=0;var failed=0;var valid=0;var call=0;var put=0;val candidates=ArrayList<Signal>();var smiLow75=0;var smiLow85=0;var smiHigh75=0;var smiHigh85=0;var lowRsi=0;var highRsi=0;var bullConfirm=0;var bearConfirm=0;var trendCallReady=0;var trendPutReady=0;var volumeReady=0;val oversold=ArrayList<Pair<String,Double>>();val overbought=ArrayList<Pair<String,Double>>();val errors=ArrayList<String>()
  for(x in stocks){if(!currentCoroutineContext().isActive)break;attempted++
   try{val c=angel.candles(s,session,x.token);success++;if(c.size>=205){valid++;val cc=Indicators.completed(c);if(cc.size<205)continue;val closes=cc.map{it.close};val last=cc.last();val prev=cc[cc.lastIndex-1];val e13=Indicators.ema(closes,13).last();val e48=Indicators.ema(closes,48).last();val e200=Indicators.ema(closes,200).last();val rsi=Indicators.rsi(closes);val smi=Indicators.smi(cc);val vw=Indicators.vwap(cc.takeLast(30));val avgVol=cc.dropLast(1).takeLast(20).map{it.volume}.average();val volOk=avgVol>0&&last.volume>=avgVol*1.10;if(smi<=-75){smiLow75++;oversold.add(x.name to smi)};if(smi<=-85)smiLow85++;if(smi>=75){smiHigh75++;overbought.add(x.name to smi)};if(smi>=85)smiHigh85++;if(rsi<=40)lowRsi++;if(rsi>=60)highRsi++;if(last.close>last.open&&last.close>prev.close)bullConfirm++;if(last.close<last.open&&last.close<prev.close)bearConfirm++;if(last.close>e200&&e13>e48&&last.close>vw&&rsi>=52)trendCallReady++;if(last.close<e200&&e13<e48&&last.close<vw&&rsi<=48)trendPutReady++;if(volOk)volumeReady++;val sig=StrategyEngine.evaluate(x.name,c);when(sig?.side){Side.CALL->{call++;candidates.add(sig)};Side.PUT->{put++;candidates.add(sig)};null->{}}}}
   catch(e:Exception){failed++;if(errors.size<10)errors.add("${x.name}: ${e.message?.take(100)}")}
   status(nm,"$attempted/${stocks.size} • OK $success • Fail $failed • Signals ${call+put}");delay(450)}
  val topCalls=candidates.filter{it.side==Side.CALL}.sortedByDescending{it.score}.take(5);val topPuts=candidates.filter{it.side==Side.PUT}.sortedByDescending{it.score}.take(5);if(topCalls.isNotEmpty())tg.send(s.telegramToken,s.telegramChatId,"🏆 TOP CALL CANDIDATES");topCalls.forEach{sendSignalWithOption(s,session,options,it)};if(topPuts.isNotEmpty())tg.send(s.telegramToken,s.telegramChatId,"🏆 TOP PUT CANDIDATES");topPuts.forEach{sendSignalWithOption(s,session,options,it)};tg.send(s.telegramToken,s.telegramChatId,"✅ Build 12 scan complete\nUniverse: ${stocks.size}\nAttempted: $attempted\nCandle success: $success\nCandle failures: $failed\nValid ≥205 candles: $valid\nCALL signals: $call\nPUT signals: $put\n\n📊 FUNNEL\nSMI ≤ -75: $smiLow75 | ≤ -85: $smiLow85\nSMI ≥ +75: $smiHigh75 | ≥ +85: $smiHigh85\nRSI ≤ 40: $lowRsi | RSI ≥ 60: $highRsi\nBull candle confirms: $bullConfirm\nBear candle confirms: $bearConfirm\nTrend CALL pre-volume: $trendCallReady\nTrend PUT pre-volume: $trendPutReady\nVolume ≥1.10x avg: $volumeReady")
  val os=oversold.sortedBy{it.second}.take(10).joinToString("\n"){it.first+": SMI "+String.format("%.1f",it.second)}
  val ob=overbought.sortedByDescending{it.second}.take(10).joinToString("\n"){it.first+": SMI "+String.format("%.1f",it.second)}
  if(os.isNotBlank())tg.send(s.telegramToken,s.telegramChatId,"🔴 Top SMI oversold watchlist\n$os")
  if(ob.isNotBlank())tg.send(s.telegramToken,s.telegramChatId,"🟢 Top SMI overbought watchlist\n$ob")
  if(errors.isNotEmpty())tg.send(s.telegramToken,s.telegramChatId,"⚠️ First candle errors:\n"+errors.joinToString("\n"))
  status(nm,"Complete • OK $success • Fail $failed • Signals ${call+put}")
 }catch(e:Exception){status(nm,"Error: ${e.message?.take(80)}")}}
 private suspend fun optionLine(s:AppSettings,session:AngelSession,options:List<AngelOption>,x:Signal):String{
  val typ=if(x.side==Side.CALL)"CE" else "PE";val pool=options.filter{it.name==x.symbol&&it.optionType==typ}
  if(pool.isEmpty())return "Option: no $typ contract found"
  val fmt=java.time.format.DateTimeFormatter.ofPattern("ddMMMyyyy",java.util.Locale.ENGLISH);val today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"))
  val valid=pool.mapNotNull{o->try{Pair(o,java.time.LocalDate.parse(o.expiry.uppercase(),fmt))}catch(_:Exception){null}}.filter{it.second>=today}
  if(valid.isEmpty())return "Option: no valid future expiry"
  val exp=valid.minByOrNull{it.second}!!.second;val same=valid.filter{it.second==exp}.map{it.first}
  val atm=same.minByOrNull{kotlin.math.abs(it.strike-x.entry)}?:return "Option: ATM not found"
  return try{val q=angel.optionQuote(s,session,atm);if(q.ltp<=0)"Option: ${atm.symbol} | Exp ${atm.expiry} | Strike ${"%.2f".format(atm.strike)} $typ | LTP unavailable" else "Option: ${atm.symbol} | Exp ${atm.expiry} | Strike ${"%.2f".format(atm.strike)} $typ | LTP ${"%.2f".format(q.ltp)} | Lot ${atm.lotSize} | Vol ${"%.0f".format(q.volume)} | OI ${"%.0f".format(q.openInterest)}"}catch(e:Exception){"Option: ${atm.symbol} | quote error ${e.message?.take(80)}"}
 }
 private suspend fun sendSignalWithOption(s:AppSettings,session:AngelSession,options:List<AngelOption>,x:Signal){
  val opt=optionLine(s,session,options,x);tg.send(s.telegramToken,s.telegramChatId,"🚨 ${x.side} • ${x.symbol}
Underlying ${"%.2f".format(x.entry)}
$opt
SL ${"%.2f".format(x.stop)} | T1 ${"%.2f".format(x.target1)} | T2 ${"%.2f".format(x.target2)}
Score ${x.score}/100
"+x.reasons.joinToString(" • "))
 }
 private fun sendSignal(s:AppSettings,x:Signal){tg.send(s.telegramToken,s.telegramChatId,"🚨 ${x.side} • ${x.symbol}\nUnderlying ${"%.2f".format(x.entry)}\nSL ${"%.2f".format(x.stop)} | T1 ${"%.2f".format(x.target1)} | T2 ${"%.2f".format(x.target2)}\nScore ${x.score}/100\n"+x.reasons.joinToString(" • "))}
 private fun status(nm:NotificationManager,t:String){nm.notify(101,NotificationCompat.Builder(this,"scanner").setContentTitle("NSE Option Buyer Scanner").setContentText(t).setSmallIcon(android.R.drawable.ic_menu_search).setOngoing(true).build())}
 override fun onDestroy(){job.cancel();super.onDestroy()};override fun onBind(i:Intent?):IBinder?=null
}
