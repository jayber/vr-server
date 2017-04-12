package controllers

import akka.actor.{Actor, ActorRef, Props}
import controllers.Game.{Broadcast, Unroll}
import play.api.Logger
import play.api.libs.json.{JsValue, Json}

import scala.collection.mutable

object Game {
  def props() = Props(new Game())

  case class Broadcast(jsonValue: JsValue)
  case class Unroll(player: ActorRef)
}

class Game() extends Actor {

  val events = mutable.Queue[Broadcast]()

  override def receive: Receive = {
    case out: ActorRef =>
      Logger.debug("making player")
      val player = context.actorOf(Player.props(out))
      sender() ! player
      context.children.filterNot {_ == player}.foreach {
        _ ! Broadcast(Json.obj("event" -> "message", "data" -> "a new player has entered"))
      }
    case Unroll(player) => events.foreach { player ! _}
    case msg: JsValue =>
      val broadcast = Broadcast(msg)
      events.enqueue(broadcast)
      context.children.foreach {
        _ ! broadcast
      }
  }
}
