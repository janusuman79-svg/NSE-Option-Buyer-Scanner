package com.nse.optionbuyerscanner.data
import okhttp3.*
class TelegramClient(private val http:OkHttpClient=OkHttpClient()){
 fun send(token:String,chat:String,text:String){ if(token.isBlank()||chat.isBlank())return; val body=FormBody.Builder().add("chat_id",chat).add("text",text).build(); http.newCall(Request.Builder().url("https://api.telegram.org/bot$token/sendMessage").post(body).build()).enqueue(object:Callback{override fun onFailure(c:Call,e:java.io.IOException){};override fun onResponse(c:Call,r:Response){r.close()}})}
}
