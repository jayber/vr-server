package controllers

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import akka.util.Timeout
import controllers.Game.{Broadcast, Leftgame, Unroll}
import play.api.Logger
import play.api.libs.json.{JsValue, Json}

import scala.collection.mutable
import scala.concurrent.duration._

object Game {
  def props(spaceId: String) = Props(new Game(spaceId))

  case class Broadcast(jsonValue: JsValue)
  case class Unroll(player: ActorRef)
  case class Leftgame(player: ActorRef)
}

class Game(spaceId: String) extends Actor {

  implicit private val timeout = Timeout(5 seconds)
  implicit private val exec = context.dispatcher

  private val events = mutable.Queue[Broadcast]()

  context.system.scheduler.schedule(Duration.create(0, "second"), Duration.create(30, "second"), self, Broadcast(Json.obj("event" -> "ping")))

  override def receive: Receive = {
    case (userId: String, out: ActorRef) =>
      Logger.debug(s"making player: $userId")
      val player = context.actorOf(Player.props(userId, out), s"user-$userId")
      sender() ! player
    case Unroll(player) =>
      player ! Broadcast(Json.obj("event" -> "reload"))
      events.foreach {
      player ! _
    }
    case Leftgame(player) =>
      Logger.debug("a player left the game")
      if (context.children.size == 1) {
        Logger.debug("game taking the pill")
        self ! PoisonPill
      }
      player ! PoisonPill
    case msg: JsValue =>
      Logger.debug("broadcasting" + Json.stringify(msg))
      val broadcast = Broadcast(msg)
      events.enqueue(broadcast)
      context.children.foreach {
        _ ! broadcast
      }
    case message@Broadcast(_) =>
      context.children.foreach {
        _ ! message
      }
  }
}
