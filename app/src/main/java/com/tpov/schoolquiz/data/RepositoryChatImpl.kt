package com.tpov.schoolquiz.data

import android.app.Application
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.tpov.schoolquiz.data.database.ChatDao
import com.tpov.schoolquiz.data.database.entities.ChatEntity
import com.tpov.schoolquiz.data.fierbase.Chat
import com.tpov.schoolquiz.data.fierbase.toChatEntity
import com.tpov.schoolquiz.domain.repository.RepositoryChat
import com.tpov.schoolquiz.presentation.DEFAULT_DATA_IN_GET_CHAT
import com.tpov.schoolquiz.presentation.DEFAULT_LOCAL_IN_GET_CHAT
import com.tpov.schoolquiz.presentation.core.*
import com.tpov.schoolquiz.presentation.core.Values.context
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class RepositoryChatImpl @Inject constructor(
    private val application: Application,
    private val chatDao: ChatDao
) : RepositoryChat {
    private lateinit var chatValueEventListener: ValueEventListener

    @OptIn(DelicateCoroutinesApi::class)
    override fun getChatFlow(): Flow<List<ChatEntity>> {
        getPersonalMassage()
        val chatRef = FirebaseDatabase.getInstance().getReference(PATH_CHAT)
        val dateFormatKiev = SimpleDateFormat(DEFAULT_DATA_IN_GET_CHAT)
        dateFormatKiev.timeZone = TimeZone.getTimeZone(DEFAULT_LOCAL_IN_GET_CHAT)

        var countSmsDoNotWatch = 0
        Values.loadText.postValue(context.getString(com.tpov.schoolquiz.R.string.fb_load_text_load_chat))
        chatValueEventListener =
            chatRef.limitToLast(20).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    log("getChatData snapshot: $snapshot")

                    var lastTime = SharedPreferencesManager.getTimeMassage()
                    for (dateSnapshot in snapshot.children) {
                        for (data in dateSnapshot.children) {
                            try {
                                val chat = data.getValue(Chat::class.java)
                                val date1 = dateFormatKiev.parse(chat?.time.toString())

                                if (chat != null) {

                                    if (lastTime == "0") {
                                        lastTime = System.currentTimeMillis().toString()
                                        SharedPreferencesManager.setTimeMassage(lastTime)
                                    }
                                    if (chat != null) {
                                        if (lastTime == "0") lastTime =
                                            System.currentTimeMillis().toString()
                                        val date2 =
                                            if (lastTime != null) Date(lastTime.toLong()) else null

                                        if (date2 != null && date1.after(date2)) {
                                            SharedPreferencesManager.setTimeMassage(date1.time.toString())
                                            chatDao.insertChat(chat.toChatEntity())
                                            countSmsDoNotWatch++
                                        }
                                    }
                                }

                            } catch (e: Exception) {
                                log("Error: ${e.message}")
                            }
                        }
                    }
                    Values.loadText.value = context.getString(com.tpov.schoolquiz.R.string.fb_load_text_load_new_massage, countSmsDoNotWatch)
                }

                override fun onCancelled(error: DatabaseError) {
                    // Обработка ошибок
                }
            })

        return chatDao.getChat()
    }

    override fun remoteChatListener() {
        val chatRef = FirebaseDatabase.getInstance().getReference(PATH_CHAT)
        chatRef.removeEventListener(chatValueEventListener)
    }

    private fun getPersonalMassage() {
        val chatRef =
            FirebaseDatabase.getInstance().getReference("$PATH_TRANSLATE/${SharedPreferencesManager.getTpovId()}")
        chatRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (dateSnapshot in snapshot.children) {
                    for (idQuizSnapshot in dateSnapshot.children) {
                        for (numQuestion in idQuizSnapshot.children) {
                            for (languageSnap in numQuestion.children) {
                                for (chatSnap in languageSnap.children) {
                                    val chat = dateSnapshot.getValue(Chat::class.java)
                                    chatDao.insertChat(chat!!.toChatEntity())
                                }
                            }
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
            }
        })
    }

    @OptIn(InternalCoroutinesApi::class)
    fun log(m: String) {
        Logcat.log(m, "RepositoryChatImpl", Logcat.LOG_DATABASE)
    }
}