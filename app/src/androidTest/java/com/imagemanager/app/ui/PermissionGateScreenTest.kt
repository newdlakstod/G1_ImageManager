package com.imagemanager.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.imagemanager.app.ui.permissions.PermissionGateScreen
import org.junit.Rule
import org.junit.Test

class PermissionGateScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsMediaPermissionButton_whenPermissionNotYetGranted() {
        composeRule.setContent {
            PermissionGateScreen(onGranted = {})
        }

        composeRule.onNodeWithText("사진/동영상 접근 허용").assertExists()
    }
}
