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

import android.content.ActivityNotFoundException
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
import com.danefinlay.ttsutil.R
import org.jetbrains.anko.find
import org.jetbrains.anko.longToast

class MainActivity : SpeakerActivity(), ObservableFileChooser {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private var chosenFileObservers = mutableSetOf<ChosenFileObserver>()

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
                R.id.nav_read_clipboard, R.id.nav_settings), drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
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
                openSystemTTSSettings()
                true
            }

            R.id.menu_reinitialise_tts -> {
                // Reinitialise the Speaker object.
                myApplication.reinitialiseSpeaker(this, null)
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

    override fun onDestroy() {
        super.onDestroy()

        // Free the speaker only if the activity (and probably the application)
        // is finishing.
        if (isFinishing) {
            myApplication.freeSpeaker()
        }
    }

    override fun showFileChooser() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "text/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            // TODO Allow processing of multiple text files.
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        }

        try {
            val title = getString(R.string.file_chooser_title)
            startActivityForResult(
                    Intent.createChooser(intent, title),
                    FILE_SELECT_CODE)
        } catch (ex: ActivityNotFoundException) {
            // Potentially direct the user to the Market with a Dialog.
            longToast(getString(R.string.no_file_manager_msg))
        }
    }

    override fun addObserver(observer: ChosenFileObserver) {
        chosenFileObservers.add(observer)
    }

    override fun deleteObserver(observer: ChosenFileObserver) {
        chosenFileObservers.remove(observer)
    }

    override fun notifyObservers(uri: Uri) {
        chosenFileObservers.forEach { it.onFileChosen(uri) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_SELECT_CODE && resultCode == RESULT_OK) {
            // Get the Uri of the selected file and pass it on.
            val uri = data?.data
            if (uri != null) notifyObservers(uri)
        }
    }

    companion object {
        private const val FILE_SELECT_CODE = 5
    }
}
