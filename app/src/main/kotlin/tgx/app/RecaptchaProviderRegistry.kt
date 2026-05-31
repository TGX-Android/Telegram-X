package tgx.app

import android.app.Application
import com.google.android.recaptcha.RecaptchaTasksClient
import me.vkryl.core.lambda.RunnableData

data class PostponedTask(
  val keyId: String,
  val actor: RunnableData<RecaptchaTasksClient>,
  val onError: RunnableData<Exception>
)

object RecaptchaProviderRegistry {
  private var application: Application? = null
  private val map: MutableMap<String, RecaptchaContext> = mutableMapOf()
  private val postponedTasks: ArrayDeque<PostponedTask> = ArrayDeque()

  @JvmStatic
  fun setApplication(application: Application) {
    this.application = application
    executePostponedTasks()
  }

  @JvmStatic
  fun execute(keyId: String, actor: RunnableData<RecaptchaTasksClient>, onError: RunnableData<Exception>) {
    postponedTasks.addLast(PostponedTask(keyId, actor, onError))
    executePostponedTasks()
  }

  private fun getContext(siteKey: String): RecaptchaContext {
    return map[siteKey] ?: application?.let {
      val modifiedContext = RecaptchaContext(it, siteKey)
      map[siteKey] = modifiedContext
      modifiedContext
    } ?: error("Not initialized.")
  }

  private fun executePostponedTasks() {
    while (application != null && postponedTasks.isNotEmpty()) {
      val task = postponedTasks.first()
      val context = getContext(task.keyId)
      postponedTasks.removeFirst()
      context.initialize()
      context.withClient { client, exception ->
        if (client != null) {
          task.actor.runWithData(client)
        } else {
          task.onError.runWithData(exception)
        }
      }
    }
  }
}