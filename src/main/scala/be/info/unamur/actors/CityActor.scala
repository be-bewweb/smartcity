package be.info.unamur.actors

import akka.actor.{ActorRef, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import be.info.unamur.messages.{Initialize, Start, Stop}
import be.info.unamur.utils.FailureSpreadingActor
import com.phidgets.InterfaceKitPhidget
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Master actor that controls the other city sub-actors.
  *
  * @author jeremyduchesne
  * @author Noé Picard
  */
class CityActor extends FailureSpreadingActor {
  val logger: Logger = LoggerFactory.getLogger(getClass)

  val ik = new InterfaceKitPhidget()

  val crossroadsActor: ActorRef = context.actorOf(Props(new CrossroadsActor(ik)), name = "crossroadsActor")
  val parkingActor   : ActorRef = context.actorOf(Props(new ParkingActor()), name = "parkingActor")

  // To know if the city is already stopped
  var stopped: Boolean = true

  // Timeout for the ask messages to some actors
  implicit val timeout = Timeout(5 seconds)


  override def receive: Receive = {
    case Initialize() =>
      if (stopped) {
        ik openAny()
        ik waitForAttachment()

        val initCrossroads = crossroadsActor ? Initialize()
        val initParking = parkingActor ? Initialize()

        val results = for {
          resultInitCrossroads <- initCrossroads
          resultInitParking <- initParking
        } yield (resultInitCrossroads, resultInitParking)

        Thread.sleep(2000)

        crossroadsActor ! Start()

        results pipeTo sender

        stopped = false
      }


    case Stop() =>
      if (!stopped) {
        val stopCrossroads = crossroadsActor ? Stop()
        val stopParking = parkingActor ? Stop()

        val results = for {
          resultStopCrossroads <- stopCrossroads
          resultStopParking <- stopParking
        } yield (resultStopCrossroads, resultStopParking)

        results pipeTo sender

        stopped = true
      }
  }
}
