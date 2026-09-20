package com.nse.optionbuyerscanner.data
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
private val Context.ds by preferencesDataStore("scanner_settings")
data class AppSettings(val angelApiKey:String="",val clientCode:String="",val pin:String="",val totpSecret:String="",val telegramToken:String="",val telegramChatId:String="")
class SettingsStore(private val c:Context){
 private object K{val api=stringPreferencesKey("api");val client=stringPreferencesKey("client");val pin=stringPreferencesKey("pin");val totp=stringPreferencesKey("totp");val tg=stringPreferencesKey("tg");val chat=stringPreferencesKey("chat")}
 val flow=c.ds.data.map{AppSettings(it[K.api]?:"",it[K.client]?:"",it[K.pin]?:"",it[K.totp]?:"",it[K.tg]?:"",it[K.chat]?:"")}
 suspend fun save(s:AppSettings)=c.ds.edit{it[K.api]=s.angelApiKey;it[K.client]=s.clientCode;it[K.pin]=s.pin;it[K.totp]=s.totpSecret;it[K.tg]=s.telegramToken;it[K.chat]=s.telegramChatId}
}
