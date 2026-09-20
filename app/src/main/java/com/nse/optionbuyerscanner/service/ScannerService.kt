package com.nse.optionbuyerscanner.service
import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nse.optionbuyerscanner.MainActivity
class ScannerService:Service(){
 override fun onCreate(){super.onCreate(); val id="scanner"; getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(id,"F&O Scanner",NotificationManager.IMPORTANCE_LOW)); val pi=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE); startForeground(101,NotificationCompat.Builder(this,id).setContentTitle("NSE Option Buyer Scanner").setContentText("Paper scanner service active").setSmallIcon(android.R.drawable.ic_menu_search).setContentIntent(pi).build())}
 override fun onBind(i:Intent?):IBinder?=null
}
