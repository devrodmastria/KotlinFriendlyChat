package com.arbormoon.kotlinfriendlychat

import android.support.v4.content.ContextCompat
import android.util.Log
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.bumptech.glide.Glide
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.google.firebase.appindexing.Action
import com.google.firebase.appindexing.FirebaseAppIndex
import com.google.firebase.appindexing.FirebaseUserActions
import com.google.firebase.appindexing.Indexable
import com.google.firebase.appindexing.builders.Indexables
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
import kotlinx.android.synthetic.main.item_message.view.*

/**
 * Created by rodrigom on 8/18/17.
 */

open class RecyclerViewAdapter<T, U> : FirebaseRecyclerAdapter<FriendlyMessage, MainActivity.MessageViewHolder>(

        FriendlyMessage::class.java,
        R.layout.item_message,
        MainActivity.MessageViewHolder::class.java,
        FirebaseDatabase.getInstance().reference.child("messages")) {

    companion object {  // Equivalent to static variables in Java
        val MESSAGE_URL = "http://friendlychat.firebase.google.com/message/"
    }

    override fun populateViewHolder(viewHolder: MainActivity.MessageViewHolder,
                                    friendlyMessage: FriendlyMessage,
                                    position: Int) {

        if (friendlyMessage.text.isNotEmpty()) {

            viewHolder.recyclerView.messageTextView.text = friendlyMessage.text
            viewHolder.recyclerView.messageTextView.visibility = TextView.VISIBLE
            viewHolder.recyclerView.messageImageView.visibility = ImageView.GONE

        } else {
            val imageUrl = friendlyMessage.imageUrl
            if (imageUrl.startsWith("gs://")) {
                val storageReference = FirebaseStorage.getInstance()
                        .getReferenceFromUrl(imageUrl)
                storageReference.downloadUrl.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val downloadUrl = task.result.toString()
                        Glide.with(viewHolder.recyclerView.messageImageView.context)
                                .load(downloadUrl)
                                .into(viewHolder.recyclerView.messageImageView)
                    } else {
                        Log.w(MainActivity.TAG, "Getting download url was not successful.",
                                task.exception)
                    }
                }
            } else {
                Glide.with(viewHolder.recyclerView.messageImageView.context)
                        .load(friendlyMessage.imageUrl)
                        .into(viewHolder.recyclerView.messageImageView)
            }
            viewHolder.recyclerView.messageImageView.visibility = ImageView.VISIBLE
            viewHolder.recyclerView.messageTextView.visibility = TextView.GONE
        }

        viewHolder.recyclerView.messengerTextView.text = friendlyMessage.name
        if (friendlyMessage.photoUrl.isNotEmpty()) {
            viewHolder.recyclerView.messengerImageView.setImageDrawable(ContextCompat.getDrawable(viewHolder.recyclerView.context,
                    R.drawable.ic_account_circle_black_36dp))
        } else {
            Glide.with(viewHolder.recyclerView.context)
                    .load(friendlyMessage.photoUrl)
                    .into(viewHolder.recyclerView.messengerImageView)
        }

        if (friendlyMessage.text.isNotEmpty()) {
            // write this message to the on-device index
            FirebaseAppIndex.getInstance().update(getMessageIndexable(friendlyMessage, MainActivity.ANONYMOUS))
        }

        // log a view action on it
        FirebaseUserActions.getInstance().end(getMessageViewAction(friendlyMessage))
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