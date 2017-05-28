package controllers

import akka.actor.{Actor, ActorRef, Props}
import controllers.Game.Broadcast


object Player {
  def props(userId: String, displayName: String, out: ActorRef) = Props(new Player(userId, displayName, out))
}

class Player(userId: String, displayName: String, out: ActorRef) extends Actor {
  override def receive: Receive = {
    case Broadcast(msg) => out ! msg
  }
}
