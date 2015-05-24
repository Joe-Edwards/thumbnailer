package thumbnailer

import akka.event.LoggingAdapter

class SimpleLogger(loggingFn: String => Unit = println) extends LoggingAdapter {
  override def isErrorEnabled: Boolean = true
  override def isInfoEnabled: Boolean = true
  override def isDebugEnabled: Boolean = true
  override def isWarningEnabled: Boolean = true

  override protected def notifyError(message: String): Unit = loggingFn(message)
  override protected def notifyError(cause: Throwable, message: String): Unit = loggingFn(message)
  override protected def notifyInfo(message: String): Unit = loggingFn(message)
  override protected def notifyWarning(message: String): Unit = loggingFn(message)
  override protected def notifyDebug(message: String): Unit = loggingFn(message)
}
