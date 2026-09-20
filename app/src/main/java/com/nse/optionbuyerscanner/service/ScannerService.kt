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
import java.time.*

data class Pick(val option:AngelOption,val quote:OptionQuote,val spread:Double,val distance:Double)

class ScannerService:Service(){
 private val job=SupervisorJob();private val scope=CoroutineScope(job+Dispatchers.IO);private val angel=AngelOneClient();private val tg=TelegramClient()
 private lateinit var settings:AppSettings;private lateinit var session:AngelSession;private var stocks:List<AngelInstrument> = emptyList();private var options:List<AngelOption> = emptyList()
 override fun onCreate(){super.onCreate();val id="scanner";getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(id,"F&O Scanner",NotificationManager.IMPORTANCE_LOW));val pi=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE);startForeground(101,NotificationCompat.Builder(this,id).setContentTitle("NSE Option Buyer Scanner").setContentText("Final Build • 15m automatic scanner").setSmallIcon(android.R.drawable.ic_menu_search).setContentIntent(pi).build());scope.launch{runLoop()}}
 private suspend fun runLoop(){
  val nm=getSystemService(NotificationManager::class.java)
  try{settings=SettingsStore(this).flow.first();session=angel.login(settings);stocks=angel.loadFnoStocks();options=angel.loadStockOptions();tg.send(settings.telegramToken,settings.telegramChatId,"✅ FINAL BUILD online\nUniverse: ${stocks.size} F&O stocks\n15-minute market-hours scanner active\nSignal-only: no automatic orders")
   while(currentCoroutineContext().isActive){
    val now=ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));val weekday=now.dayOfWeek.value in 1..5;val open=now.toLocalTime()>=LocalTime.of(9,15)&&now.toLocalTime()<=LocalTime.of(15,30)
    if(weekday&&open){scanOnce(nm);delay(msUntilNextQuarter())}else{status(nm,"Waiting for NSE market hours");delay(60000)}
   }
  }catch(e:Exception){status(nm,"Error: ${e.message?.take(80)}");delay(60000)}
 }
 private fun msUntilNextQuarter():Long{val n=ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));val nextMin=((n.minute/15)+1)*15;val x=if(nextMin>=60)n.plusHours(1).withMinute(0).withSecond(20).withNano(0) else n.withMinute(nextMin).withSecond(20).withNano(0);return maxOf(30000,Duration.between(n,x).toMillis())}
 private suspend fun scanOnce(nm:NotificationManager){
  val candidates=ArrayList<Signal>();var ok=0;var fail=0
  for((i,x) in stocks.withIndex()){try{val c=angel.candles(settings,session,x.token);ok++;StrategyEngine.evaluate(x.name,c)?.let{candidates.add(it)}}catch(_:Exception){fail++};status(nm,"${i+1}/${stocks.size} • signals ${candidates.size}");delay(450)}
  val ranked=candidates.sortedByDescending{it.score}
  val calls=selectQuality(ranked.filter{it.side==Side.CALL},5);val puts=selectQuality(ranked.filter{it.side==Side.PUT},5)
  if(calls.isNotEmpty())tg.send(settings.telegramToken,settings.telegramChatId,"🏆 FINAL TOP CALLS")
  calls.forEach{sendPick(it.first,it.second)}
  if(puts.isNotEmpty())tg.send(settings.telegramToken,settings.telegramChatId,"🏆 FINAL TOP PUTS")
  puts.forEach{sendPick(it.first,it.second)}
  tg.send(settings.telegramToken,settings.telegramChatId,"✅ Scan complete • Data OK $ok • Fail $fail • Raw signals ${candidates.size} • Quality CALL ${calls.size} • PUT ${puts.size}")
  status(nm,"Complete • CALL ${calls.size} • PUT ${puts.size}")
 }
 private suspend fun selectQuality(xs:List<Signal>,limit:Int):List<Pair<Signal,Pick>>{val out=ArrayList<Pair<Signal,Pick>>();for(x in xs){val p=bestOption(x);if(p!=null){out.add(x to p);if(out.size>=limit)break}};return out}
 private suspend fun bestOption(x:Signal):Pick?{
  val typ=if(x.side==Side.CALL)"CE" else "PE";val fmt=java.time.format.DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("ddMMMyyyy").toFormatter(java.util.Locale.ENGLISH);val today=LocalDate.now(ZoneId.of("Asia/Kolkata"))
  val valid=options.filter{it.name==x.symbol&&it.optionType==typ}.mapNotNull{o->try{o to LocalDate.parse(o.expiry.trim(),fmt)}catch(_:Exception){null}}.filter{it.second>=today}
  if(valid.isEmpty())return null;val exp=valid.minOf{it.second};val same=valid.filter{it.second==exp}.map{it.first}.sortedBy{kotlin.math.abs(it.strike-x.entry)}.take(5)
  val picks=ArrayList<Pick>()
  for(o in same){try{val q=angel.optionQuote(settings,session,o);if(q.ltp<=0||q.volume<1000||q.openInterest<1000)continue;val sp=if(q.bestBid>0&&q.bestAsk>0&&q.bestAsk>=q.bestBid)100*(q.bestAsk-q.bestBid)/((q.bestAsk+q.bestBid)/2) else 999.0;if(sp<=5.0)picks.add(Pick(o,q,sp,kotlin.math.abs(o.strike-x.entry)))}catch(_:Exception){}}
  return picks.minWithOrNull(compareBy<Pick>{it.distance}.thenBy{it.spread})
 }
 private fun sendPick(x:Signal,p:Pick){
  val q=p.quote;val entry=if(q.bestAsk>0)q.bestAsk else q.ltp
  val msg=buildString{append("🚨 ${x.side} • ${x.symbol}\n");append("Underlying ${"%.2f".format(x.entry)}\n");append("Option ${p.option.symbol} | Exp ${p.option.expiry} | Strike ${"%.2f".format(p.option.strike)} | Buy ref/Ask ${"%.2f".format(entry)} | LTP ${"%.2f".format(q.ltp)}\n");append("Bid ${"%.2f".format(q.bestBid)} | Ask ${"%.2f".format(q.bestAsk)} | Spread ${"%.2f".format(p.spread)}% | Vol ${"%.0f".format(q.volume)} | OI ${"%.0f".format(q.openInterest)} | Lot ${p.option.lotSize}\n");append("Underlying SL ${"%.2f".format(x.stop)} | T1 ${"%.2f".format(x.target1)} | T2 ${"%.2f".format(x.target2)}\n");append("Rank ${x.score}/100 • Quality PASS\n");append(x.reasons.joinToString(" • "))}
  tg.send(settings.telegramToken,settings.telegramChatId,msg)
 }
 private fun status(nm:NotificationManager,t:String){nm.notify(101,NotificationCompat.Builder(this,"scanner").setContentTitle("NSE Option Buyer Scanner").setContentText(t).setSmallIcon(android.R.drawable.ic_menu_search).setOngoing(true).build())}
 override fun onDestroy(){job.cancel();super.onDestroy()};override fun onBind(i:Intent?):IBinder?=null
}
