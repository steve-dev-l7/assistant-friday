package com.example.friday

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter (private val messages: List<Message>): RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object{
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_BOT = 2
    }

    override fun getItemViewType(position: Int): Int {

        return if(messages[position].isFromBot) VIEW_TYPE_BOT else VIEW_TYPE_USER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        return  if( viewType == VIEW_TYPE_BOT){
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_bot, parent, false)
            BotViewHolder(view)
        }else{
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_user, parent, false)
            UserViewHolder(view)
        }

    }



    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

        val message = messages[position]

        if (holder is BotViewHolder) {
            holder.botMessageText.text = message.text
            holder.botMessageTime.text = message.time
        } else if (holder is UserViewHolder) {
            holder.userMessageText.text = message.text
            holder.userMessageTime.text = message.time
        }

    }

    override fun getItemCount(): Int =  messages.size

    class BotViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val botMessageText: TextView = view.findViewById(R.id.botMessageText)
        val botMessageTime: TextView = view.findViewById(R.id.botMessageTime)
    }

    // ViewHolder for User
    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userMessageText: TextView = view.findViewById(R.id.userMessageText)
        val userMessageTime: TextView = view.findViewById(R.id.userMessageTime)
    }

}