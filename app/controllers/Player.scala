package controllers

import akka.actor.{Actor, ActorRef, Props}
import controllers.Game.Broadcast
import play.api.Logger
import play.api.libs.json.Json

import scala.concurrent.duration.Duration


object Player {
  def props(out: ActorRef) = Props(new Player(out))
}

class Player(out: ActorRef) extends Actor {

  context.system.scheduler.schedule(Duration.create(0,"second"),Duration.create(30,"second"),out,Json.obj("event" -> "ping"))(context.system.dispatcher)

  override def receive: Receive = {
    case Broadcast(msg) => out ! msg
      Logger.debug("broadcasting" + Json.stringify(msg))
  }
}
