plugins {
    id("com.android.library")
    id("module-plugin")
}

dependencies {
    implementation(project(":tdlib"))
    implementation(project(":vkryl:core"))
}