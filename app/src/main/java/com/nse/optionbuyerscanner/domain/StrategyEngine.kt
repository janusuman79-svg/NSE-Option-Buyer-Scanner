package com.nse.optionbuyerscanner.domain

object StrategyEngine {
 fun evaluate(symbol:String,c:List<Candle>):Signal? {
  if(c.size<206)return null
  val c=c.sortedBy{it.time}.dropLast(1)
  if(c.size<205)return null
  val closes=c.map{it.close}
  val e13=Indicators.ema(closes,13).last()
  val e48=Indicators.ema(closes,48).last()
  val e200=Indicators.ema(closes,200).last()
  val last=c.last()
  val prev=c[c.lastIndex-1]
  val rsi=Indicators.rsi(closes)
  val smi=Indicators.smi(c)
  val atr=Indicators.atr(c)
  val vw=Indicators.vwap(c.takeLast(30))
  val avgVol=c.takeLast(20).dropLast(1).map{it.volume}.average()
  val volumeOk=avgVol>0 && last.volume>=avgVol*1.10

  // Setup A: trend continuation.
  val trendCall=last.close>e200 && e13>e48 && last.close>vw && rsi>=52 && volumeOk
  val trendPut =last.close<e200 && e13<e48 && last.close<vw && rsi<=48 && volumeOk

  // Setup B: SMI extreme reversal. Require a confirming candle so an extreme alone
  // is not treated as an entry. -75/-85 = CALL watch zone, +75/+85 = PUT watch zone.
  val bullishCandle=last.close>last.open && last.close>prev.close
  val bearishCandle=last.close<last.open && last.close<prev.close
  val reversalCall=smi<=-75 && rsi<=40 && bullishCandle
  val reversalPut =smi>=75 && rsi>=60 && bearishCandle

  val side:Side
  val setup:String
  when {
   reversalCall -> {side=Side.CALL;setup=if(smi<=-85)"SMI EXTREME REVERSAL" else "SMI REVERSAL"}
   reversalPut  -> {side=Side.PUT; setup=if(smi>=85)"SMI EXTREME REVERSAL" else "SMI REVERSAL"}
   trendCall    -> {side=Side.CALL;setup="TREND"}
   trendPut     -> {side=Side.PUT; setup="TREND"}
   else -> return null
  }

  val risk=atr.coerceAtLeast(last.close*0.003)
  val stop=if(side==Side.CALL)last.close-risk else last.close+risk
  val t1=if(side==Side.CALL)last.close+risk else last.close-risk
  val t2=if(side==Side.CALL)last.close+2*risk else last.close-2*risk
  val score=when(setup){
   "SMI EXTREME REVERSAL" -> 85
   "SMI REVERSAL" -> 80
   else -> 75
  }
  return Signal(symbol,side,score,last.close,stop,t1,t2,listOf(
   "Setup: $setup",
   "EMA13 ${"%.2f".format(e13)} / EMA48 ${"%.2f".format(e48)} / EMA200 ${"%.2f".format(e200)}",
   "VWAP ${"%.2f".format(vw)}",
   "RSI ${"%.1f".format(rsi)}",
   "SMI ${"%.1f".format(smi)}",
   "ATR ${"%.2f".format(atr)}",
   "Volume ${if(volumeOk)"confirmed" else "normal"}"
  ))
 }
}
