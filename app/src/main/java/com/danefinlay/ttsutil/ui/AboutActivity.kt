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

import android.os.Bundle
import androidx.core.content.pm.PackageInfoCompat
import android.view.MenuItem
import android.widget.TextView
import com.danefinlay.ttsutil.APP_NAME
import com.danefinlay.ttsutil.R
import org.jetbrains.anko.find

class AboutActivity : MyAppCompatActivity() {

    private fun setAckText(ackTextViewId: Int, ackStringId: Int,
                           ackLinkStringId: Int, licenceLinkText: String) {
        val formattedAckText = getString(ackStringId,
                getString(ackLinkStringId), licenceLinkText)
        find<TextView>(ackTextViewId).setLinkText(formattedAckText)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // The following code was adapted from FreeOTP source code:
        // https://freeotp.github.io/
        // Set the app version text.
        val info = packageManager.getPackageInfo(APP_NAME, 0)
        val longVersion = PackageInfoCompat.getLongVersionCode(info)
        find<TextView>(R.id.about_version).text = getString(
                R.string.about_version, info.versionName, longVersion)

        // Set the license text and link.
        val apache2 = getString(R.string.link_apache2)
        val sourceLink = getString(R.string.link_source_code)
        val aboutApp = getString(R.string.about_app, apache2, sourceLink)
        find<TextView>(R.id.about_app).setLinkText(aboutApp)

        // Set each acknowledgement text and link.
        setAckText(R.id.about_ack_material_design_icons,
                R.string.about_ack_material_design_icons,
                R.string.link_material_design_icons, apache2)
        setAckText(R.id.about_ack_kotlin, R.string.about_ack_kotlin,
                R.string.link_kotlin, apache2)
        setAckText(R.id.about_ack_anko, R.string.about_ack_anko,
                R.string.link_anko, apache2)
        setAckText(R.id.about_ack_free_otp, R.string.about_ack_free_otp,
                R.string.link_freeotp, apache2)

        // Set links under the Translations heading.
        val contribLink = getString(R.string.link_mahongyin)
        val linkText = getString(R.string.about_translations_chinese, contribLink)
        find<TextView>(R.id.about_translations_chinese).setLinkText(linkText)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when ( item.itemId ) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
