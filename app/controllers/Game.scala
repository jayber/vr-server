package controllers

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import akka.util.Timeout
import controllers.Game._
import play.api.Logger
import play.api.libs.json.{JsValue, Json}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.util.Random

object Game {
  def props(spaceId: String) = Props(new Game(spaceId))

  case class Broadcast(jsonValue: JsValue)

  case class Ping(jsonValue: JsValue)

  case class Unroll(player: ActorRef)

  case class Leftgame(player: ActorRef)

  case class Moderator(player: ActorRef)

}

class Game(spaceId: String) extends Actor {

  implicit private val timeout = Timeout(5 seconds)
  implicit private val exec = context.dispatcher

  private val initialReload: Broadcast = Broadcast(Json.obj("event" -> "reload", "data" -> Json.obj("index" -> Random.nextInt(5))))
  private val events = mutable.Queue[Broadcast](initialReload)
  private val moderators = ArrayBuffer[ActorRef]()

  private var startStopRegister: Option[Broadcast] = None
  private var discoModeRegister: Option[Broadcast] = None
  private var moderatorsRegister: Option[Broadcast] = None
  private var clearRegister: Option[Broadcast] = None
  private var freeForAllRegister: Option[Broadcast] = None
  private var bpmList: List[Broadcast] = List()
  private val playTriggerMap: mutable.Map[(Int, Int), Broadcast] = mutable.Map()

  context.system.scheduler.schedule(Duration.create(0, "second"), Duration.create(30, "second"), self, Ping(Json.obj("event" -> "ping")))


  override def receive: Receive = {
    case (userId: String, out: ActorRef) =>
      Logger.debug(s"game $spaceId making player: $userId")
      val player = context.actorOf(Player.props(userId, out), s"user-$userId")
      sender() ! player
    case Moderator(player) =>
      moderators += player
      if (moderators.size == 1) {
        self ! Json.obj("event" -> "moderatorPresent")
      }
    case Unroll(player) =>
      events.foreach {
        player ! _
      }
    case Leftgame(player) =>
      Logger.debug("a player left the game")
      if (moderators.contains(player)) {
        moderators -= player
        if (moderators.isEmpty) {
          self ! Json.obj("event" -> "moderatorAbsent")
        }
      }
      if (context.children.size == 1) {
        Logger.debug(s"$spaceId taking the pill")
        self ! PoisonPill
      }
      player ! PoisonPill
    case msg: JsValue =>
      val broadcast = Broadcast(msg)
      consolidateWithQueue(broadcast)
      self ! broadcast
    case message@Broadcast(content) =>
      Logger.debug(s"$spaceId broadcasting ${Json.stringify(content)} - size: ${events.size}; players: ${context.children.size}")
      context.children.foreach {
        _ ! message
      }
    case message@Ping(content) => // this only exists so as not to log all the pings
      val broadcast = Broadcast(content)
      context.children.foreach {
        _ ! broadcast
      }
  }

  private def consolidateWithQueue(broadcast: Broadcast): Unit = {
    (broadcast.jsonValue \ "event").as[String] match {
      case "start" =>
        startStopRegister = keepLastOnly(startStopRegister, broadcast)
      case "stop" =>
        startStopRegister = keepLastOnly(startStopRegister, broadcast)
      case "clear" =>
        clearRegister = keepLastOnly(clearRegister, broadcast)
      case "incrementBpm" =>
        bpmList = balance(bpmList, broadcast)
      case "decrementBpm" =>
        bpmList = balance(bpmList, broadcast)
      case "discoModeOn" =>
        discoModeRegister = keepLastOnly(discoModeRegister, broadcast)
      case "discoModeOff" =>
        discoModeRegister = keepLastOnly(discoModeRegister, broadcast)
      case "moderatorPresent" =>
        moderatorsRegister = keepLastOnly(moderatorsRegister, broadcast)
      case "moderatorAbsent" =>
        moderatorsRegister = keepLastOnly(moderatorsRegister, broadcast)
      case "freeForAllOn" =>
        freeForAllRegister = keepLastOnly(freeForAllRegister, broadcast)
      case "freeForAllOff" =>
        freeForAllRegister = keepLastOnly(freeForAllRegister, broadcast)
      case "addPlayTrigger" =>
        balanceTriggers(broadcast)
      case "removePlayTrigger" =>
        balanceTriggers(broadcast)
      case "reload" =>
        events.clear()
        discoModeRegister.foreach {
          events.enqueue(_)
        }
        moderatorsRegister.foreach {
          events.enqueue(_)
        }
        freeForAllRegister.foreach {
          events.enqueue(_)
        }
        events.enqueue(broadcast)
      case _ =>
        events.enqueue(broadcast)
    }
  }

  private def balanceTriggers(broadcast: Broadcast): Unit = {
    val key = ((broadcast.jsonValue \ "data" \ "instrumentNumber").as[Int], (broadcast.jsonValue \ "data" \ "count").as[Int])
    val option = playTriggerMap.get(key)
    if (option.isDefined) {
      events.dequeueFirst { elem => elem == option.head }
      playTriggerMap.remove(key)
    } else {
      events.enqueue(broadcast)
      playTriggerMap(key) = broadcast
    }
  }

  private def balance(list: List[Broadcast], broadcast: Broadcast): List[Broadcast] = {
    if (list.isEmpty || (list.head.jsonValue \ "event") == broadcast.jsonValue \ "event") {
      events.enqueue(broadcast)
      list :+ broadcast
    } else {
      events.dequeueFirst { elem => elem == list.head }
      list.tail
    }
  }

  private def keepLastOnly(previousElement: Option[Broadcast], broadcast: Broadcast): Option[Broadcast] = {
    if (previousElement.isDefined) {
      events.dequeueFirst { elem => elem == previousElement.get }
    }
    events.enqueue(broadcast)
    Some(broadcast)
  }
}
