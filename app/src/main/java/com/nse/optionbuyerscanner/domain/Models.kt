package com.nse.optionbuyerscanner.domain

data class Candle(val time: Long, val open: Double, val high: Double, val low: Double, val close: Double, val volume: Double)
enum class Side { CALL, PUT }
data class Signal(val symbol:String,val side:Side,val score:Int,val entry:Double,val stop:Double,val target1:Double,val target2:Double,val reasons:List<String>,val timestamp:Long=System.currentTimeMillis())
