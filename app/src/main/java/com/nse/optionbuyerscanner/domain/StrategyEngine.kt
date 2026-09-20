package com.nse.optionbuyerscanner.domain

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max

object StrategyEngine {
 fun evaluate(symbol:String,input:List<Candle>):Signal? {
  val c=Indicators.completed(input)
  if(c.size<205)return null
  val closes=c.map{it.close};val last=c.last();val prev=c[c.lastIndex-1]
  val e13=Indicators.ema(closes,13).last();val e48=Indicators.ema(closes,48).last();val e200=Indicators.ema(closes,200).last()
  val rsi=Indicators.rsi(closes);val smi=Indicators.smi(c);val atr=Indicators.atr(c).coerceAtLeast(last.close*0.001)
  val z=ZoneId.of("Asia/Kolkata");val day=Instant.ofEpochMilli(last.time).atZone(z).toLocalDate()
  val session=c.filter{Instant.ofEpochMilli(it.time).atZone(z).toLocalDate()==day}
  val vw=Indicators.vwap(session)
  val bullish=last.close>last.open&&last.close>prev.close
  val bearish=last.close<last.open&&last.close<prev.close
  val avgVol=c.dropLast(1).takeLast(20).map{it.volume}.average()
  val relVol=if(avgVol>0)last.volume/avgVol else 1.0
  val trendCall=last.close>e200&&e13>e48&&last.close>vw&&rsi>=52&&bullish
  val trendPut=last.close<e200&&e13<e48&&last.close<vw&&rsi<=48&&bearish
  val reversalCall=smi<=-75&&rsi<=40&&bullish
  val reversalPut=smi>=75&&rsi>=60&&bearish
  val side:Side;val setup:String
  when{reversalCall->{side=Side.CALL;setup="SMI REVERSAL"};reversalPut->{side=Side.PUT;setup="SMI REVERSAL"};trendCall->{side=Side.CALL;setup="TREND"};trendPut->{side=Side.PUT;setup="TREND"};else->return null}
  val dir=if(side==Side.CALL)1.0 else -1.0
  val emaSep=(dir*(e13-e48)/atr).coerceIn(0.0,3.0);val vwapDist=(dir*(last.close-vw)/atr).coerceIn(0.0,3.0);val ema200Dist=(dir*(last.close-e200)/atr).coerceIn(0.0,4.0)
  val range=max(last.high-last.low,last.close*0.0001);val body=abs(last.close-last.open)/range
  val rsiQ=if(side==Side.CALL)(1.0-abs(rsi-62.0)/23.0).coerceIn(0.0,1.0) else (1.0-abs(rsi-38.0)/23.0).coerceIn(0.0,1.0)
  val smiQ=when{setup=="SMI REVERSAL"->(abs(smi)/100.0).coerceIn(0.0,1.0);side==Side.CALL->(1.0-abs(smi-35.0)/65.0).coerceIn(0.0,1.0);else->(1.0-abs(smi+35.0)/65.0).coerceIn(0.0,1.0)}
  var score=35.0+emaSep/3*14+vwapDist/3*12+ema200Dist/4*10+rsiQ*10+smiQ*8+body.coerceIn(0.0,1.0)*7+((relVol-1)/2).coerceIn(0.0,1.0)*4
  if(setup=="SMI REVERSAL")score+=4
  val risk=atr.coerceAtLeast(last.close*0.003);val stop=if(side==Side.CALL)last.close-risk else last.close+risk;val t1=if(side==Side.CALL)last.close+risk else last.close-risk;val t2=if(side==Side.CALL)last.close+2*risk else last.close-2*risk
  return Signal(symbol,side,score.toInt().coerceIn(0,100),last.close,stop,t1,t2,listOf("Setup: $setup","EMA13 ${"%.2f".format(e13)} / EMA48 ${"%.2f".format(e48)} / EMA200 ${"%.2f".format(e200)}","Session VWAP ${"%.2f".format(vw)}","RSI ${"%.1f".format(rsi)}","SMI ${"%.1f".format(smi)}","ATR ${"%.2f".format(atr)}","RelVol ${"%.2f".format(relVol)}x","Rank factors: EMA ${"%.2f".format(emaSep)} ATR • VWAP ${"%.2f".format(vwapDist)} ATR • Body ${"%.0f".format(body*100)}%"),last.time)
 }
}
