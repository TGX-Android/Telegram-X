/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.demo.shortform

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import androidx.media3.demo.shortform.viewpager.ViewPagerActivity
import java.lang.Integer.max
import java.lang.Integer.min

class MainActivity : AppCompatActivity() {

  @androidx.annotation.OptIn(UnstableApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    var numberOfPlayers = 3
    val numPlayersFieldView = findViewById<EditText>(R.id.num_players_field)
    numPlayersFieldView.addTextChangedListener(
      object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: Editable) {
          val newText = numPlayersFieldView.text.toString()
          if (newText != "") {
            numberOfPlayers = max(1, min(newText.toInt(), 5))
          }
        }
      }
    )

    findViewById<View>(R.id.view_pager_button).setOnClickListener {
      startActivity(
        Intent(this, ViewPagerActivity::class.java).putExtra(NUM_PLAYERS_EXTRA, numberOfPlayers)
      )
    }
  }

  companion object {
    const val NUM_PLAYERS_EXTRA = "number_of_players"
  }
}
