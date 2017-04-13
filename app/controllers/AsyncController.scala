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
class AsyncController @Inject()(implicit actorSystem: ActorSystem, exec: ExecutionContext, materializer: Materializer) extends Controller {

  def ws: WebSocket = WebSocket.accept[JsValue, JsValue] { request =>
    ActorFlow.actorRef(out => Props(new SocketActor(out)))
  }

  class SocketActor(out: ActorRef) extends Actor {

    implicit val timeout = Timeout(5 seconds)

    private val game = actorSystem.actorSelection(s"user/game").resolveOne
      .recover { case e =>
        Logger.debug("creating new Game")
        actorSystem.actorOf(Game.props(),"game")
      }

    private val player = game.flatMap { _ ? out}.mapTo[ActorRef]

    override def receive: Receive = {
      case msg: JsValue if (msg \ "event").as[String] == "unroll" =>
        player.foreach {thePlayer =>
          game.foreach { theGame =>
            theGame ! Unroll(thePlayer)}}
      case msg: JsValue => game.foreach { _ ! msg }
    }

    override def postStop() = {
      Logger.debug("socket stopped")
      game.foreach { game => player.foreach { player => game ! Leftgame(player) } }
    }
  }

}

