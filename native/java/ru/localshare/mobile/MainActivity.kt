package ru.localshare.mobile

import android.os.Bundle
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(LocalSharePlugin::class.java)
        super.onCreate(savedInstanceState)
    }
}
