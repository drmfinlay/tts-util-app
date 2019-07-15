package com.danefinlay.ttsutil

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.FileObserver
import android.os.IBinder
import kotlin.concurrent.thread

class MyService : Service() {
    private val notificationId by lazy { (System.currentTimeMillis() / 1000).toInt() }

    private val speaker: Speaker by lazy { Speaker(this@MyService, true) }

    @Volatile
    private var fileWatcherThread: Thread? = null

    private val fileObserver: FileObserver? by lazy {
        val path = uriAbsFilePath
        if ( path != null ) {
            object : FileObserver(path, FileObserver.MODIFY) {
                override fun onEvent(event: Int, path: String?) {
                    speakFromFile()
                    fileUriLastModifiedPrefValue = fileUriLastModified
                }
            }
        } else null
    }

    private val fileWatcherThreadBlock = {
        while ( true ) {
            try {
                val (exceptional, fileAccessible) = handleFileUriExceptions {
                    val fileUriLastModified = fileUriLastModified

                    // Only speak if the file has been updated.
                    if ( fileUriLastModified == null ) false
                    else if ( fileUriLastModified != fileUriLastModifiedPrefValue ) {
                        speakFromFile()
                        fileUriLastModifiedPrefValue = fileUriLastModified
                        true
                    } else true
                }

                if ( exceptional || fileAccessible != null && !fileAccessible ) {
                    stopSelf()
                    break
                }

                Thread.sleep(100)
            } catch (t: InterruptedException) {
                speaker.stopSpeech() // Stop speaking if necessary
                break
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize the lazy speaker and file observer so there is no unnecessary delay
        speaker
        fileObserver?.startWatching()

        // Set up the polling thread to watch the file Uri
        fileWatcherThread = thread(block = fileWatcherThreadBlock)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // The service is starting, due to a call to startService()
        startForeground(notificationId, getNotification())

        return START_STICKY
    }

    override fun onDestroy() {
        // Free the resources of the Speaker instance
        speaker.free()

        // Tell the file watcher thread to stop
        fileWatcherThread?.interrupt()

        super.onDestroy()
    }

    fun restartFileWatcher() {
        fileWatcherThread?.interrupt()
        fileWatcherThread = thread(isDaemon = true, block = fileWatcherThreadBlock)
    }

    fun speakFromFile() {
        // TODO Synchronise access to this function so that the file observer and
        // URI watcher thread don't ever read the same thing twice
        // TODO Possibly reschedule if already speaking old text from file.
        synchronized(speaker) {
            speaker.onReady = {
                // Text to speech ready!
                val lines = uriContent
                if ( lines != null ) speak(lines)

                // TODO Clear the file after it is read using an input stream on the
                // URI or on a file object if there is an absolute file path available
            }
        }
    }

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    inner class LocalBinder : Binder() {
        val service: MyService
            get() = this@MyService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent): IBinder? = binder

    private fun getNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
                .addFlags(START_ACTIVITY_FLAGS)
                .setData(Uri.parse("notificationId:$notificationId"))

        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)
        val notificationTitle = "Text to speech daemon running"
        val notificationText = "Touch to open the app"

        val notificationBuilder = Notification.Builder(this)
                .setContentTitle(notificationTitle)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentText(notificationText)
                .setContentIntent(pendingIntent)
                .setTicker(notificationTitle)

        // Build and add notification actions for the new notification
        val notificationActions = buildNotificationActions()
        notificationActions.forEach { notificationBuilder.addAction(it) }

        return notificationBuilder.build()
    }

    private fun buildNotificationActions(): List<Notification.Action> {
        // Set a value for the default PendingIntent flags to use
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_ONE_SHOT

        // Create an intent and pending intent for the stopping this application
        val stopAppIntent = Intent(this, MyControlService::class.java)
                .setAction(MyControlService.STOP_APP)
                .setData(Uri.parse("notificationId:$notificationId"))

        val stopAppPendingIntent = PendingIntent.getService(this, 0, stopAppIntent, flags)

        // Return a List of built notification actions
        return arrayOf(
                Notification.Action.Builder(
                        android.R.drawable.ic_delete,
                        getString(R.string.stop_daemon_text), stopAppPendingIntent
                )
        ).map { it.build() }
    }
}
