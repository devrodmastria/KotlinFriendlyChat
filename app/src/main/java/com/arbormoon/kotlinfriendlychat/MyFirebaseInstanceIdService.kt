package com.arbormoon.kotlinfriendlychat

import android.util.Log

import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.FirebaseInstanceIdService
import com.google.firebase.messaging.FirebaseMessaging

class MyFirebaseInstanceIdService : FirebaseInstanceIdService() {

    /**
     * The Application's current Instance ID token is no longer valid and thus a new one must be requested.
     */
    override fun onTokenRefresh() {
        // If you need to handle the generation of a token, initially or after a refresh this is
        // where you should do that.
        val token = FirebaseInstanceId.getInstance().token
        Log.d(TAG, "FCM Token: " + token!!)

        // Once a token is generated, we subscribe to topic.
        FirebaseMessaging.getInstance().subscribeToTopic(FRIENDLY_ENGAGE_TOPIC)
    }

    companion object {
        private val TAG = "MyFirebaseIIDService"
        private val FRIENDLY_ENGAGE_TOPIC = "friendly_engage"
    }
}
