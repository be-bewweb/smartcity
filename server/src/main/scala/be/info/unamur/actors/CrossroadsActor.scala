package be.info.unamur.actors

import akka.actor.{ActorRef, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import be.info.unamur.messages._
import be.info.unamur.utils.FailureSpreadingActor
import com.phidgets.InterfaceKitPhidget
import org.joda.time.{DateTime, Seconds}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

/** This actor handles the behaviour of the crossroads. It controls all the sub-actors needed by the crossroads.
  *
  * @author jeremyduchesne
  * @author Quentin Lombat
  */
class CrossroadsActor(ik: InterfaceKitPhidget) extends FailureSpreadingActor {
  val logger: Logger = LoggerFactory.getLogger(getClass)

  // The TrafficLightsActor that handles the two traffic lights of the main road of the model.
  val trafficLightsMainActor     : ActorRef = context.actorOf(Props(new TrafficLightsActor(ik, 0, 1)), name = "trafficLightsMainActor")
  // The TrafficLightsActor that handles the two traffic lights of the auxiliary road of the model.
  val trafficLightsAuxiliaryActor: ActorRef = context.actorOf(Props(new TrafficLightsActor(ik, 2, 3)), name = "trafficLightsAuxiliaryActor")

  // The PedestrianTrafficLightActor that handles the two pedestrian passages located on the auxiliary road of the model.
  val pedestrianCrossingActor: ActorRef = context.actorOf(Props(new PedestrianTrafficLightActor(ik, 4)), name = "pedestrianCrossingActor")

  // The two MainRoadCarDetectorActors that handle the detection sensors on each side of the main road of the model.
  val mainCarDetectorActor1: ActorRef = context.actorOf(Props(new MainRoadCarDetectorActor(ik, 0)), name = "mainCarDetectorActor1")
  val mainCarDetectorActor2: ActorRef = context.actorOf(Props(new MainRoadCarDetectorActor(ik, 1)), name = "mainCarDetectorActor2")

  // The two AuxiliaryCarDetectorActors that handle the detection sensors on each side of the auxiliary road of the model.
  val auxiliaryCarDetectorActor1: ActorRef = context.actorOf(Props(new AuxiliaryCarDetectorActor(ik, 2)), name = "auxiliaryCarDetectorActor1")
  val auxiliaryCarDetectorActor2: ActorRef = context.actorOf(Props(new AuxiliaryCarDetectorActor(ik, 3)), name = "auxiliaryCarDetectorActor2")

  // The two PedestrianTouchActors that handle the touch sensors on each side of the auxiliary road of the model.
  //val pedestrianTouchDetectorActor1: ActorRef = context.actorOf(Props(new PedestrianTouchActor(ik, 6)), name = "pedestrianTouchDetectorActor1")
  //val pedestrianTouchDetectorActor2: ActorRef = context.actorOf(Props(new PedestrianTouchActor(ik, 7)), name = "pedestrianTouchDetectorActor2")

  // Timeout for the asked messages to some actors.
  implicit val timeout = Timeout(5 seconds)

  // A DateTime variable that checks if the last time the two traffic lights have switched was not enough long ago.
  var timeOfLastAuxiliaryGreenLight: DateTime = _

  // A DateTime variable that checks if the last time the two pedestrians crossroads have been green was not enough long ago.
  var timeOfLastPedestrianGreenLight: DateTime = _

  // The last time the OpenAuxiliary Message has been received.
  var lastOpenAuxiliaryMessage: DateTime = _

  //the last time the Pedestrian Message has been received.
  var lastPedestrianMessage: DateTime = _


  override def receive: Receive = {

    /*
     * Initializes all the sub-actors.
     */
    case Initialize() =>
      val initTrafficLightsMainActor = trafficLightsMainActor ? Initialize()
      val initTrafficLightsAuxiliaryActor = trafficLightsAuxiliaryActor ? Initialize()
      val initPedestrianCrossingActor = pedestrianCrossingActor ? Initialize()
      val initMainRoadCarDetectorActor1 = mainCarDetectorActor1 ? Initialize()
      val initMainRoadCarDetectorActor2 = mainCarDetectorActor2 ? Initialize()
      val initAuxiliaryCarDetectorActor1 = auxiliaryCarDetectorActor1 ? Initialize()
      val initAuxiliaryCarDetectorActor2 = auxiliaryCarDetectorActor2 ? Initialize()
      //val initPedestrianTouchDetectorActor1 = pedestrianTouchDetectorActor1 ? Initialize()
      //val initPedestrianTouchDetectorActor2 = pedestrianTouchDetectorActor2 ? Initialize()
      timeOfLastAuxiliaryGreenLight = new DateTime()
      timeOfLastPedestrianGreenLight = new DateTime()
      lastOpenAuxiliaryMessage = new DateTime()
      lastPedestrianMessage = new DateTime()

      val results = for {
        resultInitTrafficLightsMainActor <- initTrafficLightsMainActor
        resultInitTrafficLightsAuxiliaryActor <- initTrafficLightsAuxiliaryActor
        resultInitPedestrianCrossingActor <- initPedestrianCrossingActor
        resultInitMainRoadCarDetectorActor1 <- initMainRoadCarDetectorActor1
        resultInitMainRoadCarDetectorActor2 <- initMainRoadCarDetectorActor2
        resultInitAuxiliaryCarDetectorActor1 <- initAuxiliaryCarDetectorActor1
        resultInitAuxiliaryCarDetectorActor2 <- initAuxiliaryCarDetectorActor2
      //resultInitPedestrianTouchDetectorActor1 <- initPedestrianTouchDetectorActor1
      //resultInitPedestrianTouchDetectorActor2 <- initPedestrianTouchDetectorActor2
      } yield (resultInitTrafficLightsMainActor,
        resultInitTrafficLightsAuxiliaryActor,
        resultInitPedestrianCrossingActor,
        resultInitMainRoadCarDetectorActor1,
        resultInitMainRoadCarDetectorActor2,
        resultInitAuxiliaryCarDetectorActor1,
        resultInitAuxiliaryCarDetectorActor2
        //resultInitPedestrianTouchDetectorActor1,
        //resultInitPedestrianTouchDetectorActor2
      )

      results pipeTo sender


    /*
     * Sets a basic situation on the model. Opens the main road and closes the auxiliary one.
     */
    case Start() =>
      trafficLightsMainActor ! SetGreen()
      trafficLightsAuxiliaryActor ! SetRed()
      pedestrianCrossingActor ! SetOn()
      mainCarDetectorActor1 ! Start()
      mainCarDetectorActor2 ! Start()
      auxiliaryCarDetectorActor1 ! Start()
      auxiliaryCarDetectorActor2 ! Start()
    //pedestrianTouchDetectorActor1 ! Start()
    //pedestrianTouchDetectorActor2 ! Start()

    /*
   * When the detection sensors located on the auxiliary road are triggered, closes the main road and opens the auxiliary one.
   */
    case OpenAuxiliary() =>

      /* Once a carDetected in Auxiliary, turn off listener to not receive too much request "OpenAuxiliary". These listener
         will be turned on again when the red light will be on. */
      auxiliaryCarDetectorActor1 ! Stop()
      auxiliaryCarDetectorActor2 ! Stop()

      //If there is no car on the main road, no need to wait the entire usual waiting time.
      val requestMainCarDetector1 = mainCarDetectorActor1 ? MainCarDetected()
      val requestMainCarDetector2 = mainCarDetectorActor2 ? MainCarDetected()

      val results: Future[(Any, Any)] = for {
        resultMainCarDetection1 <- requestMainCarDetector1
        resultMainCarDetection2 <- requestMainCarDetector2
      } yield (resultMainCarDetection1, resultMainCarDetection2)

      Await.result(results, (CrossroadsActor.waitingTimeForFuture * 1000) millis) match {
        // It there is not traffic jam, Auxiliary just waits the regulatory time
        case (false, false) =>
          if (timeOfLastAuxiliaryGreenLight.getSecondOfDay + CrossroadsActor.differenceBetweenGreenAuxiliaryTrafficLights < new DateTime().getSecondOfDay) {
            openAuxiliary()
          } else {
            Thread sleep switchTheLights(timeOfLastAuxiliaryGreenLight, CrossroadsActor.differenceBetweenGreenAuxiliaryTrafficLights)
            openAuxiliary()
          }
        // It there is traffic jam, Auxiliary waits more time to let the MainRoad progress.
        case (true, _) | (_, true) =>
          if (timeOfLastAuxiliaryGreenLight.getSecondOfDay + CrossroadsActor.differenceBetweenGreenAuxiliaryTrafficLights < new DateTime().getSecondOfDay) {
            Thread sleep (CrossroadsActor.waitingTimeWhenTrafficJam * 1000)
            openAuxiliary()
          } else {
            Thread sleep switchTheLights(timeOfLastAuxiliaryGreenLight, CrossroadsActor.differenceBetweenGreenAuxiliaryTrafficLights)
            openAuxiliary()
          }
      }


    /*
     * When the touch sensors are triggered, closes the auxiliary road if it is opened, opens the main road, and let the pedestrians pass.
     */
    case Pedestrian() =>
      // Checks if the request has been sent few minutes ago. If yes, does not add it to the message queue.
      if (lastPedestrianMessage.getSecondOfDay + CrossroadsActor.minimumTimeSinceLastRequest < new DateTime().getSecondOfDay) {
        lastPedestrianMessage = DateTime.now()
        Thread sleep switchTheLights(timeOfLastPedestrianGreenLight, CrossroadsActor.differenceBetweenGreenPedestrianCrossRoads)
        timeOfLastPedestrianGreenLight = DateTime.now()
        trafficLightsAuxiliaryActor ! SetRed()
        Thread sleep 4000
        pedestrianCrossingActor ! SetOn()
        trafficLightsMainActor ! SetGreen()
      }

    /*
     * Stops all the sub-actors.
     */
    case Stop() =>
      val stopTrafficLightsMainActor = trafficLightsMainActor ? Stop()
      val stopTrafficLightsAuxiliaryActor = trafficLightsAuxiliaryActor ? Stop()
      val stopPedestrianCrossingActor = pedestrianCrossingActor ? Stop()
      val stopMainRoadCarDetectorActor1 = mainCarDetectorActor1 ? Stop()
      val stopMainRoadCarDetectorActor2 = mainCarDetectorActor2 ? Stop()
      val stopAuxiliaryCarDetectorActor1 = auxiliaryCarDetectorActor1 ? Stop()
      val stopAuxiliaryCarDetectorActor2 = auxiliaryCarDetectorActor2 ? Stop()
      //val stopPedestrianTouchDetectorActor1 = pedestrianTouchDetectorActor1 ? Stop()
      //val stopPedestrianTouchDetectorActor2 = pedestrianTouchDetectorActor2 ? Stop()

      val results = for {
        resultStopTrafficLightsMainActor <- stopTrafficLightsMainActor
        resultStopTrafficLightsAuxiliaryActor <- stopTrafficLightsAuxiliaryActor
        resultStopPedestrianCrossingActor <- stopPedestrianCrossingActor
        resultStopMainRoadCarDetectorActor1 <- stopMainRoadCarDetectorActor1
        resultStopMainRoadCarDetectorActor2 <- stopMainRoadCarDetectorActor2
        resultStopAuxiliaryCarDetectorActor1 <- stopAuxiliaryCarDetectorActor1
        resultStopAuxiliaryCarDetectorActor2 <- stopAuxiliaryCarDetectorActor2
      //resultStopPedestrianTouchDetectorActor1 <- stopPedestrianTouchDetectorActor1
      //resultStopPedestrianTouchDetectorActor2 <- stopPedestrianTouchDetectorActor2
      } yield (resultStopTrafficLightsMainActor,
        resultStopTrafficLightsAuxiliaryActor,
        resultStopPedestrianCrossingActor,
        resultStopMainRoadCarDetectorActor1,
        resultStopMainRoadCarDetectorActor2,
        resultStopAuxiliaryCarDetectorActor1,
        resultStopAuxiliaryCarDetectorActor2
        //resultStopPedestrianTouchDetectorActor1,
        //resultStopPedestrianTouchDetectorActor2
      )

      results pipeTo sender
  }

  /*
   * Handles the switch between the main road and the auxiliary one.
   */
  def openAuxiliary(): Unit = {
    pedestrianCrossingActor ! SetOff()
    Thread sleep 3000
    trafficLightsMainActor ! SetRed()
    Thread sleep 2000
    trafficLightsAuxiliaryActor ! SetGreen()
    Thread sleep 20000
    trafficLightsAuxiliaryActor ! SetRed()
    Thread sleep 2000
    trafficLightsMainActor ! SetGreen()
    pedestrianCrossingActor ! SetOn()

    timeOfLastAuxiliaryGreenLight = DateTime.now()

    // Turn on listeners
    auxiliaryCarDetectorActor1 ! Start()
    auxiliaryCarDetectorActor2 ! Start()
  }

  /** Return the time the system has to wait before switching the lights on.
    *
    * @param timeOfLast        The last time the lights were switched on.
    * @param differenceBetween The minimum difference between two switches.
    * @return The remaining time before the lights switch on, in milliseconds.
    */
  def switchTheLights(timeOfLast: DateTime, differenceBetween: Long): Long = {
    val currentDifference: Integer = Seconds.secondsBetween(timeOfLastAuxiliaryGreenLight, new DateTime).getSeconds
    if (currentDifference > differenceBetween) {
      0
    } else {
      (differenceBetween - currentDifference) * 1000
    }
  }
}

/** Companion object for the AuxiliaryCarDetectorActor
  *
  * @author Justin SIRJACQUES
  */
object CrossroadsActor {
  /* Constants */

  // The minimum time since the last time the auxiliary trafficlights has been switched on. (seconds)
  val differenceBetweenGreenAuxiliaryTrafficLights = 1

  // The minimum time since the last time the pedestrians could cross the road. (seconds)
  val differenceBetweenGreenPedestrianCrossRoads = 15

  // The minimum time for the system to accept another similar message. (seconds)
  val minimumTimeSinceLastRequest = 10

  // The time auxiliaryStreet has to wait more if there is traffic jam in main street. (seconds)
  val waitingTimeWhenTrafficJam = 5

  // The waiting time for Future results. (seconds)
  val waitingTimeForFuture = 5
}