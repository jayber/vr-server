package controllers

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import akka.util.Timeout
import controllers.Game.{Broadcast, Leftgame, Moderator, Unroll}
import play.api.Logger
import play.api.libs.json.{JsValue, Json}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.util.Random

object Game {
  def props(spaceId: String) = Props(new Game(spaceId))

  case class Broadcast(jsonValue: JsValue)
  case class Unroll(player: ActorRef)
  case class Leftgame(player: ActorRef)

  case class Moderator(player: ActorRef)
}

class Game(spaceId: String) extends Actor {

  implicit private val timeout = Timeout(5 seconds)
  implicit private val exec = context.dispatcher

  private val events = mutable.Queue[Broadcast]()
  private val scoreIndex: Int = Random.nextInt(3)
  private var moderators = ArrayBuffer[ActorRef]()

  context.system.scheduler.schedule(Duration.create(0, "second"), Duration.create(30, "second"), self, Broadcast(Json.obj("event" -> "ping")))

  private def broadcastReload: Broadcast = {
    Broadcast(Json.obj("event" -> "reload", "data" -> Json.obj("index" -> scoreIndex)))
  }

  override def receive: Receive = {
    case (userId: String, out: ActorRef) =>
      Logger.debug(s"game $spaceId making player: $userId")
      val player = context.actorOf(Player.props(userId, out), s"user-$userId")
      sender() ! player
    case Moderator(player) =>
      moderators += player
      self ! Broadcast(Json.obj("event" -> "moderatorPresent"))
    case Unroll(player) =>
      player ! broadcastReload
      events.foreach {
      player ! _
    }
    case Leftgame(player) =>
      Logger.debug("a player left the game")
      if (moderators.contains(player)) {
        moderators -= player
        if (moderators.isEmpty) {
          self ! Broadcast(Json.obj("event" -> "moderatorAbsent"))
        }
      }
      if (context.children.size == 1) {
        Logger.debug("game taking the pill")
        self ! PoisonPill
      }
      player ! PoisonPill
    case msg: JsValue =>
      val broadcast = Broadcast(msg)
      events.enqueue(broadcast)
      context.children.foreach {
        _ ! broadcast
      }
      Logger.debug(s"game $spaceId broadcasting ${Json.stringify(msg)} - size: ${events.size}")
    case message@Broadcast(_) =>
      context.children.foreach {
        _ ! message
      }
  }
}
