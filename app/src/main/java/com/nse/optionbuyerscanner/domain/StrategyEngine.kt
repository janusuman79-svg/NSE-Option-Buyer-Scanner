package com.nse.optionbuyerscanner.domain

object StrategyEngine {
 fun evaluate(symbol:String,input:List<Candle>):Signal? {
  if(input.size<206)return null
  val c=input.sortedBy{it.time}.dropLast(1); if(c.size<205)return null
  val closes=c.map{it.close}; val last=c.last(); val prev=c[c.lastIndex-1]
  val e13=Indicators.ema(closes,13).last(); val e48=Indicators.ema(closes,48).last(); val e200=Indicators.ema(closes,200).last()
  val rsi=Indicators.rsi(closes); val smi=Indicators.smi(c); val atr=Indicators.atr(c); val vw=Indicators.vwap(c.takeLast(30))
  val bullish=last.close>last.open&&last.close>prev.close; val bearish=last.close<last.open&&last.close<prev.close
  val avgVol=c.dropLast(1).takeLast(20).map{it.volume}.average(); val volRatio=if(avgVol>0)last.volume/avgVol else 1.0
  val trendCall=last.close>e200&&e13>e48&&last.close>vw&&rsi>=52&&bullish
  val trendPut=last.close<e200&&e13<e48&&last.close<vw&&rsi<=48&&bearish
  val reversalCall=smi<=-75&&rsi<=40&&bullish; val reversalPut=smi>=75&&rsi>=60&&bearish
  val side:Side; val setup:String
  when { reversalCall->{side=Side.CALL;setup="SMI REVERSAL"}; reversalPut->{side=Side.PUT;setup="SMI REVERSAL"}; trendCall->{side=Side.CALL;setup="TREND"}; trendPut->{side=Side.PUT;setup="TREND"}; else->return null }
  var score=50
  if(side==Side.CALL&&e13>e48)score+=8
  if(side==Side.PUT&&e13<e48)score+=8
  if((side==Side.CALL&&last.close>e200)||(side==Side.PUT&&last.close<e200))score+=8
  if((side==Side.CALL&&last.close>vw)||(side==Side.PUT&&last.close<vw))score+=7
  if((bullish&&side==Side.CALL)||(bearish&&side==Side.PUT))score+=8
  if(volRatio>=1.20)score+=7 else if(volRatio>=1.0)score+=3
  if(setup=="SMI REVERSAL")score+=7
  if(setup=="TREND"&&side==Side.CALL&&(rsi>75||smi>80))score-=12
  if(setup=="TREND"&&side==Side.PUT&&(rsi<25||smi < -80))score-=12
  score=score.coerceIn(0,100)
  val risk=atr.coerceAtLeast(last.close*0.003); val stop=if(side==Side.CALL)last.close-risk else last.close+risk
  val t1=if(side==Side.CALL)last.close+risk else last.close-risk; val t2=if(side==Side.CALL)last.close+2*risk else last.close-2*risk
  return Signal(symbol,side,score,last.close,stop,t1,t2,listOf("Setup: $setup","EMA13 ${"%.2f".format(e13)} / EMA48 ${"%.2f".format(e48)} / EMA200 ${"%.2f".format(e200)}","VWAP ${"%.2f".format(vw)}","RSI ${"%.1f".format(rsi)}","SMI ${"%.1f".format(smi)}","ATR ${"%.2f".format(atr)}","RelVol ${"%.2f".format(volRatio)}x"))
 }
}
