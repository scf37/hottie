package me.scf37.hottie.impl

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

import me.scf37.filewatch.FileWatcherEvent

/**
  * Deduplicate watch events over given interval
  *
  * @param listener will be called on every deduplicated event
  * @param deduplicator deduplicate events to seq of target type T
  * @param onError if error happened
  * @param timer timer to use to schedule deduplication
  * @param dedupFlushDelayMs deduplication interval, collected events are flushed over this interval
  * @tparam T
  */
class WatchEventDedup[T](
  listener: T => Unit,
  deduplicator: Seq[FileWatcherEvent] => Seq[T],
  onError: Throwable => Unit = e => e.printStackTrace(),
  timer: ScheduledExecutorService = WatchEventDedup.defaultExecutor,
  dedupFlushDelayMs: Int = 100
) extends (FileWatcherEvent => Unit) {

  var events = Vector.empty[FileWatcherEvent]
  var isFlushScheduled = false

  private[this] def scheduleFlush() = {
    isFlushScheduled = true

    timer.schedule(new Runnable {
      override def run(): Unit = {
        synchronized {
          isFlushScheduled = false
        }

        val copy = synchronized {
          val copy = events
          events = Vector.empty
          copy
        }

        try {
          if (copy.nonEmpty) {
            deduplicator(copy).foreach(listener)
          }
        } catch {
          case e: Throwable => onError(e)
        }
      }
    }, dedupFlushDelayMs.toLong, TimeUnit.MILLISECONDS)
  }

  override def apply(event: FileWatcherEvent): Unit = synchronized {
    events = events :+ event
    if (!isFlushScheduled) {
      scheduleFlush()
    }
  }
}

object WatchEventDedup {
  private lazy val defaultExecutor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory {
    override def newThread(r: Runnable): Thread = new Thread(r) {
      setDaemon(true)
      setName("filewatch-dedup-thread")
    }
  })
}