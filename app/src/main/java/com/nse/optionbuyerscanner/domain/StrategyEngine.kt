package com.nse.optionbuyerscanner.domain

object StrategyEngine {
 fun evaluate(symbol:String,c:List<Candle>):Signal? {
  if(c.size<205)return null
  val closes=c.map{it.close}; val e13=Indicators.ema(closes,13).last(); val e48=Indicators.ema(closes,48).last(); val e200=Indicators.ema(closes,200).last()
  val last=c.last(); val rsi=Indicators.rsi(closes); val smi=Indicators.smi(c); val atr=Indicators.atr(c); val vw=Indicators.vwap(c.takeLast(30)); val avgVol=c.takeLast(20).map{it.volume}.average(); val vol=last.volume>avgVol*1.15
  val bull=last.close>e200&&e13>e48&&e48>e200&&last.close>vw&&rsi>50&&smi>-75&&vol
  val bear=last.close<e200&&e13<e48&&e48<e200&&last.close<vw&&rsi<50&&smi<75&&vol
  val side=when{bull->Side.CALL;bear->Side.PUT;else->return null}; val risk=atr.coerceAtLeast(last.close*0.003)
  val stop=if(side==Side.CALL)last.close-risk else last.close+risk; val t1=if(side==Side.CALL)last.close+risk else last.close-risk; val t2=if(side==Side.CALL)last.close+2*risk else last.close-2*risk
  return Signal(symbol,side,80,last.close,stop,t1,t2,listOf("EMA 13/48/200 trend","VWAP confirmation","RSI ${"%.1f".format(rsi)}","SMI ${"%.1f".format(smi)}","Volume expansion"))
 }
}
