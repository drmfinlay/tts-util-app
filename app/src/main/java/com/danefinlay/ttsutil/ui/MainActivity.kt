/*
 * TTS Util
 *
 * Authors: Dane Finlay <dane@danefinlay.net>
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

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import com.google.android.material.navigation.NavigationView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.danefinlay.ttsutil.R

class MainActivity : TTSActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration

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
                R.id.nav_read_text, R.id.nav_read_files,
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
        when (item.itemId) {
            R.id.menu_about -> {
                val activity = AboutActivity::class.java
                startActivity(Intent(this, activity))
                return true
            }

            R.id.menu_tts_settings -> {
                myApplication.openSystemTTSSettings(this)
                return true
            }

            R.id.menu_reinitialise_tts -> {
                // Reinitialise text-to-speech, if idle or if sample text is being
                //  read out.
                val sampleInProgress: Boolean =
                    myApplication.currentTask?.getInputSource(this)?.description ==
                            getString(R.string.sample_text_source_description)
                if (!myApplication.taskInProgress || sampleInProgress) {
                    myApplication.reinitialiseTTS(this, null)
                    return true
                }

                // If a task is in progress, build and show an alert dialog
                //  asking the user for confirmation.
                AlertDialog.Builder(this).apply {
                    setTitle(R.string.reinit_confirmation_title)
                    setMessage(getString(R.string.reinit_confirmation_message))
                    setPositiveButton(R.string.alert_positive_message_2) {
                        _: DialogInterface, _: Int ->
                        myApplication.reinitialiseTTS(this@MainActivity,null)
                    }
                    setNegativeButton(R.string.alert_negative_message_2) {
                        _: DialogInterface, _: Int ->
                    }
                    show()
                }
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) ||
                super.onSupportNavigateUp()
    }

    override fun handleActivityEvent(event: ActivityEvent) {
        // If this is a status update event, save it.
        if (event is ActivityEvent.StatusUpdateEvent) {
            mLastStatusUpdate = event
        }

        val fragmentId = R.id.nav_host_fragment
        val navHostFragment = supportFragmentManager.findFragmentById(fragmentId)
        val fragments = navHostFragment?.childFragmentManager?.fragments
        if (fragments != null) handleActivityEvent(event, fragments)
    }

    override fun onDestroy() {
        super.onDestroy()

        // Do a few things if this activity, (and, therefore, probably, the
        // application,) is finishing.
        if (isFinishing) {
            // Free TTS resources and clean up files.
            myApplication.freeTTS()
            myApplication.cleanupFiles()
        }
    }
}
