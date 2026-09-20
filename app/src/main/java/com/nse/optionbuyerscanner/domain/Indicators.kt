package com.nse.optionbuyerscanner.domain

import kotlin.math.*

object Indicators {
 fun completed(c:List<Candle>,now:Long=System.currentTimeMillis()):List<Candle> =
  c.sortedBy{it.time}.filter{x->
   val finished=x.time+15*60*1000L<=now
   val validPrice=x.open>0&&x.high>0&&x.low>0&&x.close>0
   val validVolume=x.volume>0
   finished&&validPrice&&validVolume
  }

 fun ema(v:List<Double>,p:Int):List<Double>{
  if(v.isEmpty())return emptyList()
  val k=2.0/(p+1);val o=MutableList(v.size){v[0]}
  for(i in 1 until v.size)o[i]=v[i]*k+o[i-1]*(1-k)
  return o
 }
 fun rsi(v:List<Double>,p:Int=14):Double{
  if(v.size<=p)return 50.0
  var g=0.0;var l=0.0
  for(i in v.size-p until v.size){val d=v[i]-v[i-1];if(d>0)g+=d else l-=d}
  if(l==0.0)return 100.0
  return 100-100/(1+(g/p)/(l/p))
 }
 fun atr(c:List<Candle>,p:Int=14):Double{
  if(c.size<=p)return 0.0
  return (c.size-p until c.size).map{i->
   max(c[i].high-c[i].low,max(abs(c[i].high-c[i-1].close),abs(c[i].low-c[i-1].close)))
  }.average()
 }
 fun vwap(c:List<Candle>):Double{
  val pv=c.sumOf{((it.high+it.low+it.close)/3)*it.volume};val vol=c.sumOf{it.volume}
  return if(vol==0.0)c.last().close else pv/vol
 }

 // Conventional Stochastic Momentum Index:
 // raw distance from range midpoint is double-smoothed, as is half-range.
 // Defaults: range=14, first smoothing=3, second smoothing=3.
 fun smi(c:List<Candle>,p:Int=14,s1:Int=3,s2:Int=3):Double{
  if(c.size < p+s1+s2)return 0.0
  val d=ArrayList<Double>();val r=ArrayList<Double>()
  for(i in p-1 until c.size){
   val w=c.subList(i-p+1,i+1)
   val hh=w.maxOf{it.high};val ll=w.minOf{it.low}
   d.add(c[i].close-(hh+ll)/2.0)
   r.add((hh-ll)/2.0)
  }
  val ds=ema(ema(d,s1),s2).last()
  val rs=ema(ema(r,s1),s2).last()
  return if(abs(rs)<1e-12)0.0 else (100.0*ds/rs).coerceIn(-100.0,100.0)
 }
}
