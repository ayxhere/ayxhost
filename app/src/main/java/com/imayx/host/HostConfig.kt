package com.imayx.host

import android.content.Context

object HostConfig {
    private const val PREF="host"
    private const val PROJECT="project"
    private const val TOKEN="cf_token"
    private const val PORT="port"
    private const val QUICK="quick"
    fun project(c:Context)=c.getSharedPreferences(PREF,0).getString(PROJECT,"default") ?: "default"
    fun setProject(c:Context,v:String)=c.getSharedPreferences(PREF,0).edit().putString(PROJECT,v).apply()
    fun token(c:Context)=c.getSharedPreferences(PREF,0).getString(TOKEN,"") ?: ""
    fun setToken(c:Context,v:String)=c.getSharedPreferences(PREF,0).edit().putString(TOKEN,v.trim()).apply()
    fun port(c:Context)=c.getSharedPreferences(PREF,0).getInt(PORT,3000)
    fun quick(c:Context)=c.getSharedPreferences(PREF,0).getBoolean(QUICK,true)
    fun setQuick(c:Context,v:Boolean)=c.getSharedPreferences(PREF,0).edit().putBoolean(QUICK,v).apply()
}
