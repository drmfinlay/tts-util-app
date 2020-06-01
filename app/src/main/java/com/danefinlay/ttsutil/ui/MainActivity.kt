/*
 * TTS Util
 *
 * Authors: Dane Finlay <Danesprite@posteo.net>
 *
 * Copyright (C) 2019 Dane Finlay
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.danefinlay.ttsutil.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.widget.DrawerLayout
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.danefinlay.ttsutil.APP_NAME
import com.danefinlay.ttsutil.R
import com.danefinlay.ttsutil.getDisplayName
import org.jetbrains.anko.ctx
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.find

class MainActivity : SpeakerActivity(), FileChooser {

    private lateinit var appBarConfiguration: AppBarConfiguration

    override var chooseFileAction = Intent.ACTION_OPEN_DOCUMENT
    override var chooseFileCategory = Intent.CATEGORY_OPENABLE
    override var chooseFileMimeType = "text/*"

    var fileToRead: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set up the toolbar.
        val toolbar = find<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Set up the drawer + navigation view and controller.
        val drawerLayout = find<DrawerLayout>(R.id.drawer_layout)
        val navView = find<NavigationView>(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(setOf(
                R.id.nav_read_text, R.id.nav_read_files, R.id.nav_write_files,
                R.id.nav_read_clipboard), drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        if (savedInstanceState == null) {
            // Restore fileToRead from shared preferences.
            val prefs = ctx.getSharedPreferences(ctx.packageName, MODE_PRIVATE)
            val uriString = prefs.getString(CHOSEN_FILE_URI_KEY, "")
            fileToRead = Uri.parse(uriString)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_about -> {
                val activity = AboutActivity::class.java
                startActivity(Intent(this, activity))
                true
            }

            R.id.menu_tts_settings -> {
                // Got this from: https://stackoverflow.com/a/8688354
                val intent = Intent()
                intent.action = "com.android.settings.TTS_SETTINGS"
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                true
            }

            R.id.menu_reinitialise_tts -> {
                // Reinitialise the Speaker object.
                myApplication.freeSpeaker()
                myApplication.startSpeaker(this)
                true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) ||
                super.onSupportNavigateUp()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)

        // Save instance state here.
        outState?.putParcelable("fileToRead", fileToRead)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)

        val uri: Uri? = savedInstanceState?.getParcelable("fileToRead")
        fileToRead = uri
    }

    override fun onDestroy() {
        super.onDestroy()

        // Free the speaker only if the activity (and probably the application)
        // is finishing.
        if (isFinishing) {
            myApplication.freeSpeaker()
        }
    }

    override fun onFileChosen(uri: Uri?) {
        // Set the shared preference values asynchronously.
        fileToRead = uri
        doAsync {
            val prefs = ctx.getSharedPreferences(ctx.packageName, MODE_PRIVATE)
            prefs.edit()
                    .putString(CHOSEN_FILE_URI_KEY, uri?.toString() ?: "")
                    .putString(CHOSEN_FILE_NAME_KEY,
                            uri?.getDisplayName(ctx) ?: "")
                    .apply()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super<FileChooser>.onActivityResult(requestCode, resultCode, data)
        super<SpeakerActivity>.onActivityResult(requestCode, resultCode, data)
    }

    companion object {
        private const val CHOSEN_FILE_URI_KEY = "$APP_NAME.CHOSEN_FILE_URI_KEY"
        const val CHOSEN_FILE_NAME_KEY = "$APP_NAME.CHOSEN_FILE_NAME_KEY"
    }
}
