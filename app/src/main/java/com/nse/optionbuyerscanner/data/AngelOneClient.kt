package com.nse.optionbuyerscanner.data

import com.nse.optionbuyerscanner.domain.Candle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

data class AngelSession(val jwt:String,val refresh:String,val feed:String)
data class AngelInstrument(val token:String,val symbol:String,val name:String)

class AngelOneClient(private val http:OkHttpClient=OkHttpClient()){
 private val base="https://apiconnect.angelone.in"
 private val master="https://margincalculator.angelone.in/OpenAPI_File/files/OpenAPIScripMaster.json"

 suspend fun login(s:AppSettings)=withContext(Dispatchers.IO){
  require(s.angelApiKey.isNotBlank()&&s.clientCode.isNotBlank()&&s.pin.isNotBlank()&&s.totpSecret.isNotBlank()){"Angel One settings incomplete"}
  val body=JSONObject().put("clientcode",s.clientCode.trim()).put("password",s.pin.trim()).put("totp",totp(s.totpSecret.trim())).toString().toRequestBody(JSON)
  val req=Request.Builder().url("$base/rest/auth/angelbroking/user/v1/loginByPassword").headers(headers(s.angelApiKey)).post(body).build()
  http.newCall(req).execute().use{r->
   val raw=r.body?.string().orEmpty(); if(!r.isSuccessful) error("Login HTTP ${r.code}: $raw")
   val j=JSONObject(raw); if(!j.optBoolean("status")) error(j.optString("message","Login failed"))
   val d=j.getJSONObject("data"); AngelSession(d.getString("jwtToken"),d.getString("refreshToken"),d.optString("feedToken"))
  }
 }

 suspend fun loadFnoStocks()=withContext(Dispatchers.IO){
  http.newCall(Request.Builder().url(master).build()).execute().use{r->
   if(!r.isSuccessful) error("Instrument master HTTP ${r.code}")
   val a=JSONArray(r.body?.string().orEmpty()); val names=HashSet<String>()
   for(i in 0 until a.length()){val x=a.getJSONObject(i);if(x.optString("exch_seg")=="NFO"&&x.optString("instrumenttype")=="FUTSTK")names.add(x.optString("name").uppercase())}
   val out=ArrayList<AngelInstrument>()
   for(i in 0 until a.length()){val x=a.getJSONObject(i);val n=x.optString("name").uppercase()
    if(x.optString("exch_seg")=="NSE"&&x.optString("symbol").endsWith("-EQ")&&n in names)out.add(AngelInstrument(x.optString("token"),x.optString("symbol"),n))
   }
   out.distinctBy{it.token}.sortedBy{it.name}
  }
 }

 suspend fun candles(s:AppSettings,session:AngelSession,token:String)=withContext(Dispatchers.IO){
  val f=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");val to=LocalDateTime.now();val from=to.minusDays(10)
  val body=JSONObject().put("exchange","NSE").put("symboltoken",token).put("interval","FIFTEEN_MINUTE").put("fromdate",from.format(f)).put("todate",to.format(f)).toString().toRequestBody(JSON)
  val h=headers(s.angelApiKey).newBuilder().add("Authorization","Bearer ${session.jwt}").build()
  http.newCall(Request.Builder().url("$base/rest/secure/angelbroking/historical/v1/getCandleData").headers(h).post(body).build()).execute().use{r->
   val raw=r.body?.string().orEmpty();if(!r.isSuccessful)error("Candle HTTP ${r.code}")
   val j=JSONObject(raw);if(!j.optBoolean("status"))error(j.optString("message","Candle failed"));val a=j.optJSONArray("data")?:return@use emptyList()
   List(a.length()){i->val q=a.getJSONArray(i);Candle(parseAngelTime(q.optString(0)),q.optDouble(1),q.optDouble(2),q.optDouble(3),q.optDouble(4),q.optDouble(5))}
  }
 }

 private fun parseAngelTime(v:String):Long=try{OffsetDateTime.parse(v).toInstant().toEpochMilli()}catch(_:Exception){System.currentTimeMillis()}
 private fun headers(k:String)=Headers.Builder().add("Content-Type","application/json").add("Accept","application/json").add("X-UserType","USER").add("X-SourceID","WEB").add("X-ClientLocalIP","127.0.0.1").add("X-ClientPublicIP","127.0.0.1").add("X-MACAddress","00:00:00:00:00:00").add("X-PrivateKey",k.trim()).build()
 private fun totp(s:String):String{val key=b32(s.replace(" ","").uppercase());val c=System.currentTimeMillis()/30000;val b=ByteArray(8);var v=c;for(i in 7 downTo 0){b[i]=(v and 255).toByte();v=v ushr 8};val m=Mac.getInstance("HmacSHA1");m.init(SecretKeySpec(key,"HmacSHA1"));val h=m.doFinal(b);val o=h.last().toInt() and 15;val n=((h[o].toInt() and 127) shl 24)or((h[o+1].toInt() and 255)shl 16)or((h[o+2].toInt() and 255)shl 8)or(h[o+3].toInt() and 255);return(n%10.0.pow(6).toInt()).toString().padStart(6,'0')}
 private fun b32(s:String):ByteArray{val a="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";var buf=0;var bits=0;val o=ArrayList<Byte>();for(c in s.trimEnd('=')){val x=a.indexOf(c);require(x>=0){"Invalid TOTP secret"};buf=(buf shl 5)or x;bits+=5;if(bits>=8){bits-=8;o.add(((buf shr bits)and 255).toByte())}};return o.toByteArray()}
 companion object{private val JSON="application/json; charset=utf-8".toMediaType()}
}
