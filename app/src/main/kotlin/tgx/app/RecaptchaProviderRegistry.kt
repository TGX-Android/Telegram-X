package tgx.app

import com.google.android.recaptcha.RecaptchaTasksClient
import me.vkryl.core.lambda.RunnableData

data class PostponedTask(
  val keyId: String,
  val actor: RunnableData<RecaptchaTasksClient>,
  val onError: RunnableData<Exception>
)

object RecaptchaProviderRegistry {
  private var context: RecaptchaContext? = null
  private val map: MutableMap<String, RecaptchaContext> = mutableMapOf()
  private val postponedTasks: ArrayDeque<PostponedTask> = ArrayDeque()

  fun addProvider(context: RecaptchaContext) {
    this.context = context
    val key = context.recaptchaKeyId.takeIf { !it.isNullOrEmpty() } ?: ""
    map[key] = context
    executePostponedTasks()
  }

  fun execute(keyId: String, actor: RunnableData<RecaptchaTasksClient>, onError: RunnableData<Exception>) {
    postponedTasks.addLast(PostponedTask(keyId, actor, onError))
    executePostponedTasks()
  }

  private fun getContext(keyId: String): RecaptchaContext? {
    return map[keyId] ?: context?.let {
      if (it.recaptchaKeyId == keyId) {
        it
      } else {
        val modifiedContext = RecaptchaContext(it.application, keyId)
        map[keyId] = modifiedContext
        modifiedContext
      }
    }
  }

  private fun executePostponedTasks() {
    while (postponedTasks.isNotEmpty()) {
      val task = postponedTasks.first()
      val context = getContext(task.keyId) ?: break
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