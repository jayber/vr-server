package controllers

import akka.actor.{Actor, ActorRef, Props}
import controllers.Game.Broadcast


object Player {
  def props(userId: String, out: ActorRef) = Props(new Player(userId, out))
}

class Player(userId: String, out: ActorRef) extends Actor {
  override def receive: Receive = {
    case Broadcast(msg) => out ! msg
  }
}
