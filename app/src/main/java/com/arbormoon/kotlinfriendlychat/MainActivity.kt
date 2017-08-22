/**
 * Copyright Google Inc. All Rights Reserved.
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arbormoon.kotlinfriendlychat

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar

import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.google.android.gms.ads.AdView
import com.google.android.gms.appinvite.AppInviteInvitation
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.crash.FirebaseCrash
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

import java.util.HashMap

import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), GoogleApiClient.OnConnectionFailedListener  {

    class MessageViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val recyclerView : View = v

    }

    private lateinit var username: String
    private lateinit var photoUrl: String
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var firebaseAdapter: FirebaseRecyclerAdapter<FriendlyMessage, MessageViewHolder>
    private lateinit var firebaseDatabaseReference: DatabaseReference
    private lateinit var firebaseAuth: FirebaseAuth
    private var firebaseUser: FirebaseUser? = null
    private lateinit var firebaseRemoteConfig: FirebaseRemoteConfig
    private lateinit var googleApiClient: GoogleApiClient

    companion object {  // Equivalent to static variables in Java

        val TAG = "MainActivity"
        val MESSAGES_CHILD = "messages"
        private val REQUEST_INVITE = 1
        private val REQUEST_IMAGE = 2
        val DEFAULT_MSG_LENGTH_LIMIT = 10
        val ANONYMOUS = "anonymous"
        private val MESSAGE_SENT_EVENT = "message_sent"
        private val MESSAGE_URL = "http://friendlychat.firebase.google.com/message/"
        private val LOADING_IMAGE_URL = "https://www.google.com/images/spin-32.gif"

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        username = ANONYMOUS

        // Initialize Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance()
        firebaseUser = firebaseAuth.currentUser

        if (firebaseUser == null) {
            // Not signed in, launch the Sign In activity
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
            return
        } else {
            username = firebaseUser!!.displayName.toString()

            val photoUri : Uri? = firebaseUser!!.photoUrl

            if (photoUri != null) {
                photoUrl = photoUri.toString()
            }

        }

        googleApiClient = GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */)
                .addApi(Auth.GOOGLE_SIGN_IN_API)
                .build()

        linearLayoutManager = LinearLayoutManager(this)
        linearLayoutManager.stackFromEnd = true

        firebaseDatabaseReference = FirebaseDatabase.getInstance().reference

        firebaseAdapter = RecyclerViewAdapter<FriendlyMessage, MessageViewHolder>()

        firebaseAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                val friendlyMessageCount = firebaseAdapter.itemCount
                val lastVisiblePosition = linearLayoutManager.findLastCompletelyVisibleItemPosition()
                // If the recycler view is initially being loaded or the user is at the bottom of the list, scroll
                // to the bottom of the list to show the newly added message.
                if (lastVisiblePosition == -1 || positionStart >= friendlyMessageCount - 1 && lastVisiblePosition == positionStart - 1) {
                    messageRecyclerView.scrollToPosition(positionStart)
                }
            }
        })

        messageRecyclerView.layoutManager = linearLayoutManager

        messageRecyclerView.adapter = firebaseAdapter
        progressBar.visibility = ProgressBar.INVISIBLE

        // Initialize Firebase Remote Config.
        firebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

        // Define Firebase Remote Config Settings.
        val firebaseRemoteConfigSettings = FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(true)
                .build()

        // Define default config values. Defaults are used when fetched config values are not
        // available. Eg: if an error occurred fetching values from the server.
        val defaultConfigMap = HashMap<String, Any>()
        defaultConfigMap.put("friendly_msg_length", 10L)

        // Apply config settings and default values.
        firebaseRemoteConfig.setConfigSettings(firebaseRemoteConfigSettings)
        firebaseRemoteConfig.setDefaults(defaultConfigMap)

        // Fetch remote config.
        fetchConfig()

        messageEditText.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(sharedPreferences
                .getInt(CodelabPreferences.FRIENDLY_MSG_LENGTH, DEFAULT_MSG_LENGTH_LIMIT)))
        messageEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {

                sendButton.isEnabled = charSequence.isNotEmpty()
            }

            override fun afterTextChanged(editable: Editable) {}
        })

        addMessageImageView.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "image/*"
            startActivityForResult(intent, REQUEST_IMAGE)
        }

        sendButton.setOnClickListener {
            val friendlyMessage = FriendlyMessage("", messageEditText.text.toString(), username,
                    photoUrl, "")
            firebaseDatabaseReference.child(MESSAGES_CHILD).push().setValue(friendlyMessage)
            messageEditText.setText("")
        }
    }

    public override fun onPause() {
        adView.pause()
        super.onPause()
    }

    public override fun onResume() {
        super.onResume()
        adView.resume()

    }

    public override fun onDestroy() {
        adView.destroy()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.invite_menu -> {
                sendInvitation()
                return true
            }
            R.id.crash_menu -> {
                FirebaseCrash.logcat(Log.ERROR, TAG, "crash caused")
                causeCrash()
                return true
            }
            R.id.sign_out_menu -> {
                firebaseAuth.signOut()
                Auth.GoogleSignInApi.signOut(googleApiClient)
                username = ANONYMOUS
                startActivity(Intent(this, SignInActivity::class.java))
                return true
            }
            R.id.fresh_config_menu -> {
                fetchConfig()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun causeCrash() {
        throw NullPointerException("Fake null pointer exception")
    }

    private fun sendInvitation() {
        val intent = AppInviteInvitation.IntentBuilder(getString(R.string.invitation_title))
                .setMessage(getString(R.string.invitation_message))
                .setCallToActionText(getString(R.string.invitation_cta))
                .build()
        startActivityForResult(intent, REQUEST_INVITE)
    }

    // Fetch the config to determine the allowed length of messages.
    fun fetchConfig() {
        var cacheExpiration: Long = 3600 // 1 hour in seconds
        // If developer mode is enabled reduce cacheExpiration to 0 so that each fetch goes to the
        // server. This should not be used in release builds.
        if (firebaseRemoteConfig.info.configSettings.isDeveloperModeEnabled) {
            cacheExpiration = 0
        }
        firebaseRemoteConfig.fetch(cacheExpiration)
                .addOnSuccessListener {
                    // Make the fetched config available via FirebaseRemoteConfig get<type> calls.
                    firebaseRemoteConfig.activateFetched()
                    applyRetrievedLengthLimit()
                }
                .addOnFailureListener { e ->
                    // There has been an error fetching the config
                    Log.w(TAG, "Error fetching config", e)
                    applyRetrievedLengthLimit()
                }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")

        if (requestCode == REQUEST_IMAGE) {
            if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    val uri = data.data
                    Log.d(TAG, "Uri: " + uri!!.toString())

                    val tempMessage = FriendlyMessage("", "", username, photoUrl,
                            LOADING_IMAGE_URL)
                    firebaseDatabaseReference.child(MESSAGES_CHILD).push()
                            .setValue(tempMessage) { databaseError, databaseReference ->
                                if (databaseError == null) {
                                    val key = databaseReference.key
                                    val storageReference = FirebaseStorage.getInstance()
                                            .getReference(firebaseUser!!.uid)
                                            .child(key)
                                            .child(uri.lastPathSegment)

                                    putImageInStorage(storageReference, uri, key)
                                } else {
                                    Log.w(TAG, "Unable to write message to database.",
                                            databaseError.toException())
                                }
                            }
                }
            }
        } else if (requestCode == REQUEST_INVITE) {
            if (resultCode == Activity.RESULT_OK) {
                // Use Firebase Measurement to log that invitation was sent.
                val payload = Bundle()
                payload.putString(FirebaseAnalytics.Param.VALUE, "inv_sent")

                // Check how many invitations were sent and log.
                val ids = AppInviteInvitation.getInvitationIds(resultCode, data!!)
                Log.d(TAG, "Invitations sent: " + ids.size)
            } else {
                // Use Firebase Measurement to log that invitation was not sent
                val payload = Bundle()
                payload.putString(FirebaseAnalytics.Param.VALUE, "inv_not_sent")


                val mFirebaseAnalytics = FirebaseAnalytics.getInstance(this)

                mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SHARE, payload)

                // Sending failed or it was canceled, show failure message to the user
                Log.d(TAG, "Failed to send invitation.")
            }
        }
    }

    private fun putImageInStorage(storageReference: StorageReference, uri: Uri, key: String) {
        storageReference.putFile(uri).addOnCompleteListener(this@MainActivity
        ) { task ->
            if (task.isSuccessful) {
                val friendlyMessage = FriendlyMessage("", "", username, photoUrl,
                        task.result.downloadUrl!!
                                .toString())
                firebaseDatabaseReference.child(MESSAGES_CHILD).child(key)
                        .setValue(friendlyMessage)
            } else {
                Log.w(TAG, "Image upload task was not successful.",
                        task.exception)
            }
        }
    }

    /**
     * Apply retrieved length limit to edit text field. This result may be fresh from the server or it may be from
     * cached values.
     */
    private fun applyRetrievedLengthLimit() {
        val friendly_msg_length = firebaseRemoteConfig.getLong("friendly_msg_length")
        messageEditText.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(friendly_msg_length.toInt()))
        Log.d(TAG, "Message length is: " + friendly_msg_length)
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        Log.d(TAG, "onConnectionFailed:" + connectionResult)
    }

}
