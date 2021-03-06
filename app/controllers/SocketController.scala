package controllers

import javax.inject._

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import controllers.Game.{Leftgame, Moderator, Unroll}
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.streams.ActorFlow
import play.api.mvc._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

@Singleton
class SocketController @Inject()(implicit actorSystem: ActorSystem, exec: ExecutionContext, materializer: Materializer) extends Controller {

  def ws(spaceId: String, userId: String, displayName: String): WebSocket = WebSocket.accept[JsValue, JsValue] { request =>
    Logger.debug(s"space: $spaceId; user: $userId; username: $displayName")
    ActorFlow.actorRef(out => Props(new PlayerIn(out, spaceId, userId, displayName)))
  }

  class PlayerIn(out: ActorRef, spaceId: String, userId: String, displayName: String) extends Actor {

    implicit val timeout = Timeout(5 seconds)

    private val game = actorSystem.actorSelection(s"user/game-$spaceId").resolveOne
      .recover { case e =>
        Logger.debug(s"creating new Game: $spaceId")
        actorSystem.actorOf(Game.props(spaceId), s"game-$spaceId")
      }

    private val player = game.flatMap {
      _ ? (userId, displayName, out)
    }.mapTo[ActorRef]

    override def receive: Receive = {
      case msg: JsValue if (msg \ "event").as[String] == "moderatorPresent" =>
        game.zip(player).foreach { gamePlayerTuple => gamePlayerTuple._1 ! Moderator(gamePlayerTuple._2) }
      case msg: JsValue if (msg \ "event").as[String] == "unroll" =>
        game.zip(player).foreach { tuple => tuple._1 ! Unroll(tuple._2) }
        Logger.debug(s"unrolling: $userId:$displayName")
      case msg: JsValue => game.foreach { _ ! msg }
    }

    override def postStop() = {
      Logger.debug("socket stopped")
      game.zip(player).foreach { tuple => tuple._1 ! Leftgame(tuple._2) }
    }
  }
}

