package tgx.test

import org.junit.Test

class AuthorizedLaunchTest : AuthorizedTest() {
  @Test
  fun chatsListOpens() {
    launchApplication()
    device.waitForNavigation()
    device.waitForElement("chats_list")
  }
}

class UnauthorizedLaunchTest : UnauthorizedTest() {
  @Test
  fun introOpens() {
    launchApplication()
    device.waitForNavigation()
    device.waitForElement("btn_startMessaging")
  }
}
