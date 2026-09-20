package com.nse.optionbuyerscanner.domain

import kotlin.math.*
object Indicators {
 fun ema(v:List<Double>,p:Int):List<Double>{ if(v.isEmpty())return emptyList(); val k=2.0/(p+1); val o=MutableList(v.size){v[0]}; for(i in 1 until v.size)o[i]=v[i]*k+o[i-1]*(1-k); return o }
 fun rsi(v:List<Double>,p:Int=14):Double { if(v.size<=p)return 50.0; var g=0.0;var l=0.0; for(i in v.size-p until v.size){val d=v[i]-v[i-1];if(d>0)g+=d else l-=d}; if(l==0.0)return 100.0; return 100-100/(1+(g/p)/(l/p)) }
 fun atr(c:List<Candle>,p:Int=14):Double { if(c.size<=p)return 0.0; return (c.size-p until c.size).map{i->max(c[i].high-c[i].low,max(abs(c[i].high-c[i-1].close),abs(c[i].low-c[i-1].close)))}.average() }
 fun vwap(c:List<Candle>):Double { val pv=c.sumOf{((it.high+it.low+it.close)/3)*it.volume}; val vol=c.sumOf{it.volume}; return if(vol==0.0) c.last().close else pv/vol }
 fun smi(c:List<Candle>,p:Int=14):Double { if(c.size<p)return 0.0; val w=c.takeLast(p); val hh=w.maxOf{it.high};val ll=w.minOf{it.low}; val range=hh-ll; return if(range==0.0)0.0 else 100.0*((c.last().close-(hh+ll)/2)/(range/2)) }
}
