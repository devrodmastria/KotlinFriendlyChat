package com.arbormoon.kotlinfriendlychat

import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.arbormoon.kotlinfriendlychat.R.id.*
import com.bumptech.glide.Glide
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.google.firebase.appindexing.Action
import com.google.firebase.appindexing.FirebaseAppIndex
import com.google.firebase.appindexing.FirebaseUserActions
import com.google.firebase.appindexing.Indexable
import com.google.firebase.appindexing.builders.Indexables
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.item_message.*

/**
 * Created by rodrigom on 8/18/17.
 */


open class RecyclerViewAdapter<T, U> : FirebaseRecyclerAdapter<FriendlyMessage, MainActivity.MessageViewHolder>(

        FriendlyMessage::class.java,
        R.layout.item_message,
        MainActivity.MessageViewHolder::class.java,
        FirebaseDatabase.getInstance().reference.child("messages")) {

    private lateinit var firebaseDatabaseReference: DatabaseReference
    private lateinit var firebaseAdapter: FirebaseRecyclerAdapter<FriendlyMessage, MainActivity.MessageViewHolder>
    private lateinit var linearLayoutManager: LinearLayoutManager

    companion object {  // Equivalent to static variables in Java

        private val TAG = "RecyclerViewAdapter"
        val MESSAGES_CHILD = "messages"
        private val REQUEST_INVITE = 1
        private val REQUEST_IMAGE = 2
        val DEFAULT_MSG_LENGTH_LIMIT = 10
        val ANONYMOUS = "anonymous"
        private val MESSAGE_SENT_EVENT = "message_sent"
        val MESSAGE_URL = "http://friendlychat.firebase.google.com/message/"
        private val LOADING_IMAGE_URL = "https://www.google.com/images/spin-32.gif"
    }

    class Child : AppCompatActivity() {}


        override fun populateViewHolder(
                viewHolder: MainActivity.MessageViewHolder,
                friendlyMessage: FriendlyMessage,
                position: Int) {

            progressBar.visibility = ProgressBar.INVISIBLE
            if (friendlyMessage.text.isNotEmpty()) {

                messageTextView.text = friendlyMessage.text
                messageTextView.visibility = TextView.VISIBLE
                messageImageView.visibility = ImageView.GONE

            } else {
                val imageUrl = friendlyMessage.imageUrl
                if (imageUrl.startsWith("gs://")) {
                    val storageReference = FirebaseStorage.getInstance()
                            .getReferenceFromUrl(imageUrl)
                    storageReference.downloadUrl.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val downloadUrl = task.result.toString()
                            Glide.with(messageImageView.context)
                                    .load(downloadUrl)
                                    .into(messageImageView)
                        } else {
                            Log.w(MainActivity.TAG, "Getting download url was not successful.",
                                    task.exception)
                        }
                    }
                } else {
                    Glide.with(messageImageView.context)
                            .load(friendlyMessage.imageUrl)
                            .into(messageImageView)
                }
                messageImageView.visibility = ImageView.VISIBLE
                messageTextView.visibility = TextView.GONE
            }

            messengerTextView.text = friendlyMessage.name
            if (friendlyMessage.photoUrl.isNotEmpty()) {
                messengerImageView.setImageDrawable(ContextCompat.getDrawable(this@RecyclerViewAdapter,
                        R.drawable.ic_account_circle_black_36dp))
            } else {
                Glide.with(this@RecyclerViewAdapter)
                        .load(friendlyMessage.photoUrl)
                        .into(messengerImageView)
            }

            if (friendlyMessage.text.isNotEmpty()) {
                // write this message to the on-device index
                FirebaseAppIndex.getInstance().update(getMessageIndexable(friendlyMessage))
            }

            // log a view action on it
            FirebaseUserActions.getInstance().end(getMessageViewAction(friendlyMessage))

            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.

        }



    private fun getMessageViewAction(friendlyMessage: FriendlyMessage): Action {
        return Action.Builder(Action.Builder.VIEW_ACTION)
                .setObject(friendlyMessage.name, RecyclerViewAdapter.MESSAGE_URL + friendlyMessage.id)
                .setMetadata(Action.Metadata.Builder().setUpload(false))
                .build()
    }

    private fun getMessageIndexable(friendlyMessage: FriendlyMessage, username: String): Indexable {
        val sender = Indexables.personBuilder()
                .setIsSelf(username == friendlyMessage.name)
                .setName(friendlyMessage.name)
                .setUrl(RecyclerViewAdapter.MESSAGE_URL + (friendlyMessage.id + "/sender"))

        val recipient = Indexables.personBuilder()
                .setName(username)
                .setUrl(RecyclerViewAdapter.MESSAGE_URL + (friendlyMessage.id + "/recipient"))

        val messageToIndex = Indexables.messageBuilder()
                .setName(friendlyMessage.text)
                .setUrl(RecyclerViewAdapter.MESSAGE_URL + friendlyMessage.id)
                .setSender(sender)
                .setRecipient(recipient)
                .build()

        return messageToIndex
    }

}