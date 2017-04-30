package controllers

import javax.inject._

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import controllers.Game.{Leftgame, Unroll}
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.streams.ActorFlow
import play.api.mvc._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

@Singleton
class SocketController @Inject()(implicit actorSystem: ActorSystem, exec: ExecutionContext, materializer: Materializer) extends Controller {

  def ws(spaceId: String, userId: String): WebSocket = WebSocket.accept[JsValue, JsValue] { request =>
    Logger.debug(s"space: $spaceId; user: $userId")
    ActorFlow.actorRef(out => Props(new SocketActor(out, spaceId, userId)))
  }

  class SocketActor(out: ActorRef, spaceId: String, userId: String) extends Actor {

    implicit val timeout = Timeout(5 seconds)

    private val game = actorSystem.actorSelection(s"user/game-$spaceId").resolveOne
      .recover { case e =>
        Logger.debug(s"creating new Game: $spaceId")
        actorSystem.actorOf(Game.props(spaceId), s"game-$spaceId")
      }

    private val player = game.flatMap {
      _ ? (userId, out)
    }.mapTo[ActorRef]

    override def receive: Receive = {
      case player: ActorRef =>
      case msg: JsValue if (msg \ "event").as[String] == "unroll" =>
        game.zip(player).foreach { tuple => tuple._1 ! Unroll(tuple._2) }
        player.foreach { p => Logger.debug(s"unrolling: $p") }
      case msg: JsValue => game.foreach { _ ! msg }
    }

    override def postStop() = {
      Logger.debug("socket stopped")
      game.zip(player).foreach { tuple => tuple._1 ! Leftgame(tuple._2) }
    }
  }
}

